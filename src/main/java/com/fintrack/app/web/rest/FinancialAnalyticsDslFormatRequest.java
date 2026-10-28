package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fintrack.app.service.analytics.QueryDefinition;

/** A definition-only formatter request; execution context is intentionally absent. */
@JsonDeserialize(using = FinancialAnalyticsDslContractDeserializers.FormatRequestDeserializer.class)
public record FinancialAnalyticsDslFormatRequest(QueryDefinition definition) {}
