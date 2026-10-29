package com.fintrack.app.service.analytics;

/** One exact aggregate expression approved for a dataset field. */
public record SemanticAggregateCapability(
    SemanticAggregateFunction function,
    SemanticField field,
    SemanticUnit outputUnit,
    SemanticAdditivityPolicy additivity
) {}
