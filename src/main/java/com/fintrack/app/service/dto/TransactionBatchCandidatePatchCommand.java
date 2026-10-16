package com.fintrack.app.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Internal partial patch. A nullable value is omitted; a member of clearFields is an explicit user clear.
 */
public class TransactionBatchCandidatePatchCommand {

    private Long expectedCandidateVersion;
    private LocalDate transactionDate;
    private LocalDate postingDate;
    private String description;
    private BigDecimal signedAmount;
    private String externalReference;
    private String notes;
    /** These flags distinguish an omitted classification from an explicit selection or clear in one atomic edit. */
    private Boolean categoryChanged;
    private Long categoryId;
    private Boolean tagsChanged;
    private Set<Long> tagIds = Set.of();
    private Set<TransactionBatchCandidatePatchField> clearFields = EnumSet.noneOf(TransactionBatchCandidatePatchField.class);

    public Long getExpectedCandidateVersion() {
        return expectedCandidateVersion;
    }

    public void setExpectedCandidateVersion(Long expectedCandidateVersion) {
        this.expectedCandidateVersion = expectedCandidateVersion;
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

    public Boolean getCategoryChanged() {
        return categoryChanged;
    }

    public void setCategoryChanged(Boolean categoryChanged) {
        this.categoryChanged = categoryChanged;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Boolean getTagsChanged() {
        return tagsChanged;
    }

    public void setTagsChanged(Boolean tagsChanged) {
        this.tagsChanged = tagsChanged;
    }

    public Set<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(Set<Long> tagIds) {
        this.tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
    }

    public Set<TransactionBatchCandidatePatchField> getClearFields() {
        return clearFields;
    }

    public void setClearFields(Set<TransactionBatchCandidatePatchField> clearFields) {
        this.clearFields = clearFields == null || clearFields.isEmpty()
            ? EnumSet.noneOf(TransactionBatchCandidatePatchField.class)
            : EnumSet.copyOf(clearFields);
    }
}
