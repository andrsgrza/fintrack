package com.fintrack.app.domain;

import com.fintrack.app.domain.enumeration.TransactionCandidateInputIssueField;
import com.fintrack.app.domain.enumeration.TransactionCandidateInputIssueReason;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/** Minimal repair evidence for one currently unrepresentable candidate input. */
@Entity
@Table(
    name = "transaction_candidate_input_issue",
    uniqueConstraints = @UniqueConstraint(name = "ux_candidate_input_issue_field", columnNames = { "transaction_candidate_id", "field" })
)
public class TransactionCandidateInputIssue implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false)
    private TransactionCandidateInputIssueField field;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false)
    private TransactionCandidateInputIssueReason reasonCode;

    @NotNull
    @Size(max = 100)
    @Column(name = "submitted_value", length = 100, nullable = false)
    private String submittedValue;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_candidate_id", nullable = false)
    private TransactionCandidate transactionCandidate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public TransactionCandidate getTransactionCandidate() {
        return transactionCandidate;
    }

    public void setTransactionCandidate(TransactionCandidate transactionCandidate) {
        this.transactionCandidate = transactionCandidate;
    }
}
