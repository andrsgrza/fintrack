package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

/**
 * The sole POC definition of what to calculate. Owner, period, accounts, currencies and result data are excluded.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonDeserialize(using = StrictAnalyticsContractDeserializers.SemanticQueryDefinitionDeserializer.class)
public record SemanticQueryDefinition(
    Integer schemaVersion,
    String semanticModel,
    SemanticDataset dataset,
    SemanticField timeBinding,
    List<SemanticAggregateProjection> projections,
    List<SemanticPredicate> predicates,
    List<SemanticField> groupBy,
    List<SemanticOrder> orderBy,
    Integer limit
) {
    public SemanticQueryDefinition {
        projections = projections == null ? List.of() : List.copyOf(projections);
        predicates = predicates == null ? List.of() : List.copyOf(predicates);
        groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
        orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
    }
}
