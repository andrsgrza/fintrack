package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.fintrack.app.domain.TransactionIngestion} entity.
 */
@Schema(description = "One execution that imports or receives transactions for one account.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionIngestionDTO implements Serializable {

    private Long id;

    @NotNull
    private IngestionType ingestionType;

    @NotNull
    private IngestionStatus status;

    @Size(max = 100)
    private String sourceLabel;

    @NotNull
    private Instant startedAt;

    private Instant completedAt;

    @NotNull
    @Min(value = 0)
    private Integer recordsReceived;

    @NotNull
    @Min(value = 0)
    private Integer recordsCreated;

    @NotNull
    @Min(value = 0)
    private Integer recordsSkipped;

    @NotNull
    @Min(value = 0)
    private Integer recordsRejected;

    @Size(max = 2000)
    private String errorMessage;

    @NotNull
    private Instant createdAt;

    @NotNull
    private FinancialAccountDTO account;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public IngestionType getIngestionType() {
        return ingestionType;
    }

    public void setIngestionType(IngestionType ingestionType) {
        this.ingestionType = ingestionType;
    }

    public IngestionStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionStatus status) {
        this.status = status;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getRecordsReceived() {
        return recordsReceived;
    }

    public void setRecordsReceived(Integer recordsReceived) {
        this.recordsReceived = recordsReceived;
    }

    public Integer getRecordsCreated() {
        return recordsCreated;
    }

    public void setRecordsCreated(Integer recordsCreated) {
        this.recordsCreated = recordsCreated;
    }

    public Integer getRecordsSkipped() {
        return recordsSkipped;
    }

    public void setRecordsSkipped(Integer recordsSkipped) {
        this.recordsSkipped = recordsSkipped;
    }

    public Integer getRecordsRejected() {
        return recordsRejected;
    }

    public void setRecordsRejected(Integer recordsRejected) {
        this.recordsRejected = recordsRejected;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public FinancialAccountDTO getAccount() {
        return account;
    }

    public void setAccount(FinancialAccountDTO account) {
        this.account = account;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionIngestionDTO)) {
            return false;
        }

        TransactionIngestionDTO transactionIngestionDTO = (TransactionIngestionDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, transactionIngestionDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionIngestionDTO{" +
            "id=" + getId() +
            ", ingestionType='" + getIngestionType() + "'" +
            ", status='" + getStatus() + "'" +
            ", sourceLabel='" + getSourceLabel() + "'" +
            ", startedAt='" + getStartedAt() + "'" +
            ", completedAt='" + getCompletedAt() + "'" +
            ", recordsReceived=" + getRecordsReceived() +
            ", recordsCreated=" + getRecordsCreated() +
            ", recordsSkipped=" + getRecordsSkipped() +
            ", recordsRejected=" + getRecordsRejected() +
            ", errorMessage='" + getErrorMessage() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", account=" + getAccount() +
            "}";
    }
}
