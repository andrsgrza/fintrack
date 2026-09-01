package com.fintrack.app.domain.enumeration;

/**
 * The main lifecycle status for a transaction candidate.
 */
public enum TransactionCandidateStatus {
    DRAFT,
    NEEDS_REVIEW,
    READY_TO_POST,
    POSTED,
    CANCELLED,
    FAILED,
}
