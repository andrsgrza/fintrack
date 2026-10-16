package com.fintrack.app.service.dto;

import jakarta.validation.constraints.NotNull;

/** Candidate-scoped category mutation; categoryId is deliberately nullable for an explicit clear. */
public class TransactionBatchCategoryCommand {

    @NotNull
    private Long expectedCandidateVersion;

    private Long categoryId;

    public Long getExpectedCandidateVersion() {
        return expectedCandidateVersion;
    }

    public void setExpectedCandidateVersion(Long expectedCandidateVersion) {
        this.expectedCandidateVersion = expectedCandidateVersion;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
