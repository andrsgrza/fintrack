package com.fintrack.app.domain.enumeration;

public enum BudgetAlignment {
    ANCHORED,
    NATURAL,
    /** A persisted recurring rule whose requested day is retained even when a boundary is clamped. */
    CUSTOM,
}
