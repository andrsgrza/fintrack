package com.fintrack.app.service.analytics;

import java.util.List;

/** A syntax or source-mapped semantic error in the canonical semantic language. */
public class SemanticAnalyticsDslException extends RuntimeException {

    private final List<AnalyticsDiagnostic> diagnostics;

    public SemanticAnalyticsDslException(List<AnalyticsDiagnostic> diagnostics) {
        super("Semantic Analytics DSL validation failed");
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<AnalyticsDiagnostic> getDiagnostics() {
        return diagnostics;
    }
}
