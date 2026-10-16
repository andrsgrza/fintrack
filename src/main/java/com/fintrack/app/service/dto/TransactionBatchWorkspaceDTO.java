package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.TransactionBatchStatus;
import java.time.Instant;
import java.util.List;

/** Internal batch workspace read model. It intentionally never exposes JPA graphs. */
public class TransactionBatchWorkspaceDTO {

    private Long id;
    private Long version;
    private TransactionBatchStatus status;
    private Long accountId;
    private String accountName;
    private Boolean autoApplyDescriptions;
    private Boolean autoApplyCategories;
    private Boolean autoApplyTags;
    private Boolean protectManualChanges;
    private Instant updatedAt;
    private List<TransactionBatchCandidateViewDTO> candidates = List.of();

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

    public TransactionBatchStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionBatchStatus status) {
        this.status = status;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<TransactionBatchCandidateViewDTO> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<TransactionBatchCandidateViewDTO> candidates) {
        this.candidates = candidates;
    }
}
