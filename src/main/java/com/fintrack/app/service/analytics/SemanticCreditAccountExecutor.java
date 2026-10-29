package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.service.FinancialAccountBalanceService;
import com.fintrack.app.service.dto.FinancialAccountBalanceDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Produces governed credit-card observations from the authoritative balance service. It intentionally has no
 * independent debt formula, no persisted snapshot table and no historical credit-limit reconstruction.
 */
@Component
class SemanticCreditAccountExecutor {

    private static final String NO_APPLICABLE_CREDIT_ACCOUNTS = "NO_APPLICABLE_CREDIT_ACCOUNTS";
    private static final String CREDIT_POINT_IN_TIME = "CREDIT_DEBT_POINT_IN_TIME";
    private static final String CREDIT_DETAILS_UNAVAILABLE = "CREDIT_DETAILS_UNAVAILABLE";
    private static final String CREDIT_UTILIZATION_UNAVAILABLE = "CREDIT_UTILIZATION_UNAVAILABLE";

    private final FinancialAccountBalanceService balanceService;

    SemanticCreditAccountExecutor(FinancialAccountBalanceService balanceService) {
        this.balanceService = balanceService;
    }

    SemanticAnalyticsResult execute(SemanticAnalyticsExecutionScope scope) {
        SemanticQueryDefinition definition = scope.definition();
        List<FinancialAccount> accounts = scopedCreditAccounts(scope.accounts(), definition.predicates());
        List<SemanticAnalyticsWarning> warnings = new ArrayList<>(warnings(definition, accounts));
        if (accounts.isEmpty()) {
            return SemanticAnalyticsResultFactory.result(
                scope,
                List.of(),
                SemanticAnalyticsResultFactory.columns(definition),
                warnings,
                SemanticResultAvailability.NOT_APPLICABLE
            );
        }

        boolean monthly = definition.groupBy().contains(SemanticField.OBSERVATION_MONTH);
        boolean limitDerived = definition
            .projections()
            .stream()
            .map(SemanticAggregateProjection::field)
            .anyMatch(this::isLimitDerivedField);
        boolean utilization = definition
            .projections()
            .stream()
            .anyMatch(projection -> projection.field() == SemanticField.CREDIT_UTILIZATION_PERCENT);
        boolean missingDetails = false;
        boolean nonPositiveLimit = false;
        List<SemanticAnalyticsResultRow> rows = new ArrayList<>();
        for (LocalDate observation : monthly
            ? monthEnds(scope.context().from(), scope.context().through())
            : List.of(scope.context().through())) {
            List<FinancialAccount> opened = accounts
                .stream()
                .filter(account -> !observation.isBefore(account.getInitialBalanceDate()))
                .toList();
            Map<Long, FinancialAccountBalanceDTO> balances = balanceService.calculateBalances(opened, observation);
            for (FinancialAccount account : accounts) {
                FinancialAccountBalanceDTO snapshot = observation.isBefore(account.getInitialBalanceDate())
                    ? null
                    : balances.get(account.getId());
                if (snapshot != null && limitDerived && Boolean.TRUE.equals(snapshot.getMissingCreditDetails())) {
                    missingDetails = true;
                }
                if (snapshot != null && utilization && nonPositive(snapshot.getCreditLimit())) {
                    nonPositiveLimit = true;
                }
                rows.add(row(definition, account, observation, snapshot));
            }
        }
        rows.sort(rowComparator(definition.orderBy()));
        if (rows.size() > definition.limit()) {
            rows = new ArrayList<>(rows.subList(0, definition.limit()));
        }
        if (missingDetails) {
            warnings.add(
                new SemanticAnalyticsWarning(
                    CREDIT_DETAILS_UNAVAILABLE,
                    "Some selected credit accounts have no recorded credit details; their limit-derived values are unavailable."
                )
            );
        }
        if (nonPositiveLimit) {
            warnings.add(
                new SemanticAnalyticsWarning(
                    CREDIT_UTILIZATION_UNAVAILABLE,
                    "Utilization requires a recorded positive current credit limit; debt remains available independently."
                )
            );
        }
        return SemanticAnalyticsResultFactory.result(scope, rows, SemanticAnalyticsResultFactory.columns(definition), warnings);
    }

    private List<FinancialAccount> scopedCreditAccounts(List<FinancialAccount> accounts, List<SemanticPredicate> predicates) {
        Set<AccountType> selectedTypes = predicates
            .stream()
            .filter(predicate -> predicate.field() == SemanticField.ACCOUNT_TYPE)
            .flatMap(predicate -> predicate.values().stream())
            .map(AccountType::valueOf)
            .collect(java.util.stream.Collectors.toSet());
        return accounts
            .stream()
            .filter(account -> account.getAccountType() == AccountType.CREDIT_CARD)
            .filter(account -> selectedTypes.isEmpty() || selectedTypes.contains(account.getAccountType()))
            .toList();
    }

    private List<SemanticAnalyticsWarning> warnings(SemanticQueryDefinition definition, List<FinancialAccount> accounts) {
        List<SemanticAnalyticsWarning> warnings = new ArrayList<>();
        if (accounts.isEmpty()) {
            warnings.add(
                new SemanticAnalyticsWarning(NO_APPLICABLE_CREDIT_ACCOUNTS, "No credit-card account is available in the selected context.")
            );
            return List.copyOf(warnings);
        }
        if (definition.projections().stream().anyMatch(projection -> projection.field() == SemanticField.CREDIT_DEBT)) {
            warnings.add(
                new SemanticAnalyticsWarning(
                    CREDIT_POINT_IN_TIME,
                    "Credit debt is a point-in-time balance and is never added across accounts or observation dates."
                )
            );
        }
        return List.copyOf(warnings);
    }

    private SemanticAnalyticsResultRow row(
        SemanticQueryDefinition definition,
        FinancialAccount account,
        LocalDate observation,
        FinancialAccountBalanceDTO snapshot
    ) {
        List<SemanticAnalyticsResultDimension> dimensions = new ArrayList<>();
        for (SemanticField group : definition.groupBy()) {
            dimensions.add(dimension(group, account, observation));
        }
        List<SemanticAnalyticsResultValue> values = definition
            .projections()
            .stream()
            .map(projection -> value(projection, snapshot))
            .toList();
        return new SemanticAnalyticsResultRow(dimensions, account.getCurrency().name(), values);
    }

    private SemanticAnalyticsResultValue value(SemanticAggregateProjection projection, FinancialAccountBalanceDTO snapshot) {
        BigDecimal amount =
            switch (projection.field()) {
                case CREDIT_DEBT -> snapshot == null ? null : snapshot.getCurrentDebt();
                case CREDIT_LIMIT -> snapshot == null ? null : snapshot.getCreditLimit();
                case CREDIT_AVAILABLE -> snapshot == null ? null : snapshot.getAvailableCredit();
                case CREDIT_UTILIZATION_PERCENT -> utilization(snapshot);
                default -> throw new IllegalArgumentException("Verified credit projection was not handled: " + projection.field());
            };
        SemanticResultAvailability availability = amount == null
            ? SemanticResultAvailability.UNAVAILABLE
            : amount.signum() == 0 ? SemanticResultAvailability.ZERO : SemanticResultAvailability.COMPLETE;
        return new SemanticAnalyticsResultValue(projection.alias(), amount, SemanticAnalyticsResultFactory.unit(projection), availability);
    }

    private BigDecimal utilization(FinancialAccountBalanceDTO snapshot) {
        if (snapshot == null || nonPositive(snapshot.getCreditLimit()) || snapshot.getCurrentDebt() == null) return null;
        return snapshot.getCurrentDebt().multiply(BigDecimal.valueOf(100)).divide(snapshot.getCreditLimit(), 4, RoundingMode.HALF_UP);
    }

    private boolean nonPositive(BigDecimal value) {
        return value == null || value.signum() <= 0;
    }

    private SemanticAnalyticsResultDimension dimension(SemanticField field, FinancialAccount account, LocalDate observation) {
        return switch (field) {
            case ACCOUNT_ID -> new SemanticAnalyticsResultDimension(
                field,
                account.getId(),
                new SemanticDimensionMetadata(
                    "ACCOUNT:" + account.getId(),
                    account.getName(),
                    account.getColor(),
                    account.getActive(),
                    false,
                    List.of(account.getName())
                )
            );
            case OBSERVATION_MONTH -> {
                String bucket = observation.withDayOfMonth(1).toString().substring(0, 7);
                yield new SemanticAnalyticsResultDimension(
                    field,
                    observation.withDayOfMonth(1),
                    new SemanticDimensionMetadata("OBSERVATION_MONTH:" + bucket, bucket, null, null, false, List.of(bucket))
                );
            }
            default -> throw new IllegalArgumentException("Verified credit grouping was not handled: " + field);
        };
    }

    private List<LocalDate> monthEnds(LocalDate from, LocalDate through) {
        List<LocalDate> dates = new ArrayList<>();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(through)); month = month.plusMonths(1)) {
            LocalDate end = month.atEndOfMonth();
            dates.add(end.isAfter(through) ? through : end);
        }
        return dates;
    }

    private Comparator<SemanticAnalyticsResultRow> rowComparator(List<SemanticOrder> orderBy) {
        Comparator<SemanticAnalyticsResultRow> comparator = (left, right) -> 0;
        for (SemanticOrder order : orderBy) {
            Comparator<SemanticAnalyticsResultRow> current = order.field() != null
                ? Comparator.comparing(row -> sortable(dimension(row, order.field())), Comparator.nullsLast(Comparator.naturalOrder()))
                : Comparator.comparing(
                    row -> projectionValue(row, order.projectionAlias()),
                    Comparator.nullsLast(Comparator.naturalOrder())
                );
            comparator = comparator.thenComparing(order.direction() == SemanticSortDirection.ASC ? current : current.reversed());
        }
        return comparator;
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

    private String sortable(Object value) {
        if (value instanceof Number number) return "%020d".formatted(number.longValue());
        return value == null ? null : value.toString();
    }

    private BigDecimal projectionValue(SemanticAnalyticsResultRow row, String alias) {
        return row
            .values()
            .stream()
            .filter(value -> value.alias().equalsIgnoreCase(alias))
            .map(SemanticAnalyticsResultValue::value)
            .findFirst()
            .orElse(null);
    }

    private boolean isLimitDerivedField(SemanticField field) {
        return (
            field == SemanticField.CREDIT_LIMIT ||
            field == SemanticField.CREDIT_AVAILABLE ||
            field == SemanticField.CREDIT_UTILIZATION_PERCENT
        );
    }
}
