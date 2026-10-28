package com.fintrack.app.service.analytics;

/** Monetary output is partitioned by currency unless a future FX capability explicitly says otherwise. */
public enum FinancialMetricCurrencyBehavior {
    PARTITION_BY_CURRENCY,
    NOT_MONETARY,
}
