package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.TransactionCandidateInputIssueField;
import com.fintrack.app.domain.enumeration.TransactionCandidateInputIssueReason;

/** Serialization-safe internal issue projection containing only bounded repair evidence. */
public class TransactionBatchInputIssueViewDTO {

    private TransactionCandidateInputIssueField field;
    private TransactionCandidateInputIssueReason reasonCode;
    private String submittedValue;

    public TransactionCandidateInputIssueField getField() {
        return field;
    }

    public void setField(TransactionCandidateInputIssueField field) {
        this.field = field;
    }

    public TransactionCandidateInputIssueReason getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(TransactionCandidateInputIssueReason reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getSubmittedValue() {
        return submittedValue;
    }

    public void setSubmittedValue(String submittedValue) {
        this.submittedValue = submittedValue;
    }
}
