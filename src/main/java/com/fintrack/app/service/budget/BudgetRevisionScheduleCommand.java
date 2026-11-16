package com.fintrack.app.service.budget;

import java.time.LocalDate;

/** Schedules a complete configuration snapshot now, for the current period, or for a later complete boundary. */
public record BudgetRevisionScheduleCommand(
    Long expectedVersion,
    String clientRequestId,
    String timeZone,
    boolean applyToCurrentPeriod,
    LocalDate effectivePeriodStart,
    BudgetInitialRevisionCommand revision
) {}
