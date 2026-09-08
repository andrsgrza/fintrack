package com.fintrack.app.service.dto;

import java.io.Serializable;

public class FileImportCandidateClassificationResponseDTO implements Serializable {

    private Long transactionIngestionId;

    private TransactionCandidateWorkflowSummaryDTO candidate;

    public Long getTransactionIngestionId() {
        return transactionIngestionId;
    }

    public void setTransactionIngestionId(Long transactionIngestionId) {
        this.transactionIngestionId = transactionIngestionId;
    }

    public TransactionCandidateWorkflowSummaryDTO getCandidate() {
        return candidate;
    }

    public void setCandidate(TransactionCandidateWorkflowSummaryDTO candidate) {
        this.candidate = candidate;
    }
}
