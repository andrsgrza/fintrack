package com.fintrack.app.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Aggregate-safe structural update. Accounts and flows intentionally are not editable in V1. */
public class InternalTransferUpdateCommand implements Serializable {

    @NotNull
    @PositiveOrZero
    private Long expectedVersion;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal amount;

    @NotNull
    private LocalDate transactionDate;

    private LocalDate sourcePostingDate;
    private LocalDate destinationPostingDate;

    @NotBlank
    @Size(max = 500)
    private String sourceDescription;

    @NotBlank
    @Size(max = 500)
    private String destinationDescription;

    @Size(max = 500)
    private String notes;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LocalDate getSourcePostingDate() {
        return sourcePostingDate;
    }

    public void setSourcePostingDate(LocalDate sourcePostingDate) {
        this.sourcePostingDate = sourcePostingDate;
    }

    public LocalDate getDestinationPostingDate() {
        return destinationPostingDate;
    }

    public void setDestinationPostingDate(LocalDate destinationPostingDate) {
        this.destinationPostingDate = destinationPostingDate;
    }

    public String getSourceDescription() {
        return sourceDescription;
    }

    public void setSourceDescription(String sourceDescription) {
        this.sourceDescription = sourceDescription;
    }

    public String getDestinationDescription() {
        return destinationDescription;
    }

    public void setDestinationDescription(String destinationDescription) {
        this.destinationDescription = destinationDescription;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
