package com.fintrack.app.service.analytics;

import java.time.Instant;
import java.util.List;

public record SemanticAnalyticsResult(
    int schemaVersion,
    String semanticModel,
    ResolvedVisualizationContext effectiveContext,
    Instant executedAt,
    SemanticResultAvailability availability,
    List<SemanticAnalyticsResultColumn> columns,
    List<SemanticAnalyticsResultRow> rows,
    List<SemanticAnalyticsWarning> warnings
) {
    public SemanticAnalyticsResult {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public SemanticAnalyticsResult(
        int schemaVersion,
        String semanticModel,
        ResolvedVisualizationContext effectiveContext,
        Instant executedAt,
        List<SemanticAnalyticsResultColumn> columns,
        List<SemanticAnalyticsResultRow> rows
    ) {
        this(
            schemaVersion,
            semanticModel,
            effectiveContext,
            executedAt,
            rows == null || rows.isEmpty() ? SemanticResultAvailability.NO_DATA : SemanticResultAvailability.COMPLETE,
            columns,
            rows,
            List.of()
        );
    }
}
