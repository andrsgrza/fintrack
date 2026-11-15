package com.fintrack.app.service.dashboard;

import com.fintrack.app.service.analytics.VisualizationContext;

/** Replaces dashboard metadata and its saved default context under one optimistic aggregate version. */
public record PersonalDashboardUpdateCommand(Long expectedVersion, String name, String description, VisualizationContext defaultContext) {}
