package com.fintrack.app.service.analytics;

public record SemanticAnalyticsResultColumn(
    String id,
    String label,
    boolean dimension,
    SemanticUnit unit,
    boolean currencyFaceted,
    SemanticAdditivityPolicy additivity
) {
    public SemanticAnalyticsResultColumn(String id, boolean dimension, SemanticUnit unit, boolean currencyFaceted) {
        this(id, id, dimension, unit, currencyFaceted, SemanticAdditivityPolicy.ADDITIVE);
    }
}
