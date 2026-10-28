package com.fintrack.app.service.analytics;

/** A typed, catalog-owned grouping value; arbitrary persistence fields are never exposed. */
public record FinancialAnalyticsDimensionValue(String id, Object value) {}
