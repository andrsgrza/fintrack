package com.fintrack.app.service.analytics;

import java.util.List;

/** Static, authenticated capability disclosure for the canonical semantic Analytics API. */
public record SemanticAnalyticsCatalog(
    int schemaVersion,
    String semanticModel,
    String catalogVersion,
    List<SemanticDatasetDefinition> datasets,
    List<String> supportedRelativePeriods,
    int defaultLimit,
    int maxLimit,
    boolean authenticatedOwnerRequired
) {
    public SemanticAnalyticsCatalog {
        datasets = List.copyOf(datasets);
        supportedRelativePeriods = List.copyOf(supportedRelativePeriods);
    }
}
