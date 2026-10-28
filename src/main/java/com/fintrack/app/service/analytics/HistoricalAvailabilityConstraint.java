package com.fintrack.app.service.analytics;

public enum HistoricalAvailabilityConstraint {
    REQUIRES_ACCOUNT_OPENING_DATE,
    REQUIRES_CURRENT_CREDIT_LIMIT,
    REQUIRES_NONZERO_CREDIT_LIMIT,
    ZERO_FOR_EMPTY_PERIOD,
    UNAVAILABLE_FOR_ZERO_DENOMINATOR,
}
