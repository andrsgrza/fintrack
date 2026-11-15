package com.fintrack.app.service.analytics;

/** A non-executing parse result: canonical AST plus its stable text spelling. */
public record SemanticAnalyticsDslParseResult(SemanticQueryDefinition definition, String canonicalText) {}
