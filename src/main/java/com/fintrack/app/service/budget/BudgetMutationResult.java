package com.fintrack.app.service.budget;

import java.time.LocalDate;

/** Stable acknowledgement for idempotent Budget-2 commands. */
public record BudgetMutationResult(
    Long budgetId,
    Long version,
    Long revisionId,
    Long revisionScheduleId,
    Long lifecycleEventId,
    LocalDate effectivePeriodStart
) {}
