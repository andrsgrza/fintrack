package com.fintrack.app.service.budget;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
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
    @JsonSerialize(using = BudgetDecimalJson.Serializer.class) BigDecimal previousAmount,
    @JsonSerialize(using = BudgetDecimalJson.Serializer.class) BigDecimal previousProgressPercentage,
    @JsonSerialize(using = BudgetDecimalJson.Serializer.class) BigDecimal newAmount,
    @JsonSerialize(using = BudgetDecimalJson.Serializer.class) BigDecimal newProgressPercentage,
    @JsonSerialize(using = BudgetDecimalJson.Serializer.class) BigDecimal targetAmount,
    @JsonSerialize(contentUsing = BudgetDecimalJson.Serializer.class) List<BigDecimal> newlyCrossedPercentages,
    String sourceMutationKey,
    Long effectiveRevisionId
) {}
