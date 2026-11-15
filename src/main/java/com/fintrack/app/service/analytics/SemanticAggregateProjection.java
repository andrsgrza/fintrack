package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
@JsonDeserialize(using = StrictAnalyticsContractDeserializers.SemanticAggregateProjectionDeserializer.class)
public record SemanticAggregateProjection(
    String alias,
    SemanticAggregateFunction function,
    SemanticField field,
    List<SemanticPredicate> conditions
) {
    public SemanticAggregateProjection {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    /** Keeps the original canonical constructor source-compatible for existing clients and tests. */
    public SemanticAggregateProjection(String alias, SemanticAggregateFunction function, SemanticField field) {
        this(alias, function, field, List.of());
    }
}
