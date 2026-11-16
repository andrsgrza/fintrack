package com.fintrack.app.service.budget;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetRevision;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.service.analytics.*;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compiles an effective immutable Budget revision into FINTRACK's one canonical analytics query and its separate
 * visualization context. It has no transaction repository and intentionally performs no financial calculation.
 */
@Service
@Transactional(readOnly = true)
public class BudgetSemanticQueryCompiler {

    private final SemanticAnalyticsValidator validator;
    private final Clock clock;

    public BudgetSemanticQueryCompiler(SemanticAnalyticsValidator validator, Clock clock) {
        this.validator = validator;
        this.clock = clock;
    }

    public BudgetSemanticQueryCompilation compile(Budget budget, BudgetRevision revision, BudgetPeriod period, ZoneId ownerZone) {
        if (
            budget == null ||
            budget.getId() == null ||
            budget.getUser() == null ||
            budget.getUser().getLogin() == null ||
            budget.getUser().getLogin().isBlank()
        ) {
            return failed(BudgetSemanticCompilationFailure.UNAUTHORIZED_OWNER_CONTEXT, "The Budget has no authoritative owner.");
        }
        if (budget.getType() == null || budget.getCurrency() == null) {
            return failed(BudgetSemanticCompilationFailure.INCOMPLETE_CONFIGURATION, "The Budget type and currency are required.");
        }
        if (revision == null || revision.getBudget() == null || !sameBudget(budget, revision.getBudget())) {
            return failed(BudgetSemanticCompilationFailure.INVALID_EFFECTIVE_PERIOD, "The revision does not belong to this Budget.");
        }
        if (
            period == null ||
            period.startInclusive() == null ||
            period.endExclusive() == null ||
            !period.startInclusive().isBefore(period.endExclusive())
        ) {
            return failed(
                BudgetSemanticCompilationFailure.INVALID_EFFECTIVE_PERIOD,
                "The Budget period must have an inclusive start and exclusive end."
            );
        }
        if (ownerZone == null) {
            return failed(BudgetSemanticCompilationFailure.INVALID_EFFECTIVE_PERIOD, "A resolved owner time zone is required.");
        }

        LocalDate ownerToday = LocalDate.now(clock.withZone(ownerZone));
        LocalDate through = period.endExclusive().minusDays(1);
        if (period.startInclusive().isAfter(ownerToday)) {
            return failed(
                BudgetSemanticCompilationFailure.INVALID_EFFECTIVE_PERIOD,
                "Future Budget periods are not yet eligible for financial evaluation."
            );
        }
        if (through.isAfter(ownerToday)) through = ownerToday;

        AccountScope accountScope = accounts(budget, revision);
        if (accountScope.failure() != null) return accountScope.failure();
        List<SemanticPredicate> predicates = new ArrayList<>(economicPredicates(budget.getType()));
        BudgetSemanticQueryCompilation.Failed categoryFailure = categoryPredicate(budget, revision, predicates);
        if (categoryFailure != null) return categoryFailure;
        BudgetSemanticQueryCompilation.Failed tagFailure = tagPredicate(budget, revision, predicates);
        if (tagFailure != null) return tagFailure;

        String alias = budget.getType() == BudgetType.EXPENSE ? "economic_expense" : "economic_income";
        SemanticQueryDefinition definition = new SemanticQueryDefinition(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticDataset.TRANSACTION_LEDGER,
            SemanticField.TRANSACTION_DATE,
            List.of(new SemanticAggregateProjection(alias, SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT)),
            List.copyOf(predicates),
            List.of(),
            List.of(),
            SemanticAnalyticsSchema.DEFAULT_LIMIT
        );
        VisualizationContext context = new VisualizationContext(
            SemanticAnalyticsSchema.VERSION,
            new VisualizationTime("ABSOLUTE_RANGE", null, period.startInclusive(), through),
            accountScope.accountIds(),
            List.of(budget.getCurrency().name())
        );
        try {
            definition = validator.normalizeDefinitionOrThrow(definition);
        } catch (SemanticAnalyticsValidationException exception) {
            return failed(
                BudgetSemanticCompilationFailure.UNSUPPORTED_SEMANTIC_FILTER_COMBINATION,
                "The Budget scope cannot be represented by the canonical Analytics contract."
            );
        }
        SemanticAnalyticsExecutionRequest request = new SemanticAnalyticsExecutionRequest(
            SemanticAnalyticsSchema.VERSION,
            definition,
            context
        );
        return new BudgetSemanticQueryCompilation.Compiled(
            definition,
            context,
            request,
            SemanticAnalyticsTrustedOwnerContext.forBudget(budget, ownerToday)
        );
    }

    private AccountScope accounts(Budget budget, BudgetRevision revision) {
        if (revision.getAccountScopeMode() == BudgetAccountScopeMode.ALL) return new AccountScope(List.of(), null);
        if (revision.getAccountScopeMode() != BudgetAccountScopeMode.SELECTED || revision.getAccounts().isEmpty()) {
            return new AccountScope(
                List.of(),
                failed(BudgetSemanticCompilationFailure.INCOMPLETE_CONFIGURATION, "Selected Budget accounts are no longer available.")
            );
        }
        List<FinancialAccount> accounts = revision.getAccounts().stream().sorted(Comparator.comparing(FinancialAccount::getId)).toList();
        if (
            accounts
                .stream()
                .anyMatch(
                    account ->
                        account.getId() == null || account.getCurrency() != budget.getCurrency() || !sameOwner(budget, account.getUser())
                )
        ) {
            return new AccountScope(
                List.of(),
                failed(
                    BudgetSemanticCompilationFailure.MISSING_OR_INACCESSIBLE_REFERENCE,
                    "A selected Budget account is missing, inaccessible, or has an incompatible currency."
                )
            );
        }
        return new AccountScope(accounts.stream().map(FinancialAccount::getId).toList(), null);
    }

    private BudgetSemanticQueryCompilation.Failed categoryPredicate(
        Budget budget,
        BudgetRevision revision,
        List<SemanticPredicate> predicates
    ) {
        if (revision.getCategoryScopeMode() == BudgetCategoryScopeMode.ALL) return null;
        if (revision.getCategoryScopeMode() != BudgetCategoryScopeMode.SELECTED || revision.getCategories().isEmpty()) {
            return failed(BudgetSemanticCompilationFailure.INCOMPLETE_CONFIGURATION, "Selected Budget categories are no longer available.");
        }
        List<Category> categories = revision.getCategories().stream().sorted(Comparator.comparing(Category::getId)).toList();
        if (
            categories
                .stream()
                .anyMatch(
                    category ->
                        category.getId() == null ||
                        !sameOwner(budget, category.getUser()) ||
                        !compatibleCategory(category, budget.getType())
                )
        ) {
            return failed(
                BudgetSemanticCompilationFailure.MISSING_OR_INACCESSIBLE_REFERENCE,
                "A selected Budget category is missing, inaccessible, or incompatible."
            );
        }
        predicates.add(
            new SemanticPredicate(
                SemanticField.CATEGORY_ID,
                SemanticPredicateOperator.DESCENDANT_OF_ANY,
                categories.stream().map(Category::getId).map(String::valueOf).toList()
            )
        );
        return null;
    }

    private BudgetSemanticQueryCompilation.Failed tagPredicate(Budget budget, BudgetRevision revision, List<SemanticPredicate> predicates) {
        if (revision.getTagScopeMode() == BudgetTagScopeMode.NONE) return null;
        if (revision.getTagScopeMode() != BudgetTagScopeMode.SELECTED || revision.getTags().isEmpty()) {
            return failed(BudgetSemanticCompilationFailure.INCOMPLETE_CONFIGURATION, "Selected Budget tags are no longer available.");
        }
        List<Tag> tags = revision.getTags().stream().sorted(Comparator.comparing(Tag::getId)).toList();
        if (tags.stream().anyMatch(tag -> tag.getId() == null || !sameOwner(budget, tag.getUser()))) {
            return failed(
                BudgetSemanticCompilationFailure.MISSING_OR_INACCESSIBLE_REFERENCE,
                "A selected Budget tag is missing or inaccessible."
            );
        }
        SemanticPredicateOperator operator = revision.getTagMatchMode() == BudgetTagMatchMode.ALL
            ? SemanticPredicateOperator.HAS_ALL
            : SemanticPredicateOperator.HAS_ANY;
        predicates.add(new SemanticPredicate(SemanticField.TAG_ID, operator, tags.stream().map(Tag::getId).map(String::valueOf).toList()));
        return null;
    }

    private List<SemanticPredicate> economicPredicates(BudgetType type) {
        if (type == null) return List.of();
        return List.of(
            new SemanticPredicate(
                SemanticField.TRANSACTION_FLOW,
                SemanticPredicateOperator.EQUALS,
                List.of(type == BudgetType.EXPENSE ? "OUT" : "IN")
            ),
            new SemanticPredicate(SemanticField.TRANSFER_LINK_STATUS, SemanticPredicateOperator.EQUALS, List.of("UNLINKED"))
        );
    }

    private boolean compatibleCategory(Category category, BudgetType type) {
        return (
            category.getCategoryType() == CategoryType.BOTH ||
            (type == BudgetType.EXPENSE && category.getCategoryType() == CategoryType.EXPENSE) ||
            (type == BudgetType.INCOME && category.getCategoryType() == CategoryType.INCOME)
        );
    }

    private boolean sameBudget(Budget expected, Budget actual) {
        return expected == actual || (expected.getId() != null && expected.getId().equals(actual.getId()));
    }

    private boolean sameOwner(Budget budget, com.fintrack.app.domain.User user) {
        return user != null && budget.getUser().getLogin().equals(user.getLogin());
    }

    private BudgetSemanticQueryCompilation.Failed failed(BudgetSemanticCompilationFailure reason, String message) {
        return new BudgetSemanticQueryCompilation.Failed(reason, message);
    }

    private record AccountScope(List<Long> accountIds, BudgetSemanticQueryCompilation.Failed failure) {}
}
