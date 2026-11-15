package com.fintrack.app.service.analytics;

/** A catalog-governed analytical relation; it is never a client-provided table name. */
public enum SemanticDataset {
    TRANSACTION_LEDGER,
    /** One currency-partitioned, authoritative financial position at the requested observation cutoff. */
    FINANCIAL_POSITION_SNAPSHOT,
    ACCOUNT_POSITION_SNAPSHOT,
    /** One credit-card account at one authoritative observation date. */
    CREDIT_ACCOUNT_SNAPSHOT,
    /** One validated InternalTransfer aggregate, never either individual transaction leg. */
    INTERNAL_TRANSFER_EVENT,
}
