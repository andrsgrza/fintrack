package com.fintrack.app.service.dto;

import java.util.List;

/** Result of an explicit or configured automatic application. */
public class TransactionBatchRuleApplyResultDTO {

    private Long candidateId;
    private Long candidateVersion;
    private Long batchVersion;
    private boolean changed;
    private boolean descriptionApplied;
    private boolean categoryApplied;
    private List<Long> tagIdsApplied = List.of();

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public Long getCandidateVersion() {
        return candidateVersion;
    }

    public void setCandidateVersion(Long candidateVersion) {
        this.candidateVersion = candidateVersion;
    }

    public Long getBatchVersion() {
        return batchVersion;
    }

    public void setBatchVersion(Long batchVersion) {
        this.batchVersion = batchVersion;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    public boolean isDescriptionApplied() {
        return descriptionApplied;
    }

    public void setDescriptionApplied(boolean descriptionApplied) {
        this.descriptionApplied = descriptionApplied;
    }

    public boolean isCategoryApplied() {
        return categoryApplied;
    }

    public void setCategoryApplied(boolean categoryApplied) {
        this.categoryApplied = categoryApplied;
    }

    public List<Long> getTagIdsApplied() {
        return tagIdsApplied;
    }

    public void setTagIdsApplied(List<Long> tagIdsApplied) {
        this.tagIdsApplied = tagIdsApplied == null ? List.of() : List.copyOf(tagIdsApplied);
    }
}
