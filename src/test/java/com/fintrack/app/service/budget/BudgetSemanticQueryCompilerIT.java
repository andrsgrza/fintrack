package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.*;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.*;
import com.fintrack.app.service.analytics.*;
import com.fintrack.app.web.rest.TestUtil;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL proof that Budget compiles immutable scope into the same validated semantic ledger contract used by the
 * Analytics API. Expected values are calculated directly from this fixture rather than copied from an API response.
 */
@IntegrationTest
@Transactional
@WithMockUser(username = "user")
class BudgetSemanticQueryCompilerIT {

    @Autowired
    private BudgetSemanticQueryCompiler compiler;

    @Autowired
    private SemanticAnalyticsExecutionService executionService;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private InternalTransferRepository transferRepository;

    @Autowired
    private EntityManager em;

    @Test
    void compiles_and_executes_the_same_economic_expense_query_as_analytics_without_double_counting_category_overlap() {
        Fixture fixture = fixture();
        Budget budget = budget(
            fixture.owner(),
            BudgetType.EXPENSE,
            BudgetAccountScopeMode.SELECTED,
            Set.of(fixture.debit()),
            BudgetCategoryScopeMode.SELECTED,
            // Household already includes Food; the union must still count the Food transaction only once.
            Set.of(fixture.household(), fixture.food()),
            BudgetTagScopeMode.SELECTED,
            Set.of(fixture.essential(), fixture.recurring()),
            BudgetTagMatchMode.ALL
        );
        BudgetRevision revision = budget.getRevisions().get(0);
        long transactionCountBefore = transactionRepository.count();

        BudgetSemanticQueryCompilation compilation = compiler.compile(
            budget,
            revision,
            new BudgetPeriod(0, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1)),
            ZoneId.of("America/Mexico_City")
        );

        assertThat(compilation).isInstanceOf(BudgetSemanticQueryCompilation.Compiled.class);
        BudgetSemanticQueryCompilation.Compiled compiled = (BudgetSemanticQueryCompilation.Compiled) compilation;
        assertThat(compiled.definition().predicates()).contains(
            new SemanticPredicate(SemanticField.TRANSACTION_FLOW, SemanticPredicateOperator.EQUALS, List.of("OUT")),
            new SemanticPredicate(SemanticField.TRANSFER_LINK_STATUS, SemanticPredicateOperator.EQUALS, List.of("UNLINKED")),
            new SemanticPredicate(
                SemanticField.CATEGORY_ID,
                SemanticPredicateOperator.DESCENDANT_OF_ANY,
                List.of(fixture.household().getId().toString(), fixture.food().getId().toString())
            ),
            new SemanticPredicate(
                SemanticField.TAG_ID,
                SemanticPredicateOperator.HAS_ALL,
                List.of(fixture.recurring().getId().toString(), fixture.essential().getId().toString())
            )
        );
        assertThat(compiled.context().accountIds()).containsExactly(fixture.debit().getId());
        assertThat(compiled.context().currencyCodes()).containsExactly("MXN");

        // Independent fixture arithmetic: only the MXN Food expense of 125 has both tags. Travel has one tag and
        // the linked transfer leg never qualifies as an economic expense.
        SemanticAnalyticsResult internal = executionService.executeForTrustedOwner(compiled.request(), compiled.trustedOwner());
        SemanticAnalyticsResult apiEquivalent = executionService.execute(compiled.request());
        assertThat(internal.rows())
            .singleElement()
            .satisfies(row -> {
                assertThat(row.currencyCode()).isEqualTo("MXN");
                assertThat(row.values())
                    .singleElement()
                    .satisfies(value -> {
                        assertThat(value.value()).isEqualByComparingTo("125");
                        assertThat(value.availability()).isEqualTo(SemanticResultAvailability.COMPLETE);
                    });
            });
        assertThat(apiEquivalent.rows()).isEqualTo(internal.rows());
        assertThat(transactionRepository.count()).isEqualTo(transactionCountBefore);
    }

    @Test
    void preserves_owner_isolation_for_trusted_budget_execution_even_when_another_user_is_authenticated() {
        Fixture fixture = fixture();
        User foreign = user("budget-private-owner", "budget-private-owner@example.invalid");
        FinancialAccount privateAccount = account(foreign, "Private", AccountType.DEBIT, CurrencyCode.MXN);
        Category privateCategory = category(foreign, "Private expenses", CategoryType.EXPENSE, null);
        transactionRepository.saveAndFlush(
            transaction(privateAccount, privateCategory, TransactionFlow.OUT, "73", LocalDate.of(2026, 2, 2))
        );
        Budget privateBudget = budget(
            foreign,
            BudgetType.EXPENSE,
            BudgetAccountScopeMode.ALL,
            Set.of(),
            BudgetCategoryScopeMode.ALL,
            Set.of(),
            BudgetTagScopeMode.NONE,
            Set.of(),
            BudgetTagMatchMode.ANY
        );

        BudgetSemanticQueryCompilation compilation = compiler.compile(
            privateBudget,
            privateBudget.getRevisions().get(0),
            new BudgetPeriod(0, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)),
            ZoneId.of("America/Mexico_City")
        );

        assertThat(compilation).isInstanceOf(BudgetSemanticQueryCompilation.Compiled.class);
        BudgetSemanticQueryCompilation.Compiled compiled = (BudgetSemanticQueryCompilation.Compiled) compilation;
        SemanticAnalyticsResult result = executionService.executeForTrustedOwner(compiled.request(), compiled.trustedOwner());
        assertThat(result.rows())
            .singleElement()
            .satisfies(row -> {
                assertThat(row.currencyCode()).isEqualTo("MXN");
                assertThat(row.values()).singleElement().satisfies(value -> assertThat(value.value()).isEqualByComparingTo("73"));
            });
        // The interactive user owns different records. Internal execution must not accidentally read them.
        assertThat(result.rows().get(0).values().get(0).value()).isNotEqualByComparingTo("125");
    }

    @Test
    void reports_empty_selected_scope_as_incomplete_instead_of_compiling_an_unrestricted_query() {
        Fixture fixture = fixture();
        Budget budget = budget(
            fixture.owner(),
            BudgetType.EXPENSE,
            BudgetAccountScopeMode.SELECTED,
            Set.of(),
            BudgetCategoryScopeMode.ALL,
            Set.of(),
            BudgetTagScopeMode.NONE,
            Set.of(),
            BudgetTagMatchMode.ANY
        );

        BudgetSemanticQueryCompilation compilation = compiler.compile(
            budget,
            budget.getRevisions().get(0),
            new BudgetPeriod(0, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)),
            ZoneId.of("America/Mexico_City")
        );

        assertThat(compilation).isEqualTo(
            new BudgetSemanticQueryCompilation.Failed(
                BudgetSemanticCompilationFailure.INCOMPLETE_CONFIGURATION,
                "Selected Budget accounts are no longer available."
            )
        );
    }

    private Fixture fixture() {
        User owner = TestUtil.findAll(em, User.class).stream().filter(user -> "user".equals(user.getLogin())).findFirst().orElseThrow();
        FinancialAccount debit = account(owner, "Budget debit", AccountType.DEBIT, CurrencyCode.MXN);
        FinancialAccount cash = account(owner, "Budget cash", AccountType.CASH, CurrencyCode.MXN);
        Category household = category(owner, "Household", CategoryType.EXPENSE, null);
        Category food = category(owner, "Food", CategoryType.EXPENSE, household);
        Category travel = category(owner, "Travel", CategoryType.EXPENSE, household);
        Tag recurring = tag(owner, "Recurring");
        Tag essential = tag(owner, "Essential");
        Tag travelTag = tag(owner, "Travel");

        FinancialTransaction foodExpense = transaction(debit, food, TransactionFlow.OUT, "125", LocalDate.of(2026, 1, 10));
        foodExpense.addTags(recurring).addTags(essential);
        transactionRepository.saveAndFlush(foodExpense);
        FinancialTransaction travelExpense = transaction(debit, travel, TransactionFlow.OUT, "50", LocalDate.of(2026, 2, 15));
        travelExpense.addTags(travelTag);
        transactionRepository.saveAndFlush(travelExpense);
        transactionRepository.saveAndFlush(transaction(debit, food, TransactionFlow.IN, "1000", LocalDate.of(2026, 1, 5)));
        FinancialTransaction outgoing = transaction(debit, null, TransactionFlow.OUT, "200", LocalDate.of(2026, 2, 20));
        FinancialTransaction incoming = transaction(cash, null, TransactionFlow.IN, "200", LocalDate.of(2026, 2, 20));
        transactionRepository.saveAndFlush(outgoing);
        transactionRepository.saveAndFlush(incoming);
        transferRepository.saveAndFlush(
            new InternalTransfer()
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .outgoingTransaction(outgoing)
                .incomingTransaction(incoming)
        );
        return new Fixture(owner, debit, household, food, recurring, essential);
    }

    private Budget budget(
        User owner,
        BudgetType type,
        BudgetAccountScopeMode accountMode,
        Set<FinancialAccount> accounts,
        BudgetCategoryScopeMode categoryMode,
        Set<Category> categories,
        BudgetTagScopeMode tagMode,
        Set<Tag> tags,
        BudgetTagMatchMode tagMatchMode
    ) {
        Budget budget = Budget.create(owner, "Budget", null, type, CurrencyCode.MXN, "budget-" + System.nanoTime(), "hash", Instant.now());
        budget.addInitialRevision(
            BudgetRevision.initial(
                new BudgetRevisionConfiguration(
                    new BigDecimal("500"),
                    1,
                    BudgetIntervalUnit.MONTH,
                    BudgetAlignment.NATURAL,
                    LocalDate.of(2026, 1, 1),
                    BudgetRecurrenceMode.RECURRING,
                    BudgetRecurrenceTermination.NONE,
                    null,
                    null,
                    accountMode,
                    categoryMode,
                    tagMode,
                    tagMatchMode
                ),
                accounts,
                categories,
                tags,
                Instant.now()
            )
        );
        return budgetRepository.saveAndFlush(budget);
    }

    private User user(String login, String email) {
        User user = new User();
        user.setLogin(login);
        user.setPassword("x".repeat(60));
        user.setActivated(true);
        user.setEmail(email);
        user.setLangKey("en");
        em.persist(user);
        em.flush();
        return user;
    }

    private FinancialAccount account(User owner, String name, AccountType type, CurrencyCode currency) {
        return accountRepository.saveAndFlush(
            new FinancialAccount()
                .name(name)
                .institutionName("Budget bank")
                .accountType(type)
                .currency(currency)
                .initialBalance(BigDecimal.ZERO)
                .initialBalanceDate(LocalDate.of(2025, 1, 1))
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .user(owner)
        );
    }

    private Category category(User owner, String name, CategoryType type, Category parent) {
        return categoryRepository.saveAndFlush(
            new Category()
                .name(name)
                .categoryType(type)
                .color("#225588")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .user(owner)
                .parentCategory(parent)
        );
    }

    private Tag tag(User owner, String name) {
        return tagRepository.saveAndFlush(
            new Tag().name(name).color("#225588").active(true).createdAt(Instant.now()).updatedAt(Instant.now()).user(owner)
        );
    }

    private FinancialTransaction transaction(
        FinancialAccount account,
        Category category,
        TransactionFlow flow,
        String amount,
        LocalDate date
    ) {
        return new FinancialTransaction()
            .account(account)
            .category(category)
            .transactionDate(date)
            .postingDate(date)
            .description("Budget fixture " + amount)
            .amount(new BigDecimal(amount))
            .flow(flow)
            .origin(TransactionOrigin.MANUAL)
            .createdAt(Instant.now())
            .updatedAt(Instant.now());
    }

    private record Fixture(User owner, FinancialAccount debit, Category household, Category food, Tag recurring, Tag essential) {}
}
