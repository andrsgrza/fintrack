package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Canonical future execution input. It intentionally contains no owner, SQL, dashboard identity, or result rows. */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonDeserialize(using = StrictAnalyticsContractDeserializers.ExecutionRequestDeserializer.class)
public record FinancialAnalyticsExecutionRequest(Integer schemaVersion, QueryDefinition definition, VisualizationContext context) {}
