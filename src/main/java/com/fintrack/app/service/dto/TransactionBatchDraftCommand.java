package com.fintrack.app.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Internal input for adding one incomplete manual row to an owned OPEN batch. */
public class TransactionBatchDraftCommand {

    /** Structural row creation is protected by the last root revision observed by the caller. */
    private Long expectedBatchVersion;
    private LocalDate transactionDate;
    private LocalDate postingDate;
    private String description;
    private BigDecimal signedAmount;
    private String externalReference;
    private String notes;

    public Long getExpectedBatchVersion() {
        return expectedBatchVersion;
    }

    public void setExpectedBatchVersion(Long expectedBatchVersion) {
        this.expectedBatchVersion = expectedBatchVersion;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public void setPostingDate(LocalDate postingDate) {
        this.postingDate = postingDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getSignedAmount() {
        return signedAmount;
    }

    public void setSignedAmount(BigDecimal signedAmount) {
        this.signedAmount = signedAmount;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
