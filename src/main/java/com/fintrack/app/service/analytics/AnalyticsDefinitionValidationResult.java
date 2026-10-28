package com.fintrack.app.service.analytics;

import java.util.List;
import java.util.Optional;

/** Validation outcome for a definition before a visualization supplies its execution context. */
public record AnalyticsDefinitionValidationResult(Optional<QueryDefinition> normalizedDefinition, List<AnalyticsDiagnostic> diagnostics) {
    public AnalyticsDefinitionValidationResult {
        normalizedDefinition = normalizedDefinition == null ? Optional.empty() : normalizedDefinition;
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean isValid() {
        return diagnostics.isEmpty() && normalizedDefinition.isPresent();
    }
}
