package com.fintrack.app.service.budget;

import com.fintrack.app.domain.enumeration.BudgetPeriodOperationalStatus;
import java.time.LocalDate;

/** A calendar/status projection only; no spending, income or progress is calculated in Budget-2. */
public record BudgetOperationalPeriodView(
    long index,
    LocalDate startInclusive,
    LocalDate endExclusive,
    Long effectiveRevisionId,
    BudgetPeriodOperationalStatus status,
    boolean evaluationEligible
) {}
