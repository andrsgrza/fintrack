package com.fintrack.app.service.budget;

import java.util.List;

/** Bounded, deterministic page of derived Budget period evaluations; it does not persist period snapshots. */
public record BudgetEvaluationHistory(long firstPeriodIndex, int size, List<BudgetEvaluationResult> evaluations) {
    public BudgetEvaluationHistory {
        evaluations = List.copyOf(evaluations);
    }
}
