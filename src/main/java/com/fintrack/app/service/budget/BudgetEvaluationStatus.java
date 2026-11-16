package com.fintrack.app.service.budget;

/** Whether FINTRACK calculated an authoritative financial actual for this period. */
public enum BudgetEvaluationStatus {
    EVALUATED,
    NOT_YET_EVALUATED,
    NOT_EVALUATED,
    INCOMPLETE_CONFIGURATION,
    SEMANTIC_UNAVAILABLE,
    SEMANTIC_CONTRACT_ERROR,
    SEMANTIC_EXECUTION_FAILED,
}
