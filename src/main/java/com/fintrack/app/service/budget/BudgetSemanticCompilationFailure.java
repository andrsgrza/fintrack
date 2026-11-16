package com.fintrack.app.service.budget;

/** A typed, non-financial outcome for compiling a Budget period into the canonical Analytics contract. */
public enum BudgetSemanticCompilationFailure {
    INCOMPLETE_CONFIGURATION,
    INVALID_EFFECTIVE_PERIOD,
    MISSING_OR_INACCESSIBLE_REFERENCE,
    UNSUPPORTED_SEMANTIC_FILTER_COMBINATION,
    UNAUTHORIZED_OWNER_CONTEXT,
}
