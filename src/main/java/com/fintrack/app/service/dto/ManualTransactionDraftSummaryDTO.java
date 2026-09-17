package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lightweight product-facing summary for recoverable manual transaction drafts.
 */
public class ManualTransactionDraftSummaryDTO implements Serializable {

    private Long id;

    private TransactionCandidateStatus status;

    private TransactionCandidateClassificationReviewStatus classificationReviewStatus;

    private Long accountId;

    private String accountName;

    private LocalDate transactionDate;

    private String description;

    private BigDecimal amount;

    private TransactionFlow flow;

    private CurrencyCode currencySnapshot;

    private Instant createdAt;

    private Instant updatedAt;

    private String categoryName;

    private List<String> tagNames = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransactionCandidateStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionCandidateStatus status) {
        this.status = status;
    }

    public TransactionCandidateClassificationReviewStatus getClassificationReviewStatus() {
        return classificationReviewStatus;
    }

    public void setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus classificationReviewStatus) {
        this.classificationReviewStatus = classificationReviewStatus;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionFlow getFlow() {
        return flow;
    }

    public void setFlow(TransactionFlow flow) {
        this.flow = flow;
    }

    public CurrencyCode getCurrencySnapshot() {
        return currencySnapshot;
    }

    public void setCurrencySnapshot(CurrencyCode currencySnapshot) {
        this.currencySnapshot = currencySnapshot;
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

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public List<String> getTagNames() {
        return tagNames;
    }

    public void setTagNames(List<String> tagNames) {
        this.tagNames = Objects.requireNonNullElseGet(tagNames, ArrayList::new);
    }
}
