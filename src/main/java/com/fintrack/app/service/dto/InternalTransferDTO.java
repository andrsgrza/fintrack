package com.fintrack.app.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
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

    @Size(max = 500)
    private String notes;

    @NotNull
    private Instant createdAt;

    @NotNull
    private FinancialTransactionDTO outgoingTransaction;

    @NotNull
    private FinancialTransactionDTO incomingTransaction;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
            ", notes='" + getNotes() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", outgoingTransaction=" + getOutgoingTransaction() +
            ", incomingTransaction=" + getIncomingTransaction() +
            "}";
    }
}
