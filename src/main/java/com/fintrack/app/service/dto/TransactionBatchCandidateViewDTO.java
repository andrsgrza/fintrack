package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Internal, serialization-safe projection of one batch candidate's current state. */
public class TransactionBatchCandidateViewDTO {

    private Long id;
    private Long version;
    private Long rowPosition;
    private TransactionCandidateSource source;
    private TransactionCandidateStatus status;
    private TransactionCandidateValidationStatus validationStatus;
    private LocalDate transactionDate;
    private LocalDate postingDate;
    private String description;
    /**
     * Temporary immutable CSV baseline. It is exposed only while the candidate belongs to an OPEN batch so a
     * contextual description-normalization rule can be authored without treating an edited effective description as
     * source input. Manual candidates deliberately have no baseline.
     */
    private String sourceDescription;
    private TransactionCandidateDescriptionSource descriptionSource;
    private AppliedRuleAttributionDTO descriptionAppliedByRule;
    private BigDecimal signedAmount;
    private BigDecimal amount;
    private TransactionFlow flow;
    private CurrencyCode currencySnapshot;
    private String externalReference;
    private String notes;
    private Long categoryId;
    private String categoryName;
    private String categoryColor;
    private Boolean categoryActive;
    private TransactionCandidateClassificationSource categorySource;
    private AppliedRuleAttributionDTO categoryAppliedByRule;
    private Boolean categoryManualClear;
    private List<TransactionBatchTagViewDTO> tags = List.of();
    private List<Long> suppressedTagIds = List.of();
    private List<TransactionBatchInputIssueViewDTO> inputIssues = List.of();
    private TransactionBatchInternalTransferViewDTO internalTransfer;

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

    public Long getRowPosition() {
        return rowPosition;
    }

    public void setRowPosition(Long rowPosition) {
        this.rowPosition = rowPosition;
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

    public String getSourceDescription() {
        return sourceDescription;
    }

    public void setSourceDescription(String sourceDescription) {
        this.sourceDescription = sourceDescription;
    }

    public TransactionCandidateDescriptionSource getDescriptionSource() {
        return descriptionSource;
    }

    public void setDescriptionSource(TransactionCandidateDescriptionSource descriptionSource) {
        this.descriptionSource = descriptionSource;
    }

    public AppliedRuleAttributionDTO getDescriptionAppliedByRule() {
        return descriptionAppliedByRule;
    }

    public void setDescriptionAppliedByRule(AppliedRuleAttributionDTO descriptionAppliedByRule) {
        this.descriptionAppliedByRule = descriptionAppliedByRule;
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

    public String getCategoryColor() {
        return categoryColor;
    }

    public void setCategoryColor(String categoryColor) {
        this.categoryColor = categoryColor;
    }

    public Boolean getCategoryActive() {
        return categoryActive;
    }

    public void setCategoryActive(Boolean categoryActive) {
        this.categoryActive = categoryActive;
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

    public Boolean getCategoryManualClear() {
        return categoryManualClear;
    }

    public void setCategoryManualClear(Boolean categoryManualClear) {
        this.categoryManualClear = categoryManualClear;
    }

    public List<TransactionBatchTagViewDTO> getTags() {
        return tags;
    }

    public void setTags(List<TransactionBatchTagViewDTO> tags) {
        this.tags = tags;
    }

    public List<Long> getSuppressedTagIds() {
        return suppressedTagIds;
    }

    public void setSuppressedTagIds(List<Long> suppressedTagIds) {
        this.suppressedTagIds = suppressedTagIds;
    }

    public List<TransactionBatchInputIssueViewDTO> getInputIssues() {
        return inputIssues;
    }

    public void setInputIssues(List<TransactionBatchInputIssueViewDTO> inputIssues) {
        this.inputIssues = inputIssues;
    }

    public TransactionBatchInternalTransferViewDTO getInternalTransfer() {
        return internalTransfer;
    }

    public void setInternalTransfer(TransactionBatchInternalTransferViewDTO internalTransfer) {
        this.internalTransfer = internalTransfer;
    }
}
