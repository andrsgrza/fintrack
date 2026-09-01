package com.fintrack.app.domain.enumeration;

/**
 * Validation state for a transaction candidate's transaction fields.
 */
public enum TransactionCandidateValidationStatus {
    UNKNOWN,
    VALID,
    INVALID,
    STALE,
}
