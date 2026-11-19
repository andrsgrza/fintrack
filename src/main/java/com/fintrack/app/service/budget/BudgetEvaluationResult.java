package com.fintrack.app.service.budget;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fintrack.app.domain.enumeration.BudgetPeriodOperationalStatus;
import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.service.analytics.SemanticResultAvailability;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only financial interpretation of one immutable Budget period. Operational, semantic-availability, and goal
 * states intentionally remain separate: none of them may be inferred from a numeric zero.
 */
public record BudgetEvaluationResult(
    Long budgetId,
    String budgetName,
    BudgetType budgetType,
    CurrencyCode currency,
    long periodIndex,
    LocalDate startInclusive,
    LocalDate endExclusive,
    Long revisionId,
    @JsonSerialize(using = BudgetDecimalJson.Serializer.class) BigDecimal targetAmount,
    BudgetPeriodOperationalStatus operationalStatus,
    BudgetEvaluationPeriodTiming periodTiming,
    boolean evaluationApplicable,
    @JsonSerialize(using = BudgetDecimalJson.Serializer.class) BigDecimal actualAmount,
    @JsonSerialize(using = BudgetDecimalJson.Serializer.class) BigDecimal progressPercentage,
    @JsonSerialize(using = BudgetDecimalJson.Serializer.class) BigDecimal remainingAmount,
    BudgetGoalStatus goalStatus,
    BudgetEvaluationStatus evaluationStatus,
    SemanticResultAvailability semanticAvailability,
    BudgetEvaluationFailure failureReason,
    String message
) {}
