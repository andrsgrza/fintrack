package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * One execution that imports or receives one or many transactions.
 */
@Entity
@Table(name = "transaction_ingestion")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionIngestion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_type", nullable = false)
    private IngestionType ingestionType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IngestionStatus status;

    @Size(max = 100)
    @Column(name = "source_label", length = 100)
    private String sourceLabel;

    @NotNull
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @NotNull
    @Min(value = 0)
    @Column(name = "records_received", nullable = false)
    private Integer recordsReceived;

    @NotNull
    @Min(value = 0)
    @Column(name = "records_created", nullable = false)
    private Integer recordsCreated;

    @NotNull
    @Min(value = 0)
    @Column(name = "records_skipped", nullable = false)
    private Integer recordsSkipped;

    @NotNull
    @Min(value = 0)
    @Column(name = "records_rejected", nullable = false)
    private Integer recordsRejected;

    @Size(max = 2000)
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @NotNull
    @JoinTable(
        name = "rel_transaction_ingestion__accounts",
        joinColumns = @JoinColumn(name = "transaction_ingestion_id"),
        inverseJoinColumns = @JoinColumn(name = "accounts_id")
    )
    @JsonIgnoreProperties(
        value = {
            "user", "creditAccountDetails", "financialTransactions", "subscriptions", "budgets", "transactionIngestions", "apiAccessTokens",
        },
        allowSetters = true
    )
    private Set<FinancialAccount> accounts = new HashSet<>();

    @JsonIgnoreProperties(value = { "transactionIngestion" }, allowSetters = true)
    @OneToOne(fetch = FetchType.LAZY, mappedBy = "transactionIngestion")
    private FileIngestion fileIngestion;

    @JsonIgnoreProperties(value = { "transactionIngestion", "apiAccessToken" }, allowSetters = true)
    @OneToOne(fetch = FetchType.LAZY, mappedBy = "transactionIngestion")
    private ApiIngestion apiIngestion;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "transactionIngestion")
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
    private Set<FinancialTransaction> financialTransactions = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "transactionIngestion")
    @JsonIgnoreProperties(value = { "financialTransaction", "transactionIngestion" }, allowSetters = true)
    private Set<IngestionRecord> records = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public TransactionIngestion id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public IngestionType getIngestionType() {
        return this.ingestionType;
    }

    public TransactionIngestion ingestionType(IngestionType ingestionType) {
        this.setIngestionType(ingestionType);
        return this;
    }

    public void setIngestionType(IngestionType ingestionType) {
        this.ingestionType = ingestionType;
    }

    public IngestionStatus getStatus() {
        return this.status;
    }

    public TransactionIngestion status(IngestionStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(IngestionStatus status) {
        this.status = status;
    }

    public String getSourceLabel() {
        return this.sourceLabel;
    }

    public TransactionIngestion sourceLabel(String sourceLabel) {
        this.setSourceLabel(sourceLabel);
        return this;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public Instant getStartedAt() {
        return this.startedAt;
    }

    public TransactionIngestion startedAt(Instant startedAt) {
        this.setStartedAt(startedAt);
        return this;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return this.completedAt;
    }

    public TransactionIngestion completedAt(Instant completedAt) {
        this.setCompletedAt(completedAt);
        return this;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getRecordsReceived() {
        return this.recordsReceived;
    }

    public TransactionIngestion recordsReceived(Integer recordsReceived) {
        this.setRecordsReceived(recordsReceived);
        return this;
    }

    public void setRecordsReceived(Integer recordsReceived) {
        this.recordsReceived = recordsReceived;
    }

    public Integer getRecordsCreated() {
        return this.recordsCreated;
    }

    public TransactionIngestion recordsCreated(Integer recordsCreated) {
        this.setRecordsCreated(recordsCreated);
        return this;
    }

    public void setRecordsCreated(Integer recordsCreated) {
        this.recordsCreated = recordsCreated;
    }

    public Integer getRecordsSkipped() {
        return this.recordsSkipped;
    }

    public TransactionIngestion recordsSkipped(Integer recordsSkipped) {
        this.setRecordsSkipped(recordsSkipped);
        return this;
    }

    public void setRecordsSkipped(Integer recordsSkipped) {
        this.recordsSkipped = recordsSkipped;
    }

    public Integer getRecordsRejected() {
        return this.recordsRejected;
    }

    public TransactionIngestion recordsRejected(Integer recordsRejected) {
        this.setRecordsRejected(recordsRejected);
        return this;
    }

    public void setRecordsRejected(Integer recordsRejected) {
        this.recordsRejected = recordsRejected;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public TransactionIngestion errorMessage(String errorMessage) {
        this.setErrorMessage(errorMessage);
        return this;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public TransactionIngestion createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Set<FinancialAccount> getAccounts() {
        return this.accounts;
    }

    public void setAccounts(Set<FinancialAccount> financialAccounts) {
        this.accounts = financialAccounts;
    }

    public TransactionIngestion accounts(Set<FinancialAccount> financialAccounts) {
        this.setAccounts(financialAccounts);
        return this;
    }

    public TransactionIngestion addAccounts(FinancialAccount financialAccount) {
        this.accounts.add(financialAccount);
        return this;
    }

    public TransactionIngestion removeAccounts(FinancialAccount financialAccount) {
        this.accounts.remove(financialAccount);
        return this;
    }

    public FileIngestion getFileIngestion() {
        return this.fileIngestion;
    }

    public void setFileIngestion(FileIngestion fileIngestion) {
        if (this.fileIngestion != null) {
            this.fileIngestion.setTransactionIngestion(null);
        }
        if (fileIngestion != null) {
            fileIngestion.setTransactionIngestion(this);
        }
        this.fileIngestion = fileIngestion;
    }

    public TransactionIngestion fileIngestion(FileIngestion fileIngestion) {
        this.setFileIngestion(fileIngestion);
        return this;
    }

    public ApiIngestion getApiIngestion() {
        return this.apiIngestion;
    }

    public void setApiIngestion(ApiIngestion apiIngestion) {
        if (this.apiIngestion != null) {
            this.apiIngestion.setTransactionIngestion(null);
        }
        if (apiIngestion != null) {
            apiIngestion.setTransactionIngestion(this);
        }
        this.apiIngestion = apiIngestion;
    }

    public TransactionIngestion apiIngestion(ApiIngestion apiIngestion) {
        this.setApiIngestion(apiIngestion);
        return this;
    }

    public Set<FinancialTransaction> getFinancialTransactions() {
        return this.financialTransactions;
    }

    public void setFinancialTransactions(Set<FinancialTransaction> financialTransactions) {
        if (this.financialTransactions != null) {
            this.financialTransactions.forEach(i -> i.setTransactionIngestion(null));
        }
        if (financialTransactions != null) {
            financialTransactions.forEach(i -> i.setTransactionIngestion(this));
        }
        this.financialTransactions = financialTransactions;
    }

    public TransactionIngestion financialTransactions(Set<FinancialTransaction> financialTransactions) {
        this.setFinancialTransactions(financialTransactions);
        return this;
    }

    public TransactionIngestion addFinancialTransactions(FinancialTransaction financialTransaction) {
        this.financialTransactions.add(financialTransaction);
        financialTransaction.setTransactionIngestion(this);
        return this;
    }

    public TransactionIngestion removeFinancialTransactions(FinancialTransaction financialTransaction) {
        this.financialTransactions.remove(financialTransaction);
        financialTransaction.setTransactionIngestion(null);
        return this;
    }

    public Set<IngestionRecord> getRecords() {
        return this.records;
    }

    public void setRecords(Set<IngestionRecord> ingestionRecords) {
        if (this.records != null) {
            this.records.forEach(i -> i.setTransactionIngestion(null));
        }
        if (ingestionRecords != null) {
            ingestionRecords.forEach(i -> i.setTransactionIngestion(this));
        }
        this.records = ingestionRecords;
    }

    public TransactionIngestion records(Set<IngestionRecord> ingestionRecords) {
        this.setRecords(ingestionRecords);
        return this;
    }

    public TransactionIngestion addRecords(IngestionRecord ingestionRecord) {
        this.records.add(ingestionRecord);
        ingestionRecord.setTransactionIngestion(this);
        return this;
    }

    public TransactionIngestion removeRecords(IngestionRecord ingestionRecord) {
        this.records.remove(ingestionRecord);
        ingestionRecord.setTransactionIngestion(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionIngestion)) {
            return false;
        }
        return getId() != null && getId().equals(((TransactionIngestion) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionIngestion{" +
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
            "}";
    }
}
