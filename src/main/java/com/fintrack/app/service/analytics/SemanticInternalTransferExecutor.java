package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.repository.InternalTransferRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Executes the transfer relation at its real grain: one validated InternalTransfer, not two FinancialTransaction
 * legs. The owner-scoped repository query and the key below guarantee that selecting both endpoints never doubles
 * an event or its amount.
 */
@Component
class SemanticInternalTransferExecutor {

    private final InternalTransferRepository transferRepository;

    SemanticInternalTransferExecutor(InternalTransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    SemanticAnalyticsResult execute(SemanticAnalyticsExecutionScope scope) {
        SemanticQueryDefinition definition = scope.definition();
        List<SemanticAnalyticsResultColumn> columns = SemanticAnalyticsResultFactory.columns(definition);
        if (scope.accounts().isEmpty()) return SemanticAnalyticsResultFactory.result(scope, List.of(), columns);

        List<Long> accountIds = scope.accounts().stream().map(FinancialAccount::getId).toList();
        List<InternalTransfer> events = transferRepository
            .findAllForSemanticAnalytics(scope.ownerLogin(), accountIds, scope.context().from(), scope.context().through())
            .stream()
            .filter(transfer -> matches(transfer, definition.predicates()))
            .toList();
        Map<TransferGroupKey, List<InternalTransfer>> grouped = new LinkedHashMap<>();
        for (InternalTransfer transfer : events) {
            List<Object> dimensions = definition.groupBy().stream().map(field -> dimensionValue(field, transfer)).toList();
            String currency = transfer.getOutgoingTransaction().getAccount().getCurrency().name();
            grouped.computeIfAbsent(new TransferGroupKey(dimensions, currency), ignored -> new ArrayList<>()).add(transfer);
        }

        List<SemanticAnalyticsResultRow> rows = grouped
            .entrySet()
            .stream()
            .map(entry -> row(definition, entry.getKey(), entry.getValue()))
            .sorted(rowComparator(definition.orderBy()))
            .limit(definition.limit())
            .toList();
        return SemanticAnalyticsResultFactory.result(
            scope,
            rows,
            columns,
            List.of(
                new SemanticAnalyticsWarning(
                    "TRANSFER_EVENT_VOLUME_NOT_ECONOMIC_FLOW",
                    "Transfer volume is one linked-transfer event and is never economic income or economic expense."
                )
            )
        );
    }

    private boolean matches(InternalTransfer transfer, List<SemanticPredicate> predicates) {
        for (SemanticPredicate predicate : predicates) {
            AccountType actual =
                switch (predicate.field()) {
                    case SOURCE_ACCOUNT_TYPE -> transfer.getOutgoingTransaction().getAccount().getAccountType();
                    case DESTINATION_ACCOUNT_TYPE -> transfer.getIncomingTransaction().getAccount().getAccountType();
                    default -> throw new IllegalArgumentException("Validated transfer predicate was not handled: " + predicate.field());
                };
            boolean selected = predicate.values().stream().map(AccountType::valueOf).anyMatch(actual::equals);
            if (!selected) return false;
        }
        return true;
    }

    private SemanticAnalyticsResultRow row(SemanticQueryDefinition definition, TransferGroupKey key, List<InternalTransfer> events) {
        List<SemanticAnalyticsResultDimension> dimensions = new ArrayList<>();
        for (int index = 0; index < definition.groupBy().size(); index++) {
            dimensions.add(dimension(definition.groupBy().get(index), key.dimensions().get(index), events.get(0)));
        }
        List<SemanticAnalyticsResultValue> values = definition.projections().stream().map(projection -> value(projection, events)).toList();
        return new SemanticAnalyticsResultRow(dimensions, key.currency(), values);
    }

    private SemanticAnalyticsResultValue value(SemanticAggregateProjection projection, List<InternalTransfer> events) {
        BigDecimal value =
            switch (projection.function()) {
                case COUNT -> BigDecimal.valueOf(events.size());
                case SUM -> events
                    .stream()
                    .map(transfer -> transfer.getOutgoingTransaction().getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                case AVG -> throw new IllegalArgumentException("Verified transfer aggregate was not handled: " + projection.function());
            };
        return SemanticAnalyticsResultFactory.value(projection, value);
    }

    private Object dimensionValue(SemanticField field, InternalTransfer transfer) {
        FinancialAccount source = transfer.getOutgoingTransaction().getAccount();
        FinancialAccount destination = transfer.getIncomingTransaction().getAccount();
        LocalDate date = transfer.getOutgoingTransaction().getTransactionDate();
        return switch (field) {
            case TRANSFER_DAY -> date.toString();
            case TRANSFER_WEEK -> "%d-W%02d".formatted(
                    date.get(WeekFields.ISO.weekBasedYear()),
                    date.get(WeekFields.ISO.weekOfWeekBasedYear())
                );
            case TRANSFER_MONTH -> "%d-%02d".formatted(date.getYear(), date.getMonthValue());
            case TRANSFER_YEAR -> String.valueOf(date.getYear());
            case SOURCE_ACCOUNT_ID -> source.getId();
            case SOURCE_ACCOUNT_INSTITUTION -> institution(source);
            case SOURCE_ACCOUNT_TYPE -> source.getAccountType().name();
            case DESTINATION_ACCOUNT_ID -> destination.getId();
            case DESTINATION_ACCOUNT_INSTITUTION -> institution(destination);
            case DESTINATION_ACCOUNT_TYPE -> destination.getAccountType().name();
            default -> throw new IllegalArgumentException("Verified transfer grouping was not handled: " + field);
        };
    }

    private SemanticAnalyticsResultDimension dimension(SemanticField field, Object raw, InternalTransfer transfer) {
        FinancialAccount source = transfer.getOutgoingTransaction().getAccount();
        FinancialAccount destination = transfer.getIncomingTransaction().getAccount();
        if (isTemporalGrouping(field)) {
            String bucket = String.valueOf(raw);
            String label = field == SemanticField.TRANSFER_WEEK ? "ISO " + bucket : bucket;
            return new SemanticAnalyticsResultDimension(
                field,
                bucket,
                new SemanticDimensionMetadata(field.name() + ":" + bucket, label, null, null, false, List.of(bucket))
            );
        }
        return switch (field) {
            case SOURCE_ACCOUNT_ID -> accountDimension(field, source, "SOURCE_ACCOUNT:");
            case DESTINATION_ACCOUNT_ID -> accountDimension(field, destination, "DESTINATION_ACCOUNT:");
            case SOURCE_ACCOUNT_INSTITUTION -> institutionDimension(field, String.valueOf(raw), "SOURCE_INSTITUTION:");
            case DESTINATION_ACCOUNT_INSTITUTION -> institutionDimension(field, String.valueOf(raw), "DESTINATION_INSTITUTION:");
            default -> new SemanticAnalyticsResultDimension(field, raw);
        };
    }

    private SemanticAnalyticsResultDimension accountDimension(SemanticField field, FinancialAccount account, String prefix) {
        return new SemanticAnalyticsResultDimension(
            field,
            account.getId(),
            new SemanticDimensionMetadata(
                prefix + account.getId(),
                account.getName(),
                account.getColor(),
                account.getActive(),
                false,
                List.of(account.getName())
            )
        );
    }

    private SemanticAnalyticsResultDimension institutionDimension(SemanticField field, String institution, String prefix) {
        return new SemanticAnalyticsResultDimension(
            field,
            institution,
            new SemanticDimensionMetadata(prefix + institution, institution, null, null, false, List.of(institution))
        );
    }

    private String institution(FinancialAccount account) {
        String value = account.getInstitutionName();
        return value == null || value.isBlank() ? "UNSPECIFIED" : value.trim();
    }

    private boolean isTemporalGrouping(SemanticField field) {
        return switch (field) {
            case TRANSFER_DAY, TRANSFER_WEEK, TRANSFER_MONTH, TRANSFER_YEAR -> true;
            default -> false;
        };
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

    private record TransferGroupKey(List<Object> dimensions, String currency) {
        private TransferGroupKey {
            dimensions = List.copyOf(dimensions);
        }
    }
}
