package com.fintrack.app.domain.enumeration;

/**
 * The IngestionStatus enumeration.
 */
public enum IngestionStatus {
    PENDING,
    READY,
    PARTIALLY_READY,
    PROCESSING,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
}
