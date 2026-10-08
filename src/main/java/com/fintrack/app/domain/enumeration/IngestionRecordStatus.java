package com.fintrack.app.domain.enumeration;

/**
 * The IngestionRecordStatus enumeration.
 */
public enum IngestionRecordStatus {
    VALID,
    DISABLED,
    IMPORTED,
    DELETED_AFTER_IMPORT,
    SKIPPED_DUPLICATE,
    REJECTED,
    FAILED,
}
