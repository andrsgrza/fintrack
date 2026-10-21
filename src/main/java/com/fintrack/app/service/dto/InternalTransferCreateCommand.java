package com.fintrack.app.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Intent to create both financial legs and their InternalTransfer atomically. */
public class InternalTransferCreateCommand implements Serializable {

    @NotNull
    @Positive
    private Long sourceAccountId;

    @NotNull
    @Positive
    private Long destinationAccountId;

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

    @Size(max = 150)
    private String sourceExternalReference;

    @Size(max = 150)
    private String destinationExternalReference;

    @Size(max = 1000)
    private String sourceNotes;

    @Size(max = 1000)
    private String destinationNotes;

    @Size(max = 500)
    private String transferNotes;

    @Valid
    private InternalTransferLegSelectionCommand sourceClassification = new InternalTransferLegSelectionCommand();

    @Valid
    private InternalTransferLegSelectionCommand destinationClassification = new InternalTransferLegSelectionCommand();

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(Long sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public Long getDestinationAccountId() {
        return destinationAccountId;
    }

    public void setDestinationAccountId(Long destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
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

    public String getSourceExternalReference() {
        return sourceExternalReference;
    }

    public void setSourceExternalReference(String sourceExternalReference) {
        this.sourceExternalReference = sourceExternalReference;
    }

    public String getDestinationExternalReference() {
        return destinationExternalReference;
    }

    public void setDestinationExternalReference(String destinationExternalReference) {
        this.destinationExternalReference = destinationExternalReference;
    }

    public String getSourceNotes() {
        return sourceNotes;
    }

    public void setSourceNotes(String sourceNotes) {
        this.sourceNotes = sourceNotes;
    }

    public String getDestinationNotes() {
        return destinationNotes;
    }

    public void setDestinationNotes(String destinationNotes) {
        this.destinationNotes = destinationNotes;
    }

    public String getTransferNotes() {
        return transferNotes;
    }

    public void setTransferNotes(String transferNotes) {
        this.transferNotes = transferNotes;
    }

    public InternalTransferLegSelectionCommand getSourceClassification() {
        return sourceClassification;
    }

    public void setSourceClassification(InternalTransferLegSelectionCommand sourceClassification) {
        this.sourceClassification = sourceClassification;
    }

    public InternalTransferLegSelectionCommand getDestinationClassification() {
        return destinationClassification;
    }

    public void setDestinationClassification(InternalTransferLegSelectionCommand destinationClassification) {
        this.destinationClassification = destinationClassification;
    }
}
