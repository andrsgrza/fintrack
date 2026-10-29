package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = false)
@JsonDeserialize(using = StrictAnalyticsContractDeserializers.SemanticOrderDeserializer.class)
public record SemanticOrder(SemanticField field, String projectionAlias, SemanticSortDirection direction) {
    public SemanticOrder(SemanticField field, SemanticSortDirection direction) {
        this(field, null, direction);
    }

    public static SemanticOrder projection(String projectionAlias, SemanticSortDirection direction) {
        return new SemanticOrder(null, projectionAlias, direction);
    }
}
