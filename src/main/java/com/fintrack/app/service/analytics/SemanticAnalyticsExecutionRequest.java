package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Couples the semantic definition with the separate, caller-selected visualization context. */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonDeserialize(using = StrictAnalyticsContractDeserializers.SemanticExecutionRequestDeserializer.class)
public record SemanticAnalyticsExecutionRequest(Integer schemaVersion, SemanticQueryDefinition definition, VisualizationContext context) {}
