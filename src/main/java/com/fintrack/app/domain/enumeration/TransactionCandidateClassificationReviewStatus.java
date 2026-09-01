package com.fintrack.app.domain.enumeration;

/**
 * Category/tag classification review state for a transaction candidate.
 */
public enum TransactionCandidateClassificationReviewStatus {
    NOT_EVALUATED,
    SUGGESTED,
    USER_SELECTED,
    STALE,
    NOT_APPLICABLE,
}
