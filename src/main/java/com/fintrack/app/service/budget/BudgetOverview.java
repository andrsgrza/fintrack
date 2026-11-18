package com.fintrack.app.service.budget;

import java.time.LocalDate;
import java.util.List;

/**
 * Owner-scoped Budget list. A null reference date means each card was resolved at its own authoritative current
 * Budget date; a non-null value is one explicitly selected shared calendar date.
 */
public record BudgetOverview(LocalDate referenceDate, String timeZone, List<BudgetOverviewItem> budgets) {}
