package com.fintrack.app.service.analytics;

/** Future execution must preserve these states instead of encoding unavailable or inapplicable values as zero. */
public enum FinancialMetricResultState {
    ZERO,
    NO_DATA,
    NOT_APPLICABLE,
    UNAVAILABLE,
    PARTIAL,
    COMPLETE,
}
