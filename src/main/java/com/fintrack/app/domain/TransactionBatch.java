package com.fintrack.app.domain;

import com.fintrack.app.domain.enumeration.TransactionBatchStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;

/**
 * A temporary, owned workspace for a set of common transaction candidates.
 *
 * OPEN batches retain account and draft data. PUBLISHED receipt retention is implemented by a later slice; its
 * minimal receipt fields are deliberately present here so it never needs a second history aggregate.
 */
@Entity
@Table(
    name = "transaction_batch",
    indexes = { @Index(name = "ix_transaction_batch_owner_state_updated", columnList = "user_id,status,updated_at") }
)
public class TransactionBatch implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionBatchStatus status;

    /** Next stable row position; positions are never renumbered after a candidate deletion. */
    /** Required for OPEN workspaces; cleared with all workspace-only state when a future publish creates a receipt. */
    @Column(name = "next_row_position")
    private Long nextRowPosition;

    /** OPEN-only setting. The state check requires it for OPEN and removes it from a PUBLISHED receipt. */
    @Column(name = "auto_apply_descriptions")
    private Boolean autoApplyDescriptions;

    @Column(name = "auto_apply_categories")
    private Boolean autoApplyCategories;

    @Column(name = "auto_apply_tags")
    private Boolean autoApplyTags;

    @Column(name = "protect_manual_changes")
    private Boolean protectManualChanges;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** PUBLISHED-only technical receipt values; no draft/final transaction history is stored here. */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "publication_operation_key", length = 100)
    private String publicationOperationKey;

    @Column(name = "publication_request_digest", length = 128)
    private String publicationRequestDigest;

    @Column(name = "published_transaction_count")
    private Integer publishedTransactionCount;

    /**
     * PUBLISHED-only receipt counters introduced after the original batch receipt.
     * The legacy publishedTransactionCount remains the number of candidate rows
     * published so existing receipts retain their original meaning.
     */
    @Column(name = "created_counterpart_count")
    private Integer createdCounterpartCount;

    @Column(name = "created_internal_transfer_count")
    private Integer createdInternalTransferCount;

    @Column(name = "receipt_expires_at")
    private Instant receiptExpiresAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Present while OPEN; publication clears it so an expired receipt cannot retain a live account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private FinancialAccount account;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public TransactionBatchStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionBatchStatus status) {
        this.status = status;
    }

    public Long getNextRowPosition() {
        return nextRowPosition;
    }

    public void setNextRowPosition(Long nextRowPosition) {
        this.nextRowPosition = nextRowPosition;
    }

    public Boolean getAutoApplyDescriptions() {
        return autoApplyDescriptions;
    }

    public void setAutoApplyDescriptions(Boolean autoApplyDescriptions) {
        this.autoApplyDescriptions = autoApplyDescriptions;
    }

    public Boolean getAutoApplyCategories() {
        return autoApplyCategories;
    }

    public void setAutoApplyCategories(Boolean autoApplyCategories) {
        this.autoApplyCategories = autoApplyCategories;
    }

    public Boolean getAutoApplyTags() {
        return autoApplyTags;
    }

    public void setAutoApplyTags(Boolean autoApplyTags) {
        this.autoApplyTags = autoApplyTags;
    }

    public Boolean getProtectManualChanges() {
        return protectManualChanges;
    }

    public void setProtectManualChanges(Boolean protectManualChanges) {
        this.protectManualChanges = protectManualChanges;
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

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getPublicationOperationKey() {
        return publicationOperationKey;
    }

    public void setPublicationOperationKey(String publicationOperationKey) {
        this.publicationOperationKey = publicationOperationKey;
    }

    public String getPublicationRequestDigest() {
        return publicationRequestDigest;
    }

    public void setPublicationRequestDigest(String publicationRequestDigest) {
        this.publicationRequestDigest = publicationRequestDigest;
    }

    public Integer getPublishedTransactionCount() {
        return publishedTransactionCount;
    }

    public void setPublishedTransactionCount(Integer publishedTransactionCount) {
        this.publishedTransactionCount = publishedTransactionCount;
    }

    public Integer getCreatedCounterpartCount() {
        return createdCounterpartCount;
    }

    public void setCreatedCounterpartCount(Integer createdCounterpartCount) {
        this.createdCounterpartCount = createdCounterpartCount;
    }

    public Integer getCreatedInternalTransferCount() {
        return createdInternalTransferCount;
    }

    public void setCreatedInternalTransferCount(Integer createdInternalTransferCount) {
        this.createdInternalTransferCount = createdInternalTransferCount;
    }

    public Instant getReceiptExpiresAt() {
        return receiptExpiresAt;
    }

    public void setReceiptExpiresAt(Instant receiptExpiresAt) {
        this.receiptExpiresAt = receiptExpiresAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public FinancialAccount getAccount() {
        return account;
    }

    public void setAccount(FinancialAccount account) {
        this.account = account;
    }
}
