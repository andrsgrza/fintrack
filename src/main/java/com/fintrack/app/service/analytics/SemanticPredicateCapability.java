package com.fintrack.app.service.analytics;

import java.util.List;

/** Lists the operators approved for a filterable semantic field. */
public record SemanticPredicateCapability(SemanticField field, List<SemanticPredicateOperator> operators) {
    public SemanticPredicateCapability {
        operators = List.copyOf(operators);
    }
}
