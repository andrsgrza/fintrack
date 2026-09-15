package com.fintrack.app.domain.enumeration;

/**
 * Origin of a persisted classification value on a transaction candidate.
 *
 * This is deliberately independent of the workflow-level classification review status.
 */
public enum TransactionCandidateClassificationSource {
    AUTOMATIC,
    MANUAL,
}
