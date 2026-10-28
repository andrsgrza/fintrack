package com.fintrack.app.service.analytics;

import java.time.Instant;
import java.util.List;

/** Versioned, bounded output of the sole canonical Analytics execution service. */
public record FinancialAnalyticsResult(
    int schemaVersion,
    String semanticModel,
    ResolvedVisualizationContext effectiveContext,
    Instant executedAt,
    List<FinancialAnalyticsResultColumn> columns,
    List<FinancialAnalyticsResultRow> rows,
    FinancialMetricResultState completeness,
    List<FinancialAnalyticsWarning> warnings
) {
    public FinancialAnalyticsResult {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
        warnings = List.copyOf(warnings);
    }
}
