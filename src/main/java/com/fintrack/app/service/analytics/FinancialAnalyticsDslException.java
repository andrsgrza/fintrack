package com.fintrack.app.service.analytics;

import java.util.List;

/** A bounded parser/formatter failure that can be rendered without exposing implementation details. */
public class FinancialAnalyticsDslException extends RuntimeException {

    private final List<AnalyticsDiagnostic> diagnostics;

    public FinancialAnalyticsDslException(List<AnalyticsDiagnostic> diagnostics) {
        super("Financial Analytics DSL validation failed");
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<AnalyticsDiagnostic> getDiagnostics() {
        return diagnostics;
    }
}
