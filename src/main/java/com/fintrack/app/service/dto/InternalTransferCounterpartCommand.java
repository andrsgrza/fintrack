package com.fintrack.app.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDate;

/** Intent to create exactly one opposite financial leg for an existing transaction. */
public class InternalTransferCounterpartCommand implements Serializable {

    @NotNull
    @Positive
    private Long targetAccountId;

    private LocalDate postingDate;

    @NotBlank
    @Size(max = 500)
    private String description;

    @Size(max = 150)
    private String externalReference;

    @Size(max = 1000)
    private String notes;

    @Size(max = 500)
    private String transferNotes;

    @Valid
    private InternalTransferLegSelectionCommand classification = new InternalTransferLegSelectionCommand();

    public Long getTargetAccountId() {
        return targetAccountId;
    }

    public void setTargetAccountId(Long targetAccountId) {
        this.targetAccountId = targetAccountId;
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

    public String getTransferNotes() {
        return transferNotes;
    }

    public void setTransferNotes(String transferNotes) {
        this.transferNotes = transferNotes;
    }

    public InternalTransferLegSelectionCommand getClassification() {
        return classification;
    }

    public void setClassification(InternalTransferLegSelectionCommand classification) {
        this.classification = classification;
    }
}
