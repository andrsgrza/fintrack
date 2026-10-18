package com.fintrack.app.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.fintrack.app.domain.InternalTransfer} entity.
 */
@Schema(
    description = "Links the OUT and IN transactions that represent a movement between two\naccounts owned by the same user and using the same currency."
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class InternalTransferDTO implements Serializable {

    private Long id;

    private Long version;

    private String notes;

    private Instant createdAt;

    private Instant updatedAt;

    private FinancialTransactionDTO outgoingTransaction;

    private FinancialTransactionDTO incomingTransaction;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public FinancialTransactionDTO getOutgoingTransaction() {
        return outgoingTransaction;
    }

    public void setOutgoingTransaction(FinancialTransactionDTO outgoingTransaction) {
        this.outgoingTransaction = outgoingTransaction;
    }

    public FinancialTransactionDTO getIncomingTransaction() {
        return incomingTransaction;
    }

    public void setIncomingTransaction(FinancialTransactionDTO incomingTransaction) {
        this.incomingTransaction = incomingTransaction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InternalTransferDTO)) {
            return false;
        }

        InternalTransferDTO internalTransferDTO = (InternalTransferDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, internalTransferDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "InternalTransferDTO{" +
            "id=" + getId() +
            ", version=" + getVersion() +
            ", notes='" + getNotes() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", outgoingTransaction=" + getOutgoingTransaction() +
            ", incomingTransaction=" + getIncomingTransaction() +
            "}";
    }
}
