package com.fintrack.app.service.dashboard;

import com.fintrack.app.service.analytics.VisualizationContext;
import java.time.Instant;
import java.util.List;

/** Aggregate read model used by the future personal-dashboard viewer. */
public record PersonalDashboardDetail(
    Long id,
    String name,
    String description,
    int schemaVersion,
    Long version,
    VisualizationContext defaultContext,
    List<PersonalDashboardWidgetView> widgets,
    Instant createdAt,
    Instant updatedAt
) {
    public PersonalDashboardDetail {
        widgets = List.copyOf(widgets);
    }
}
