package com.fintrack.app.service.analytics;

/** Machine-readable non-fatal execution information. */
public record FinancialAnalyticsWarning(AnalyticsDiagnosticCode code, String message) {}
