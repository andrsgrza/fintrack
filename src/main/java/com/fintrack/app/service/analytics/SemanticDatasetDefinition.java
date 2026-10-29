package com.fintrack.app.service.analytics;

import java.util.List;

/** Complete, static capability declaration for a governed analytical dataset. */
public record SemanticDatasetDefinition(
    SemanticDataset dataset,
    String grain,
    SemanticField timeBinding,
    List<SemanticRelationshipDefinition> relationships,
    List<SemanticFieldDefinition> fields,
    List<SemanticAggregateCapability> aggregateCapabilities,
    List<SemanticPredicateCapability> predicateCapabilities,
    List<SemanticField> groupings,
    SemanticAdditivityPolicy additivity,
    SemanticCurrencyPolicy currencyPolicy,
    String temporalSemantics
) {
    public SemanticDatasetDefinition {
        relationships = List.copyOf(relationships);
        fields = List.copyOf(fields);
        aggregateCapabilities = List.copyOf(aggregateCapabilities);
        predicateCapabilities = List.copyOf(predicateCapabilities);
        groupings = List.copyOf(groupings);
    }
}
