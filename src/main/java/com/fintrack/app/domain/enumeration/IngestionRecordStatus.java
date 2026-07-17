package com.fintrack.app.domain.enumeration;

/**
 * The IngestionRecordStatus enumeration.
 */
public enum IngestionRecordStatus {
    VALID,
    DISABLED,
    IMPORTED,
    SKIPPED_DUPLICATE,
    REJECTED,
    FAILED,
}
