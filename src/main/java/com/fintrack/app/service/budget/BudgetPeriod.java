package com.fintrack.app.service.budget;

import java.time.LocalDate;

/** One budget window: its start is inclusive and its end is exclusive. */
public record BudgetPeriod(long index, LocalDate startInclusive, LocalDate endExclusive) {}
