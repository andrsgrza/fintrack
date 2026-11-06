package com.fintrack.app.service.dashboard;

/** Changes only owner-visible dashboard metadata; context, widgets, and layout are deliberately absent. */
public record PersonalDashboardMetadataUpdateCommand(Long expectedVersion, String name, String description) {}
