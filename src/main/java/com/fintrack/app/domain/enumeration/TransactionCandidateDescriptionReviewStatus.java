package com.fintrack.app.domain.enumeration;

/**
 * Description-normalization review state for a transaction candidate.
 */
public enum TransactionCandidateDescriptionReviewStatus {
    NOT_EVALUATED,
    AUTO_APPLIED,
    USER_EDITED,
    STALE,
    NOT_APPLICABLE,
}
