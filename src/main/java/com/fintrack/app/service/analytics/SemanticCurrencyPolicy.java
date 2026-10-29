package com.fintrack.app.service.analytics;

/** States how a dataset keeps monetary observations safe when more than one currency is in scope. */
public enum SemanticCurrencyPolicy {
    NOT_MONETARY,
    PARTITION_BY_ACCOUNT_CURRENCY,
}
