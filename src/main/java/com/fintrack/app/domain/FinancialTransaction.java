package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * A monetary movement in an account.
 *
 * The amount is always positive. The flow determines whether money enters
 * or leaves the account. There is no transaction-level currency field.
 */
@Entity
@Table(name = "financial_transaction")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class FinancialTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "posting_date")
    private LocalDate postingDate;

    @NotNull
    @Size(min = 1, max = 500)
    @Column(name = "description", length = 500, nullable = false)
    private String description;

    @NotNull
    @DecimalMin(value = "0")
    @Column(name = "amount", precision = 21, scale = 2, nullable = false)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "flow", nullable = false)
    private TransactionFlow flow;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false)
    private TransactionOrigin origin;

    @Size(max = 150)
    @Column(name = "external_reference", length = 150)
    private String externalReference;

    @Size(max = 1000)
    @Column(name = "notes", length = 1000)
    private String notes;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(
        value = {
            "user", "creditAccountDetails", "financialTransactions", "subscriptions", "budgets", "transactionIngestions", "apiAccessTokens",
        },
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
    @JsonIgnoreProperties(
        value = { "user", "account", "category", "tags", "financialTransactions", "transactionRules" },
        allowSetters = true
    )
    private FinancialSubscription financialSubscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "accounts", "fileIngestion", "apiIngestion", "financialTransactions", "records" }, allowSetters = true)
    private TransactionIngestion transactionIngestion;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rel_financial_transaction__tags",
        joinColumns = @JoinColumn(name = "financial_transaction_id"),
        inverseJoinColumns = @JoinColumn(name = "tags_id")
    )
    @JsonIgnoreProperties(value = { "user", "financialTransactions", "transactionRules", "subscriptions", "budgets" }, allowSetters = true)
    private Set<Tag> tags = new HashSet<>();

    @JsonIgnoreProperties(value = { "outgoingTransaction", "incomingTransaction" }, allowSetters = true)
    @OneToOne(fetch = FetchType.LAZY, mappedBy = "outgoingTransaction")
    private InternalTransfer outgoingInternalTransfer;

    @JsonIgnoreProperties(value = { "outgoingTransaction", "incomingTransaction" }, allowSetters = true)
    @OneToOne(fetch = FetchType.LAZY, mappedBy = "incomingTransaction")
    private InternalTransfer incomingInternalTransfer;

    @JsonIgnoreProperties(value = { "financialTransaction", "transactionIngestion" }, allowSetters = true)
    @OneToOne(fetch = FetchType.LAZY, mappedBy = "financialTransaction")
    private IngestionRecord ingestionRecord;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public FinancialTransaction id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getTransactionDate() {
        return this.transactionDate;
    }

    public FinancialTransaction transactionDate(LocalDate transactionDate) {
        this.setTransactionDate(transactionDate);
        return this;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LocalDate getPostingDate() {
        return this.postingDate;
    }

    public FinancialTransaction postingDate(LocalDate postingDate) {
        this.setPostingDate(postingDate);
        return this;
    }

    public void setPostingDate(LocalDate postingDate) {
        this.postingDate = postingDate;
    }

    public String getDescription() {
        return this.description;
    }

    public FinancialTransaction description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return this.amount;
    }

    public FinancialTransaction amount(BigDecimal amount) {
        this.setAmount(amount);
        return this;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionFlow getFlow() {
        return this.flow;
    }

    public FinancialTransaction flow(TransactionFlow flow) {
        this.setFlow(flow);
        return this;
    }

    public void setFlow(TransactionFlow flow) {
        this.flow = flow;
    }

    public TransactionOrigin getOrigin() {
        return this.origin;
    }

    public FinancialTransaction origin(TransactionOrigin origin) {
        this.setOrigin(origin);
        return this;
    }

    public void setOrigin(TransactionOrigin origin) {
        this.origin = origin;
    }

    public String getExternalReference() {
        return this.externalReference;
    }

    public FinancialTransaction externalReference(String externalReference) {
        this.setExternalReference(externalReference);
        return this;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getNotes() {
        return this.notes;
    }

    public FinancialTransaction notes(String notes) {
        this.setNotes(notes);
        return this;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public FinancialTransaction createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public FinancialTransaction updatedAt(Instant updatedAt) {
        this.setUpdatedAt(updatedAt);
        return this;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public FinancialAccount getAccount() {
        return this.account;
    }

    public void setAccount(FinancialAccount financialAccount) {
        this.account = financialAccount;
    }

    public FinancialTransaction account(FinancialAccount financialAccount) {
        this.setAccount(financialAccount);
        return this;
    }

    public Category getCategory() {
        return this.category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public FinancialTransaction category(Category category) {
        this.setCategory(category);
        return this;
    }

    public FinancialSubscription getFinancialSubscription() {
        return this.financialSubscription;
    }

    public void setFinancialSubscription(FinancialSubscription financialSubscription) {
        this.financialSubscription = financialSubscription;
    }

    public FinancialTransaction financialSubscription(FinancialSubscription financialSubscription) {
        this.setFinancialSubscription(financialSubscription);
        return this;
    }

    public TransactionIngestion getTransactionIngestion() {
        return this.transactionIngestion;
    }

    public void setTransactionIngestion(TransactionIngestion transactionIngestion) {
        this.transactionIngestion = transactionIngestion;
    }

    public FinancialTransaction transactionIngestion(TransactionIngestion transactionIngestion) {
        this.setTransactionIngestion(transactionIngestion);
        return this;
    }

    public Set<Tag> getTags() {
        return this.tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public FinancialTransaction tags(Set<Tag> tags) {
        this.setTags(tags);
        return this;
    }

    public FinancialTransaction addTags(Tag tag) {
        this.tags.add(tag);
        return this;
    }

    public FinancialTransaction removeTags(Tag tag) {
        this.tags.remove(tag);
        return this;
    }

    public InternalTransfer getOutgoingInternalTransfer() {
        return this.outgoingInternalTransfer;
    }

    public void setOutgoingInternalTransfer(InternalTransfer internalTransfer) {
        if (this.outgoingInternalTransfer != null) {
            this.outgoingInternalTransfer.setOutgoingTransaction(null);
        }
        if (internalTransfer != null) {
            internalTransfer.setOutgoingTransaction(this);
        }
        this.outgoingInternalTransfer = internalTransfer;
    }

    public FinancialTransaction outgoingInternalTransfer(InternalTransfer internalTransfer) {
        this.setOutgoingInternalTransfer(internalTransfer);
        return this;
    }

    public InternalTransfer getIncomingInternalTransfer() {
        return this.incomingInternalTransfer;
    }

    public void setIncomingInternalTransfer(InternalTransfer internalTransfer) {
        if (this.incomingInternalTransfer != null) {
            this.incomingInternalTransfer.setIncomingTransaction(null);
        }
        if (internalTransfer != null) {
            internalTransfer.setIncomingTransaction(this);
        }
        this.incomingInternalTransfer = internalTransfer;
    }

    public FinancialTransaction incomingInternalTransfer(InternalTransfer internalTransfer) {
        this.setIncomingInternalTransfer(internalTransfer);
        return this;
    }

    public IngestionRecord getIngestionRecord() {
        return this.ingestionRecord;
    }

    public void setIngestionRecord(IngestionRecord ingestionRecord) {
        if (this.ingestionRecord != null) {
            this.ingestionRecord.setFinancialTransaction(null);
        }
        if (ingestionRecord != null) {
            ingestionRecord.setFinancialTransaction(this);
        }
        this.ingestionRecord = ingestionRecord;
    }

    public FinancialTransaction ingestionRecord(IngestionRecord ingestionRecord) {
        this.setIngestionRecord(ingestionRecord);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FinancialTransaction)) {
            return false;
        }
        return getId() != null && getId().equals(((FinancialTransaction) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "FinancialTransaction{" +
            "id=" + getId() +
            ", transactionDate='" + getTransactionDate() + "'" +
            ", postingDate='" + getPostingDate() + "'" +
            ", description='" + getDescription() + "'" +
            ", amount=" + getAmount() +
            ", flow='" + getFlow() + "'" +
            ", origin='" + getOrigin() + "'" +
            ", externalReference='" + getExternalReference() + "'" +
            ", notes='" + getNotes() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            "}";
    }
}
