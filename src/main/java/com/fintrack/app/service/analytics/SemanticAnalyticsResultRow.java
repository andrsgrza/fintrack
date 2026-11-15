package com.fintrack.app.service.analytics;

import java.util.List;

public record SemanticAnalyticsResultRow(
    List<SemanticAnalyticsResultDimension> dimensions,
    String currencyCode,
    List<SemanticAnalyticsResultValue> values
) {
    public SemanticAnalyticsResultRow {
        dimensions = List.copyOf(dimensions);
        values = List.copyOf(values);
    }
}
