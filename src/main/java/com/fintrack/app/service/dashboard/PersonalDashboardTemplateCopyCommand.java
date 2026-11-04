package com.fintrack.app.service.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.enumeration.PersonalDashboardVisualizationType;
import com.fintrack.app.domain.enumeration.PersonalDashboardWidgetWidth;
import com.fintrack.app.service.analytics.SemanticQueryDefinition;
import com.fintrack.app.service.analytics.VisualizationContext;
import java.util.List;

/**
 * A client may request a copy of one official presentation, but every supplied widget still goes through the same
 * owner-scoped semantic validation as a manually persisted widget. Nothing here records a mutable link back to the
 * source template.
 */
public record PersonalDashboardTemplateCopyCommand(
    String templateId,
    String name,
    String description,
    VisualizationContext defaultContext,
    List<TemplateWidget> widgets,
    String clientRequestId
) {
    public record TemplateWidget(
        String title,
        String description,
        SemanticQueryDefinition definition,
        PersonalDashboardVisualizationType visualizationType,
        JsonNode visualizationOptions,
        PersonalDashboardWidgetWidth width,
        Boolean visible
    ) {}
}
