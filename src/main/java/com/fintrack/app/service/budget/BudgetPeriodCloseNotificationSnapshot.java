package com.fintrack.app.service.budget;

import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Immutable financial evidence captured when one eligible Budget period closes. */
public record BudgetPeriodCloseNotificationSnapshot(
    Long budgetId,
    String budgetName,
    BudgetType budgetType,
    CurrencyCode currency,
    LocalDate periodStart,
    LocalDate periodEndExclusive,
    String timeZone,
    Long revisionId,
    BigDecimal targetAmount,
    BigDecimal actualAmount,
    BigDecimal progressPercentage,
    BudgetEvaluationStatus evaluationStatus,
    Instant logicalClosedAt,
    Instant capturedAt,
    String logicalEventKey
) {}
