package com.fintrack.app.service.analytics;

public record SemanticAnalyticsResultDimension(SemanticField field, Object value, SemanticDimensionMetadata metadata) {
    public SemanticAnalyticsResultDimension(SemanticField field, Object value) {
        this(field, value, null);
    }
}
