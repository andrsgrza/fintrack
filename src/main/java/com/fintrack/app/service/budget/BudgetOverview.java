package com.fintrack.app.service.budget;

import java.time.LocalDate;
import java.util.List;

/** Owner-scoped Budget list evaluated against one intentionally shared reference date. */
public record BudgetOverview(LocalDate referenceDate, String timeZone, List<BudgetOverviewItem> budgets) {}
