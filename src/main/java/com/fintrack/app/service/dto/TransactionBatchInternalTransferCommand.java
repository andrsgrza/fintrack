package com.fintrack.app.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.Serializable;

/**
 * Intent-only command for one OPEN batch row. The referenced transaction is
 * never modified here; it is revalidated and linked only during publication.
 */
public class TransactionBatchInternalTransferCommand implements Serializable {

    @NotNull
    @PositiveOrZero
    private Long expectedCandidateVersion;

    @NotNull
    @Positive
    private Long counterpartTransactionId;

    public Long getExpectedCandidateVersion() {
        return expectedCandidateVersion;
    }

    public void setExpectedCandidateVersion(Long expectedCandidateVersion) {
        this.expectedCandidateVersion = expectedCandidateVersion;
    }

    public Long getCounterpartTransactionId() {
        return counterpartTransactionId;
    }

    public void setCounterpartTransactionId(Long counterpartTransactionId) {
        this.counterpartTransactionId = counterpartTransactionId;
    }
}
