package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

/** Declares what a governed financial query means, without owner scope or execution context. */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonDeserialize(using = StrictAnalyticsContractDeserializers.QueryDefinitionDeserializer.class)
public record QueryDefinition(
    Integer schemaVersion,
    String semanticModel,
    List<String> measures,
    List<String> groupings,
    List<AnalyticalPredicate> analyticalPredicates,
    String dateBasis,
    AnalyticsSort sort,
    Integer limit
) {
    public QueryDefinition {
        measures = measures == null ? List.of() : List.copyOf(measures);
        groupings = groupings == null ? List.of() : List.copyOf(groupings);
        analyticalPredicates = analyticalPredicates == null ? List.of() : List.copyOf(analyticalPredicates);
    }
}
