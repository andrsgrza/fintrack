package com.fintrack.app.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.CreditAccountDetailsRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.InternalTransferRepository;
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

/** PostgreSQL evidence for governed point-in-time credit and one-event transfer analytics. */
@IntegrationTest
@Transactional
@WithMockUser(username = "user")
class SemanticSpecializedAnalyticsExecutionServiceIT {

    private static final LocalDate HISTORY_FROM = LocalDate.of(2025, 12, 1);
    private static final LocalDate HISTORY_THROUGH = LocalDate.of(2026, 3, 31);

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
    private CreditAccountDetailsRepository creditDetailsRepository;

    @Autowired
    private EntityManager em;

    @Test
    void calculates_historical_credit_debt_without_using_credit_limit_history_and_preserves_unavailable_opening_periods() {
        Fixture fixture = fixture();
        SemanticQueryDefinition debtByMonth = creditDefinition(
            List.of(new SemanticAggregateProjection("debt", SemanticAggregateFunction.SUM, SemanticField.CREDIT_DEBT)),
            List.of(SemanticField.ACCOUNT_ID, SemanticField.OBSERVATION_MONTH)
        );

        SemanticAnalyticsResult result = execute(
            debtByMonth,
            List.of(fixture.creditWithLimit().getId()),
            List.of("MXN"),
            HISTORY_FROM,
            HISTORY_THROUGH
        );

        // Opening debt is MXN 100; +300 OUT -50 IN +900 OUT yields 400, 350 and 1,250 respectively.
        assertCreditValue(
            result,
            fixture.creditWithLimit().getId(),
            LocalDate.of(2025, 12, 1),
            "debt",
            null,
            SemanticResultAvailability.UNAVAILABLE
        );
        assertCreditValue(
            result,
            fixture.creditWithLimit().getId(),
            LocalDate.of(2026, 1, 1),
            "debt",
            "400",
            SemanticResultAvailability.COMPLETE
        );
        assertCreditValue(
            result,
            fixture.creditWithLimit().getId(),
            LocalDate.of(2026, 2, 1),
            "debt",
            "350",
            SemanticResultAvailability.COMPLETE
        );
        assertCreditValue(
            result,
            fixture.creditWithLimit().getId(),
            LocalDate.of(2026, 3, 1),
            "debt",
            "1250",
            SemanticResultAvailability.COMPLETE
        );
        assertThat(result.warnings()).extracting(SemanticAnalyticsWarning::code).contains("CREDIT_DEBT_POINT_IN_TIME");
    }

    @Test
    void exposes_current_limit_available_credit_and_uncapped_utilization_but_never_invents_limit_history() {
        Fixture fixture = fixture();
        SemanticQueryDefinition currentCredit = creditDefinition(
            List.of(
                new SemanticAggregateProjection("debt", SemanticAggregateFunction.SUM, SemanticField.CREDIT_DEBT),
                new SemanticAggregateProjection("limit", SemanticAggregateFunction.SUM, SemanticField.CREDIT_LIMIT),
                new SemanticAggregateProjection("available", SemanticAggregateFunction.SUM, SemanticField.CREDIT_AVAILABLE),
                new SemanticAggregateProjection("utilization", SemanticAggregateFunction.SUM, SemanticField.CREDIT_UTILIZATION_PERCENT)
            ),
            List.of(SemanticField.ACCOUNT_ID)
        );
        SemanticAnalyticsResult current = executeCurrent(currentCredit, List.of(fixture.creditWithLimit().getId()), List.of("MXN"));
        SemanticAnalyticsResultRow row = accountRow(current, fixture.creditWithLimit().getId());
        assertThat(value(row, "debt").value()).isEqualByComparingTo("1250");
        assertThat(value(row, "limit").value()).isEqualByComparingTo("1000");
        assertThat(value(row, "available").value()).isEqualByComparingTo("-250");
        assertThat(value(row, "utilization").value()).isEqualByComparingTo("125.0000");
        assertThat(value(row, "utilization").unit()).isEqualTo(SemanticUnit.PERCENT);

        assertThatThrownBy(() ->
            execute(currentCredit, List.of(fixture.creditWithLimit().getId()), List.of("MXN"), HISTORY_FROM, HISTORY_THROUGH)
        )
            .isInstanceOf(SemanticAnalyticsValidationException.class)
            .satisfies(error ->
                assertThat(((SemanticAnalyticsValidationException) error).getDiagnostics())
                    .extracting(AnalyticsDiagnostic::code)
                    .contains(AnalyticsDiagnosticCode.INCOMPATIBLE_HISTORICAL_CREDIT_LIMIT)
            );
    }

    @Test
    void distinguishes_missing_details_zero_limit_zero_debt_and_non_credit_account_applicability() {
        Fixture fixture = fixture();
        SemanticQueryDefinition currentCredit = creditDefinition(
            List.of(
                new SemanticAggregateProjection("debt", SemanticAggregateFunction.SUM, SemanticField.CREDIT_DEBT),
                new SemanticAggregateProjection("limit", SemanticAggregateFunction.SUM, SemanticField.CREDIT_LIMIT),
                new SemanticAggregateProjection("available", SemanticAggregateFunction.SUM, SemanticField.CREDIT_AVAILABLE),
                new SemanticAggregateProjection("utilization", SemanticAggregateFunction.SUM, SemanticField.CREDIT_UTILIZATION_PERCENT)
            ),
            List.of(SemanticField.ACCOUNT_ID)
        );
        SemanticAnalyticsResult result = executeCurrent(
            currentCredit,
            List.of(fixture.creditWithoutDetails().getId(), fixture.creditZeroLimit().getId(), fixture.creditUsdZeroDebt().getId()),
            List.of()
        );
        SemanticAnalyticsResultRow noDetails = accountRow(result, fixture.creditWithoutDetails().getId());
        assertThat(value(noDetails, "debt").value()).isEqualByComparingTo("75");
        assertThat(value(noDetails, "limit").availability()).isEqualTo(SemanticResultAvailability.UNAVAILABLE);
        assertThat(value(noDetails, "available").availability()).isEqualTo(SemanticResultAvailability.UNAVAILABLE);
        assertThat(value(noDetails, "utilization").availability()).isEqualTo(SemanticResultAvailability.UNAVAILABLE);

        SemanticAnalyticsResultRow zeroLimit = accountRow(result, fixture.creditZeroLimit().getId());
        assertThat(value(zeroLimit, "debt").value()).isEqualByComparingTo("20");
        assertThat(value(zeroLimit, "limit").availability()).isEqualTo(SemanticResultAvailability.ZERO);
        assertThat(value(zeroLimit, "available").value()).isEqualByComparingTo("-20");
        assertThat(value(zeroLimit, "utilization").availability()).isEqualTo(SemanticResultAvailability.UNAVAILABLE);

        SemanticAnalyticsResultRow zeroDebt = accountRow(result, fixture.creditUsdZeroDebt().getId());
        assertThat(zeroDebt.currencyCode()).isEqualTo("USD");
        assertThat(value(zeroDebt, "debt").availability()).isEqualTo(SemanticResultAvailability.ZERO);
        assertThat(value(zeroDebt, "available").value()).isEqualByComparingTo("500");
        assertThat(value(zeroDebt, "utilization").availability()).isEqualTo(SemanticResultAvailability.ZERO);
        assertThat(result.warnings())
            .extracting(SemanticAnalyticsWarning::code)
            .contains("CREDIT_DETAILS_UNAVAILABLE", "CREDIT_UTILIZATION_UNAVAILABLE");

        SemanticAnalyticsResult notApplicable = executeCurrent(currentCredit, List.of(fixture.transferSource().getId()), List.of("MXN"));
        assertThat(notApplicable.availability()).isEqualTo(SemanticResultAvailability.NOT_APPLICABLE);
        assertThat(notApplicable.warnings()).extracting(SemanticAnalyticsWarning::code).containsExactly("NO_APPLICABLE_CREDIT_ACCOUNTS");
    }

    @Test
    void counts_and_sums_each_transfer_event_once_with_endpoint_context_temporal_dimensions_and_currency_partitions() {
        Fixture fixture = fixture();
        SemanticQueryDefinition byMonthAndDestination = transferDefinition(
            List.of(
                new SemanticAggregateProjection("transfer_count", SemanticAggregateFunction.COUNT, SemanticField.TRANSFER_ID),
                new SemanticAggregateProjection("transfer_volume", SemanticAggregateFunction.SUM, SemanticField.TRANSFER_AMOUNT)
            ),
            List.of(SemanticField.TRANSFER_MONTH, SemanticField.DESTINATION_ACCOUNT_ID)
        );
        SemanticAnalyticsResult bothEndpoints = execute(
            byMonthAndDestination,
            List.of(fixture.transferSource().getId(), fixture.transferDestination().getId(), fixture.usdTransferSource().getId()),
            List.of(),
            HISTORY_FROM,
            HISTORY_THROUGH
        );
        // Selecting both MXN endpoints still yields one February MXN 250 event, never its two legs.
        assertTransferValues(bothEndpoints, "2026-02", fixture.transferDestination().getId(), "MXN", "1", "250");
        assertTransferValues(bothEndpoints, "2026-03", fixture.transferDestination().getId(), "MXN", "1", "100");
        assertTransferValues(bothEndpoints, "2026-02", fixture.usdTransferDestination().getId(), "USD", "1", "40");
        assertThat(bothEndpoints.warnings())
            .extracting(SemanticAnalyticsWarning::code)
            .containsExactly("TRANSFER_EVENT_VOLUME_NOT_ECONOMIC_FLOW");

        SemanticAnalyticsResult sourceOnly = execute(
            byMonthAndDestination,
            List.of(fixture.transferSource().getId()),
            List.of("MXN"),
            HISTORY_FROM,
            HISTORY_THROUGH
        );
        assertThat(sourceOnly.rows()).hasSize(2);
        assertTransferValues(sourceOnly, "2026-02", fixture.transferDestination().getId(), "MXN", "1", "250");

        SemanticAnalyticsResult week = executeRoundTrip(
            "SELECT COUNT(TRANSFER_ID) AS count FROM INTERNAL_TRANSFER_EVENT " +
            "GROUP BY TRANSFER_WEEK, SOURCE_ACCOUNT_INSTITUTION LIMIT 100",
            List.of(fixture.weekTransferSource().getId()),
            List.of("MXN"),
            LocalDate.of(2025, 12, 29),
            LocalDate.of(2026, 1, 3)
        );
        assertThat(
            row(week, "MXN", SemanticField.TRANSFER_WEEK, "2026-W01", SemanticField.SOURCE_ACCOUNT_INSTITUTION, "Week Source")
                .values()
                .get(0)
                .value()
        ).isEqualByComparingTo("1");
    }

    @Test
    void parses_formats_and_executes_credit_and_transfer_queries_through_the_single_semantic_pipeline() {
        Fixture fixture = fixture();
        SemanticAnalyticsResult debt = executeRoundTrip(
            "SELECT SUM(CREDIT_DEBT) AS debt FROM CREDIT_ACCOUNT_SNAPSHOT TIME BINDING OBSERVATION_DATE " +
            "GROUP BY ACCOUNT_ID, OBSERVATION_MONTH ORDER BY OBSERVATION_MONTH ASC LIMIT 100",
            List.of(fixture.creditWithLimit().getId()),
            List.of("MXN"),
            HISTORY_FROM,
            HISTORY_THROUGH
        );
        assertCreditValue(
            debt,
            fixture.creditWithLimit().getId(),
            LocalDate.of(2026, 1, 1),
            "debt",
            "400",
            SemanticResultAvailability.COMPLETE
        );
        assertCreditValue(
            debt,
            fixture.creditWithLimit().getId(),
            LocalDate.of(2026, 3, 1),
            "debt",
            "1250",
            SemanticResultAvailability.COMPLETE
        );

        LocalDate current = LocalDate.now(SemanticAnalyticsTimePolicy.BUSINESS_ZONE);
        SemanticAnalyticsResult currentCredit = executeRoundTrip(
            "SELECT SUM(CREDIT_LIMIT) AS credit_limit, SUM(CREDIT_AVAILABLE) AS available, " +
            "SUM(CREDIT_UTILIZATION_PERCENT) AS utilization FROM CREDIT_ACCOUNT_SNAPSHOT TIME BINDING OBSERVATION_DATE " +
            "GROUP BY ACCOUNT_ID ORDER BY ACCOUNT_ID ASC LIMIT 100",
            List.of(fixture.creditWithLimit().getId()),
            List.of("MXN"),
            current,
            current
        );
        SemanticAnalyticsResultRow creditRow = accountRow(currentCredit, fixture.creditWithLimit().getId());
        assertThat(value(creditRow, "credit_limit").value()).isEqualByComparingTo("1000");
        assertThat(value(creditRow, "available").value()).isEqualByComparingTo("-250");
        assertThat(value(creditRow, "utilization").value()).isEqualByComparingTo("125.0000");

        SemanticAnalyticsResult transfer = executeRoundTrip(
            "SELECT COUNT(TRANSFER_ID) AS transfer_count, SUM(TRANSFER_AMOUNT) AS transfer_volume " +
            "FROM INTERNAL_TRANSFER_EVENT TIME BINDING TRANSFER_DATE " +
            "WHERE SOURCE_ACCOUNT_TYPE = DEBIT AND DESTINATION_ACCOUNT_TYPE = CASH " +
            "GROUP BY TRANSFER_MONTH, DESTINATION_ACCOUNT_ID ORDER BY TRANSFER_MONTH ASC LIMIT 100",
            List.of(fixture.transferSource().getId(), fixture.transferDestination().getId()),
            List.of("MXN"),
            HISTORY_FROM,
            HISTORY_THROUGH
        );
        assertTransferValues(transfer, "2026-02", fixture.transferDestination().getId(), "MXN", "1", "250");
        assertTransferValues(transfer, "2026-03", fixture.transferDestination().getId(), "MXN", "1", "100");

        SemanticAnalyticsDslParseResult historicalUtilization = dslService.parse(
            "SELECT SUM(CREDIT_UTILIZATION_PERCENT) AS utilization FROM CREDIT_ACCOUNT_SNAPSHOT " +
            "TIME BINDING OBSERVATION_DATE GROUP BY ACCOUNT_ID LIMIT 100"
        );
        assertThat(dslService.format(historicalUtilization.definition())).isEqualTo(historicalUtilization.canonicalText());
        assertThatThrownBy(() ->
            execute(
                historicalUtilization.definition(),
                List.of(fixture.creditWithLimit().getId()),
                List.of("MXN"),
                HISTORY_FROM,
                HISTORY_THROUGH
            )
        )
            .isInstanceOf(SemanticAnalyticsValidationException.class)
            .satisfies(error ->
                assertThat(((SemanticAnalyticsValidationException) error).getDiagnostics())
                    .extracting(AnalyticsDiagnostic::code)
                    .contains(AnalyticsDiagnosticCode.INCOMPATIBLE_HISTORICAL_CREDIT_LIMIT)
            );
    }

    @Test
    void keeps_transfer_events_out_of_economic_income_and_expense_and_owner_scope() {
        Fixture fixture = fixture();
        SemanticQueryDefinition economicMovements = new SemanticQueryDefinition(
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
            List.of(),
            List.of(),
            100
        );
        SemanticAnalyticsResult result = execute(
            economicMovements,
            List.of(fixture.transferSource().getId()),
            List.of("MXN"),
            HISTORY_FROM,
            HISTORY_THROUGH
        );
        // The MXN 250 and MXN 100 linked outgoing legs are excluded; only ordinary IN 60 and OUT 77 remain.
        assertThat(result.rows())
            .singleElement()
            .satisfies(row -> {
                assertThat(value(row, "income").value()).isEqualByComparingTo("60");
                assertThat(value(row, "expense").value()).isEqualByComparingTo("77");
            });

        SemanticQueryDefinition transfers = transferDefinition(
            List.of(new SemanticAggregateProjection("count", SemanticAggregateFunction.COUNT, SemanticField.TRANSFER_ID)),
            List.of(SemanticField.SOURCE_ACCOUNT_ID)
        );
        assertThatThrownBy(() ->
            execute(transfers, List.of(fixture.foreignTransferSource().getId()), List.of(), HISTORY_FROM, HISTORY_THROUGH)
        )
            .isInstanceOf(SemanticAnalyticsValidationException.class)
            .satisfies(error ->
                assertThat(((SemanticAnalyticsValidationException) error).getDiagnostics())
                    .extracting(AnalyticsDiagnostic::code)
                    .contains(AnalyticsDiagnosticCode.FOREIGN_ACCOUNT_REFERENCE)
            );
    }

    @Test
    void preserves_investment_book_balances_as_non_additive_positions_without_implying_market_value() {
        Fixture fixture = fixture();
        SemanticQueryDefinition positions = new SemanticQueryDefinition(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticDataset.ACCOUNT_POSITION_SNAPSHOT,
            SemanticField.OBSERVATION_DATE,
            List.of(new SemanticAggregateProjection("book_balance", SemanticAggregateFunction.SUM, SemanticField.POSITION_BALANCE)),
            List.of(),
            List.of(SemanticField.ACCOUNT_ID, SemanticField.OBSERVATION_MONTH),
            List.of(),
            100
        );
        SemanticAnalyticsResult result = execute(
            positions,
            List.of(fixture.investment().getId()),
            List.of("MXN"),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 31)
        );
        // MXN 800 opening book balance less MXN 100 OUT in February: each observation stays separate.
        assertPositionValue(result, fixture.investment().getId(), LocalDate.of(2026, 1, 1), "800");
        assertPositionValue(result, fixture.investment().getId(), LocalDate.of(2026, 2, 1), "700");
        assertPositionValue(result, fixture.investment().getId(), LocalDate.of(2026, 3, 1), "700");
        assertThat(result.warnings())
            .extracting(SemanticAnalyticsWarning::code)
            .contains("NON_ADDITIVE_ACCOUNT_POSITION", "INVESTMENT_BOOK_BALANCE_NOT_MARKET_VALUE");
    }

    private Fixture fixture() {
        User owner = TestUtil.findAll(em, User.class).stream().filter(user -> "user".equals(user.getLogin())).findFirst().orElseThrow();
        User foreignOwner = foreignUser();
        LocalDate opening = LocalDate.of(2026, 1, 1);
        FinancialAccount creditWithLimit = account(
            owner,
            "Gold credit",
            "Credit Bank",
            AccountType.CREDIT_CARD,
            CurrencyCode.MXN,
            "100",
            opening
        );
        creditDetails(creditWithLimit, "1000");
        transactionRepository.saveAndFlush(transaction(creditWithLimit, TransactionFlow.OUT, "300", LocalDate.of(2026, 1, 15)));
        transactionRepository.saveAndFlush(transaction(creditWithLimit, TransactionFlow.IN, "50", LocalDate.of(2026, 2, 10)));
        transactionRepository.saveAndFlush(transaction(creditWithLimit, TransactionFlow.OUT, "900", LocalDate.of(2026, 3, 10)));

        FinancialAccount creditWithoutDetails = account(
            owner,
            "No detail credit",
            "Credit Bank",
            AccountType.CREDIT_CARD,
            CurrencyCode.MXN,
            "75",
            opening
        );
        FinancialAccount creditZeroLimit = account(
            owner,
            "Zero limit credit",
            "Credit Bank",
            AccountType.CREDIT_CARD,
            CurrencyCode.MXN,
            "20",
            opening
        );
        creditDetails(creditZeroLimit, "0");
        FinancialAccount creditUsdZeroDebt = account(
            owner,
            "USD credit",
            "Credit USD",
            AccountType.CREDIT_CARD,
            CurrencyCode.USD,
            "0",
            opening
        );
        creditDetails(creditUsdZeroDebt, "500");

        FinancialAccount investment = account(owner, "Book investment", "Broker", AccountType.INVESTMENT, CurrencyCode.MXN, "800", opening);
        transactionRepository.saveAndFlush(transaction(investment, TransactionFlow.OUT, "100", LocalDate.of(2026, 2, 1)));

        FinancialAccount transferSource = account(
            owner,
            "Transfer source",
            "Source Bank",
            AccountType.DEBIT,
            CurrencyCode.MXN,
            "0",
            opening
        );
        FinancialAccount transferDestination = account(
            owner,
            "Transfer destination",
            "Destination Bank",
            AccountType.CASH,
            CurrencyCode.MXN,
            "0",
            opening
        );
        transfer(transferSource, transferDestination, "250", LocalDate.of(2026, 2, 15));
        transfer(transferSource, transferDestination, "100", LocalDate.of(2026, 3, 1));
        transactionRepository.saveAndFlush(transaction(transferSource, TransactionFlow.IN, "60", LocalDate.of(2026, 2, 5)));
        transactionRepository.saveAndFlush(transaction(transferSource, TransactionFlow.OUT, "77", LocalDate.of(2026, 2, 6)));

        FinancialAccount usdTransferSource = account(owner, "USD source", "USD Source", AccountType.DEBIT, CurrencyCode.USD, "0", opening);
        FinancialAccount usdTransferDestination = account(
            owner,
            "USD destination",
            "USD Destination",
            AccountType.CASH,
            CurrencyCode.USD,
            "0",
            opening
        );
        transfer(usdTransferSource, usdTransferDestination, "40", LocalDate.of(2026, 2, 16));

        FinancialAccount weekTransferSource = account(
            owner,
            "Week source",
            "Week Source",
            AccountType.DEBIT,
            CurrencyCode.MXN,
            "0",
            LocalDate.of(2025, 12, 1)
        );
        FinancialAccount weekTransferDestination = account(
            owner,
            "Week destination",
            "Week Destination",
            AccountType.CASH,
            CurrencyCode.MXN,
            "0",
            LocalDate.of(2025, 12, 1)
        );
        transfer(weekTransferSource, weekTransferDestination, "12", LocalDate.of(2025, 12, 31));

        FinancialAccount foreignTransferSource = account(
            foreignOwner,
            "Foreign source",
            "Private",
            AccountType.DEBIT,
            CurrencyCode.MXN,
            "0",
            opening
        );
        FinancialAccount foreignTransferDestination = account(
            foreignOwner,
            "Foreign destination",
            "Private",
            AccountType.CASH,
            CurrencyCode.MXN,
            "0",
            opening
        );
        transfer(foreignTransferSource, foreignTransferDestination, "999", LocalDate.of(2026, 2, 2));
        return new Fixture(
            creditWithLimit,
            creditWithoutDetails,
            creditZeroLimit,
            creditUsdZeroDebt,
            investment,
            transferSource,
            transferDestination,
            usdTransferSource,
            usdTransferDestination,
            weekTransferSource,
            foreignTransferSource
        );
    }

    private SemanticQueryDefinition creditDefinition(List<SemanticAggregateProjection> projections, List<SemanticField> groupBy) {
        return new SemanticQueryDefinition(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT,
            SemanticField.OBSERVATION_DATE,
            projections,
            List.of(),
            groupBy,
            List.of(),
            100
        );
    }

    private SemanticQueryDefinition transferDefinition(List<SemanticAggregateProjection> projections, List<SemanticField> groupBy) {
        return new SemanticQueryDefinition(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticDataset.INTERNAL_TRANSFER_EVENT,
            SemanticField.TRANSFER_DATE,
            projections,
            List.of(),
            groupBy,
            List.of(),
            100
        );
    }

    private SemanticAnalyticsResult executeCurrent(SemanticQueryDefinition definition, List<Long> accountIds, List<String> currencies) {
        LocalDate current = LocalDate.now(SemanticAnalyticsTimePolicy.BUSINESS_ZONE);
        return execute(definition, accountIds, currencies, current, current);
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

    private SemanticAnalyticsResult executeRoundTrip(
        String text,
        List<Long> accountIds,
        List<String> currencies,
        LocalDate from,
        LocalDate through
    ) {
        SemanticAnalyticsDslParseResult parsed = dslService.parse(text);
        assertThat(dslService.parse(parsed.canonicalText()).definition()).isEqualTo(parsed.definition());
        assertThat(dslService.format(parsed.definition())).isEqualTo(parsed.canonicalText());
        return execute(parsed.definition(), accountIds, currencies, from, through);
    }

    private void assertCreditValue(
        SemanticAnalyticsResult result,
        Long accountId,
        LocalDate month,
        String alias,
        String expected,
        SemanticResultAvailability availability
    ) {
        SemanticAnalyticsResultValue actual = value(
            row(result, null, SemanticField.ACCOUNT_ID, accountId, SemanticField.OBSERVATION_MONTH, month),
            alias
        );
        assertThat(actual.availability()).isEqualTo(availability);
        if (expected == null) {
            assertThat(actual.value()).isNull();
        } else {
            assertThat(actual.value()).isEqualByComparingTo(expected);
        }
    }

    private void assertTransferValues(
        SemanticAnalyticsResult result,
        String month,
        Long destination,
        String currency,
        String expectedCount,
        String expectedVolume
    ) {
        SemanticAnalyticsResultRow row = row(
            result,
            currency,
            SemanticField.TRANSFER_MONTH,
            month,
            SemanticField.DESTINATION_ACCOUNT_ID,
            destination
        );
        assertThat(value(row, "transfer_count").value()).isEqualByComparingTo(expectedCount);
        assertThat(value(row, "transfer_volume").value()).isEqualByComparingTo(expectedVolume);
    }

    private void assertPositionValue(SemanticAnalyticsResult result, Long accountId, LocalDate month, String expected) {
        assertThat(
            value(row(result, "MXN", SemanticField.ACCOUNT_ID, accountId, SemanticField.OBSERVATION_MONTH, month), "book_balance").value()
        ).isEqualByComparingTo(expected);
    }

    private SemanticAnalyticsResultRow accountRow(SemanticAnalyticsResult result, Long accountId) {
        return row(result, null, SemanticField.ACCOUNT_ID, accountId, null, null);
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
            .filter(candidate -> currency == null || currency.equals(candidate.currencyCode()))
            .filter(candidate -> java.util.Objects.equals(dimension(candidate, firstField), firstValue))
            .filter(candidate -> secondField == null || java.util.Objects.equals(dimension(candidate, secondField), secondValue))
            .findFirst()
            .orElseThrow();
    }

    private SemanticAnalyticsResultValue value(SemanticAnalyticsResultRow row, String alias) {
        return row.values().stream().filter(value -> value.alias().equals(alias)).findFirst().orElseThrow();
    }

    private Object dimension(SemanticAnalyticsResultRow row, SemanticField field) {
        return row
            .dimensions()
            .stream()
            .filter(dimension -> dimension.field() == field)
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
        String opening,
        LocalDate openingDate
    ) {
        return accountRepository.saveAndFlush(
            new FinancialAccount()
                .name(name)
                .institutionName(institution)
                .accountType(type)
                .currency(currency)
                .initialBalance(new BigDecimal(opening))
                .initialBalanceDate(openingDate)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .user(owner)
        );
    }

    private void creditDetails(FinancialAccount account, String limit) {
        creditDetailsRepository.saveAndFlush(
            new CreditAccountDetails()
                .account(account)
                .creditLimit(new BigDecimal(limit))
                .statementDay(15)
                .paymentDueDay(25)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
        );
    }

    private void transfer(FinancialAccount source, FinancialAccount destination, String amount, LocalDate date) {
        FinancialTransaction outgoing = transaction(source, TransactionFlow.OUT, amount, date);
        FinancialTransaction incoming = transaction(destination, TransactionFlow.IN, amount, date);
        transactionRepository.saveAndFlush(outgoing);
        transactionRepository.saveAndFlush(incoming);
        transferRepository.saveAndFlush(
            new InternalTransfer()
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .outgoingTransaction(outgoing)
                .incomingTransaction(incoming)
        );
    }

    private FinancialTransaction transaction(FinancialAccount account, TransactionFlow flow, String amount, LocalDate date) {
        return new FinancialTransaction()
            .account(account)
            .transactionDate(date)
            .postingDate(date)
            .description("specialized fixture " + amount)
            .amount(new BigDecimal(amount))
            .flow(flow)
            .origin(TransactionOrigin.MANUAL)
            .createdAt(Instant.now())
            .updatedAt(Instant.now());
    }

    private User foreignUser() {
        User user = new User();
        user.setLogin("specialized-private-owner");
        user.setPassword("x".repeat(60));
        user.setActivated(true);
        user.setEmail("specialized-private-owner@example.invalid");
        user.setLangKey("en");
        em.persist(user);
        em.flush();
        return user;
    }

    private record Fixture(
        FinancialAccount creditWithLimit,
        FinancialAccount creditWithoutDetails,
        FinancialAccount creditZeroLimit,
        FinancialAccount creditUsdZeroDebt,
        FinancialAccount investment,
        FinancialAccount transferSource,
        FinancialAccount transferDestination,
        FinancialAccount usdTransferSource,
        FinancialAccount usdTransferDestination,
        FinancialAccount weekTransferSource,
        FinancialAccount foreignTransferSource
    ) {}
}
