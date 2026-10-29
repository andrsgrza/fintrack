package com.fintrack.app.service.analytics;

import java.util.List;

/** Internal POC validation boundary; no transport-specific error type is introduced. */
public class SemanticAnalyticsValidationException extends RuntimeException {

    private final List<AnalyticsDiagnostic> diagnostics;

    public SemanticAnalyticsValidationException(List<AnalyticsDiagnostic> diagnostics) {
        super("Semantic Analytics request validation failed");
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<AnalyticsDiagnostic> getDiagnostics() {
        return diagnostics;
    }
}
