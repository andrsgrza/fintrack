package com.fintrack.app.service.budget;

/** Stable reason for a result whose financial actual could not be evaluated. */
public enum BudgetEvaluationFailure {
    PAUSED_PERIOD,
    COMPLETED_BUDGET,
    FUTURE_PERIOD,
    INCOMPLETE_CONFIGURATION,
    MISSING_OR_INACCESSIBLE_REFERENCE,
    UNSUPPORTED_SEMANTIC_FILTER_COMBINATION,
    UNAUTHORIZED_OWNER_CONTEXT,
    UNAVAILABLE_FINANCIAL_RESULT,
    INVALID_SEMANTIC_RESULT,
    SEMANTIC_EXECUTION_FAILED,
}
