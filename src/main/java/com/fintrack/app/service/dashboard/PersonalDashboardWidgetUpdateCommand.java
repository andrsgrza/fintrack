package com.fintrack.app.service.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.enumeration.PersonalDashboardVisualizationType;
import com.fintrack.app.domain.enumeration.PersonalDashboardWidgetWidth;
import com.fintrack.app.service.analytics.SemanticQueryDefinition;

/** Widget mutations participate in the parent dashboard's single optimistic-concurrency boundary. */
public record PersonalDashboardWidgetUpdateCommand(
    Long expectedVersion,
    String title,
    String description,
    SemanticQueryDefinition definition,
    PersonalDashboardVisualizationType visualizationType,
    JsonNode visualizationOptions,
    PersonalDashboardWidgetWidth width,
    Boolean visible
) {}
