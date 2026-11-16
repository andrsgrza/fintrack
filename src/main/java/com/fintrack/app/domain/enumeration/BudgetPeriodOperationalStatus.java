package com.fintrack.app.domain.enumeration;

/** Operational state of a whole scheduled Budget period; it is never a financial evaluation result. */
public enum BudgetPeriodOperationalStatus {
    SCHEDULED,
    ACTIVE,
    PAUSED,
    COMPLETED,
}
