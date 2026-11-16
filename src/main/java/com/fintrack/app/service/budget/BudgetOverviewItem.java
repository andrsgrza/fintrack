package com.fintrack.app.service.budget;

import com.fintrack.app.domain.enumeration.BudgetPeriodOperationalStatus;
import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CurrencyCode;

/** A card-level read model. `evaluation` is absent only when the reference date has no scheduled period. */
public record BudgetOverviewItem(
    Long budgetId,
    String name,
    String description,
    BudgetType type,
    CurrencyCode currency,
    BudgetPeriodOperationalStatus operationalStatus,
    boolean hasPeriodAtReferenceDate,
    String referenceMessage,
    BudgetEvaluationResult evaluation
) {}
