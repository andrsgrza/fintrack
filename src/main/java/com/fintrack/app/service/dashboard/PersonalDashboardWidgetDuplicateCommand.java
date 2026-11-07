package com.fintrack.app.service.dashboard;

/** Idempotent request to create an independent copy of one owned widget in the same dashboard. */
public record PersonalDashboardWidgetDuplicateCommand(Long expectedVersion, String clientRequestId) {}
