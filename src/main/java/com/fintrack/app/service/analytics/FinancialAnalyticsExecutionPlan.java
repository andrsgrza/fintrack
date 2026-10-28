package com.fintrack.app.service.analytics;

/** Marker plan for R3-2. This slice deliberately provides no executor implementation. */
public record FinancialAnalyticsExecutionPlan(ValidatedFinancialAnalyticsRequest validatedRequest) {}
