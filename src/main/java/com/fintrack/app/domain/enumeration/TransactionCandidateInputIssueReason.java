package com.fintrack.app.domain.enumeration;

/** Why a bounded submitted token cannot be represented by the current candidate field. */
public enum TransactionCandidateInputIssueReason {
    MALFORMED_VALUE,
    UNSUPPORTED_CURRENCY,
    CURRENCY_MISMATCH,
}
