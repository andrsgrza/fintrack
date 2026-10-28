package com.fintrack.app.service.analytics;

import java.util.List;

/** Future Analytics entry points can surface the same typed diagnostics without inventing transport-specific errors. */
public class FinancialAnalyticsValidationException extends RuntimeException {

    private final List<AnalyticsDiagnostic> diagnostics;

    public FinancialAnalyticsValidationException(List<AnalyticsDiagnostic> diagnostics) {
        super("Financial Analytics request validation failed");
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<AnalyticsDiagnostic> getDiagnostics() {
        return diagnostics;
    }
}
