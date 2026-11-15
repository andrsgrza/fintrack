package com.fintrack.app.service.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.enumeration.PersonalDashboardVisualizationType;
import com.fintrack.app.domain.enumeration.PersonalDashboardWidgetWidth;
import com.fintrack.app.service.analytics.SemanticQueryDefinition;
import java.time.Instant;

/** Persisted widget read model; the caller still supplies any interactive context override in a future UI slice. */
public record PersonalDashboardWidgetView(
    Long id,
    String title,
    String description,
    SemanticQueryDefinition definition,
    PersonalDashboardVisualizationType visualizationType,
    JsonNode visualizationOptions,
    int position,
    PersonalDashboardWidgetWidth width,
    boolean visible,
    Instant createdAt,
    Instant updatedAt
) {}
