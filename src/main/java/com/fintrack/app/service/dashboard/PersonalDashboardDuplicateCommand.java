package com.fintrack.app.service.dashboard;

/** Creates an independent copy of one owner-scoped dashboard using a durable client retry key. */
public record PersonalDashboardDuplicateCommand(Long expectedVersion, String name, String description, String clientRequestId) {}
