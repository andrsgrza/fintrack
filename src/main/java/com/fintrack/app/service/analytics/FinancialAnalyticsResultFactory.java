package com.fintrack.app.service.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Shared result construction; all executors use the same typed availability and column contract. */
final class FinancialAnalyticsResultFactory {

    private FinancialAnalyticsResultFactory() {}

    static List<FinancialAnalyticsResultColumn> columns(FinancialSemanticCatalog catalog, List<String> groupings, List<String> measures) {
        List<FinancialAnalyticsResultColumn> columns = new ArrayList<>();
        for (String grouping : groupings) {
            FinancialSemanticAttributeDefinition attribute = catalog.attribute(grouping).orElseThrow();
            columns.add(
                new FinancialAnalyticsResultColumn(grouping, FinancialAnalyticsColumnKind.DIMENSION, attribute.valueType(), null, false)
            );
        }
        for (String measure : measures) {
            FinancialMetricDefinition definition = catalog.metric(measure).orElseThrow();
            columns.add(
                new FinancialAnalyticsResultColumn(
                    measure,
                    FinancialAnalyticsColumnKind.MEASURE,
                    measureValueType(definition.unit()),
                    definition.unit(),
                    definition.currencyBehavior() == FinancialMetricCurrencyBehavior.PARTITION_BY_CURRENCY
                )
            );
        }
        return List.copyOf(columns);
    }

    static FinancialAnalyticsResult result(
        FinancialAnalyticsExecutionScope scope,
        FinancialSemanticCatalog catalog,
        List<FinancialAnalyticsResultRow> rows,
        FinancialMetricResultState completeness,
        List<FinancialAnalyticsWarning> warnings
    ) {
        QueryDefinition definition = scope.validatedRequest().request().definition();
        return new FinancialAnalyticsResult(
            AnalyticsSchema.VERSION,
            AnalyticsSchema.SEMANTIC_MODEL,
            scope.validatedRequest().resolvedContext(),
            Instant.now(),
            columns(catalog, definition.groupings(), definition.measures()),
            rows,
            completeness,
            warnings
        );
    }

    static FinancialAnalyticsMeasureValue zero(String id, FinancialMetricDefinition definition, String currencyCode) {
        return new FinancialAnalyticsMeasureValue(id, BigDecimal.ZERO, FinancialMetricResultState.ZERO, definition.unit(), currencyCode);
    }

    static FinancialAnalyticsMeasureValue unavailable(
        String id,
        FinancialMetricDefinition definition,
        FinancialMetricResultState state,
        String currencyCode
    ) {
        return new FinancialAnalyticsMeasureValue(id, null, state, definition.unit(), currencyCode);
    }

    static FinancialMetricResultState stateFor(BigDecimal value) {
        return value != null && value.signum() == 0 ? FinancialMetricResultState.ZERO : FinancialMetricResultState.COMPLETE;
    }

    private static SemanticValueType measureValueType(FinancialMetricUnit unit) {
        return switch (unit) {
            case MONEY, RATIO -> SemanticValueType.DECIMAL;
            case COUNT -> SemanticValueType.INTEGER;
        };
    }
}
