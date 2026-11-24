package com.fintrack.app.service.budget;

import java.time.LocalDate;
import java.util.List;

/** Authoritative persisted profile preference plus the Budget boundaries affected by an explicit change. */
public record BudgetProfileTimeZoneView(String timeZone, boolean configurationRequired, List<EffectiveChange> effectiveChanges) {
    public record EffectiveChange(Long budgetId, String budgetName, LocalDate effectivePeriodStart) {}
}
