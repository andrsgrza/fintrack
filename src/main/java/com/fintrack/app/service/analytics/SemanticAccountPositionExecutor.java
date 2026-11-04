package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.service.FinancialAccountBalanceService;
import com.fintrack.app.service.dto.FinancialAccountBalanceDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Produces virtual, authoritative account-position rows. It never persists or backfills snapshots. */
@Component
class SemanticAccountPositionExecutor {

    private static final Set<AccountType> ASSET_ACCOUNT_TYPES = EnumSet.of(AccountType.DEBIT, AccountType.CASH, AccountType.INVESTMENT);

    private final FinancialAccountBalanceService balanceService;

    SemanticAccountPositionExecutor(FinancialAccountBalanceService balanceService) {
        this.balanceService = balanceService;
    }

    SemanticAnalyticsResult execute(SemanticAnalyticsExecutionScope scope) {
        SemanticQueryDefinition definition = scope.definition();
        List<FinancialAccount> accounts = scopedAssetAccounts(scope.accounts(), definition.predicates());
        List<SemanticAnalyticsResultRow> rows = new ArrayList<>();
        List<LocalDate> observations = definition.groupBy().contains(SemanticField.OBSERVATION_MONTH)
            ? monthEnds(scope.context().from(), scope.context().through())
            : List.of(scope.context().through());
        for (LocalDate asOf : observations) {
            List<FinancialAccount> opened = accounts.stream().filter(account -> !asOf.isBefore(account.getInitialBalanceDate())).toList();
            Map<Long, FinancialAccountBalanceDTO> balances = balanceService.calculateBalances(opened, asOf);
            for (FinancialAccount account : accounts) {
                BigDecimal balance = asOf.isBefore(account.getInitialBalanceDate())
                    ? null
                    : balances.get(account.getId()).getCurrentBalance();
                rows.add(row(definition, account, asOf, balance));
            }
        }
        rows.sort(rowComparator(definition.orderBy()));
        if (rows.size() > definition.limit()) {
            rows = new ArrayList<>(rows.subList(0, definition.limit()));
        }
        List<SemanticAnalyticsWarning> warnings = new ArrayList<>();
        warnings.add(
            new SemanticAnalyticsWarning(
                definition.groupBy().contains(SemanticField.OBSERVATION_MONTH)
                    ? "NON_ADDITIVE_ACCOUNT_POSITION"
                    : "ACCOUNT_POSITION_AT_CUTOFF",
                definition.groupBy().contains(SemanticField.OBSERVATION_MONTH)
                    ? "Account positions are point-in-time values and are never added across accounts or observation dates."
                    : "Account positions use one authoritative observation cutoff per account and are never added across dates."
            )
        );
        if (accounts.stream().anyMatch(account -> account.getAccountType() == AccountType.INVESTMENT)) {
            warnings.add(
                new SemanticAnalyticsWarning(
                    "INVESTMENT_BOOK_BALANCE_NOT_MARKET_VALUE",
                    "Investment accounts report their registered book balance, not market value or investment return."
                )
            );
        }
        return SemanticAnalyticsResultFactory.result(scope, rows, SemanticAnalyticsResultFactory.columns(definition), warnings);
    }

    private List<FinancialAccount> scopedAssetAccounts(List<FinancialAccount> accounts, List<SemanticPredicate> predicates) {
        Set<AccountType> selectedTypes = predicates
            .stream()
            .filter(predicate -> predicate.field() == SemanticField.ACCOUNT_TYPE)
            .flatMap(predicate -> predicate.values().stream())
            .map(AccountType::valueOf)
            .collect(java.util.stream.Collectors.toSet());
        return accounts
            .stream()
            .filter(account -> ASSET_ACCOUNT_TYPES.contains(account.getAccountType()))
            .filter(account -> selectedTypes.isEmpty() || selectedTypes.contains(account.getAccountType()))
            .toList();
    }

    private SemanticAnalyticsResultRow row(
        SemanticQueryDefinition definition,
        FinancialAccount account,
        LocalDate asOf,
        BigDecimal balance
    ) {
        List<SemanticAnalyticsResultDimension> dimensions = new ArrayList<>();
        for (SemanticField group : definition.groupBy()) {
            dimensions.add(dimension(group, account, asOf));
        }
        SemanticAggregateProjection projection = definition.projections().get(0);
        return new SemanticAnalyticsResultRow(
            dimensions,
            account.getCurrency().name(),
            List.of(SemanticAnalyticsResultFactory.value(projection, balance))
        );
    }

    private SemanticAnalyticsResultDimension dimension(SemanticField field, FinancialAccount account, LocalDate asOf) {
        return switch (field) {
            case ACCOUNT_ID -> {
                String label = account.getName();
                yield new SemanticAnalyticsResultDimension(
                    field,
                    account.getId(),
                    new SemanticDimensionMetadata(
                        "ACCOUNT:" + account.getId(),
                        label,
                        account.getColor(),
                        account.getActive(),
                        false,
                        List.of(label)
                    )
                );
            }
            case OBSERVATION_MONTH -> {
                String bucket = asOf.withDayOfMonth(1).toString().substring(0, 7);
                yield new SemanticAnalyticsResultDimension(
                    field,
                    asOf.withDayOfMonth(1),
                    new SemanticDimensionMetadata("OBSERVATION_MONTH:" + bucket, bucket, null, null, false, List.of(bucket))
                );
            }
            default -> throw new IllegalArgumentException("Verified position grouping was not handled: " + field);
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
}
