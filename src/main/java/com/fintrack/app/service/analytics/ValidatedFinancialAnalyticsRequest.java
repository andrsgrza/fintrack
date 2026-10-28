package com.fintrack.app.service.analytics;

/** A normalized request plus its server-resolved context. Validation has not calculated any financial values. */
public record ValidatedFinancialAnalyticsRequest(
    FinancialAnalyticsExecutionRequest request,
    ResolvedVisualizationContext resolvedContext
) {}
