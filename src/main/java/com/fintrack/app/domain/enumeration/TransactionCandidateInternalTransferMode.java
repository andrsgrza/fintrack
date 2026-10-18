package com.fintrack.app.domain.enumeration;

/**
 * The one intentional choice a resumable manual transaction draft makes when
 * it will be posted as an internal transfer.  The current candidate always
 * becomes one leg; only the other leg is either created or selected.
 */
public enum TransactionCandidateInternalTransferMode {
    CREATE_COUNTERPART,
    LINK_EXISTING,
}
