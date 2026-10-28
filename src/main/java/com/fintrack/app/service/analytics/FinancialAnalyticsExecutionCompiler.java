package com.fintrack.app.service.analytics;

/** R3-2 will compile a validated semantic request onto the authoritative FINTRACK financial executors. */
public interface FinancialAnalyticsExecutionCompiler {
    FinancialAnalyticsExecutionPlan compile(ValidatedFinancialAnalyticsRequest request);
}
