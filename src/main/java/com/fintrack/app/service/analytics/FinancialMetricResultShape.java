package com.fintrack.app.service.analytics;

/**
 * Result-shape policy for R3-2. A currency facet is a required result partition for money and does not consume a
 * user-requested grouping slot. No FX conversion is modeled by this contract.
 */
public record FinancialMetricResultShape(
    FinancialMetricUnit unit,
    FinancialMetricCurrencyBehavior currencyBehavior,
    boolean explicitCurrencyFacetRequired,
    boolean userGroupingCapacityConsumed
) {
    public static FinancialMetricResultShape forMetric(FinancialMetricDefinition metric) {
        boolean monetary = metric.currencyBehavior() == FinancialMetricCurrencyBehavior.PARTITION_BY_CURRENCY;
        return new FinancialMetricResultShape(metric.unit(), metric.currencyBehavior(), monetary, false);
    }
}
