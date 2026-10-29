package com.fintrack.app.service.analytics;

/** A catalog-owned relationship; clients never supply a persistence path or a join expression. */
public record SemanticRelationshipDefinition(String id, String target, String cardinality, String purpose) {}
