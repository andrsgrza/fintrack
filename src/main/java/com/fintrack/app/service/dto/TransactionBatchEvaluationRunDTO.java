package com.fintrack.app.service.dto;

import java.util.List;

/** Outcome of an explicit reevaluation orchestration; previews remain available even where no row changed. */
public class TransactionBatchEvaluationRunDTO {

    private Long batchId;
    private Long batchVersion;
    private List<TransactionBatchRulePreviewDTO> previews = List.of();
    private List<TransactionBatchRuleApplyResultDTO> applications = List.of();

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getBatchVersion() {
        return batchVersion;
    }

    public void setBatchVersion(Long batchVersion) {
        this.batchVersion = batchVersion;
    }

    public List<TransactionBatchRulePreviewDTO> getPreviews() {
        return previews;
    }

    public void setPreviews(List<TransactionBatchRulePreviewDTO> previews) {
        this.previews = previews == null ? List.of() : List.copyOf(previews);
    }

    public List<TransactionBatchRuleApplyResultDTO> getApplications() {
        return applications;
    }

    public void setApplications(List<TransactionBatchRuleApplyResultDTO> applications) {
        this.applications = applications == null ? List.of() : List.copyOf(applications);
    }
}
