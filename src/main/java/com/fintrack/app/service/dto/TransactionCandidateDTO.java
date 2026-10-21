package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateInternalTransferMode;
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
@Schema(description = "A transaction in progress/review before it becomes final ledger data. Candidates never affect balances.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionCandidateDTO implements Serializable {

    private Long id;

    /** Optimistic token read by the transfer-post command after a draft resume. */
    private Long version;

    @NotNull
    private TransactionCandidateSource source;

    private TransactionCandidateStatus status;

    private TransactionCandidateValidationStatus validationStatus;

    private TransactionCandidateDescriptionReviewStatus descriptionReviewStatus;

    private TransactionCandidateClassificationReviewStatus classificationReviewStatus;

    private TransactionCandidateClassificationSource categorySource;

    /** Read-only, candidate-lifetime rule attribution. */
    private AppliedRuleAttributionDTO categoryAppliedByRule;

    /** Read-only, candidate-lifetime rule attribution. */
    private AppliedRuleAttributionDTO descriptionAppliedByRule;

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

    private Boolean internalTransferEnabled;

    private TransactionCandidateInternalTransferMode internalTransferMode;

    private FinancialAccountDTO internalTransferCounterpartAccount;

    private LocalDate internalTransferCounterpartPostingDate;

    @Size(max = 500)
    private String internalTransferCounterpartDescription;

    @Size(max = 150)
    private String internalTransferCounterpartReference;

    @Size(max = 1000)
    private String internalTransferCounterpartNotes;

    private FinancialTransactionDTO internalTransferCounterpartTransaction;

    @Size(max = 1000)
    private String failureReason;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant cancelledAt;

    private Instant failedAt;

    private UserDTO user;

    private FinancialAccountDTO account;

    private CategoryDTO category;

    private Set<TagDTO> tags = new HashSet<>();

    /**
     * Per-tag source and temporary applied-rule attribution for product draft screens. Kept nullable so input commands can
     * distinguish an omitted read-only field from a client attempt to set it.
     */
    private Set<TransactionCandidateTagSelectionDTO> selectedTags;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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

    public AppliedRuleAttributionDTO getCategoryAppliedByRule() {
        return categoryAppliedByRule;
    }

    public void setCategoryAppliedByRule(AppliedRuleAttributionDTO categoryAppliedByRule) {
        this.categoryAppliedByRule = categoryAppliedByRule;
    }

    public AppliedRuleAttributionDTO getDescriptionAppliedByRule() {
        return descriptionAppliedByRule;
    }

    public void setDescriptionAppliedByRule(AppliedRuleAttributionDTO descriptionAppliedByRule) {
        this.descriptionAppliedByRule = descriptionAppliedByRule;
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

    public Boolean getInternalTransferEnabled() {
        return internalTransferEnabled;
    }

    public void setInternalTransferEnabled(Boolean internalTransferEnabled) {
        this.internalTransferEnabled = internalTransferEnabled;
    }

    public TransactionCandidateInternalTransferMode getInternalTransferMode() {
        return internalTransferMode;
    }

    public void setInternalTransferMode(TransactionCandidateInternalTransferMode internalTransferMode) {
        this.internalTransferMode = internalTransferMode;
    }

    public FinancialAccountDTO getInternalTransferCounterpartAccount() {
        return internalTransferCounterpartAccount;
    }

    public void setInternalTransferCounterpartAccount(FinancialAccountDTO internalTransferCounterpartAccount) {
        this.internalTransferCounterpartAccount = internalTransferCounterpartAccount;
    }

    public LocalDate getInternalTransferCounterpartPostingDate() {
        return internalTransferCounterpartPostingDate;
    }

    public void setInternalTransferCounterpartPostingDate(LocalDate internalTransferCounterpartPostingDate) {
        this.internalTransferCounterpartPostingDate = internalTransferCounterpartPostingDate;
    }

    public String getInternalTransferCounterpartDescription() {
        return internalTransferCounterpartDescription;
    }

    public void setInternalTransferCounterpartDescription(String internalTransferCounterpartDescription) {
        this.internalTransferCounterpartDescription = internalTransferCounterpartDescription;
    }

    public String getInternalTransferCounterpartReference() {
        return internalTransferCounterpartReference;
    }

    public void setInternalTransferCounterpartReference(String internalTransferCounterpartReference) {
        this.internalTransferCounterpartReference = internalTransferCounterpartReference;
    }

    public String getInternalTransferCounterpartNotes() {
        return internalTransferCounterpartNotes;
    }

    public void setInternalTransferCounterpartNotes(String internalTransferCounterpartNotes) {
        this.internalTransferCounterpartNotes = internalTransferCounterpartNotes;
    }

    public FinancialTransactionDTO getInternalTransferCounterpartTransaction() {
        return internalTransferCounterpartTransaction;
    }

    public void setInternalTransferCounterpartTransaction(FinancialTransactionDTO internalTransferCounterpartTransaction) {
        this.internalTransferCounterpartTransaction = internalTransferCounterpartTransaction;
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

    public Set<TagDTO> getTags() {
        return tags;
    }

    public void setTags(Set<TagDTO> tags) {
        this.tags = tags;
    }

    public Set<TransactionCandidateTagSelectionDTO> getSelectedTags() {
        return selectedTags;
    }

    public void setSelectedTags(Set<TransactionCandidateTagSelectionDTO> selectedTags) {
        this.selectedTags = selectedTags;
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
