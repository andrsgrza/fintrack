package com.fintrack.app.service.budget;

/** Lifecycle operations use the caller's explicit IANA zone to choose the next local calendar boundary. */
public record BudgetLifecycleCommand(Long expectedVersion, String clientRequestId, String timeZone) {}
