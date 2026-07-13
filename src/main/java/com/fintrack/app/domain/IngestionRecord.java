package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * One row, object or extracted item processed during an ingestion.
 */
@Entity
@Table(name = "ingestion_record")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class IngestionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Min(value = 0)
    @Column(name = "record_index", nullable = false)
    private Integer recordIndex;

    @Size(max = 150)
    @Column(name = "external_record_id", length = 150)
    private String externalRecordId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IngestionRecordStatus status;

    @Lob
    @Column(name = "raw_data")
    private String rawData;

    @Size(max = 100)
    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Size(max = 1000)
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JsonIgnoreProperties(
        value = {
            "account",
            "category",
            "financialSubscription",
            "transactionIngestion",
            "tags",
            "outgoingInternalTransfer",
            "incomingInternalTransfer",
            "ingestionRecord",
        },
        allowSetters = true
    )
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    private FinancialTransaction financialTransaction;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "accounts", "fileIngestion", "apiIngestion", "financialTransactions", "records" }, allowSetters = true)
    private TransactionIngestion transactionIngestion;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public IngestionRecord id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getRecordIndex() {
        return this.recordIndex;
    }

    public IngestionRecord recordIndex(Integer recordIndex) {
        this.setRecordIndex(recordIndex);
        return this;
    }

    public void setRecordIndex(Integer recordIndex) {
        this.recordIndex = recordIndex;
    }

    public String getExternalRecordId() {
        return this.externalRecordId;
    }

    public IngestionRecord externalRecordId(String externalRecordId) {
        this.setExternalRecordId(externalRecordId);
        return this;
    }

    public void setExternalRecordId(String externalRecordId) {
        this.externalRecordId = externalRecordId;
    }

    public IngestionRecordStatus getStatus() {
        return this.status;
    }

    public IngestionRecord status(IngestionRecordStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(IngestionRecordStatus status) {
        this.status = status;
    }

    public String getRawData() {
        return this.rawData;
    }

    public IngestionRecord rawData(String rawData) {
        this.setRawData(rawData);
        return this;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    public String getErrorCode() {
        return this.errorCode;
    }

    public IngestionRecord errorCode(String errorCode) {
        this.setErrorCode(errorCode);
        return this;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public IngestionRecord errorMessage(String errorMessage) {
        this.setErrorMessage(errorMessage);
        return this;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public IngestionRecord createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public FinancialTransaction getFinancialTransaction() {
        return this.financialTransaction;
    }

    public void setFinancialTransaction(FinancialTransaction financialTransaction) {
        this.financialTransaction = financialTransaction;
    }

    public IngestionRecord financialTransaction(FinancialTransaction financialTransaction) {
        this.setFinancialTransaction(financialTransaction);
        return this;
    }

    public TransactionIngestion getTransactionIngestion() {
        return this.transactionIngestion;
    }

    public void setTransactionIngestion(TransactionIngestion transactionIngestion) {
        this.transactionIngestion = transactionIngestion;
    }

    public IngestionRecord transactionIngestion(TransactionIngestion transactionIngestion) {
        this.setTransactionIngestion(transactionIngestion);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IngestionRecord)) {
            return false;
        }
        return getId() != null && getId().equals(((IngestionRecord) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "IngestionRecord{" +
            "id=" + getId() +
            ", recordIndex=" + getRecordIndex() +
            ", externalRecordId='" + getExternalRecordId() + "'" +
            ", status='" + getStatus() + "'" +
            ", rawDataPresent='" + (getRawData() != null) + "'" +
            ", rawDataLength=" + (getRawData() != null ? getRawData().length() : 0) +
            ", errorCode='" + getErrorCode() + "'" +
            ", errorMessage='" + getErrorMessage() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            "}";
    }
}
