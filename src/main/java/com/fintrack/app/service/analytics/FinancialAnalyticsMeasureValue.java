package com.fintrack.app.service.analytics;

import java.math.BigDecimal;

/**
 * A calculated measure. A null value is meaningful only with UNAVAILABLE or NOT_APPLICABLE state; zero is never
 * used as a missing-data sentinel.
 */
public record FinancialAnalyticsMeasureValue(
    String measureId,
    BigDecimal value,
    FinancialMetricResultState state,
    FinancialMetricUnit unit,
    String currencyCode
) {}
