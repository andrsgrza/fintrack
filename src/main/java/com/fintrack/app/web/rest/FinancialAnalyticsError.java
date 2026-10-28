package com.fintrack.app.web.rest;

import com.fintrack.app.service.analytics.AnalyticsDiagnostic;
import java.util.List;

/** Stable 400-class response for predictable canonical Analytics contract failures. */
public record FinancialAnalyticsError(String code, List<AnalyticsDiagnostic> diagnostics) {
    public FinancialAnalyticsError {
        diagnostics = List.copyOf(diagnostics);
    }
}
