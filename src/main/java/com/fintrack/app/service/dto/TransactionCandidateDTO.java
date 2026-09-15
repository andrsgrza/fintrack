package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateValidationStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A DTO for the {@link com.fintrack.app.domain.TransactionCandidate} entity.
 */
@Schema(description = "A transaction in progress/review before it becomes posted ledger data. Candidates never affect balances.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionCandidateDTO implements Serializable {

    private Long id;

    @NotNull
    private TransactionCandidateSource source;

    private TransactionCandidateStatus status;

    private TransactionCandidateValidationStatus validationStatus;

    private TransactionCandidateDescriptionReviewStatus descriptionReviewStatus;

    private TransactionCandidateClassificationReviewStatus classificationReviewStatus;

    private TransactionCandidateClassificationSource categorySource;

    private LocalDate transactionDate;

    private LocalDate postingDate;

    @Size(max = 500)
    private String description;

    private BigDecimal signedAmount;

    private BigDecimal amount;

    private TransactionFlow flow;

    private CurrencyCode currencySnapshot;

    @Size(max = 150)
    private String externalReference;

    @Size(max = 1000)
    private String notes;

    @Size(max = 1000)
    private String failureReason;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant postedAt;

    private Instant cancelledAt;

    private Instant failedAt;

    private UserDTO user;

    private FinancialAccountDTO account;

    private CategoryDTO category;

    private TransactionIngestionDTO transactionIngestion;

    private IngestionRecordDTO ingestionRecord;

    private FinancialTransactionDTO financialTransaction;

    private Set<TagDTO> tags = new HashSet<>();

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

    public TransactionCandidateDescriptionReviewStatus getDescriptionReviewStatus() {
        return descriptionReviewStatus;
    }

    public void setDescriptionReviewStatus(TransactionCandidateDescriptionReviewStatus descriptionReviewStatus) {
        this.descriptionReviewStatus = descriptionReviewStatus;
    }

    public TransactionCandidateClassificationReviewStatus getClassificationReviewStatus() {
        return classificationReviewStatus;
    }

    public void setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus classificationReviewStatus) {
        this.classificationReviewStatus = classificationReviewStatus;
    }

    public TransactionCandidateClassificationSource getCategorySource() {
        return categorySource;
    }

    public void setCategorySource(TransactionCandidateClassificationSource categorySource) {
        this.categorySource = categorySource;
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

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
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

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public UserDTO getUser() {
        return user;
    }

    public void setUser(UserDTO user) {
        this.user = user;
    }

    public FinancialAccountDTO getAccount() {
        return account;
    }

    public void setAccount(FinancialAccountDTO account) {
        this.account = account;
    }

    public CategoryDTO getCategory() {
        return category;
    }

    public void setCategory(CategoryDTO category) {
        this.category = category;
    }

    public TransactionIngestionDTO getTransactionIngestion() {
        return transactionIngestion;
    }

    public void setTransactionIngestion(TransactionIngestionDTO transactionIngestion) {
        this.transactionIngestion = transactionIngestion;
    }

    public IngestionRecordDTO getIngestionRecord() {
        return ingestionRecord;
    }

    public void setIngestionRecord(IngestionRecordDTO ingestionRecord) {
        this.ingestionRecord = ingestionRecord;
    }

    public FinancialTransactionDTO getFinancialTransaction() {
        return financialTransaction;
    }

    public void setFinancialTransaction(FinancialTransactionDTO financialTransaction) {
        this.financialTransaction = financialTransaction;
    }

    public Set<TagDTO> getTags() {
        return tags;
    }

    public void setTags(Set<TagDTO> tags) {
        this.tags = tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionCandidateDTO)) {
            return false;
        }

        TransactionCandidateDTO transactionCandidateDTO = (TransactionCandidateDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, transactionCandidateDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }
}
