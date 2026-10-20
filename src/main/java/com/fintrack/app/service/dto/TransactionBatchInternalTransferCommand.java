package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.TransactionCandidateInternalTransferMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * Intent-only command for one OPEN batch row. Both modes remain draft data:
 * neither an {@code InternalTransfer} nor a new FinancialTransaction exists
 * until the batch publication transaction succeeds.
 */
public class TransactionBatchInternalTransferCommand implements Serializable {

    @NotNull
    @PositiveOrZero
    private Long expectedCandidateVersion;

    private TransactionCandidateInternalTransferMode mode;

    @Positive
    private Long counterpartTransactionId;

    @Positive
    private Long counterpartAccountId;

    private LocalDate counterpartPostingDate;

    private String counterpartDescription;

    private String counterpartExternalReference;

    private String counterpartNotes;

    public Long getExpectedCandidateVersion() {
        return expectedCandidateVersion;
    }

    public void setExpectedCandidateVersion(Long expectedCandidateVersion) {
        this.expectedCandidateVersion = expectedCandidateVersion;
    }

    public TransactionCandidateInternalTransferMode getMode() {
        return mode;
    }

    public void setMode(TransactionCandidateInternalTransferMode mode) {
        this.mode = mode;
    }

    public Long getCounterpartTransactionId() {
        return counterpartTransactionId;
    }

    public void setCounterpartTransactionId(Long counterpartTransactionId) {
        this.counterpartTransactionId = counterpartTransactionId;
    }

    public Long getCounterpartAccountId() {
        return counterpartAccountId;
    }

    public void setCounterpartAccountId(Long counterpartAccountId) {
        this.counterpartAccountId = counterpartAccountId;
    }

    public LocalDate getCounterpartPostingDate() {
        return counterpartPostingDate;
    }

    public void setCounterpartPostingDate(LocalDate counterpartPostingDate) {
        this.counterpartPostingDate = counterpartPostingDate;
    }

    public String getCounterpartDescription() {
        return counterpartDescription;
    }

    public void setCounterpartDescription(String counterpartDescription) {
        this.counterpartDescription = counterpartDescription;
    }

    public String getCounterpartExternalReference() {
        return counterpartExternalReference;
    }

    public void setCounterpartExternalReference(String counterpartExternalReference) {
        this.counterpartExternalReference = counterpartExternalReference;
    }

    public String getCounterpartNotes() {
        return counterpartNotes;
    }

    public void setCounterpartNotes(String counterpartNotes) {
        this.counterpartNotes = counterpartNotes;
    }
}
