package com.fintrack.app.domain.enumeration;

/** Lifecycle intent takes effect only at a complete Budget period boundary. */
public enum BudgetLifecycleEventType {
    PAUSE_REQUESTED,
    PAUSE_CANCELLED,
    RESUME_REQUESTED,
    FINISH_REQUESTED,
}
