package com.fintrack.app.web.rest;

import com.fintrack.app.service.analytics.AnalyticsDiagnostic;
import java.util.List;

/** Stable 400-class response for predictable canonical semantic-query failures. */
public record SemanticAnalyticsError(String code, List<AnalyticsDiagnostic> diagnostics) {
    public SemanticAnalyticsError {
        diagnostics = List.copyOf(diagnostics);
    }
}
