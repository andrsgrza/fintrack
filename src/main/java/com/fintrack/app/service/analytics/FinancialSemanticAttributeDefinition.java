package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;

/** A controlled analytical field, deliberately separate from physical persistence fields and relationships. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record FinancialSemanticAttributeDefinition(
    String id,
    String displayNameEn,
    String displayNameEs,
    String descriptionEn,
    String descriptionEs,
    SemanticValueType valueType,
    Set<String> allowedOperators,
    boolean groupingSupported,
    boolean predicateSupported,
    Set<String> compatibleSubjectAreas,
    String cardinality,
    boolean requiresOwnedReferenceValidation
) {
    public FinancialSemanticAttributeDefinition {
        allowedOperators = Set.copyOf(allowedOperators);
        compatibleSubjectAreas = Set.copyOf(compatibleSubjectAreas);
    }
}
