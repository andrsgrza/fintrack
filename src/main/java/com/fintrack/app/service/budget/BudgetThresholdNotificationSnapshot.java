package com.fintrack.app.service.budget;

import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Immutable payload written before the transaction that caused it can commit. */
public record BudgetThresholdNotificationSnapshot(
    Long ownerId,
    Long budgetId,
    String budgetName,
    BudgetType budgetType,
    CurrencyCode currency,
    LocalDate periodStart,
    LocalDate periodEndExclusive,
    String timeZone,
    Instant eventAt,
    BigDecimal previousAmount,
    BigDecimal previousProgressPercentage,
    BigDecimal newAmount,
    BigDecimal newProgressPercentage,
    BigDecimal targetAmount,
    List<BigDecimal> newlyCrossedPercentages,
    String sourceMutationKey,
    Long effectiveRevisionId
) {}
