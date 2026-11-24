package com.fintrack.app.domain.enumeration;

/** Durable state of an already-captured Budget notification event. */
public enum BudgetNotificationOutboxState {
    PENDING,
    MATERIALIZED,
    FAILED,
}
