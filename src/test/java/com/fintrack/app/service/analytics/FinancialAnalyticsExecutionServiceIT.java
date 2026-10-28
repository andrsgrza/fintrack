package com.fintrack.app.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.CreditAccountDetails;
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
import com.fintrack.app.repository.CreditAccountDetailsRepository;
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
 * Golden PostgreSQL fixtures for the canonical R3 executor. Expected values below are independently calculated from
 * the ledger fixture; they do not call an executor or a production balance API to establish an assertion.
 */
@IntegrationTest
@Transactional
@WithMockUser(username = "user")
class FinancialAnalyticsExecutionServiceIT {

    @Autowired
    private FinancialAnalyticsExecutionService executionService;

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
    private CreditAccountDetailsRepository creditDetailsRepository;

    @Autowired
    private EntityManager em;

    @Test
    void calculatesEveryEconomicAndAccountMovementMetricWithLinkedTransferTreatment() {
        Fixture fixture = fixture();

        FinancialAnalyticsResult economic = execute(
            List.of("INCOME_TOTAL", "EXPENSE_TOTAL", "NET_ECONOMIC_FLOW"),
            List.of(fixture.debit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today().minusDays(5),
            fixture.today()
        );
        assertValue(economic, "MXN", "INCOME_TOTAL", "1000");
        assertValue(economic, "MXN", "EXPENSE_TOTAL", "100");
        assertValue(economic, "MXN", "NET_ECONOMIC_FLOW", "900");

        FinancialAnalyticsResult count = execute(
            List.of("ECONOMIC_TRANSACTION_COUNT"),
            List.of(fixture.debit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today().minusDays(5),
            fixture.today()
        );
        assertValue(count, null, "ECONOMIC_TRANSACTION_COUNT", "2");

        FinancialAnalyticsResult average = execute(
            List.of("AVERAGE_EXPENSE"),
            List.of(fixture.debit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today().minusDays(5),
            fixture.today()
        );
        assertValue(average, "MXN", "AVERAGE_EXPENSE", "100");

        FinancialAnalyticsResult movement = execute(
            List.of("ACCOUNT_INFLOW", "ACCOUNT_OUTFLOW", "ACCOUNT_NET_MOVEMENT"),
            List.of(fixture.debit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today().minusDays(5),
            fixture.today()
        );
        // Account movement includes the MXN 200 transfer leg, unlike economic expense.
        assertValue(movement, "MXN", "ACCOUNT_INFLOW", "1000");
        assertValue(movement, "MXN", "ACCOUNT_OUTFLOW", "300");
        assertValue(movement, "MXN", "ACCOUNT_NET_MOVEMENT", "700");
    }

    @Test
    void recalculatesEconomicClassificationWhenATransferIsUnlinkedAndCountsTheRelationOnce() {
        Fixture fixture = fixture();
        FinancialAnalyticsResult before = execute(
            List.of("EXPENSE_TOTAL"),
            List.of(fixture.debit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today().minusDays(5),
            fixture.today()
        );
        assertValue(before, "MXN", "EXPENSE_TOTAL", "100");

        FinancialAnalyticsResult transferCount = execute(
            List.of("INTERNAL_TRANSFER_COUNT"),
            List.of(fixture.debit().getId(), fixture.credit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today().minusDays(5),
            fixture.today()
        );
        assertValue(transferCount, null, "INTERNAL_TRANSFER_COUNT", "1");

        transferRepository.delete(fixture.transfer());
        transferRepository.flush();

        FinancialAnalyticsResult after = execute(
            List.of("EXPENSE_TOTAL"),
            List.of(fixture.debit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today().minusDays(5),
            fixture.today()
        );
        assertValue(after, "MXN", "EXPENSE_TOTAL", "300");
    }

    @Test
    void calculatesAllPositionAndCreditMetricsFromInitialBalancesAndCurrentLedger() {
        Fixture fixture = fixture();

        FinancialAnalyticsResult positions = execute(
            List.of("ACCOUNT_BALANCE", "ACCOUNT_SIGNED_POSITION", "NET_RECORDED_POSITION"),
            List.of(fixture.debit().getId(), fixture.credit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today(),
            fixture.today()
        );
        // Debit: 100 initial + 1,000 IN - 100 expense - 200 transfer = 800. Credit debt: 300 OUT - 200 IN = 100.
        assertValue(positions, "MXN", "ACCOUNT_BALANCE", "800");
        assertState(positions, "MXN", "ACCOUNT_BALANCE", FinancialMetricResultState.PARTIAL);
        assertValue(positions, "MXN", "ACCOUNT_SIGNED_POSITION", "700");
        assertValue(positions, "MXN", "NET_RECORDED_POSITION", "700");

        FinancialAnalyticsResult creditMoney = execute(
            List.of("CREDIT_CARD_DEBT", "CREDIT_LIMIT", "AVAILABLE_CREDIT"),
            List.of(fixture.credit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today(),
            fixture.today()
        );
        assertValue(creditMoney, "MXN", "CREDIT_CARD_DEBT", "100");
        assertValue(creditMoney, "MXN", "CREDIT_LIMIT", "1000");
        assertValue(creditMoney, "MXN", "AVAILABLE_CREDIT", "900");

        FinancialAnalyticsResult utilization = execute(
            List.of("CREDIT_UTILIZATION"),
            List.of(fixture.credit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today(),
            fixture.today()
        );
        assertValue(utilization, "MXN", "CREDIT_UTILIZATION", "0.1");
    }

    @Test
    void preservesHistoricalAvailabilityAndInvestmentBookBalancesWithoutInventingCreditHistory() {
        Fixture fixture = fixture();
        LocalDate beforeOpening = fixture.today().minusDays(40);

        FinancialAnalyticsResult beforeOpeningResult = execute(
            List.of("ACCOUNT_BALANCE"),
            List.of(fixture.debit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            beforeOpening,
            beforeOpening
        );
        assertState(beforeOpeningResult, "MXN", "ACCOUNT_BALANCE", FinancialMetricResultState.UNAVAILABLE);

        FinancialAnalyticsResult historicalCredit = execute(
            List.of("CREDIT_CARD_DEBT", "CREDIT_LIMIT", "AVAILABLE_CREDIT"),
            List.of(fixture.credit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today().minusDays(1),
            fixture.today().minusDays(1)
        );
        // The purchase predates the linked repayment, so historical debt remains independently calculable.
        assertValue(historicalCredit, "MXN", "CREDIT_CARD_DEBT", "300");
        assertState(historicalCredit, "MXN", "CREDIT_LIMIT", FinancialMetricResultState.UNAVAILABLE);
        assertState(historicalCredit, "MXN", "AVAILABLE_CREDIT", FinancialMetricResultState.UNAVAILABLE);

        FinancialAnalyticsResult investment = execute(
            List.of("ACCOUNT_BALANCE"),
            List.of(fixture.investment().getId()),
            List.of(),
            List.of(),
            List.of("USD"),
            fixture.today(),
            fixture.today()
        );
        assertValue(investment, "USD", "ACCOUNT_BALANCE", "480");

        FinancialAnalyticsResult cash = execute(
            List.of("ACCOUNT_BALANCE"),
            List.of(fixture.cash().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today(),
            fixture.today()
        );
        assertValue(cash, "MXN", "ACCOUNT_BALANCE", "50");

        FinancialAnalyticsResult debtWithoutLimit = execute(
            List.of("CREDIT_CARD_DEBT"),
            List.of(fixture.creditWithoutLimit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today(),
            fixture.today()
        );
        assertValue(debtWithoutLimit, "MXN", "CREDIT_CARD_DEBT", "75");

        FinancialAnalyticsResult overLimit = execute(
            List.of("CREDIT_UTILIZATION"),
            List.of(fixture.overLimitCredit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today(),
            fixture.today()
        );
        assertValue(overLimit, "MXN", "CREDIT_UTILIZATION", "1.5");
    }

    @Test
    void calculatesAsOfTimeSeriesFromHistoricalLedgerPositions() {
        Fixture fixture = fixture();
        FinancialAnalyticsResult series = execute(
            List.of("ACCOUNT_BALANCE"),
            List.of(fixture.debit().getId()),
            List.of(FinancialSemanticCatalog.DAY),
            List.of(),
            List.of("MXN"),
            fixture.today().minusDays(1),
            fixture.today()
        );

        assertThat(series.rows()).hasSize(2);
        assertValueForDay(series, fixture.today().minusDays(1), "ACCOUNT_BALANCE", "1000");
        assertValueForDay(series, fixture.today(), "ACCOUNT_BALANCE", "800");
    }

    @Test
    void partitionsCurrenciesAndProtectsCategoryAndOverlappingTagAggregates() {
        Fixture fixture = fixture();
        FinancialAnalyticsResult multiCurrency = execute(
            List.of("EXPENSE_TOTAL"),
            List.of(fixture.debit().getId(), fixture.investment().getId()),
            List.of(),
            List.of(),
            List.of("MXN", "USD"),
            fixture.today().minusDays(5),
            fixture.today()
        );
        assertThat(multiCurrency.rows()).hasSize(2);
        assertValue(multiCurrency, "MXN", "EXPENSE_TOTAL", "100");
        assertValue(multiCurrency, "USD", "EXPENSE_TOTAL", "20");

        FinancialAnalyticsResult byRoot = execute(
            List.of("EXPENSE_TOTAL"),
            List.of(fixture.debit().getId()),
            List.of(FinancialSemanticCatalog.ROOT_CATEGORY),
            List.of(),
            List.of("MXN"),
            fixture.today().minusDays(5),
            fixture.today()
        );
        assertThat(byRoot.rows())
            .filteredOn(row ->
                row
                    .dimensions()
                    .contains(new FinancialAnalyticsDimensionValue(FinancialSemanticCatalog.ROOT_CATEGORY, fixture.root().getId()))
            )
            .singleElement()
            .satisfies(row -> assertThat(measure(row, "EXPENSE_TOTAL").value()).isEqualByComparingTo("100"));

        FinancialAnalyticsResult byTag = execute(
            List.of("EXPENSE_TOTAL"),
            List.of(fixture.debit().getId()),
            List.of(FinancialSemanticCatalog.TAG),
            List.of(),
            List.of("MXN"),
            fixture.today().minusDays(5),
            fixture.today()
        );
        assertThat(byTag.rows()).filteredOn(row -> measure(row, "EXPENSE_TOTAL").value().compareTo(new BigDecimal("100")) == 0).hasSize(2);
        assertThat(byTag.warnings())
            .extracting(FinancialAnalyticsWarning::code)
            .contains(AnalyticsDiagnosticCode.NON_ADDITIVE_TAG_GROUPING);
    }

    @Test
    void appliesTransactionOrPostingDateAndKeepsForeignOwnedDataOutsideTheResult() {
        Fixture fixture = fixture();
        transactionRepository.saveAndFlush(
            transaction(fixture.debit(), TransactionFlow.OUT, "50", "No posting date", fixture.today(), null)
        );
        FinancialAnalyticsResult posting = execute(
            List.of("EXPENSE_TOTAL"),
            List.of(fixture.debit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today().minusDays(5),
            fixture.today(),
            FinancialSemanticCatalog.POSTING_DATE
        );
        assertValue(posting, "MXN", "EXPENSE_TOTAL", "100");
        assertThat(posting.warnings())
            .extracting(FinancialAnalyticsWarning::code)
            .contains(AnalyticsDiagnosticCode.POSTING_DATE_OMITS_MISSING_VALUES);

        FinancialAccount foreign = account(
            user("admin"),
            "Foreign analytics",
            AccountType.DEBIT,
            CurrencyCode.MXN,
            BigDecimal.ZERO,
            fixture.today().minusDays(1)
        );
        transactionRepository.saveAndFlush(transaction(foreign, TransactionFlow.OUT, "999", "Foreign", fixture.today(), fixture.today()));
        FinancialAnalyticsResult own = execute(
            List.of("EXPENSE_TOTAL"),
            List.of(fixture.debit().getId()),
            List.of(),
            List.of(),
            List.of("MXN"),
            fixture.today().minusDays(5),
            fixture.today()
        );
        assertValue(own, "MXN", "EXPENSE_TOTAL", "150");
    }

    private Fixture fixture() {
        LocalDate today = LocalDate.now(FinancialMetricTimePolicy.BUSINESS_ZONE);
        User owner = user("user");
        FinancialAccount debit = account(
            owner,
            "Analytics debit " + System.nanoTime(),
            AccountType.DEBIT,
            CurrencyCode.MXN,
            new BigDecimal("100"),
            today.minusDays(30)
        );
        FinancialAccount cash = account(
            owner,
            "Analytics cash " + System.nanoTime(),
            AccountType.CASH,
            CurrencyCode.MXN,
            new BigDecimal("50"),
            today.minusDays(30)
        );
        FinancialAccount credit = account(
            owner,
            "Analytics credit " + System.nanoTime(),
            AccountType.CREDIT_CARD,
            CurrencyCode.MXN,
            BigDecimal.ZERO,
            today.minusDays(30)
        );
        FinancialAccount creditWithoutLimit = account(
            owner,
            "Analytics credit without limit " + System.nanoTime(),
            AccountType.CREDIT_CARD,
            CurrencyCode.MXN,
            BigDecimal.ZERO,
            today.minusDays(30)
        );
        FinancialAccount overLimitCredit = account(
            owner,
            "Analytics over-limit credit " + System.nanoTime(),
            AccountType.CREDIT_CARD,
            CurrencyCode.MXN,
            BigDecimal.ZERO,
            today.minusDays(30)
        );
        FinancialAccount investment = account(
            owner,
            "Analytics investment " + System.nanoTime(),
            AccountType.INVESTMENT,
            CurrencyCode.USD,
            new BigDecimal("500"),
            today.minusDays(30)
        );
        creditDetailsRepository.saveAndFlush(creditDetails(credit, "1000"));
        creditDetailsRepository.saveAndFlush(creditDetails(overLimitCredit, "100"));
        Category root = category(owner, "Analytics root " + System.nanoTime(), null);
        Category child = category(owner, "Analytics child " + System.nanoTime(), root);
        Tag tagOne = tag(owner, "Analytics one " + System.nanoTime());
        Tag tagTwo = tag(owner, "Analytics two " + System.nanoTime());
        transactionRepository.saveAndFlush(
            transaction(debit, TransactionFlow.IN, "1000", "Salary", today.minusDays(2), today.minusDays(2))
        );
        FinancialTransaction categorized = transaction(
            debit,
            TransactionFlow.OUT,
            "100",
            "Groceries",
            today.minusDays(1),
            today.minusDays(1)
        );
        categorized.setCategory(child);
        categorized.addTags(tagOne).addTags(tagTwo);
        transactionRepository.saveAndFlush(categorized);
        FinancialTransaction outgoing = transaction(debit, TransactionFlow.OUT, "200", "Card payment", today, today);
        FinancialTransaction incoming = transaction(credit, TransactionFlow.IN, "200", "Card payment", today, today);
        transactionRepository.saveAndFlush(outgoing);
        transactionRepository.saveAndFlush(incoming);
        InternalTransfer transfer = transferRepository.saveAndFlush(
            new InternalTransfer()
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .outgoingTransaction(outgoing)
                .incomingTransaction(incoming)
        );
        transactionRepository.saveAndFlush(
            transaction(credit, TransactionFlow.OUT, "300", "Card purchase", today.minusDays(1), today.minusDays(1))
        );
        transactionRepository.saveAndFlush(transaction(creditWithoutLimit, TransactionFlow.OUT, "75", "Card without limit", today, today));
        transactionRepository.saveAndFlush(transaction(overLimitCredit, TransactionFlow.OUT, "150", "Over limit card", today, today));
        transactionRepository.saveAndFlush(transaction(investment, TransactionFlow.OUT, "20", "Book-balance expense", today, today));
        return new Fixture(today, debit, cash, credit, creditWithoutLimit, overLimitCredit, investment, root, transfer);
    }

    private FinancialAnalyticsResult execute(
        List<String> measures,
        List<Long> accountIds,
        List<String> groupings,
        List<AnalyticalPredicate> predicates,
        List<String> currencies,
        LocalDate from,
        LocalDate through
    ) {
        return execute(measures, accountIds, groupings, predicates, currencies, from, through, FinancialSemanticCatalog.TRANSACTION_DATE);
    }

    private FinancialAnalyticsResult execute(
        List<String> measures,
        List<Long> accountIds,
        List<String> groupings,
        List<AnalyticalPredicate> predicates,
        List<String> currencies,
        LocalDate from,
        LocalDate through,
        String dateBasis
    ) {
        return executionService.execute(
            new FinancialAnalyticsExecutionRequest(
                AnalyticsSchema.VERSION,
                new QueryDefinition(
                    AnalyticsSchema.VERSION,
                    AnalyticsSchema.SEMANTIC_MODEL,
                    measures,
                    groupings,
                    predicates,
                    dateBasis,
                    null,
                    AnalyticsSchema.DEFAULT_LIMIT
                ),
                new VisualizationContext(
                    AnalyticsSchema.VERSION,
                    new VisualizationTime("ABSOLUTE_RANGE", null, from, through),
                    accountIds,
                    currencies
                )
            )
        );
    }

    private void assertValue(FinancialAnalyticsResult result, String currency, String measure, String expected) {
        FinancialAnalyticsMeasureValue value = result
            .rows()
            .stream()
            .filter(row -> java.util.Objects.equals(currency, row.currencyCode()))
            .findFirst()
            .map(row -> measure(row, measure))
            .orElseThrow();
        assertThat(value.value()).isEqualByComparingTo(expected);
    }

    private void assertState(FinancialAnalyticsResult result, String currency, String measure, FinancialMetricResultState expected) {
        assertThat(
            result
                .rows()
                .stream()
                .filter(row -> java.util.Objects.equals(currency, row.currencyCode()))
                .findFirst()
                .map(row -> measure(row, measure))
                .orElseThrow()
                .state()
        ).isEqualTo(expected);
    }

    private FinancialAnalyticsMeasureValue measure(FinancialAnalyticsResultRow row, String id) {
        return row.measures().stream().filter(value -> id.equals(value.measureId())).findFirst().orElseThrow();
    }

    private void assertValueForDay(FinancialAnalyticsResult result, LocalDate day, String measure, String expected) {
        BigDecimal value = result
            .rows()
            .stream()
            .filter(row -> row.dimensions().contains(new FinancialAnalyticsDimensionValue(FinancialSemanticCatalog.DAY, day)))
            .map(row -> measure(row, measure).value())
            .findFirst()
            .orElseThrow();
        assertThat(value).isEqualByComparingTo(expected);
    }

    private User user(String login) {
        return TestUtil.findAll(em, User.class).stream().filter(user -> login.equals(user.getLogin())).findFirst().orElseThrow();
    }

    private FinancialAccount account(
        User user,
        String name,
        AccountType type,
        CurrencyCode currency,
        BigDecimal initial,
        LocalDate initialDate
    ) {
        return accountRepository.saveAndFlush(
            new FinancialAccount()
                .name(name)
                .accountType(type)
                .currency(currency)
                .initialBalance(initial)
                .initialBalanceDate(initialDate)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .user(user)
        );
    }

    private FinancialTransaction transaction(
        FinancialAccount account,
        TransactionFlow flow,
        String amount,
        String description,
        LocalDate transactionDate,
        LocalDate postingDate
    ) {
        return new FinancialTransaction()
            .account(account)
            .transactionDate(transactionDate)
            .postingDate(postingDate)
            .description(description)
            .amount(new BigDecimal(amount))
            .flow(flow)
            .origin(TransactionOrigin.MANUAL)
            .createdAt(Instant.now())
            .updatedAt(Instant.now());
    }

    private CreditAccountDetails creditDetails(FinancialAccount account, String limit) {
        return new CreditAccountDetails()
            .account(account)
            .creditLimit(new BigDecimal(limit))
            .statementDay(1)
            .paymentDueDay(10)
            .createdAt(Instant.now())
            .updatedAt(Instant.now());
    }

    private Category category(User owner, String name, Category parent) {
        return categoryRepository.saveAndFlush(
            new Category()
                .name(name)
                .categoryType(CategoryType.EXPENSE)
                .color("#224466")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .user(owner)
                .parentCategory(parent)
        );
    }

    private Tag tag(User owner, String name) {
        return tagRepository.saveAndFlush(
            new Tag().name(name).color("#224466").active(true).createdAt(Instant.now()).updatedAt(Instant.now()).user(owner)
        );
    }

    private record Fixture(
        LocalDate today,
        FinancialAccount debit,
        FinancialAccount cash,
        FinancialAccount credit,
        FinancialAccount creditWithoutLimit,
        FinancialAccount overLimitCredit,
        FinancialAccount investment,
        Category root,
        InternalTransfer transfer
    ) {}
}
