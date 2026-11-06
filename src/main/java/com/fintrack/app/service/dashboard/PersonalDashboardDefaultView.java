package com.fintrack.app.service.dashboard;

/** Exactly one selection is effective: an owned dashboard ID or a stable official template ID. */
public record PersonalDashboardDefaultView(Long dashboardId, String officialTemplateId, Long version) {}
