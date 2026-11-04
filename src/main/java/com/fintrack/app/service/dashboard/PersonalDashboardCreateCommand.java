package com.fintrack.app.service.dashboard;

import com.fintrack.app.service.analytics.VisualizationContext;

/** Creation input intentionally has no owner, results, or arbitrary entity IDs. */
public record PersonalDashboardCreateCommand(
    String name,
    String description,
    VisualizationContext defaultContext,
    String clientRequestId
) {}
