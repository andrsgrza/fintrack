package com.fintrack.app.domain.enumeration;

/**
 * Domain conditions that make a future permanent FinancialAccount deletion unsafe.
 *
 * <p>These codes are returned by the read-only deletion preview. They do not change the
 * legacy account delete endpoint until the aggregate-delete command is introduced.</p>
 */
public enum FinancialAccountDeletionBlockerCode {
    CROSS_ACCOUNT_TRANSFER,
    BUDGET_SCOPE_WOULD_BROADEN,
    RULE_ACCOUNT_CONDITION_REFERENCE,
    CORRUPT_CANDIDATE,
    CORRUPT_INGESTION_GRAPH,
    UNSUPPORTED_API_IMPORT_CANDIDATE,
}
