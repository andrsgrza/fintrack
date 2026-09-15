package com.fintrack.app.domain;

import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationSource;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/** A candidate-tag association together with the origin of that selected tag. */
@Entity
@Table(
    name = "rel_transaction_candidate__tags",
    uniqueConstraints = @UniqueConstraint(name = "ux_transaction_candidate_tag", columnNames = { "transaction_candidate_id", "tags_id" })
)
public class TransactionCandidateTag implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private TransactionCandidateClassificationSource source;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_candidate_id", nullable = false)
    private TransactionCandidate transactionCandidate;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tags_id", nullable = false)
    private Tag tag;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransactionCandidateClassificationSource getSource() {
        return source;
    }

    public void setSource(TransactionCandidateClassificationSource source) {
        this.source = source;
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
