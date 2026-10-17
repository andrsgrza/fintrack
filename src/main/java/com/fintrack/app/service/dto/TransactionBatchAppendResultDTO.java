package com.fintrack.app.service.dto;

import java.time.Instant;
import java.util.List;

/** Receipt-safe outcome of one CSV append. It contains no file bytes, raw row, or parser payload. */
public class TransactionBatchAppendResultDTO {

    private Long batchId;
    private Long batchVersion;
    private String operationKey;
    private Integer acceptedCandidateCount;
    private Instant acceptedAt;
    private boolean replayed;
    private String evaluationStatus;
    private List<Long> appendedCandidateIds = List.of();

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

    public String getOperationKey() {
        return operationKey;
    }

    public void setOperationKey(String operationKey) {
        this.operationKey = operationKey;
    }

    public Integer getAcceptedCandidateCount() {
        return acceptedCandidateCount;
    }

    public void setAcceptedCandidateCount(Integer acceptedCandidateCount) {
        this.acceptedCandidateCount = acceptedCandidateCount;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public void setReplayed(boolean replayed) {
        this.replayed = replayed;
    }

    public String getEvaluationStatus() {
        return evaluationStatus;
    }

    public void setEvaluationStatus(String evaluationStatus) {
        this.evaluationStatus = evaluationStatus;
    }

    public List<Long> getAppendedCandidateIds() {
        return appendedCandidateIds;
    }

    public void setAppendedCandidateIds(List<Long> appendedCandidateIds) {
        this.appendedCandidateIds = appendedCandidateIds == null ? List.of() : List.copyOf(appendedCandidateIds);
    }
}
