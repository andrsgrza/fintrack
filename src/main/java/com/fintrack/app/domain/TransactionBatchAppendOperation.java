package com.fintrack.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;

/** A bounded idempotency receipt for a future CSV append; it never retains file content or row payload. */
@Entity
@Table(
    name = "transaction_batch_append_operation",
    uniqueConstraints = @UniqueConstraint(name = "ux_batch_append_operation_key", columnNames = { "transaction_batch_id", "operation_key" })
)
public class TransactionBatchAppendOperation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @Size(max = 100)
    @Column(name = "operation_key", length = 100, nullable = false)
    private String operationKey;

    @NotNull
    @Size(max = 128)
    @Column(name = "request_digest", length = 128, nullable = false)
    private String requestDigest;

    @NotNull
    @Column(name = "accepted_candidate_count", nullable = false)
    private Integer acceptedCandidateCount;

    @Size(max = 255)
    @Column(name = "display_filename", length = 255)
    private String displayFilename;

    @NotNull
    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_batch_id", nullable = false)
    private TransactionBatch transactionBatch;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public void setOperationKey(String operationKey) {
        this.operationKey = operationKey;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String requestDigest) {
        this.requestDigest = requestDigest;
    }

    public Integer getAcceptedCandidateCount() {
        return acceptedCandidateCount;
    }

    public void setAcceptedCandidateCount(Integer acceptedCandidateCount) {
        this.acceptedCandidateCount = acceptedCandidateCount;
    }

    public String getDisplayFilename() {
        return displayFilename;
    }

    public void setDisplayFilename(String displayFilename) {
        this.displayFilename = displayFilename;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public TransactionBatch getTransactionBatch() {
        return transactionBatch;
    }

    public void setTransactionBatch(TransactionBatch transactionBatch) {
        this.transactionBatch = transactionBatch;
    }
}
