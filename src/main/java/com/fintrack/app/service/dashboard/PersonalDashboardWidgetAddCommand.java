package com.fintrack.app.service.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.enumeration.PersonalDashboardVisualizationType;
import com.fintrack.app.domain.enumeration.PersonalDashboardWidgetWidth;
import com.fintrack.app.service.analytics.SemanticQueryDefinition;

/** A widget contains a definition and rendering configuration, never an execution context or financial result. */
public record PersonalDashboardWidgetAddCommand(
    Long expectedVersion,
    String title,
    String description,
    SemanticQueryDefinition definition,
    PersonalDashboardVisualizationType visualizationType,
    JsonNode visualizationOptions,
    PersonalDashboardWidgetWidth width,
    Boolean visible,
    String clientRequestId
) {}
