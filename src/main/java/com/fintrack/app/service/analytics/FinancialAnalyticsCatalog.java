package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Versioned, data-free capability catalog consumed by future clients. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record FinancialAnalyticsCatalog(
    int schemaVersion,
    String semanticModel,
    String catalogVersion,
    List<FinancialMetricDefinition> metrics,
    List<FinancialSemanticAttributeDefinition> attributes,
    List<String> supportedOperators,
    List<String> supportedRelativePeriods,
    int defaultLimit,
    int maxLimit,
    int maxMeasures,
    int maxGroupings,
    int maxPredicates,
    List<String> subjectAreas
) {
    public FinancialAnalyticsCatalog {
        metrics = List.copyOf(metrics);
        attributes = List.copyOf(attributes);
        supportedOperators = List.copyOf(supportedOperators);
        supportedRelativePeriods = List.copyOf(supportedRelativePeriods);
        subjectAreas = List.copyOf(subjectAreas);
    }
}
