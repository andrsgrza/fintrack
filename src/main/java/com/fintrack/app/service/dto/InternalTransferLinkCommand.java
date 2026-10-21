package com.fintrack.app.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/** Intent to link two already-posted financial transactions without mutating either leg. */
public class InternalTransferLinkCommand implements Serializable {

    @NotNull
    @Positive
    private Long outgoingTransactionId;

    @NotNull
    @Positive
    private Long incomingTransactionId;

    @Size(max = 500)
    private String notes;

    public Long getOutgoingTransactionId() {
        return outgoingTransactionId;
    }

    public void setOutgoingTransactionId(Long outgoingTransactionId) {
        this.outgoingTransactionId = outgoingTransactionId;
    }

    public Long getIncomingTransactionId() {
        return incomingTransactionId;
    }

    public void setIncomingTransactionId(Long incomingTransactionId) {
        this.incomingTransactionId = incomingTransactionId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
