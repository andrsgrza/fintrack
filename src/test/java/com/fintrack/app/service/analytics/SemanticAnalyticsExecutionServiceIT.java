package com.fintrack.app.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.web.rest.TestUtil;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL golden proof for the composable semantic engine. The fixture is independently calculated below and
 * deliberately includes two tags on one transaction, linked transfers, another owner and multiple currencies.
 */
@IntegrationTest
@Transactional
@WithMockUser(username = "user")
class SemanticAnalyticsExecutionServiceIT {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate THROUGH = LocalDate.of(2026, 3, 31);

    @Autowired
    private SemanticAnalyticsExecutionService executionService;

    @Autowired
    private SemanticAnalyticsDslService dslService;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private InternalTransferRepository transferRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private EntityManager em;

    @Test
    void countsAllOwnedLedgerTransactionsByInstitutionAndComposesAnotherGroupingFromTheSameAst() {
        Fixture fixture = fixture();
        SemanticQueryDefinition byInstitution = ledgerDefinition(
            new SemanticAggregateProjection("transaction_count", SemanticAggregateFunction.COUNT, SemanticField.TRANSACTION_ID),
            List.of(),
            List.of(SemanticField.ACCOUNT_INSTITUTION),
            List.of(new SemanticOrder(SemanticField.ACCOUNT_INSTITUTION, SemanticSortDirection.ASC))
        );

        SemanticAnalyticsResult allAccounts = execute(byInstitution, List.of(), List.of());
        assertValue(allAccounts, SemanticField.ACCOUNT_INSTITUTION, "Banco Norte", null, "5");
        assertValue(allAccounts, SemanticField.ACCOUNT_INSTITUTION, "Banco Centro", null, "1");
        assertValue(allAccounts, SemanticField.ACCOUNT_INSTITUTION, "Broker USD", null, "1");
        assertThat(allAccounts.rows()).hasSize(3);

        // Context changes the result but does not mutate the definition; foreign data never becomes visible.
        SemanticAnalyticsResult debitOnly = execute(byInstitution, List.of(fixture.debit().getId()), List.of("MXN"));
        assertValue(debitOnly, SemanticField.ACCOUNT_INSTITUTION, "Banco Norte", null, "5");
        assertThat(debitOnly.rows()).hasSize(1);

        SemanticQueryDefinition byAccountType = ledgerDefinition(
            new SemanticAggregateProjection("transaction_count", SemanticAggregateFunction.COUNT, SemanticField.TRANSACTION_ID),
            List.of(),
            List.of(SemanticField.ACCOUNT_TYPE),
            List.of(new SemanticOrder(SemanticField.ACCOUNT_TYPE, SemanticSortDirection.ASC))
        );
        SemanticAnalyticsResult variation = execute(byAccountType, List.of(), List.of());
        assertValue(variation, SemanticField.ACCOUNT_TYPE, "CASH", null, "1");
        assertValue(variation, SemanticField.ACCOUNT_TYPE, "DEBIT", null, "5");
        assertValue(variation, SemanticField.ACCOUNT_TYPE, "INVESTMENT", null, "1");
    }

    @Test
    void averagesAndSumsEconomicExpensesByCategoryWithoutTransferOrTagMultiplication() {
        Fixture fixture = fixture();
        List<SemanticPredicate> economicExpense = List.of(
            new SemanticPredicate(SemanticField.TRANSACTION_FLOW, SemanticPredicateOperator.EQUALS, List.of("OUT")),
            new SemanticPredicate(SemanticField.TRANSFER_LINK_STATUS, SemanticPredicateOperator.EQUALS, List.of("UNLINKED"))
        );
        SemanticQueryDefinition averageExpense = ledgerDefinition(
            new SemanticAggregateProjection("average_expense", SemanticAggregateFunction.AVG, SemanticField.TRANSACTION_AMOUNT),
            economicExpense,
            List.of(SemanticField.CATEGORY_ID),
            List.of(new SemanticOrder(SemanticField.CATEGORY_ID, SemanticSortDirection.ASC))
        );

        SemanticAnalyticsResult average = execute(averageExpense, List.of(), List.of());
        assertValue(average, SemanticField.CATEGORY_ID, fixture.food().getId(), "MXN", "200");
        assertValue(average, SemanticField.CATEGORY_ID, fixture.travel().getId(), "MXN", "50");
        assertValue(average, SemanticField.CATEGORY_ID, fixture.food().getId(), "USD", "20");
        assertThat(average.rows()).hasSize(3);

        SemanticQueryDefinition totalExpense = ledgerDefinition(
            new SemanticAggregateProjection("total_expense", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            economicExpense,
            List.of(SemanticField.CATEGORY_ID),
            List.of(new SemanticOrder(SemanticField.CATEGORY_ID, SemanticSortDirection.ASC))
        );
        SemanticAnalyticsResult total = execute(totalExpense, List.of(), List.of());
        assertValue(total, SemanticField.CATEGORY_ID, fixture.food().getId(), "MXN", "400");
        assertValue(total, SemanticField.CATEGORY_ID, fixture.travel().getId(), "MXN", "50");
        assertValue(total, SemanticField.CATEGORY_ID, fixture.food().getId(), "USD", "20");
    }

    @Test
    void combinesCountAndAverageInOneQueryAndOrdersByTheAverageAlias() {
        Fixture fixture = fixture();
        SemanticAnalyticsResult result = executeRoundTrip(
            "SELECT COUNT(TRANSACTION_ID) AS expense_count, AVG(TRANSACTION_AMOUNT) AS average_expense FROM TRANSACTION_LEDGER " +
            "WHERE TRANSACTION_FLOW = OUT AND TRANSFER_LINK_STATUS = UNLINKED " +
            "GROUP BY CATEGORY_ID ORDER BY average_expense DESC LIMIT 100",
            List.of(),
            List.of()
        );

        assertThat(result.rows()).hasSize(3);
        assertThat(result.rows().get(0).values().get(0).alias()).isEqualTo("expense_count");
        assertThat(result.rows().get(0).values().get(0).value()).isEqualByComparingTo("2");
        assertThat(result.rows().get(0).values().get(1).alias()).isEqualTo("average_expense");
        assertThat(result.rows().get(0).values().get(1).value()).isEqualByComparingTo("200");
        assertThat(dimension(result.rows().get(0), SemanticField.CATEGORY_ID)).isEqualTo(fixture.food().getId());
        assertThat(result.rows().get(0).currencyCode()).isEqualTo("MXN");
        assertThat(result.rows().get(1).values().get(1).value()).isEqualByComparingTo("50");
        assertThat(result.rows().get(2).values().get(1).value()).isEqualByComparingTo("20");
    }

    @Test
    void groups_a_deep_category_branch_without_losing_direct_assignments_or_inactive_category_identity() {
        Fixture fixture = fixture();
        User owner = TestUtil.findAll(em, User.class).stream().filter(user -> "user".equals(user.getLogin())).findFirst().orElseThrow();
        Category household = category(owner, "Household");
        Category groceries = category(owner, "Groceries", household).active(false);
        categoryRepository.saveAndFlush(groceries);
        Category produce = category(owner, "Produce", groceries);

        transactionRepository.saveAndFlush(transaction(fixture.debit(), household, TransactionFlow.OUT, "10", LocalDate.of(2026, 1, 20)));
        transactionRepository.saveAndFlush(transaction(fixture.debit(), produce, TransactionFlow.OUT, "40", LocalDate.of(2026, 1, 21)));

        SemanticQueryDefinition byImmediateBranch = ledgerDefinition(
            new SemanticAggregateProjection("branch_total", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            List.of(
                new SemanticPredicate(
                    SemanticField.CATEGORY_ID,
                    SemanticPredicateOperator.DESCENDANT_OF,
                    List.of(household.getId().toString())
                )
            ),
            List.of(SemanticField.CATEGORY_BRANCH_CHILD),
            List.of(new SemanticOrder(SemanticField.CATEGORY_BRANCH_CHILD, SemanticSortDirection.ASC))
        );

        SemanticAnalyticsResult result = execute(byImmediateBranch, List.of(fixture.debit().getId()), List.of("MXN"));
        assertValue(
            result,
            SemanticField.CATEGORY_BRANCH_CHILD,
            SemanticAnalyticsReferenceCatalog.DIRECT_ASSIGNMENT_ID + ":" + household.getId(),
            "MXN",
            "10"
        );
        assertValue(result, SemanticField.CATEGORY_BRANCH_CHILD, groceries.getId(), "MXN", "40");
        SemanticAnalyticsResultDimension groceriesDimension = result
            .rows()
            .stream()
            .flatMap(row -> row.dimensions().stream())
            .filter(dimension -> java.util.Objects.equals(dimension.value(), groceries.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(groceriesDimension.metadata().label()).isEqualTo("Groceries");
        assertThat(groceriesDimension.metadata().active()).isFalse();

        SemanticQueryDefinition byRoot = ledgerDefinition(
            new SemanticAggregateProjection("root_total", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            List.of(
                new SemanticPredicate(
                    SemanticField.CATEGORY_ID,
                    SemanticPredicateOperator.DESCENDANT_OF,
                    List.of(household.getId().toString())
                )
            ),
            List.of(SemanticField.CATEGORY_ROOT),
            List.of()
        );
        assertValue(
            execute(byRoot, List.of(fixture.debit().getId()), List.of("MXN")),
            SemanticField.CATEGORY_ROOT,
            household.getId(),
            "MXN",
            "50"
        );

        SemanticQueryDefinition byFirstSubcategory = ledgerDefinition(
            new SemanticAggregateProjection("subcategory_total", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            List.of(
                new SemanticPredicate(
                    SemanticField.CATEGORY_ID,
                    SemanticPredicateOperator.DESCENDANT_OF,
                    List.of(household.getId().toString())
                )
            ),
            List.of(SemanticField.CATEGORY_SUBCATEGORY),
            List.of()
        );
        SemanticAnalyticsResult firstLevel = execute(byFirstSubcategory, List.of(fixture.debit().getId()), List.of("MXN"));
        assertValue(
            firstLevel,
            SemanticField.CATEGORY_SUBCATEGORY,
            SemanticAnalyticsReferenceCatalog.DIRECT_ASSIGNMENT_ID + ":" + household.getId(),
            "MXN",
            "10"
        );
        assertValue(firstLevel, SemanticField.CATEGORY_SUBCATEGORY, groceries.getId(), "MXN", "40");

        SemanticQueryDefinition uncategorized = ledgerDefinition(
            new SemanticAggregateProjection("uncategorized_total", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            List.of(new SemanticPredicate(SemanticField.CATEGORY_ID, SemanticPredicateOperator.IS_UNCATEGORIZED, List.of())),
            List.of(SemanticField.CATEGORY_ID),
            List.of()
        );
        SemanticAnalyticsResult noCategory = execute(uncategorized, List.of(fixture.debit().getId()), List.of("MXN"));
        assertValue(noCategory, SemanticField.CATEGORY_ID, SemanticAnalyticsReferenceCatalog.UNCATEGORIZED_ID, "MXN", "200");
    }

    @Test
    void evaluates_per_calculation_tag_conditions_without_join_multiplication_and_warns_for_tag_groups() {
        Fixture fixture = fixture();
        User owner = TestUtil.findAll(em, User.class).stream().filter(user -> "user".equals(user.getLogin())).findFirst().orElseThrow();
        Category household = category(owner, "Tagged household");
        Tag recurring = tag(owner, "recurring");
        Tag essential = tag(owner, "essential");
        FinancialTransaction bothTags = transaction(fixture.debit(), household, TransactionFlow.OUT, "120", LocalDate.of(2026, 2, 22));
        bothTags.addTags(recurring).addTags(essential);
        transactionRepository.saveAndFlush(bothTags);
        FinancialTransaction oneTag = transaction(fixture.debit(), household, TransactionFlow.OUT, "40", LocalDate.of(2026, 2, 23));
        oneTag.addTags(recurring);
        transactionRepository.saveAndFlush(oneTag);
        transactionRepository.saveAndFlush(transaction(fixture.debit(), household, TransactionFlow.OUT, "10", LocalDate.of(2026, 2, 24)));

        List<SemanticPredicate> householdOnly = List.of(
            new SemanticPredicate(SemanticField.CATEGORY_ID, SemanticPredicateOperator.EQUALS, List.of(household.getId().toString()))
        );
        SemanticQueryDefinition calculations = new SemanticQueryDefinition(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticDataset.TRANSACTION_LEDGER,
            SemanticField.TRANSACTION_DATE,
            List.of(
                new SemanticAggregateProjection(
                    "expense_count",
                    SemanticAggregateFunction.COUNT,
                    SemanticField.TRANSACTION_ID,
                    List.of(new SemanticPredicate(SemanticField.TRANSACTION_FLOW, SemanticPredicateOperator.EQUALS, List.of("OUT")))
                ),
                new SemanticAggregateProjection(
                    "both_tag_average",
                    SemanticAggregateFunction.AVG,
                    SemanticField.TRANSACTION_AMOUNT,
                    List.of(
                        new SemanticPredicate(
                            SemanticField.TAG_ID,
                            SemanticPredicateOperator.HAS_ALL,
                            List.of(recurring.getId().toString(), essential.getId().toString())
                        )
                    )
                )
            ),
            householdOnly,
            List.of(),
            List.of(),
            100
        );

        SemanticAnalyticsResult calculationResult = execute(calculations, List.of(fixture.debit().getId()), List.of("MXN"));
        assertThat(calculationResult.rows())
            .singleElement()
            .satisfies(row -> {
                assertThat(row.values().get(0).value()).isEqualByComparingTo("3");
                assertThat(row.values().get(1).value()).isEqualByComparingTo("120");
            });

        SemanticQueryDefinition byTag = ledgerDefinition(
            new SemanticAggregateProjection("tag_total", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            householdOnly,
            List.of(SemanticField.TAG_ID),
            List.of(new SemanticOrder(SemanticField.TAG_ID, SemanticSortDirection.ASC))
        );
        SemanticAnalyticsResult tagResult = execute(byTag, List.of(fixture.debit().getId()), List.of("MXN"));
        assertValue(tagResult, SemanticField.TAG_ID, recurring.getId(), "MXN", "160");
        assertValue(tagResult, SemanticField.TAG_ID, essential.getId(), "MXN", "120");
        assertThat(tagResult.warnings()).extracting(SemanticAnalyticsWarning::code).containsExactly("NON_ADDITIVE_OVERLAPPING_TAG_GROUPS");

        SemanticQueryDefinition anyTag = ledgerDefinition(
            new SemanticAggregateProjection("any_tag_total", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            List.of(
                new SemanticPredicate(SemanticField.CATEGORY_ID, SemanticPredicateOperator.EQUALS, List.of(household.getId().toString())),
                new SemanticPredicate(SemanticField.TAG_ID, SemanticPredicateOperator.HAS_ANY, List.of(recurring.getId().toString()))
            ),
            List.of(),
            List.of()
        );
        assertThat(execute(anyTag, List.of(fixture.debit().getId()), List.of("MXN")).rows())
            .singleElement()
            .satisfies(row ->
                assertThat(row.values()).singleElement().satisfies(value -> assertThat(value.value()).isEqualByComparingTo("160"))
            );

        SemanticQueryDefinition noTags = ledgerDefinition(
            new SemanticAggregateProjection("untagged_total", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            List.of(
                new SemanticPredicate(SemanticField.CATEGORY_ID, SemanticPredicateOperator.EQUALS, List.of(household.getId().toString())),
                new SemanticPredicate(SemanticField.TAG_ID, SemanticPredicateOperator.HAS_NONE, List.of())
            ),
            List.of(),
            List.of()
        );
        assertThat(execute(noTags, List.of(fixture.debit().getId()), List.of("MXN")).rows())
            .singleElement()
            .satisfies(row ->
                assertThat(row.values()).singleElement().satisfies(value -> assertThat(value.value()).isEqualByComparingTo("10"))
            );
    }

    @Test
    void calculates_economic_income_and_expense_as_independent_projections_under_the_transfer_policy() {
        Fixture fixture = fixture();
        SemanticQueryDefinition incomeAndExpense = new SemanticQueryDefinition(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticDataset.TRANSACTION_LEDGER,
            SemanticField.TRANSACTION_DATE,
            List.of(
                new SemanticAggregateProjection(
                    "economic_income",
                    SemanticAggregateFunction.SUM,
                    SemanticField.TRANSACTION_AMOUNT,
                    List.of(
                        new SemanticPredicate(SemanticField.TRANSACTION_FLOW, SemanticPredicateOperator.EQUALS, List.of("IN")),
                        new SemanticPredicate(SemanticField.TRANSFER_LINK_STATUS, SemanticPredicateOperator.EQUALS, List.of("UNLINKED"))
                    )
                ),
                new SemanticAggregateProjection(
                    "economic_expense",
                    SemanticAggregateFunction.SUM,
                    SemanticField.TRANSACTION_AMOUNT,
                    List.of(
                        new SemanticPredicate(SemanticField.TRANSACTION_FLOW, SemanticPredicateOperator.EQUALS, List.of("OUT")),
                        new SemanticPredicate(SemanticField.TRANSFER_LINK_STATUS, SemanticPredicateOperator.EQUALS, List.of("UNLINKED"))
                    )
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            100
        );

        SemanticAnalyticsResult result = execute(incomeAndExpense, List.of(fixture.debit().getId()), List.of("MXN"));
        assertThat(result.rows())
            .singleElement()
            .satisfies(row -> {
                assertThat(row.currencyCode()).isEqualTo("MXN");
                assertThat(row.values().get(0).value()).isEqualByComparingTo("1000");
                assertThat(row.values().get(1).value()).isEqualByComparingTo("450");
            });
    }

    @Test
    void partitions_transaction_dates_by_day_iso_week_month_and_year_without_changing_the_context_range() {
        Fixture fixture = fixture();
        SemanticQueryDefinition day = ledgerDefinition(
            new SemanticAggregateProjection("count", SemanticAggregateFunction.COUNT, SemanticField.TRANSACTION_ID),
            List.of(),
            List.of(SemanticField.TRANSACTION_DAY),
            List.of()
        );
        SemanticAnalyticsResult daily = execute(day, List.of(fixture.debit().getId()), List.of("MXN"));
        assertValue(daily, SemanticField.TRANSACTION_DAY, "2026-01-10", null, "1");
        assertThat(daily.rows().get(0).dimensions().get(0).metadata().stableId()).startsWith("TRANSACTION_DAY:");

        SemanticQueryDefinition week = ledgerDefinition(
            new SemanticAggregateProjection("count", SemanticAggregateFunction.COUNT, SemanticField.TRANSACTION_ID),
            List.of(),
            List.of(SemanticField.TRANSACTION_WEEK, SemanticField.ACCOUNT_ID),
            List.of()
        );
        SemanticAnalyticsResult weekly = execute(week, List.of(fixture.debit().getId()), List.of("MXN"));
        assertTupleValue(weekly, SemanticField.TRANSACTION_WEEK, "2026-W02", SemanticField.ACCOUNT_ID, fixture.debit().getId(), null, "1");

        SemanticQueryDefinition month = ledgerDefinition(
            new SemanticAggregateProjection("count", SemanticAggregateFunction.COUNT, SemanticField.TRANSACTION_ID),
            List.of(),
            List.of(SemanticField.TRANSACTION_MONTH),
            List.of()
        );
        SemanticAnalyticsResult monthly = execute(month, List.of(fixture.debit().getId()), List.of("MXN"));
        assertValue(monthly, SemanticField.TRANSACTION_MONTH, "2026-01", null, "2");
        assertValue(monthly, SemanticField.TRANSACTION_MONTH, "2026-02", null, "2");
        assertValue(monthly, SemanticField.TRANSACTION_MONTH, "2026-03", null, "1");

        SemanticQueryDefinition year = ledgerDefinition(
            new SemanticAggregateProjection("count", SemanticAggregateFunction.COUNT, SemanticField.TRANSACTION_ID),
            List.of(),
            List.of(SemanticField.TRANSACTION_YEAR, SemanticField.ACCOUNT_TYPE),
            List.of()
        );
        assertTupleValue(
            execute(year, List.of(fixture.debit().getId()), List.of("MXN")),
            SemanticField.TRANSACTION_YEAR,
            "2026",
            SemanticField.ACCOUNT_TYPE,
            "DEBIT",
            null,
            "5"
        );

        User owner = TestUtil.findAll(em, User.class).stream().filter(user -> "user".equals(user.getLogin())).findFirst().orElseThrow();
        FinancialAccount historicalAccount = account(
            owner,
            "Historical debit",
            "Banco Norte",
            AccountType.DEBIT,
            CurrencyCode.MXN,
            "0",
            LocalDate.of(2025, 12, 1)
        );
        transactionRepository.saveAndFlush(
            transaction(historicalAccount, fixture.food(), TransactionFlow.OUT, "7", LocalDate.of(2025, 12, 31))
        );
        transactionRepository.saveAndFlush(
            transaction(historicalAccount, fixture.food(), TransactionFlow.OUT, "9", LocalDate.of(2026, 1, 1))
        );
        SemanticAnalyticsResult acrossIsoYear = execute(
            week,
            List.of(historicalAccount.getId()),
            List.of("MXN"),
            LocalDate.of(2025, 12, 29),
            LocalDate.of(2026, 1, 3)
        );
        assertTupleValue(
            acrossIsoYear,
            SemanticField.TRANSACTION_WEEK,
            "2026-W01",
            SemanticField.ACCOUNT_ID,
            historicalAccount.getId(),
            null,
            "2"
        );
    }

    @Test
    void combines_a_month_with_category_hierarchy_account_institution_and_overlapping_tags() {
        Fixture fixture = fixture();
        User owner = TestUtil.findAll(em, User.class).stream().filter(user -> "user".equals(user.getLogin())).findFirst().orElseThrow();
        Category root = category(owner, "Expenses");
        fixture.food().parentCategory(root);
        fixture.travel().parentCategory(root);
        categoryRepository.saveAndFlush(fixture.food());
        categoryRepository.saveAndFlush(fixture.travel());
        List<SemanticPredicate> economicOut = List.of(
            new SemanticPredicate(SemanticField.TRANSACTION_FLOW, SemanticPredicateOperator.EQUALS, List.of("OUT")),
            new SemanticPredicate(SemanticField.TRANSFER_LINK_STATUS, SemanticPredicateOperator.EQUALS, List.of("UNLINKED"))
        );

        SemanticQueryDefinition byMonthAndCategory = ledgerDefinition(
            new SemanticAggregateProjection("expense", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            economicOut,
            List.of(SemanticField.TRANSACTION_MONTH, SemanticField.CATEGORY_ID),
            List.of()
        );
        SemanticAnalyticsResult categories = execute(byMonthAndCategory, List.of(fixture.debit().getId()), List.of("MXN"));
        assertTupleValue(
            categories,
            SemanticField.TRANSACTION_MONTH,
            "2026-01",
            SemanticField.CATEGORY_ID,
            fixture.food().getId(),
            "MXN",
            "100"
        );
        assertTupleValue(
            categories,
            SemanticField.TRANSACTION_MONTH,
            "2026-02",
            SemanticField.CATEGORY_ID,
            fixture.food().getId(),
            "MXN",
            "300"
        );
        assertTupleValue(
            categories,
            SemanticField.TRANSACTION_MONTH,
            "2026-03",
            SemanticField.CATEGORY_ID,
            fixture.travel().getId(),
            "MXN",
            "50"
        );

        SemanticQueryDefinition byMonthAndRoot = ledgerDefinition(
            new SemanticAggregateProjection("expense", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            economicOut,
            List.of(SemanticField.TRANSACTION_MONTH, SemanticField.CATEGORY_ROOT),
            List.of()
        );
        assertTupleValue(
            execute(byMonthAndRoot, List.of(fixture.debit().getId()), List.of("MXN")),
            SemanticField.TRANSACTION_MONTH,
            "2026-02",
            SemanticField.CATEGORY_ROOT,
            root.getId(),
            "MXN",
            "300"
        );

        SemanticQueryDefinition byMonthAndSubcategory = ledgerDefinition(
            new SemanticAggregateProjection("expense", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            economicOut,
            List.of(SemanticField.TRANSACTION_MONTH, SemanticField.CATEGORY_SUBCATEGORY),
            List.of()
        );
        assertTupleValue(
            execute(byMonthAndSubcategory, List.of(fixture.debit().getId()), List.of("MXN")),
            SemanticField.TRANSACTION_MONTH,
            "2026-01",
            SemanticField.CATEGORY_SUBCATEGORY,
            fixture.food().getId(),
            "MXN",
            "100"
        );

        SemanticQueryDefinition byMonthAndBranch = ledgerDefinition(
            new SemanticAggregateProjection("expense", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            List.of(
                new SemanticPredicate(SemanticField.CATEGORY_ID, SemanticPredicateOperator.DESCENDANT_OF, List.of(root.getId().toString())),
                new SemanticPredicate(SemanticField.TRANSACTION_FLOW, SemanticPredicateOperator.EQUALS, List.of("OUT")),
                new SemanticPredicate(SemanticField.TRANSFER_LINK_STATUS, SemanticPredicateOperator.EQUALS, List.of("UNLINKED"))
            ),
            List.of(SemanticField.TRANSACTION_MONTH, SemanticField.CATEGORY_BRANCH_CHILD),
            List.of()
        );
        assertTupleValue(
            execute(byMonthAndBranch, List.of(fixture.debit().getId()), List.of("MXN")),
            SemanticField.TRANSACTION_MONTH,
            "2026-03",
            SemanticField.CATEGORY_BRANCH_CHILD,
            fixture.travel().getId(),
            "MXN",
            "50"
        );

        SemanticQueryDefinition byMonthAndAccount = ledgerDefinition(
            new SemanticAggregateProjection("count", SemanticAggregateFunction.COUNT, SemanticField.TRANSACTION_ID),
            List.of(),
            List.of(SemanticField.TRANSACTION_MONTH, SemanticField.ACCOUNT_ID),
            List.of()
        );
        SemanticAnalyticsResult accounts = execute(byMonthAndAccount, List.of(fixture.debit().getId()), List.of("MXN"));
        assertTupleValue(
            accounts,
            SemanticField.TRANSACTION_MONTH,
            "2026-02",
            SemanticField.ACCOUNT_ID,
            fixture.debit().getId(),
            null,
            "2"
        );
        assertThat(
            accounts
                .rows()
                .get(0)
                .dimensions()
                .stream()
                .filter(value -> value.field() == SemanticField.ACCOUNT_ID)
                .findFirst()
                .orElseThrow()
                .metadata()
                .label()
        ).isEqualTo("Debit");

        SemanticQueryDefinition byMonthAndInstitution = ledgerDefinition(
            new SemanticAggregateProjection("count", SemanticAggregateFunction.COUNT, SemanticField.TRANSACTION_ID),
            List.of(),
            List.of(SemanticField.TRANSACTION_MONTH, SemanticField.ACCOUNT_INSTITUTION),
            List.of()
        );
        assertTupleValue(
            execute(byMonthAndInstitution, List.of(fixture.debit().getId()), List.of("MXN")),
            SemanticField.TRANSACTION_MONTH,
            "2026-03",
            SemanticField.ACCOUNT_INSTITUTION,
            "Banco Norte",
            null,
            "1"
        );

        SemanticQueryDefinition uncategorizedByMonth = ledgerDefinition(
            new SemanticAggregateProjection("uncategorized", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            List.of(new SemanticPredicate(SemanticField.CATEGORY_ID, SemanticPredicateOperator.IS_UNCATEGORIZED, List.of())),
            List.of(SemanticField.TRANSACTION_MONTH, SemanticField.CATEGORY_ID),
            List.of()
        );
        assertTupleValue(
            execute(uncategorizedByMonth, List.of(fixture.debit().getId()), List.of("MXN")),
            SemanticField.TRANSACTION_MONTH,
            "2026-02",
            SemanticField.CATEGORY_ID,
            SemanticAnalyticsReferenceCatalog.UNCATEGORIZED_ID,
            "MXN",
            "200"
        );

        SemanticQueryDefinition byMonthAndTag = ledgerDefinition(
            new SemanticAggregateProjection("tagged", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            economicOut,
            List.of(SemanticField.TRANSACTION_MONTH, SemanticField.TAG_ID),
            List.of()
        );
        Tag overlapOne = tag(owner, "month-overlap-one");
        Tag overlapTwo = tag(owner, "month-overlap-two");
        FinancialTransaction overlapping = transaction(
            fixture.debit(),
            fixture.food(),
            TransactionFlow.OUT,
            "77",
            LocalDate.of(2026, 1, 2)
        );
        overlapping.addTags(overlapOne).addTags(overlapTwo);
        transactionRepository.saveAndFlush(overlapping);
        SemanticAnalyticsResult tags = execute(byMonthAndTag, List.of(fixture.debit().getId()), List.of("MXN"));
        assertThat(tags.warnings()).extracting(SemanticAnalyticsWarning::code).containsExactly("NON_ADDITIVE_OVERLAPPING_TAG_GROUPS");
        assertTupleValue(tags, SemanticField.TRANSACTION_MONTH, "2026-01", SemanticField.TAG_ID, overlapOne.getId(), "MXN", "77");
        assertTupleValue(tags, SemanticField.TRANSACTION_MONTH, "2026-01", SemanticField.TAG_ID, overlapTwo.getId(), "MXN", "77");
        assertThat(tags.rows()).allSatisfy(row -> assertThat(row.dimensions()).hasSize(2));
    }

    @Test
    void calculates_income_and_expense_series_with_zero_and_completes_valid_empty_months_without_mixing_currencies() {
        Fixture fixture = fixture();
        SemanticQueryDefinition incomeAndExpenseByMonth = new SemanticQueryDefinition(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticDataset.TRANSACTION_LEDGER,
            SemanticField.TRANSACTION_DATE,
            List.of(
                new SemanticAggregateProjection(
                    "income",
                    SemanticAggregateFunction.SUM,
                    SemanticField.TRANSACTION_AMOUNT,
                    List.of(
                        new SemanticPredicate(SemanticField.TRANSACTION_FLOW, SemanticPredicateOperator.EQUALS, List.of("IN")),
                        new SemanticPredicate(SemanticField.TRANSFER_LINK_STATUS, SemanticPredicateOperator.EQUALS, List.of("UNLINKED"))
                    )
                ),
                new SemanticAggregateProjection(
                    "expense",
                    SemanticAggregateFunction.SUM,
                    SemanticField.TRANSACTION_AMOUNT,
                    List.of(
                        new SemanticPredicate(SemanticField.TRANSACTION_FLOW, SemanticPredicateOperator.EQUALS, List.of("OUT")),
                        new SemanticPredicate(SemanticField.TRANSFER_LINK_STATUS, SemanticPredicateOperator.EQUALS, List.of("UNLINKED"))
                    )
                )
            ),
            List.of(),
            List.of(SemanticField.TRANSACTION_MONTH),
            List.of(),
            100
        );
        SemanticAnalyticsResult series = execute(incomeAndExpenseByMonth, List.of(fixture.debit().getId()), List.of("MXN"));
        SemanticAnalyticsResultRow february = row(series, "MXN", SemanticField.TRANSACTION_MONTH, "2026-02", null, null);
        assertThat(value(february, "income").value()).isZero();
        assertThat(value(february, "income").availability()).isEqualTo(SemanticResultAvailability.ZERO);
        assertThat(value(february, "expense").value()).isEqualByComparingTo("300");

        SemanticAnalyticsResult countAndAverage = executeRoundTrip(
            "SELECT COUNT(TRANSACTION_ID) AS count, AVG(TRANSACTION_AMOUNT) AS average FROM TRANSACTION_LEDGER " +
            "WHERE TRANSACTION_FLOW = OUT AND TRANSFER_LINK_STATUS = UNLINKED GROUP BY TRANSACTION_MONTH LIMIT 100",
            List.of(fixture.debit().getId()),
            List.of("MXN")
        );
        SemanticAnalyticsResultRow january = row(countAndAverage, "MXN", SemanticField.TRANSACTION_MONTH, "2026-01", null, null);
        assertThat(value(january, "count").unit()).isEqualTo(SemanticUnit.COUNT);
        assertThat(value(january, "average").unit()).isEqualTo(SemanticUnit.MONEY);
        assertThat(value(january, "count").value()).isEqualByComparingTo("1");
        assertThat(value(january, "average").value()).isEqualByComparingTo("100");

        SemanticQueryDefinition byMonthAndCategory = ledgerDefinition(
            new SemanticAggregateProjection("expense", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            List.of(
                new SemanticPredicate(SemanticField.TRANSACTION_FLOW, SemanticPredicateOperator.EQUALS, List.of("OUT")),
                new SemanticPredicate(SemanticField.TRANSFER_LINK_STATUS, SemanticPredicateOperator.EQUALS, List.of("UNLINKED"))
            ),
            List.of(SemanticField.TRANSACTION_MONTH, SemanticField.CATEGORY_ID),
            List.of()
        );
        SemanticAnalyticsResult currencies = execute(byMonthAndCategory, List.of(), List.of());
        assertTupleValue(
            currencies,
            SemanticField.TRANSACTION_MONTH,
            "2026-02",
            SemanticField.CATEGORY_ID,
            fixture.food().getId(),
            "MXN",
            "300"
        );
        assertTupleValue(
            currencies,
            SemanticField.TRANSACTION_MONTH,
            "2026-02",
            SemanticField.CATEGORY_ID,
            fixture.food().getId(),
            "USD",
            "20"
        );

        SemanticAnalyticsResult sparse = execute(
            incomeAndExpenseByMonth,
            List.of(fixture.debit().getId()),
            List.of("MXN"),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 4, 30)
        );
        SemanticAnalyticsResultRow april = row(sparse, "MXN", SemanticField.TRANSACTION_MONTH, "2026-04", null, null);
        assertThat(value(april, "income").value()).isZero();
        assertThat(value(april, "income").availability()).isEqualTo(SemanticResultAvailability.ZERO);
        assertThat(value(april, "income").derived()).isTrue();
        assertThat(value(april, "expense").value()).isZero();
        assertThat(value(april, "expense").availability()).isEqualTo(SemanticResultAvailability.ZERO);
        assertThat(value(april, "expense").derived()).isTrue();
        assertThat(sparse.availability()).isEqualTo(SemanticResultAvailability.COMPLETE);
    }

    @Test
    void completes_empty_weeks_by_measure_and_series_without_fabricating_averages_categories_or_currencies() {
        Fixture fixture = fixture();
        List<SemanticPredicate> economicExpense = List.of(
            new SemanticPredicate(SemanticField.TRANSACTION_FLOW, SemanticPredicateOperator.EQUALS, List.of("OUT")),
            new SemanticPredicate(SemanticField.TRANSFER_LINK_STATUS, SemanticPredicateOperator.EQUALS, List.of("UNLINKED"))
        );
        SemanticQueryDefinition weeklyMeasures = new SemanticQueryDefinition(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticDataset.TRANSACTION_LEDGER,
            SemanticField.TRANSACTION_DATE,
            List.of(
                new SemanticAggregateProjection("expense", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
                new SemanticAggregateProjection("movement_count", SemanticAggregateFunction.COUNT, SemanticField.TRANSACTION_ID),
                new SemanticAggregateProjection("average_expense", SemanticAggregateFunction.AVG, SemanticField.TRANSACTION_AMOUNT)
            ),
            economicExpense,
            List.of(SemanticField.TRANSACTION_WEEK),
            List.of(),
            100
        );

        SemanticAnalyticsResult weekly = execute(
            weeklyMeasures,
            List.of(fixture.debit().getId()),
            List.of("MXN"),
            LocalDate.of(2026, 2, 9),
            LocalDate.of(2026, 2, 22)
        );
        SemanticAnalyticsResultRow weekWithExpense = row(weekly, "MXN", SemanticField.TRANSACTION_WEEK, "2026-W07", null, null);
        assertThat(value(weekWithExpense, "expense").value()).isEqualByComparingTo("300");
        assertThat(value(weekWithExpense, "movement_count").value()).isEqualByComparingTo("1");
        assertThat(value(weekWithExpense, "average_expense").value()).isEqualByComparingTo("300");

        SemanticAnalyticsResultRow emptyWeek = row(weekly, "MXN", SemanticField.TRANSACTION_WEEK, "2026-W08", null, null);
        assertThat(value(emptyWeek, "expense").value()).isZero();
        assertThat(value(emptyWeek, "expense").availability()).isEqualTo(SemanticResultAvailability.ZERO);
        assertThat(value(emptyWeek, "expense").derived()).isTrue();
        assertThat(value(emptyWeek, "movement_count").value()).isZero();
        assertThat(value(emptyWeek, "movement_count").availability()).isEqualTo(SemanticResultAvailability.ZERO);
        assertThat(value(emptyWeek, "movement_count").derived()).isTrue();
        assertThat(value(emptyWeek, "average_expense").value()).isNull();
        assertThat(value(emptyWeek, "average_expense").availability()).isEqualTo(SemanticResultAvailability.NO_DATA);
        assertThat(value(emptyWeek, "average_expense").derived()).isTrue();

        SemanticQueryDefinition byWeekAndCategory = ledgerDefinition(
            new SemanticAggregateProjection("expense", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            economicExpense,
            List.of(SemanticField.TRANSACTION_WEEK, SemanticField.CATEGORY_ID),
            List.of()
        );
        SemanticAnalyticsResult categories = execute(
            byWeekAndCategory,
            List.of(fixture.debit().getId()),
            List.of("MXN"),
            LocalDate.of(2026, 2, 9),
            LocalDate.of(2026, 3, 8)
        );
        SemanticAnalyticsResultRow foodInEmptyWeek = row(
            categories,
            "MXN",
            SemanticField.TRANSACTION_WEEK,
            "2026-W08",
            SemanticField.CATEGORY_ID,
            fixture.food().getId()
        );
        assertThat(value(foodInEmptyWeek, null).value()).isZero();
        assertThat(value(foodInEmptyWeek, null).derived()).isTrue();
        assertThat(
            categories
                .rows()
                .stream()
                .filter(candidate -> "2026-W08".equals(dimension(candidate, SemanticField.TRANSACTION_WEEK)))
                .map(candidate -> dimension(candidate, SemanticField.CATEGORY_ID))
        ).containsExactlyInAnyOrder(fixture.food().getId(), fixture.travel().getId());

        SemanticAnalyticsResult currencies = execute(
            byWeekAndCategory,
            List.of(fixture.debit().getId(), fixture.investment().getId()),
            List.of("MXN", "USD"),
            LocalDate.of(2026, 2, 9),
            LocalDate.of(2026, 2, 22)
        );
        assertThat(
            value(
                row(currencies, "MXN", SemanticField.TRANSACTION_WEEK, "2026-W08", SemanticField.CATEGORY_ID, fixture.food().getId()),
                null
            ).value()
        ).isZero();
        assertThat(
            value(
                row(currencies, "USD", SemanticField.TRANSACTION_WEEK, "2026-W08", SemanticField.CATEGORY_ID, fixture.food().getId()),
                null
            ).value()
        ).isZero();
    }

    @Test
    void preserves_the_31275_13_mxn_weekly_expense_peak_while_completing_the_following_empty_week() {
        Fixture fixture = fixture();
        transactionRepository.saveAndFlush(
            transaction(fixture.debit(), fixture.food(), TransactionFlow.OUT, "31275.13", LocalDate.of(2026, 2, 2))
        );
        SemanticQueryDefinition weeklyExpenses = ledgerDefinition(
            new SemanticAggregateProjection("expense", SemanticAggregateFunction.SUM, SemanticField.TRANSACTION_AMOUNT),
            List.of(
                new SemanticPredicate(SemanticField.TRANSACTION_FLOW, SemanticPredicateOperator.EQUALS, List.of("OUT")),
                new SemanticPredicate(SemanticField.TRANSFER_LINK_STATUS, SemanticPredicateOperator.EQUALS, List.of("UNLINKED"))
            ),
            List.of(SemanticField.TRANSACTION_WEEK),
            List.of()
        );
        SemanticAnalyticsResult result = execute(
            weeklyExpenses,
            List.of(fixture.debit().getId()),
            List.of("MXN"),
            LocalDate.of(2026, 1, 26),
            LocalDate.of(2026, 2, 8)
        );
        assertThat(value(row(result, "MXN", SemanticField.TRANSACTION_WEEK, "2026-W06", null, null), null).value()).isEqualByComparingTo(
            "31275.13"
        );
    }

    @Test
    @WithMockUser(username = "user", roles = "ADMIN")
    void keeps_an_admin_self_scoped_and_rejects_foreign_category_and_tag_references() {
        Fixture fixture = fixture();
        Category foreignCategory = category(fixture.foreignOwner(), "Private category");
        Tag foreignTag = tag(fixture.foreignOwner(), "private tag");
        SemanticQueryDefinition foreignCategoryQuery = dslService
            .parse(
                "SELECT COUNT(TRANSACTION_ID) AS count FROM TRANSACTION_LEDGER WHERE CATEGORY_ID = '" +
                foreignCategory.getId() +
                "' LIMIT 100"
            )
            .definition();
        SemanticQueryDefinition foreignTagQuery = dslService
            .parse(
                "SELECT COUNT(TRANSACTION_ID) AS count FROM TRANSACTION_LEDGER WHERE TAG_ID HAS_ANY ('" +
                foreignTag.getId() +
                "') LIMIT 100"
            )
            .definition();

        assertThatThrownBy(() -> execute(foreignCategoryQuery, List.of(), List.of()))
            .isInstanceOf(SemanticAnalyticsValidationException.class)
            .satisfies(error ->
                assertThat(((SemanticAnalyticsValidationException) error).getDiagnostics())
                    .extracting(AnalyticsDiagnostic::code)
                    .contains(AnalyticsDiagnosticCode.FOREIGN_CATEGORY_REFERENCE)
            );
        assertThatThrownBy(() -> execute(foreignTagQuery, List.of(), List.of()))
            .isInstanceOf(SemanticAnalyticsValidationException.class)
            .satisfies(error ->
                assertThat(((SemanticAnalyticsValidationException) error).getDiagnostics())
                    .extracting(AnalyticsDiagnostic::code)
                    .contains(AnalyticsDiagnosticCode.FOREIGN_TAG_REFERENCE)
            );
    }

    @Test
    void calculatesAuthoritativeMonthlyAssetBalancesWithoutInventingEarlyHistory() {
        Fixture fixture = fixture();
        SemanticQueryDefinition balances = new SemanticQueryDefinition(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticDataset.ACCOUNT_POSITION_SNAPSHOT,
            SemanticField.OBSERVATION_DATE,
            List.of(new SemanticAggregateProjection("historical_balance", SemanticAggregateFunction.SUM, SemanticField.POSITION_BALANCE)),
            List.of(),
            List.of(SemanticField.ACCOUNT_ID, SemanticField.OBSERVATION_MONTH),
            List.of(
                new SemanticOrder(SemanticField.OBSERVATION_MONTH, SemanticSortDirection.ASC),
                new SemanticOrder(SemanticField.ACCOUNT_ID, SemanticSortDirection.ASC)
            ),
            100
        );

        SemanticAnalyticsResult result = execute(
            balances,
            List.of(fixture.debit().getId(), fixture.laterAccount().getId()),
            List.of("MXN")
        );
        assertThat(result.availability()).isEqualTo(SemanticResultAvailability.PARTIAL);
        assertPosition(result, fixture.debit().getId(), LocalDate.of(2026, 1, 1), "1900", SemanticResultAvailability.COMPLETE);
        assertPosition(result, fixture.debit().getId(), LocalDate.of(2026, 2, 1), "1400", SemanticResultAvailability.COMPLETE);
        assertPosition(result, fixture.debit().getId(), LocalDate.of(2026, 3, 1), "1350", SemanticResultAvailability.COMPLETE);
        assertPosition(result, fixture.laterAccount().getId(), LocalDate.of(2026, 1, 1), null, SemanticResultAvailability.UNAVAILABLE);
        assertPosition(result, fixture.laterAccount().getId(), LocalDate.of(2026, 2, 1), null, SemanticResultAvailability.UNAVAILABLE);
        assertPosition(result, fixture.laterAccount().getId(), LocalDate.of(2026, 3, 1), "500", SemanticResultAvailability.COMPLETE);
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ROLE_ADMIN")
    void administratorsRemainSelfScopedAndCannotQueryAnotherUsersAccount() {
        Fixture fixture = fixture();
        SemanticQueryDefinition definition = ledgerDefinition(
            new SemanticAggregateProjection("transaction_count", SemanticAggregateFunction.COUNT, SemanticField.TRANSACTION_ID),
            List.of(),
            List.of(SemanticField.ACCOUNT_INSTITUTION),
            List.of(new SemanticOrder(SemanticField.ACCOUNT_INSTITUTION, SemanticSortDirection.ASC))
        );

        assertThat(execute(definition, List.of(), List.of()).rows()).isEmpty();
        assertThatThrownBy(() -> execute(definition, List.of(fixture.debit().getId()), List.of()))
            .isInstanceOf(SemanticAnalyticsValidationException.class)
            .satisfies(exception ->
                assertThat(((SemanticAnalyticsValidationException) exception).getDiagnostics()).anySatisfy(diagnostic ->
                    assertThat(diagnostic.code()).isEqualTo(AnalyticsDiagnosticCode.FOREIGN_ACCOUNT_REFERENCE)
                )
            );
    }

    @Test
    void parsesFormatsRoundTripsAndExecutesTheFiveGoldenSemanticQueriesAgainstPostgres() {
        Fixture fixture = fixture();

        SemanticAnalyticsResult q1 = executeRoundTrip(
            "SELECT COUNT(TRANSACTION_ID) AS transaction_count FROM TRANSACTION_LEDGER " +
            "GROUP BY ACCOUNT_INSTITUTION ORDER BY ACCOUNT_INSTITUTION ASC LIMIT 100",
            List.of(),
            List.of()
        );
        assertValue(q1, SemanticField.ACCOUNT_INSTITUTION, "Banco Norte", null, "5");
        assertValue(q1, SemanticField.ACCOUNT_INSTITUTION, "Banco Centro", null, "1");
        assertValue(q1, SemanticField.ACCOUNT_INSTITUTION, "Broker USD", null, "1");

        SemanticAnalyticsResult q2 = executeRoundTrip(
            "SELECT AVG(TRANSACTION_AMOUNT) AS average_expense FROM TRANSACTION_LEDGER " +
            "WHERE TRANSACTION_FLOW = OUT AND TRANSFER_LINK_STATUS = UNLINKED " +
            "GROUP BY CATEGORY_ID LIMIT 100",
            List.of(),
            List.of()
        );
        assertValue(q2, SemanticField.CATEGORY_ID, fixture.food().getId(), "MXN", "200");
        assertValue(q2, SemanticField.CATEGORY_ID, fixture.travel().getId(), "MXN", "50");
        assertValue(q2, SemanticField.CATEGORY_ID, fixture.food().getId(), "USD", "20");

        SemanticAnalyticsResult q3 = executeRoundTrip(
            "SELECT SUM(POSITION_BALANCE) AS balance FROM ACCOUNT_POSITION_SNAPSHOT " +
            "GROUP BY ACCOUNT_ID, OBSERVATION_MONTH ORDER BY OBSERVATION_MONTH ASC LIMIT 500",
            List.of(fixture.debit().getId(), fixture.laterAccount().getId()),
            List.of("MXN")
        );
        assertPosition(q3, fixture.debit().getId(), LocalDate.of(2026, 1, 1), "1900", SemanticResultAvailability.COMPLETE);
        assertPosition(q3, fixture.debit().getId(), LocalDate.of(2026, 2, 1), "1400", SemanticResultAvailability.COMPLETE);
        assertPosition(q3, fixture.debit().getId(), LocalDate.of(2026, 3, 1), "1350", SemanticResultAvailability.COMPLETE);
        assertPosition(q3, fixture.laterAccount().getId(), LocalDate.of(2026, 1, 1), null, SemanticResultAvailability.UNAVAILABLE);
        assertPosition(q3, fixture.laterAccount().getId(), LocalDate.of(2026, 2, 1), null, SemanticResultAvailability.UNAVAILABLE);
        assertPosition(q3, fixture.laterAccount().getId(), LocalDate.of(2026, 3, 1), "500", SemanticResultAvailability.COMPLETE);

        SemanticAnalyticsResult q4 = executeRoundTrip(
            "SELECT COUNT(TRANSACTION_ID) AS transaction_count FROM TRANSACTION_LEDGER " +
            "GROUP BY ACCOUNT_TYPE ORDER BY ACCOUNT_TYPE ASC LIMIT 100",
            List.of(),
            List.of()
        );
        assertValue(q4, SemanticField.ACCOUNT_TYPE, "CASH", null, "1");
        assertValue(q4, SemanticField.ACCOUNT_TYPE, "DEBIT", null, "5");
        assertValue(q4, SemanticField.ACCOUNT_TYPE, "INVESTMENT", null, "1");

        SemanticAnalyticsResult q5 = executeRoundTrip(
            "SELECT SUM(TRANSACTION_AMOUNT) AS total_expense FROM TRANSACTION_LEDGER " +
            "WHERE TRANSACTION_FLOW = OUT AND TRANSFER_LINK_STATUS = UNLINKED " +
            "GROUP BY CATEGORY_ID LIMIT 100",
            List.of(),
            List.of()
        );
        assertValue(q5, SemanticField.CATEGORY_ID, fixture.food().getId(), "MXN", "400");
        assertValue(q5, SemanticField.CATEGORY_ID, fixture.travel().getId(), "MXN", "50");
        assertValue(q5, SemanticField.CATEGORY_ID, fixture.food().getId(), "USD", "20");

        // Same definition, narrower context: no source text owns the period, account IDs or currency selection.
        SemanticAnalyticsResult narrowerContext = executeRoundTrip(
            "SELECT COUNT(TRANSACTION_ID) AS transaction_count FROM TRANSACTION_LEDGER " +
            "GROUP BY ACCOUNT_INSTITUTION ORDER BY ACCOUNT_INSTITUTION ASC LIMIT 100",
            List.of(fixture.debit().getId()),
            List.of("MXN")
        );
        assertThat(narrowerContext.rows())
            .singleElement()
            .satisfies(row -> assertThat(row.values().get(0).value()).isEqualByComparingTo("5"));
    }

    private Fixture fixture() {
        User owner = TestUtil.findAll(em, User.class).stream().filter(user -> "user".equals(user.getLogin())).findFirst().orElseThrow();
        User foreignOwner = foreignUser();

        Category food = category(owner, "Food");
        Category travel = category(owner, "Travel");
        FinancialAccount debit = account(owner, "Debit", "Banco Norte", AccountType.DEBIT, CurrencyCode.MXN, "1000", FROM);
        FinancialAccount cash = account(owner, "Cash", "Banco Centro", AccountType.CASH, CurrencyCode.MXN, "500", FROM);
        FinancialAccount investment = account(owner, "Investment", "Broker USD", AccountType.INVESTMENT, CurrencyCode.USD, "100", FROM);
        FinancialAccount laterAccount = account(
            owner,
            "Later account",
            "Banco Norte",
            AccountType.DEBIT,
            CurrencyCode.MXN,
            "500",
            LocalDate.of(2026, 3, 1)
        );
        FinancialAccount foreignAccount = account(foreignOwner, "Private", "Private Bank", AccountType.DEBIT, CurrencyCode.MXN, "0", FROM);

        Tag firstTag = tag(owner, "first");
        Tag secondTag = tag(owner, "second");
        FinancialTransaction janFood = transaction(debit, food, TransactionFlow.OUT, "100", LocalDate.of(2026, 1, 15));
        janFood.addTags(firstTag).addTags(secondTag);
        transactionRepository.saveAndFlush(janFood);
        transactionRepository.saveAndFlush(transaction(debit, food, TransactionFlow.IN, "1000", LocalDate.of(2026, 1, 10)));
        transactionRepository.saveAndFlush(transaction(debit, food, TransactionFlow.OUT, "300", LocalDate.of(2026, 2, 10)));
        transactionRepository.saveAndFlush(transaction(debit, travel, TransactionFlow.OUT, "50", LocalDate.of(2026, 3, 5)));
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
        transactionRepository.saveAndFlush(transaction(investment, food, TransactionFlow.OUT, "20", LocalDate.of(2026, 2, 12)));
        transactionRepository.saveAndFlush(transaction(foreignAccount, food, TransactionFlow.OUT, "999", LocalDate.of(2026, 2, 1)));
        return new Fixture(debit, investment, food, travel, laterAccount, foreignOwner);
    }

    private SemanticQueryDefinition ledgerDefinition(
        SemanticAggregateProjection projection,
        List<SemanticPredicate> predicates,
        List<SemanticField> groups,
        List<SemanticOrder> order
    ) {
        return new SemanticQueryDefinition(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticDataset.TRANSACTION_LEDGER,
            SemanticField.TRANSACTION_DATE,
            List.of(projection),
            predicates,
            groups,
            order,
            100
        );
    }

    private SemanticAnalyticsResult execute(SemanticQueryDefinition definition, List<Long> accountIds, List<String> currencies) {
        return execute(definition, accountIds, currencies, FROM, THROUGH);
    }

    private SemanticAnalyticsResult execute(
        SemanticQueryDefinition definition,
        List<Long> accountIds,
        List<String> currencies,
        LocalDate from,
        LocalDate through
    ) {
        return executionService.execute(
            new SemanticAnalyticsExecutionRequest(
                SemanticAnalyticsSchema.VERSION,
                definition,
                new VisualizationContext(
                    SemanticAnalyticsSchema.VERSION,
                    new VisualizationTime("ABSOLUTE_RANGE", null, from, through),
                    accountIds,
                    currencies
                )
            )
        );
    }

    private SemanticAnalyticsResult executeRoundTrip(String text, List<Long> accountIds, List<String> currencies) {
        SemanticAnalyticsDslParseResult parsed = dslService.parse(text);
        assertThat(dslService.parse(parsed.canonicalText()).definition()).isEqualTo(parsed.definition());
        assertThat(dslService.format(parsed.definition())).isEqualTo(parsed.canonicalText());
        return execute(parsed.definition(), accountIds, currencies);
    }

    private void assertValue(
        SemanticAnalyticsResult result,
        SemanticField field,
        Object expectedDimension,
        String currency,
        String expectedValue
    ) {
        SemanticAnalyticsResultRow row = result
            .rows()
            .stream()
            .filter(candidate -> java.util.Objects.equals(dimension(candidate, field), expectedDimension))
            .filter(candidate -> java.util.Objects.equals(candidate.currencyCode(), currency))
            .findFirst()
            .orElseThrow();
        assertThat(row.values()).singleElement().satisfies(value -> assertThat(value.value()).isEqualByComparingTo(expectedValue));
    }

    private void assertPosition(
        SemanticAnalyticsResult result,
        Long accountId,
        LocalDate month,
        String expectedValue,
        SemanticResultAvailability availability
    ) {
        SemanticAnalyticsResultRow row = result
            .rows()
            .stream()
            .filter(candidate -> java.util.Objects.equals(dimension(candidate, SemanticField.ACCOUNT_ID), accountId))
            .filter(candidate -> java.util.Objects.equals(dimension(candidate, SemanticField.OBSERVATION_MONTH), month))
            .findFirst()
            .orElseThrow();
        assertThat(row.values())
            .singleElement()
            .satisfies(value -> {
                assertThat(value.availability()).isEqualTo(availability);
                if (expectedValue == null) {
                    assertThat(value.value()).isNull();
                } else {
                    assertThat(value.value()).isEqualByComparingTo(expectedValue);
                }
            });
    }

    private void assertTupleValue(
        SemanticAnalyticsResult result,
        SemanticField firstField,
        Object firstValue,
        SemanticField secondField,
        Object secondValue,
        String currency,
        String expectedValue
    ) {
        assertThat(value(row(result, currency, firstField, firstValue, secondField, secondValue), null).value()).isEqualByComparingTo(
            expectedValue
        );
    }

    private SemanticAnalyticsResultRow row(
        SemanticAnalyticsResult result,
        String currency,
        SemanticField firstField,
        Object firstValue,
        SemanticField secondField,
        Object secondValue
    ) {
        return result
            .rows()
            .stream()
            .filter(candidate -> java.util.Objects.equals(candidate.currencyCode(), currency))
            .filter(candidate -> java.util.Objects.equals(dimension(candidate, firstField), firstValue))
            .filter(candidate -> secondField == null || java.util.Objects.equals(dimension(candidate, secondField), secondValue))
            .findFirst()
            .orElseThrow();
    }

    private SemanticAnalyticsResultValue value(SemanticAnalyticsResultRow row, String alias) {
        if (alias == null) return row.values().get(0);
        return row.values().stream().filter(value -> value.alias().equals(alias)).findFirst().orElseThrow();
    }

    private Object dimension(SemanticAnalyticsResultRow row, SemanticField field) {
        return row
            .dimensions()
            .stream()
            .filter(value -> value.field() == field)
            .map(SemanticAnalyticsResultDimension::value)
            .findFirst()
            .orElse(null);
    }

    private FinancialAccount account(
        User owner,
        String name,
        String institution,
        AccountType type,
        CurrencyCode currency,
        String initialBalance,
        LocalDate initialBalanceDate
    ) {
        return accountRepository.saveAndFlush(
            new FinancialAccount()
                .name(name)
                .institutionName(institution)
                .accountType(type)
                .currency(currency)
                .initialBalance(new BigDecimal(initialBalance))
                .initialBalanceDate(initialBalanceDate)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .user(owner)
        );
    }

    private Category category(User owner, String name) {
        return category(owner, name, null);
    }

    private Category category(User owner, String name, Category parentCategory) {
        return categoryRepository.saveAndFlush(
            new Category()
                .name(name)
                .categoryType(CategoryType.EXPENSE)
                .color("#225588")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .user(owner)
                .parentCategory(parentCategory)
        );
    }

    private User foreignUser() {
        User user = new User();
        user.setLogin("semantic-private-owner");
        user.setPassword("x".repeat(60));
        user.setActivated(true);
        user.setEmail("semantic-private-owner@example.invalid");
        user.setLangKey("en");
        em.persist(user);
        em.flush();
        return user;
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
        LocalDate transactionDate
    ) {
        return new FinancialTransaction()
            .account(account)
            .category(category)
            .transactionDate(transactionDate)
            .postingDate(transactionDate)
            .description("fixture " + amount)
            .amount(new BigDecimal(amount))
            .flow(flow)
            .origin(TransactionOrigin.MANUAL)
            .createdAt(Instant.now())
            .updatedAt(Instant.now());
    }

    private record Fixture(
        FinancialAccount debit,
        FinancialAccount investment,
        Category food,
        Category travel,
        FinancialAccount laterAccount,
        User foreignOwner
    ) {}
}
