package com.fintrack.app.service.analytics;

import java.util.List;

/** One deterministic analytical observation. Currency is a monetary facet even when it was not requested as a grouping. */
public record FinancialAnalyticsResultRow(
    List<FinancialAnalyticsDimensionValue> dimensions,
    String currencyCode,
    List<FinancialAnalyticsMeasureValue> measures
) {
    public FinancialAnalyticsResultRow {
        dimensions = List.copyOf(dimensions);
        measures = List.copyOf(measures);
    }
}
