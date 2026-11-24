package com.fintrack.app.service.budget;

import com.fintrack.app.service.analytics.SemanticAnalyticsExecutionRequest;
import com.fintrack.app.service.analytics.SemanticAnalyticsTrustedOwnerContext;
import com.fintrack.app.service.analytics.SemanticQueryDefinition;
import com.fintrack.app.service.analytics.VisualizationContext;

/**
 * The complete typed result of compiling a Budget period. A failure is deliberately distinct from a financial
 * zero; callers must never turn an incomplete Budget configuration into progress of zero.
 */
public sealed interface BudgetSemanticQueryCompilation
    permits BudgetSemanticQueryCompilation.Compiled, BudgetSemanticQueryCompilation.Failed {
    record Compiled(
        SemanticQueryDefinition definition,
        VisualizationContext context,
        SemanticAnalyticsExecutionRequest request,
        SemanticAnalyticsTrustedOwnerContext trustedOwner
    )
        implements BudgetSemanticQueryCompilation {}

    record Failed(BudgetSemanticCompilationFailure reason, String message) implements BudgetSemanticQueryCompilation {}
}
