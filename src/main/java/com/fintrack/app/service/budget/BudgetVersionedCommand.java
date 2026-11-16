package com.fintrack.app.service.budget;

/** Optimistic-lock and retry material common to non-creation Budget commands. */
public record BudgetVersionedCommand(Long expectedVersion, String clientRequestId, String timeZone) {}
