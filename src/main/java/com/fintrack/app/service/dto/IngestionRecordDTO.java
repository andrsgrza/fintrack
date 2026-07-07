package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Lob;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.fintrack.app.domain.IngestionRecord} entity.
 */
@Schema(description = "One row, object or extracted item processed during an ingestion.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class IngestionRecordDTO implements Serializable {

    private Long id;

    @NotNull
    @Min(value = 0)
    private Integer recordIndex;

    @Size(max = 150)
    private String externalRecordId;

    @NotNull
    private IngestionRecordStatus status;

    @Lob
    private String rawData;

    @Size(max = 100)
    private String errorCode;

    @Size(max = 1000)
    private String errorMessage;

    @NotNull
    private Instant createdAt;

    private FinancialTransactionDTO financialTransaction;

    @NotNull
    private TransactionIngestionDTO transactionIngestion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getRecordIndex() {
        return recordIndex;
    }

    public void setRecordIndex(Integer recordIndex) {
        this.recordIndex = recordIndex;
    }

    public String getExternalRecordId() {
        return externalRecordId;
    }

    public void setExternalRecordId(String externalRecordId) {
        this.externalRecordId = externalRecordId;
    }

    public IngestionRecordStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionRecordStatus status) {
        this.status = status;
    }

    public String getRawData() {
        return rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
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

    public FinancialTransactionDTO getFinancialTransaction() {
        return financialTransaction;
    }

    public void setFinancialTransaction(FinancialTransactionDTO financialTransaction) {
        this.financialTransaction = financialTransaction;
    }

    public TransactionIngestionDTO getTransactionIngestion() {
        return transactionIngestion;
    }

    public void setTransactionIngestion(TransactionIngestionDTO transactionIngestion) {
        this.transactionIngestion = transactionIngestion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IngestionRecordDTO)) {
            return false;
        }

        IngestionRecordDTO ingestionRecordDTO = (IngestionRecordDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, ingestionRecordDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "IngestionRecordDTO{" +
            "id=" + getId() +
            ", recordIndex=" + getRecordIndex() +
            ", externalRecordId='" + getExternalRecordId() + "'" +
            ", status='" + getStatus() + "'" +
            ", rawData='" + getRawData() + "'" +
            ", errorCode='" + getErrorCode() + "'" +
            ", errorMessage='" + getErrorMessage() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", financialTransaction=" + getFinancialTransaction() +
            ", transactionIngestion=" + getTransactionIngestion() +
            "}";
    }
}
