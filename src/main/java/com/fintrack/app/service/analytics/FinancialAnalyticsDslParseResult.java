package com.fintrack.app.service.analytics;

/** A parsed normalized definition plus its deterministic text representation. */
public record FinancialAnalyticsDslParseResult(QueryDefinition definition, String canonicalText) {}
