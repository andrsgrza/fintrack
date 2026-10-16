package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.TransactionBatchStatus;
import java.time.Instant;

/** Bounded result of an internal batch publication; it deliberately contains no financial transaction IDs. */
public class TransactionBatchPublicationReceiptDTO {

    private Long batchId;
    private TransactionBatchStatus status;
    private String operationKey;
    private Instant publishedAt;
    private Integer publishedTransactionCount;
    private Instant receiptExpiresAt;
    private boolean replayed;

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public TransactionBatchStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionBatchStatus status) {
        this.status = status;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public void setOperationKey(String operationKey) {
        this.operationKey = operationKey;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Integer getPublishedTransactionCount() {
        return publishedTransactionCount;
    }

    public void setPublishedTransactionCount(Integer publishedTransactionCount) {
        this.publishedTransactionCount = publishedTransactionCount;
    }

    public Instant getReceiptExpiresAt() {
        return receiptExpiresAt;
    }

    public void setReceiptExpiresAt(Instant receiptExpiresAt) {
        this.receiptExpiresAt = receiptExpiresAt;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public void setReplayed(boolean replayed) {
        this.replayed = replayed;
    }
}
