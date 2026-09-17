package com.fintrack.app.service.dto;

import java.io.Serializable;

public class PrepareTransactionCandidateRowResultDTO implements Serializable {

    private Long ingestionRecordId;

    private Integer recordIndex;

    private Long candidateId;

    private PrepareTransactionCandidateRowAction action;

    private String reason;

    public Long getIngestionRecordId() {
        return ingestionRecordId;
    }

    public void setIngestionRecordId(Long ingestionRecordId) {
        this.ingestionRecordId = ingestionRecordId;
    }

    public Integer getRecordIndex() {
        return recordIndex;
    }

    public void setRecordIndex(Integer recordIndex) {
        this.recordIndex = recordIndex;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public PrepareTransactionCandidateRowAction getAction() {
        return action;
    }

    public void setAction(PrepareTransactionCandidateRowAction action) {
        this.action = action;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
