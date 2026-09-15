package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateValidationStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * A transaction in progress/review before it becomes posted ledger data.
 *
 * TransactionCandidate is intentionally separate from FinancialTransaction:
 * candidates never affect balances, dashboards, budgets, or reports.
 */
@Entity
@Table(name = "transaction_candidate")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionCandidate implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private TransactionCandidateSource source;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionCandidateStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private TransactionCandidateValidationStatus validationStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "description_review_status", nullable = false)
    private TransactionCandidateDescriptionReviewStatus descriptionReviewStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "classification_review_status", nullable = false)
    private TransactionCandidateClassificationReviewStatus classificationReviewStatus;

    /** Server-owned origin of the persisted category; null means no category is selected. */
    @Enumerated(EnumType.STRING)
    @Column(name = "category_source")
    private TransactionCandidateClassificationSource categorySource;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "posting_date")
    private LocalDate postingDate;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "signed_amount", precision = 21, scale = 2)
    private BigDecimal signedAmount;

    @Column(name = "amount", precision = 21, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow")
    private TransactionFlow flow;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_snapshot")
    private CurrencyCode currencySnapshot;

    @Size(max = 150)
    @Column(name = "external_reference", length = 150)
    private String externalReference;

    @Size(max = 1000)
    @Column(name = "notes", length = 1000)
    private String notes;

    @Size(max = 1000)
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @ManyToOne(optional = false)
    @NotNull
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(
        value = { "user", "creditAccountDetails", "financialTransactions", "subscriptions", "transactionIngestions", "budgets" },
        allowSetters = true
    )
    private FinancialAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(
        value = { "user", "parentCategory", "financialTransactions", "childCategories", "transactionRules", "subscriptions", "budgets" },
        allowSetters = true
    )
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "account", "fileIngestion", "apiIngestion", "financialTransactions", "records" }, allowSetters = true)
    private TransactionIngestion transactionIngestion;

    @OneToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "financialTransaction", "transactionIngestion" }, allowSetters = true)
    private IngestionRecord ingestionRecord;

    @OneToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(
        value = {
            "account",
            "category",
            "financialSubscription",
            "transactionIngestion",
            "tags",
            "outgoingInternalTransfer",
            "incomingInternalTransfer",
            "ingestionRecord",
        },
        allowSetters = true
    )
    private FinancialTransaction financialTransaction;

    @OneToMany(mappedBy = "transactionCandidate", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<TransactionCandidateTag> tagAssociations = new HashSet<>();

    public Long getId() {
        return this.id;
    }

    public TransactionCandidate id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransactionCandidateSource getSource() {
        return this.source;
    }

    public TransactionCandidate source(TransactionCandidateSource source) {
        this.setSource(source);
        return this;
    }

    public void setSource(TransactionCandidateSource source) {
        this.source = source;
    }

    public TransactionCandidateStatus getStatus() {
        return this.status;
    }

    public TransactionCandidate status(TransactionCandidateStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(TransactionCandidateStatus status) {
        this.status = status;
    }

    public TransactionCandidateValidationStatus getValidationStatus() {
        return this.validationStatus;
    }

    public TransactionCandidate validationStatus(TransactionCandidateValidationStatus validationStatus) {
        this.setValidationStatus(validationStatus);
        return this;
    }

    public void setValidationStatus(TransactionCandidateValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public TransactionCandidateDescriptionReviewStatus getDescriptionReviewStatus() {
        return this.descriptionReviewStatus;
    }

    public TransactionCandidate descriptionReviewStatus(TransactionCandidateDescriptionReviewStatus descriptionReviewStatus) {
        this.setDescriptionReviewStatus(descriptionReviewStatus);
        return this;
    }

    public void setDescriptionReviewStatus(TransactionCandidateDescriptionReviewStatus descriptionReviewStatus) {
        this.descriptionReviewStatus = descriptionReviewStatus;
    }

    public TransactionCandidateClassificationReviewStatus getClassificationReviewStatus() {
        return this.classificationReviewStatus;
    }

    public TransactionCandidate classificationReviewStatus(TransactionCandidateClassificationReviewStatus classificationReviewStatus) {
        this.setClassificationReviewStatus(classificationReviewStatus);
        return this;
    }

    public void setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus classificationReviewStatus) {
        this.classificationReviewStatus = classificationReviewStatus;
    }

    public TransactionCandidateClassificationSource getCategorySource() {
        return categorySource;
    }

    public void setCategorySource(TransactionCandidateClassificationSource categorySource) {
        this.categorySource = categorySource;
    }

    public LocalDate getTransactionDate() {
        return this.transactionDate;
    }

    public TransactionCandidate transactionDate(LocalDate transactionDate) {
        this.setTransactionDate(transactionDate);
        return this;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LocalDate getPostingDate() {
        return this.postingDate;
    }

    public TransactionCandidate postingDate(LocalDate postingDate) {
        this.setPostingDate(postingDate);
        return this;
    }

    public void setPostingDate(LocalDate postingDate) {
        this.postingDate = postingDate;
    }

    public String getDescription() {
        return this.description;
    }

    public TransactionCandidate description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getSignedAmount() {
        return this.signedAmount;
    }

    public TransactionCandidate signedAmount(BigDecimal signedAmount) {
        this.setSignedAmount(signedAmount);
        return this;
    }

    public void setSignedAmount(BigDecimal signedAmount) {
        this.signedAmount = signedAmount;
    }

    public BigDecimal getAmount() {
        return this.amount;
    }

    public TransactionCandidate amount(BigDecimal amount) {
        this.setAmount(amount);
        return this;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionFlow getFlow() {
        return this.flow;
    }

    public TransactionCandidate flow(TransactionFlow flow) {
        this.setFlow(flow);
        return this;
    }

    public void setFlow(TransactionFlow flow) {
        this.flow = flow;
    }

    public CurrencyCode getCurrencySnapshot() {
        return this.currencySnapshot;
    }

    public TransactionCandidate currencySnapshot(CurrencyCode currencySnapshot) {
        this.setCurrencySnapshot(currencySnapshot);
        return this;
    }

    public void setCurrencySnapshot(CurrencyCode currencySnapshot) {
        this.currencySnapshot = currencySnapshot;
    }

    public String getExternalReference() {
        return this.externalReference;
    }

    public TransactionCandidate externalReference(String externalReference) {
        this.setExternalReference(externalReference);
        return this;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getNotes() {
        return this.notes;
    }

    public TransactionCandidate notes(String notes) {
        this.setNotes(notes);
        return this;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getFailureReason() {
        return this.failureReason;
    }

    public TransactionCandidate failureReason(String failureReason) {
        this.setFailureReason(failureReason);
        return this;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public TransactionCandidate createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public TransactionCandidate updatedAt(Instant updatedAt) {
        this.setUpdatedAt(updatedAt);
        return this;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getPostedAt() {
        return this.postedAt;
    }

    public TransactionCandidate postedAt(Instant postedAt) {
        this.setPostedAt(postedAt);
        return this;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public Instant getCancelledAt() {
        return this.cancelledAt;
    }

    public TransactionCandidate cancelledAt(Instant cancelledAt) {
        this.setCancelledAt(cancelledAt);
        return this;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Instant getFailedAt() {
        return this.failedAt;
    }

    public TransactionCandidate failedAt(Instant failedAt) {
        this.setFailedAt(failedAt);
        return this;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public User getUser() {
        return this.user;
    }

    public TransactionCandidate user(User user) {
        this.setUser(user);
        return this;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public FinancialAccount getAccount() {
        return this.account;
    }

    public TransactionCandidate account(FinancialAccount account) {
        this.setAccount(account);
        return this;
    }

    public void setAccount(FinancialAccount account) {
        this.account = account;
    }

    public Category getCategory() {
        return this.category;
    }

    public TransactionCandidate category(Category category) {
        this.setCategory(category);
        return this;
    }

    public void setCategory(Category category) {
        this.category = category;
        if (category == null) {
            this.categorySource = null;
        }
    }

    public TransactionIngestion getTransactionIngestion() {
        return this.transactionIngestion;
    }

    public TransactionCandidate transactionIngestion(TransactionIngestion transactionIngestion) {
        this.setTransactionIngestion(transactionIngestion);
        return this;
    }

    public void setTransactionIngestion(TransactionIngestion transactionIngestion) {
        this.transactionIngestion = transactionIngestion;
    }

    public IngestionRecord getIngestionRecord() {
        return this.ingestionRecord;
    }

    public TransactionCandidate ingestionRecord(IngestionRecord ingestionRecord) {
        this.setIngestionRecord(ingestionRecord);
        return this;
    }

    public void setIngestionRecord(IngestionRecord ingestionRecord) {
        this.ingestionRecord = ingestionRecord;
    }

    public FinancialTransaction getFinancialTransaction() {
        return this.financialTransaction;
    }

    public TransactionCandidate financialTransaction(FinancialTransaction financialTransaction) {
        this.setFinancialTransaction(financialTransaction);
        return this;
    }

    public void setFinancialTransaction(FinancialTransaction financialTransaction) {
        this.financialTransaction = financialTransaction;
    }

    /**
     * Derived compatibility view of the explicit candidate-tag associations.
     *
     * Persist classification provenance through {@link #addTag(Tag, TransactionCandidateClassificationSource)} or
     * {@link #replaceTags(Set, TransactionCandidateClassificationSource)}, never by mutating this returned set.
     */
    public Set<Tag> getTags() {
        Set<Tag> tags = new HashSet<>();
        for (TransactionCandidateTag association : tagAssociations) {
            if (association.getTag() != null) tags.add(association.getTag());
        }
        return tags;
    }

    /**
     * Compatibility setter for explicit user selections. Runtime code that knows the origin must use
     * {@link #replaceTags(Set, TransactionCandidateClassificationSource)} directly.
     */
    public void setTags(Set<Tag> tags) {
        replaceTags(tags, TransactionCandidateClassificationSource.MANUAL);
    }

    public void replaceTags(Set<Tag> tags, TransactionCandidateClassificationSource source) {
        if (source == null && tags != null && !tags.isEmpty()) {
            throw new IllegalArgumentException("Transaction candidate tag source is required");
        }
        Set<Tag> selectedTags = tags == null ? Set.of() : new HashSet<>(tags);
        // Keep retained associations in place. Clearing and recreating them can make Hibernate insert a
        // replacement before it deletes the orphan, violating the candidate/tag unique constraint.
        tagAssociations.removeIf(association -> association.getTag() == null || !selectedTags.contains(association.getTag()));
        selectedTags.forEach(tag -> addTag(tag, source));
    }

    public TransactionCandidate tags(Set<Tag> tags) {
        this.setTags(tags);
        return this;
    }

    public TransactionCandidate addTags(Tag tag) {
        addTag(tag, TransactionCandidateClassificationSource.MANUAL);
        return this;
    }

    public TransactionCandidate removeTags(Tag tag) {
        tagAssociations.removeIf(association -> association.getTag() != null && association.getTag().equals(tag));
        return this;
    }

    public Set<TransactionCandidateTag> getTagAssociations() {
        return tagAssociations;
    }

    public void setTagAssociations(Set<TransactionCandidateTag> tagAssociations) {
        this.tagAssociations.clear();
        if (tagAssociations != null) tagAssociations.forEach(association -> addTag(association.getTag(), association.getSource()));
    }

    public void addTag(Tag tag, TransactionCandidateClassificationSource source) {
        if (tag == null) return;
        tagAssociations
            .stream()
            .filter(association -> tag.equals(association.getTag()))
            .findFirst()
            .ifPresentOrElse(
                association -> association.setSource(source),
                () -> {
                    TransactionCandidateTag association = new TransactionCandidateTag();
                    association.setTransactionCandidate(this);
                    association.setTag(tag);
                    association.setSource(source);
                    tagAssociations.add(association);
                }
            );
    }

    public TransactionCandidateClassificationSource getTagSource(Tag tag) {
        return tagAssociations
            .stream()
            .filter(association -> tag != null && tag.equals(association.getTag()))
            .map(TransactionCandidateTag::getSource)
            .findFirst()
            .orElse(null);
    }

    public boolean hasManualClassification() {
        return (
            categorySource == TransactionCandidateClassificationSource.MANUAL ||
            tagAssociations.stream().anyMatch(association -> association.getSource() == TransactionCandidateClassificationSource.MANUAL)
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionCandidate)) {
            return false;
        }
        return getId() != null && getId().equals(((TransactionCandidate) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
