package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateValidationStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TransactionCandidateWorkflowSummaryDTO implements Serializable {

    private Long id;

    private TransactionCandidateSource source;

    private TransactionCandidateStatus status;

    private TransactionCandidateValidationStatus validationStatus;

    private TransactionCandidateClassificationReviewStatus classificationReviewStatus;

    private TransactionCandidateDescriptionReviewStatus descriptionReviewStatus;

    private LocalDate transactionDate;

    private LocalDate postingDate;

    private String description;

    private BigDecimal signedAmount;

    private BigDecimal amount;

    private TransactionFlow flow;

    private CurrencyCode currencySnapshot;

    private String externalReference;

    private String notes;

    private Long accountId;

    private String accountName;

    private Long categoryId;

    private String categoryName;

    private TransactionCandidateClassificationSource categorySource;

    private List<Long> tagIds = new ArrayList<>();

    private List<String> tagNames = new ArrayList<>();

    private List<TransactionCandidateTagSelectionDTO> selectedTags = new ArrayList<>();

    private Long financialTransactionId;

    private Instant createdAt;

    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransactionCandidateSource getSource() {
        return source;
    }

    public void setSource(TransactionCandidateSource source) {
        this.source = source;
    }

    public TransactionCandidateStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionCandidateStatus status) {
        this.status = status;
    }

    public TransactionCandidateValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(TransactionCandidateValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public TransactionCandidateClassificationReviewStatus getClassificationReviewStatus() {
        return classificationReviewStatus;
    }

    public void setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus classificationReviewStatus) {
        this.classificationReviewStatus = classificationReviewStatus;
    }

    public TransactionCandidateDescriptionReviewStatus getDescriptionReviewStatus() {
        return descriptionReviewStatus;
    }

    public void setDescriptionReviewStatus(TransactionCandidateDescriptionReviewStatus descriptionReviewStatus) {
        this.descriptionReviewStatus = descriptionReviewStatus;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public void setPostingDate(LocalDate postingDate) {
        this.postingDate = postingDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getSignedAmount() {
        return signedAmount;
    }

    public void setSignedAmount(BigDecimal signedAmount) {
        this.signedAmount = signedAmount;
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

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public TransactionCandidateClassificationSource getCategorySource() {
        return categorySource;
    }

    public void setCategorySource(TransactionCandidateClassificationSource categorySource) {
        this.categorySource = categorySource;
    }

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
    }

    public List<String> getTagNames() {
        return tagNames;
    }

    public void setTagNames(List<String> tagNames) {
        this.tagNames = tagNames;
    }

    public List<TransactionCandidateTagSelectionDTO> getSelectedTags() {
        return selectedTags;
    }

    public void setSelectedTags(List<TransactionCandidateTagSelectionDTO> selectedTags) {
        this.selectedTags = selectedTags;
    }

    public Long getFinancialTransactionId() {
        return financialTransactionId;
    }

    public void setFinancialTransactionId(Long financialTransactionId) {
        this.financialTransactionId = financialTransactionId;
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
}
