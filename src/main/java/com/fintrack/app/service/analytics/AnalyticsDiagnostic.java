package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One user-comprehensible, field-addressable validation diagnostic. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AnalyticsDiagnostic(AnalyticsDiagnosticCode code, String field, String message) {}
