package com.fintrack.app.service.dto;

/** Explicitly clearable current-value fields in an internal batch candidate patch. */
public enum TransactionBatchCandidatePatchField {
    TRANSACTION_DATE,
    POSTING_DATE,
    DESCRIPTION,
    SIGNED_AMOUNT,
    EXTERNAL_REFERENCE,
    NOTES,
}
