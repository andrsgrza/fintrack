package com.fintrack.app.service.analytics;

import java.time.LocalDate;

/** Inclusive business-date range. */
public record AnalyticsDateRange(LocalDate from, LocalDate through) {
    public AnalyticsDateRange {
        if (from == null || through == null || from.isAfter(through)) {
            throw new IllegalArgumentException("An Analytics date range must have an inclusive from date on or before through date");
        }
    }
}
