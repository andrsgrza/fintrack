package com.fintrack.app.service.dto;

/** Result of a row command with authoritative candidate and workspace versions. */
public class TransactionBatchRowMutationDTO {

    private TransactionBatchCandidateViewDTO candidate;
    private Long batchVersion;

    public TransactionBatchCandidateViewDTO getCandidate() {
        return candidate;
    }

    public void setCandidate(TransactionBatchCandidateViewDTO candidate) {
        this.candidate = candidate;
    }

    public Long getBatchVersion() {
        return batchVersion;
    }

    public void setBatchVersion(Long batchVersion) {
        this.batchVersion = batchVersion;
    }
}
