package com.fintrack.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/** Temporary manual intent that suppresses a future automatic tag add for one candidate. */
@Entity
@Table(
    name = "transaction_candidate_tag_suppression",
    uniqueConstraints = @UniqueConstraint(name = "ux_candidate_tag_suppression", columnNames = { "transaction_candidate_id", "tag_id" })
)
public class TransactionCandidateTagSuppression implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_candidate_id", nullable = false)
    private TransactionCandidate transactionCandidate;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransactionCandidate getTransactionCandidate() {
        return transactionCandidate;
    }

    public void setTransactionCandidate(TransactionCandidate transactionCandidate) {
        this.transactionCandidate = transactionCandidate;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }
}
