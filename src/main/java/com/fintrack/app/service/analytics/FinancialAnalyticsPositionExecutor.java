package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.repository.CreditAccountDetailsRepository;
import com.fintrack.app.service.FinancialAccountBalanceService;
import com.fintrack.app.service.dto.FinancialAccountBalanceDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Uses FINTRACK's authoritative account-balance service for all derived position and credit calculations. */
@Component
class FinancialAnalyticsPositionExecutor {

    private final FinancialAccountBalanceService balanceService;
    private final CreditAccountDetailsRepository creditDetailsRepository;
    private final FinancialMetricTimePolicy timePolicy;
    private final FinancialSemanticCatalog catalog;

    FinancialAnalyticsPositionExecutor(
        FinancialAccountBalanceService balanceService,
        CreditAccountDetailsRepository creditDetailsRepository,
        FinancialMetricTimePolicy timePolicy,
        FinancialSemanticCatalog catalog
    ) {
        this.balanceService = balanceService;
        this.creditDetailsRepository = creditDetailsRepository;
        this.timePolicy = timePolicy;
        this.catalog = catalog;
    }

    FinancialAnalyticsResult execute(FinancialAnalyticsExecutionScope scope) {
        QueryDefinition definition = scope.validatedRequest().request().definition();
        List<FinancialMetricDefinition> metrics = definition.measures().stream().map(id -> catalog.metric(id).orElseThrow()).toList();
        if (scope.accounts().isEmpty()) {
            return noApplicableAccounts(scope, metrics);
        }
        Map<Long, CreditAccountDetails> creditDetails = creditDetailsRepository
            .findAllByAccount_IdIn(scope.accounts().stream().map(FinancialAccount::getId).toList())
            .stream()
            .collect(Collectors.toMap(details -> details.getAccount().getId(), Function.identity()));
        Map<PositionKey, PositionAccumulator> groups = new LinkedHashMap<>();
        Set<FinancialAnalyticsWarning> warnings = new LinkedHashSet<>();
        for (LocalDate asOf : observationDates(definition.groupings(), scope.validatedRequest().resolvedContext())) {
            List<FinancialAccount> openedAccounts = scope
                .accounts()
                .stream()
                .filter(account -> !asOf.isBefore(account.getInitialBalanceDate()))
                .toList();
            Map<Long, FinancialAccountBalanceDTO> balances = balanceService.calculateBalances(openedAccounts, asOf);
            for (FinancialAccount account : scope.accounts()) {
                PositionKey key = key(definition.groupings(), account, asOf);
                PositionAccumulator accumulator = groups.computeIfAbsent(key, ignored -> new PositionAccumulator(metrics));
                for (FinancialMetricDefinition metric : metrics) {
                    if (!metric.applicability().supports(account.getAccountType())) {
                        accumulator.notApplicable(metric.id());
                    } else if (asOf.isBefore(account.getInitialBalanceDate())) {
                        accumulator.unavailable(metric.id());
                        warnings.add(
                            new FinancialAnalyticsWarning(
                                AnalyticsDiagnosticCode.UNAVAILABLE_HISTORICAL_DATA,
                                "No account position is invented before an account's initial balance date."
                            )
                        );
                    } else {
                        add(
                            accumulator,
                            metric,
                            account,
                            balances.get(account.getId()),
                            creditDetails.get(account.getId()),
                            asOf,
                            warnings
                        );
                    }
                }
            }
        }
        List<FinancialAnalyticsResultRow> rows = groups
            .entrySet()
            .stream()
            .map(entry -> row(entry.getKey(), entry.getValue(), metrics))
            .sorted(rowComparator())
            .limit(definition.limit())
            .toList();
        FinancialMetricResultState completeness = completeness(rows);
        if (completeness == FinancialMetricResultState.PARTIAL) {
            warnings.add(
                new FinancialAnalyticsWarning(
                    AnalyticsDiagnosticCode.PARTIAL_AVAILABILITY,
                    "Some selected accounts are not applicable or lack historical coverage."
                )
            );
        }
        return FinancialAnalyticsResultFactory.result(scope, catalog, rows, completeness, List.copyOf(warnings));
    }

    private void add(
        PositionAccumulator accumulator,
        FinancialMetricDefinition metric,
        FinancialAccount account,
        FinancialAccountBalanceDTO balance,
        CreditAccountDetails details,
        LocalDate asOf,
        Set<FinancialAnalyticsWarning> warnings
    ) {
        switch (metric.id()) {
            case "ACCOUNT_BALANCE" -> accumulator.add(metric.id(), balance.getCurrentBalance());
            case "ACCOUNT_SIGNED_POSITION", "NET_RECORDED_POSITION" -> accumulator.add(metric.id(), signedPosition(account, balance));
            case "CREDIT_CARD_DEBT" -> accumulator.add(metric.id(), balance.getCurrentDebt());
            case "CREDIT_LIMIT" -> addLimit(accumulator, metric, details, asOf, warnings);
            case "AVAILABLE_CREDIT" -> addAvailableCredit(accumulator, metric, balance, details, asOf, warnings);
            case "CREDIT_UTILIZATION" -> addUtilization(accumulator, metric, balance, details, asOf, warnings);
            default -> throw new IllegalArgumentException("Unsupported account-position metric: " + metric.id());
        }
    }

    private BigDecimal signedPosition(FinancialAccount account, FinancialAccountBalanceDTO balance) {
        return account.getAccountType() == AccountType.CREDIT_CARD ? balance.getCurrentDebt().negate() : balance.getCurrentBalance();
    }

    private void addLimit(
        PositionAccumulator accumulator,
        FinancialMetricDefinition metric,
        CreditAccountDetails details,
        LocalDate asOf,
        Set<FinancialAnalyticsWarning> warnings
    ) {
        if (!hasCurrentLimit(details, asOf)) {
            accumulator.unavailable(metric.id());
            warnings.add(historicalLimitWarning());
            return;
        }
        accumulator.add(metric.id(), details.getCreditLimit());
    }

    private void addAvailableCredit(
        PositionAccumulator accumulator,
        FinancialMetricDefinition metric,
        FinancialAccountBalanceDTO balance,
        CreditAccountDetails details,
        LocalDate asOf,
        Set<FinancialAnalyticsWarning> warnings
    ) {
        if (!hasCurrentLimit(details, asOf)) {
            accumulator.unavailable(metric.id());
            warnings.add(historicalLimitWarning());
            return;
        }
        accumulator.add(metric.id(), details.getCreditLimit().subtract(balance.getCurrentDebt()));
    }

    private void addUtilization(
        PositionAccumulator accumulator,
        FinancialMetricDefinition metric,
        FinancialAccountBalanceDTO balance,
        CreditAccountDetails details,
        LocalDate asOf,
        Set<FinancialAnalyticsWarning> warnings
    ) {
        if (!hasCurrentLimit(details, asOf) || details.getCreditLimit().signum() == 0) {
            accumulator.unavailable(metric.id());
            warnings.add(historicalLimitWarning());
            return;
        }
        accumulator.addUtilization(metric.id(), balance.getCurrentDebt(), details.getCreditLimit());
    }

    private boolean hasCurrentLimit(CreditAccountDetails details, LocalDate asOf) {
        return details != null && details.getCreditLimit() != null && asOf.equals(timePolicy.today());
    }

    private FinancialAnalyticsWarning historicalLimitWarning() {
        return new FinancialAnalyticsWarning(
            AnalyticsDiagnosticCode.UNAVAILABLE_HISTORICAL_DATA,
            "Credit-limit dependent measures are unavailable without current, date-proven credit-limit data."
        );
    }

    private FinancialAnalyticsResultRow row(PositionKey key, PositionAccumulator accumulator, List<FinancialMetricDefinition> metrics) {
        List<FinancialAnalyticsMeasureValue> measures = new ArrayList<>();
        for (FinancialMetricDefinition metric : metrics) {
            measures.add(accumulator.value(metric, key.currency()));
        }
        return new FinancialAnalyticsResultRow(key.dimensions(), key.currency(), measures);
    }

    private PositionKey key(List<String> groupings, FinancialAccount account, LocalDate asOf) {
        List<FinancialAnalyticsDimensionValue> dimensions = new ArrayList<>();
        for (String grouping : groupings) {
            dimensions.add(new FinancialAnalyticsDimensionValue(grouping, dimensionValue(grouping, account, asOf)));
        }
        return new PositionKey(List.copyOf(dimensions), account.getCurrency().name());
    }

    private Object dimensionValue(String grouping, FinancialAccount account, LocalDate asOf) {
        return switch (grouping) {
            case FinancialSemanticCatalog.DAY -> asOf;
            case FinancialSemanticCatalog.WEEK -> asOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case FinancialSemanticCatalog.MONTH -> asOf.withDayOfMonth(1);
            case FinancialSemanticCatalog.YEAR -> asOf.getYear();
            case FinancialSemanticCatalog.ACCOUNT -> account.getId();
            case FinancialSemanticCatalog.ACCOUNT_TYPE -> account.getAccountType().name();
            case FinancialSemanticCatalog.CURRENCY -> account.getCurrency().name();
            default -> throw new IllegalArgumentException("Unsupported verified position grouping: " + grouping);
        };
    }

    private List<LocalDate> observationDates(List<String> groupings, ResolvedVisualizationContext context) {
        String temporal = groupings
            .stream()
            .filter(grouping ->
                Set.of(
                    FinancialSemanticCatalog.DAY,
                    FinancialSemanticCatalog.WEEK,
                    FinancialSemanticCatalog.MONTH,
                    FinancialSemanticCatalog.YEAR
                ).contains(grouping)
            )
            .findFirst()
            .orElse(null);
        if (temporal == null) return List.of(context.through());
        return switch (temporal) {
            case FinancialSemanticCatalog.DAY -> context.from().datesUntil(context.through().plusDays(1)).toList();
            case FinancialSemanticCatalog.WEEK -> weekEnds(context.from(), context.through());
            case FinancialSemanticCatalog.MONTH -> monthEnds(context.from(), context.through());
            case FinancialSemanticCatalog.YEAR -> yearEnds(context.from(), context.through());
            default -> throw new IllegalStateException("Known temporal grouping was not handled: " + temporal);
        };
    }

    private List<LocalDate> weekEnds(LocalDate from, LocalDate through) {
        List<LocalDate> result = new ArrayList<>();
        LocalDate cursor = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        while (!cursor.isAfter(through)) {
            result.add(cursor);
            cursor = cursor.plusWeeks(1);
        }
        if (result.isEmpty() || !result.get(result.size() - 1).equals(through)) result.add(through);
        return result;
    }

    private List<LocalDate> monthEnds(LocalDate from, LocalDate through) {
        List<LocalDate> result = new ArrayList<>();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(through)); month = month.plusMonths(1)) {
            result.add(month.atEndOfMonth().isAfter(through) ? through : month.atEndOfMonth());
        }
        return result;
    }

    private List<LocalDate> yearEnds(LocalDate from, LocalDate through) {
        List<LocalDate> result = new ArrayList<>();
        for (int year = from.getYear(); year <= through.getYear(); year++) {
            LocalDate end = LocalDate.of(year, 12, 31);
            result.add(end.isAfter(through) ? through : end);
        }
        return result;
    }

    private FinancialMetricResultState completeness(List<FinancialAnalyticsResultRow> rows) {
        boolean complete = rows
            .stream()
            .flatMap(row -> row.measures().stream())
            .anyMatch(value -> value.state() == FinancialMetricResultState.COMPLETE || value.state() == FinancialMetricResultState.ZERO);
        boolean incomplete = rows
            .stream()
            .flatMap(row -> row.measures().stream())
            .anyMatch(
                value ->
                    value.state() == FinancialMetricResultState.UNAVAILABLE ||
                    value.state() == FinancialMetricResultState.NOT_APPLICABLE ||
                    value.state() == FinancialMetricResultState.PARTIAL
            );
        if (!complete && incomplete) return FinancialMetricResultState.UNAVAILABLE;
        return incomplete ? FinancialMetricResultState.PARTIAL : FinancialMetricResultState.COMPLETE;
    }

    private Comparator<FinancialAnalyticsResultRow> rowComparator() {
        return Comparator.comparing(
            row ->
                row.dimensions().stream().map(value -> String.valueOf(value.value())).collect(Collectors.joining("\u0001")) +
                "\u0001" +
                row.currencyCode()
        );
    }

    private FinancialAnalyticsResult noApplicableAccounts(FinancialAnalyticsExecutionScope scope, List<FinancialMetricDefinition> metrics) {
        List<FinancialAnalyticsMeasureValue> measures = metrics
            .stream()
            .map(metric -> FinancialAnalyticsResultFactory.unavailable(metric.id(), metric, FinancialMetricResultState.NOT_APPLICABLE, null)
            )
            .toList();
        return FinancialAnalyticsResultFactory.result(
            scope,
            catalog,
            List.of(new FinancialAnalyticsResultRow(List.of(), null, measures)),
            FinancialMetricResultState.NOT_APPLICABLE,
            List.of(
                new FinancialAnalyticsWarning(
                    AnalyticsDiagnosticCode.NO_APPLICABLE_ACCOUNTS,
                    "No selected account is applicable to this execution context."
                )
            )
        );
    }

    private record PositionKey(List<FinancialAnalyticsDimensionValue> dimensions, String currency) {
        PositionKey {
            dimensions = List.copyOf(dimensions);
        }
    }

    private static final class PositionAccumulator {

        private final Map<String, BigDecimal> sums = new LinkedHashMap<>();
        private final Map<String, BigDecimal> utilizationDebts = new LinkedHashMap<>();
        private final Map<String, BigDecimal> utilizationLimits = new LinkedHashMap<>();
        private final Map<String, Integer> applicable = new LinkedHashMap<>();
        private final Map<String, Integer> unavailable = new LinkedHashMap<>();
        private final Map<String, Integer> notApplicable = new LinkedHashMap<>();

        PositionAccumulator(List<FinancialMetricDefinition> metrics) {
            metrics.forEach(metric -> {
                sums.put(metric.id(), BigDecimal.ZERO);
                applicable.put(metric.id(), 0);
                unavailable.put(metric.id(), 0);
                notApplicable.put(metric.id(), 0);
            });
        }

        void add(String metric, BigDecimal value) {
            applicable.merge(metric, 1, Integer::sum);
            sums.merge(metric, value, BigDecimal::add);
        }

        void addUtilization(String metric, BigDecimal debt, BigDecimal limit) {
            applicable.merge(metric, 1, Integer::sum);
            utilizationDebts.merge(metric, debt, BigDecimal::add);
            utilizationLimits.merge(metric, limit, BigDecimal::add);
        }

        void unavailable(String metric) {
            applicable.merge(metric, 1, Integer::sum);
            unavailable.merge(metric, 1, Integer::sum);
        }

        void notApplicable(String metric) {
            notApplicable.merge(metric, 1, Integer::sum);
        }

        FinancialAnalyticsMeasureValue value(FinancialMetricDefinition definition, String currency) {
            String metric = definition.id();
            if (applicable.get(metric) == 0) {
                return FinancialAnalyticsResultFactory.unavailable(metric, definition, FinancialMetricResultState.NOT_APPLICABLE, currency);
            }
            if (unavailable.get(metric) == applicable.get(metric)) {
                return FinancialAnalyticsResultFactory.unavailable(metric, definition, FinancialMetricResultState.UNAVAILABLE, currency);
            }
            BigDecimal value;
            if ("CREDIT_UTILIZATION".equals(metric)) {
                BigDecimal limit = utilizationLimits.get(metric);
                value = limit == null || limit.signum() == 0 ? null : utilizationDebts.get(metric).divide(limit, 8, RoundingMode.HALF_UP);
            } else {
                value = sums.get(metric);
            }
            if (value == null) return FinancialAnalyticsResultFactory.unavailable(
                metric,
                definition,
                FinancialMetricResultState.UNAVAILABLE,
                currency
            );
            FinancialMetricResultState state = unavailable.get(metric) > 0 || notApplicable.get(metric) > 0
                ? FinancialMetricResultState.PARTIAL
                : FinancialAnalyticsResultFactory.stateFor(value);
            return new FinancialAnalyticsMeasureValue(metric, value.stripTrailingZeros(), state, definition.unit(), currency);
        }
    }
}
