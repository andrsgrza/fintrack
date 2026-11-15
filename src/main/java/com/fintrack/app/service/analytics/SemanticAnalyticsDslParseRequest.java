package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Strict, text-only input for a non-executing semantic parse operation. */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonDeserialize(using = StrictAnalyticsContractDeserializers.SemanticParseRequestDeserializer.class)
public record SemanticAnalyticsDslParseRequest(String text) {}
