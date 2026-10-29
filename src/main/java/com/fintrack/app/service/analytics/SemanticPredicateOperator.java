package com.fintrack.app.service.analytics;

public enum SemanticPredicateOperator {
    EQUALS,
    IN,
    /** A category and every descendant in its owned hierarchy. */
    DESCENDANT_OF,
    /** The transaction has no assigned category. */
    IS_UNCATEGORIZED,
    /** The transaction has at least one selected tag. */
    HAS_ANY,
    /** The transaction has every selected tag. */
    HAS_ALL,
    /** The transaction has no tags. */
    HAS_NONE,
}
