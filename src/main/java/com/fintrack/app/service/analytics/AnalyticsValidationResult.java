package com.fintrack.app.service.analytics;

import java.util.List;
import java.util.Optional;

/** A validation result never implies that a financial aggregation was executed. */
public record AnalyticsValidationResult(
    Optional<ValidatedFinancialAnalyticsRequest> normalizedRequest,
    List<AnalyticsDiagnostic> diagnostics
) {
    public AnalyticsValidationResult {
        normalizedRequest = normalizedRequest == null ? Optional.empty() : normalizedRequest;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean isValid() {
        return diagnostics.isEmpty() && normalizedRequest.isPresent();
    }
}
