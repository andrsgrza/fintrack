package com.fintrack.app.service.dashboard;

/** Selects exactly one owned dashboard or one governed official template as the owner's default. */
public record PersonalDashboardDefaultCommand(Long expectedVersion, Long dashboardId, String officialTemplateId) {}
