package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Bounded text input for the canonical Analytics definition language. */
@JsonDeserialize(using = FinancialAnalyticsDslContractDeserializers.ParseRequestDeserializer.class)
public record FinancialAnalyticsDslParseRequest(String text) {}
