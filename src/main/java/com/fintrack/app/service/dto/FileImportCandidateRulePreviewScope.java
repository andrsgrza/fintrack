package com.fintrack.app.service.dto;

/**
 * Controls which TransactionRule outputs are exposed by a read-only FILE_IMPORT preview.
 * The evaluator still runs once with its normal category-and-tag semantics.
 */
public enum FileImportCandidateRulePreviewScope {
    CATEGORY,
    TAGS,
    ALL,
}
