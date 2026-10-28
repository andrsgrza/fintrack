package com.fintrack.app.service.analytics;

/** A future executor is selected by semantic behavior, never by a physical table name. */
public enum FinancialMetricExecutorSpecialization {
    TRANSACTION_AGGREGATE,
    ACCOUNT_SNAPSHOT,
    TRANSFER_RELATIONSHIP_AGGREGATE,
}
