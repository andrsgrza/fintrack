package com.fintrack.app.service.budget;

import java.time.LocalDate;
import java.util.List;

/** Authoritative, bounded projection of a proposed calendar; it is never persisted. */
public record BudgetCalendarPreview(LocalDate localToday, List<BudgetPeriod> periods) {}
