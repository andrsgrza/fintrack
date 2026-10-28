package com.fintrack.app.service.analytics;

/** Stable metadata for a result value. Values themselves live in ordered rows. */
public record FinancialAnalyticsResultColumn(
    String id,
    FinancialAnalyticsColumnKind kind,
    SemanticValueType valueType,
    FinancialMetricUnit unit,
    boolean currencyFaceted
) {}
