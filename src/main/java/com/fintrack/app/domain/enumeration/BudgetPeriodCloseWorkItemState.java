package com.fintrack.app.domain.enumeration;

/** Durable execution state for one logical Budget period-close event. */
public enum BudgetPeriodCloseWorkItemState {
    PENDING,
    PROCESSING,
    RETRY_SCHEDULED,
    CAPTURED,
    BLOCKED,
    CANCELLED,
}
