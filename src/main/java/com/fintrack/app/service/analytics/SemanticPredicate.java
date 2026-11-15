package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
@JsonDeserialize(using = StrictAnalyticsContractDeserializers.SemanticPredicateDeserializer.class)
public record SemanticPredicate(SemanticField field, SemanticPredicateOperator operator, List<String> values) {
    public SemanticPredicate {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
