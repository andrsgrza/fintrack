package com.fintrack.app.service.analytics;

/** A catalog-governed analytical relation; it is never a client-provided table name. */
public enum SemanticDataset {
    TRANSACTION_LEDGER,
    ACCOUNT_POSITION_SNAPSHOT,
}
