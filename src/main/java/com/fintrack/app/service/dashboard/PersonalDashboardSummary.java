package com.fintrack.app.service.dashboard;

import java.time.Instant;

/** List response deliberately omits definitions and financial results. */
public record PersonalDashboardSummary(Long id, String name, String description, Long version, int widgetCount, Instant updatedAt) {}
