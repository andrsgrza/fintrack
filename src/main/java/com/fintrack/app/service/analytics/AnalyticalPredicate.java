package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

/** An AND-composed, typed predicate over a registry-owned semantic attribute. */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonDeserialize(using = StrictAnalyticsContractDeserializers.PredicateDeserializer.class)
public record AnalyticalPredicate(String attributeId, String operatorId, List<String> values) {
    public AnalyticalPredicate {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
