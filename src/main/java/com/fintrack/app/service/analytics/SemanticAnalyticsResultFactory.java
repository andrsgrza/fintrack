package com.fintrack.app.service.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class SemanticAnalyticsResultFactory {

    private SemanticAnalyticsResultFactory() {}

    static SemanticAnalyticsResult result(
        SemanticAnalyticsExecutionScope scope,
        List<SemanticAnalyticsResultRow> rows,
        List<SemanticAnalyticsResultColumn> columns
    ) {
        return result(scope, rows, columns, List.of());
    }

    static SemanticAnalyticsResult result(
        SemanticAnalyticsExecutionScope scope,
        List<SemanticAnalyticsResultRow> rows,
        List<SemanticAnalyticsResultColumn> columns,
        List<SemanticAnalyticsWarning> warnings
    ) {
        return result(scope, rows, columns, warnings, availability(rows));
    }

    static SemanticAnalyticsResult result(
        SemanticAnalyticsExecutionScope scope,
        List<SemanticAnalyticsResultRow> rows,
        List<SemanticAnalyticsResultColumn> columns,
        List<SemanticAnalyticsWarning> warnings,
        SemanticResultAvailability availability
    ) {
        return new SemanticAnalyticsResult(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            scope.context(),
            Instant.now(),
            availability,
            columns,
            rows,
            warnings
        );
    }

    static List<SemanticAnalyticsResultColumn> columns(SemanticQueryDefinition definition) {
        List<SemanticAnalyticsResultColumn> columns = new ArrayList<>();
        definition
            .groupBy()
            .forEach(field ->
                columns.add(new SemanticAnalyticsResultColumn(field.name(), field.name(), true, null, false, additivity(field)))
            );
        definition
            .projections()
            .forEach(projection ->
                columns.add(
                    new SemanticAnalyticsResultColumn(
                        projection.alias(),
                        projection.alias(),
                        false,
                        unit(projection),
                        unit(projection) == SemanticUnit.MONEY,
                        projectionAdditivity(projection)
                    )
                )
            );
        return List.copyOf(columns);
    }

    static SemanticAnalyticsResultValue value(SemanticAggregateProjection projection, BigDecimal amount) {
        SemanticResultAvailability availability = amount == null
            ? SemanticResultAvailability.UNAVAILABLE
            : amount.signum() == 0 ? SemanticResultAvailability.ZERO : SemanticResultAvailability.COMPLETE;
        return new SemanticAnalyticsResultValue(projection.alias(), amount, unit(projection), availability);
    }

    static SemanticUnit unit(SemanticAggregateProjection projection) {
        if (projection.function() == SemanticAggregateFunction.COUNT) return SemanticUnit.COUNT;
        return projection.field() == SemanticField.CREDIT_UTILIZATION_PERCENT ? SemanticUnit.PERCENT : SemanticUnit.MONEY;
    }

    private static SemanticResultAvailability availability(List<SemanticAnalyticsResultRow> rows) {
        if (rows.isEmpty()) return SemanticResultAvailability.NO_DATA;
        long unavailable = rows
            .stream()
            .flatMap(row -> row.values().stream())
            .filter(value -> value.availability() == SemanticResultAvailability.UNAVAILABLE)
            .count();
        if (unavailable == 0) return SemanticResultAvailability.COMPLETE;
        long values = rows.stream().mapToLong(row -> row.values().size()).sum();
        return unavailable == values ? SemanticResultAvailability.UNAVAILABLE : SemanticResultAvailability.PARTIAL;
    }

    private static SemanticAdditivityPolicy additivity(SemanticField field) {
        return field == SemanticField.TAG_ID ? SemanticAdditivityPolicy.NON_ADDITIVE_OVERLAPPING : SemanticAdditivityPolicy.ADDITIVE;
    }

    private static SemanticAdditivityPolicy projectionAdditivity(SemanticAggregateProjection projection) {
        if (projection.field() == SemanticField.POSITION_BALANCE || projection.field() == SemanticField.CREDIT_DEBT) {
            return SemanticAdditivityPolicy.POINT_IN_TIME_REQUIRES_ACCOUNT_AND_MONTH;
        }
        if (
            projection.field() == SemanticField.CREDIT_LIMIT ||
            projection.field() == SemanticField.CREDIT_AVAILABLE ||
            projection.field() == SemanticField.CREDIT_UTILIZATION_PERCENT
        ) {
            return SemanticAdditivityPolicy.POINT_IN_TIME_REQUIRES_ACCOUNT;
        }
        return projection.function() == SemanticAggregateFunction.AVG
            ? SemanticAdditivityPolicy.NON_ADDITIVE
            : SemanticAdditivityPolicy.ADDITIVE;
    }
}
