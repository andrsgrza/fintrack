package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.RecurrenceUnit;
import com.fintrack.app.domain.enumeration.SubscriptionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * A recurring expected charge. It does not generate transactions.
 */
@Entity
@Table(name = "financial_subscription")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class FinancialSubscription implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 1, max = 100)
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status;

    @DecimalMin(value = "0")
    @Column(name = "expected_amount", precision = 21, scale = 2)
    private BigDecimal expectedAmount;

    @DecimalMin(value = "0")
    @DecimalMax(value = "100")
    @Column(name = "amount_tolerance_percentage", precision = 21, scale = 2)
    private BigDecimal amountTolerancePercentage;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private CurrencyCode currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_unit", nullable = false)
    private RecurrenceUnit recurrenceUnit;

    @NotNull
    @Min(value = 1)
    @Column(name = "interval_count", nullable = false)
    private Integer intervalCount;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "next_expected_date")
    private LocalDate nextExpectedDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @NotNull
    @Column(name = "automatic_payment", nullable = false)
    private Boolean automaticPayment;

    @Size(max = 500)
    @Column(name = "notes", length = 500)
    private String notes;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(optional = false)
    @NotNull
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(
        value = { "user", "creditAccountDetails", "financialTransactions", "subscriptions", "budgets", "transactionIngestions" },
        allowSetters = true
    )
    private FinancialAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(
        value = { "user", "parentCategory", "financialTransactions", "childCategories", "transactionRules", "subscriptions", "budgets" },
        allowSetters = true
    )
    private Category category;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rel_financial_subscription__tags",
        joinColumns = @JoinColumn(name = "financial_subscription_id"),
        inverseJoinColumns = @JoinColumn(name = "tags_id")
    )
    @JsonIgnoreProperties(value = { "user", "financialTransactions", "transactionRules", "subscriptions", "budgets" }, allowSetters = true)
    private Set<Tag> tags = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "financialSubscription")
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
    private Set<FinancialTransaction> financialTransactions = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "resultingFinancialSubscription")
    @JsonIgnoreProperties(
        value = { "user", "resultingCategory", "resultingFinancialSubscription", "resultingTags", "conditions" },
        allowSetters = true
    )
    private Set<TransactionRule> transactionRules = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public FinancialSubscription id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public FinancialSubscription name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public FinancialSubscription description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SubscriptionStatus getStatus() {
        return this.status;
    }

    public FinancialSubscription status(SubscriptionStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public BigDecimal getExpectedAmount() {
        return this.expectedAmount;
    }

    public FinancialSubscription expectedAmount(BigDecimal expectedAmount) {
        this.setExpectedAmount(expectedAmount);
        return this;
    }

    public void setExpectedAmount(BigDecimal expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public BigDecimal getAmountTolerancePercentage() {
        return this.amountTolerancePercentage;
    }

    public FinancialSubscription amountTolerancePercentage(BigDecimal amountTolerancePercentage) {
        this.setAmountTolerancePercentage(amountTolerancePercentage);
        return this;
    }

    public void setAmountTolerancePercentage(BigDecimal amountTolerancePercentage) {
        this.amountTolerancePercentage = amountTolerancePercentage;
    }

    public CurrencyCode getCurrency() {
        return this.currency;
    }

    public FinancialSubscription currency(CurrencyCode currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(CurrencyCode currency) {
        this.currency = currency;
    }

    public RecurrenceUnit getRecurrenceUnit() {
        return this.recurrenceUnit;
    }

    public FinancialSubscription recurrenceUnit(RecurrenceUnit recurrenceUnit) {
        this.setRecurrenceUnit(recurrenceUnit);
        return this;
    }

    public void setRecurrenceUnit(RecurrenceUnit recurrenceUnit) {
        this.recurrenceUnit = recurrenceUnit;
    }

    public Integer getIntervalCount() {
        return this.intervalCount;
    }

    public FinancialSubscription intervalCount(Integer intervalCount) {
        this.setIntervalCount(intervalCount);
        return this;
    }

    public void setIntervalCount(Integer intervalCount) {
        this.intervalCount = intervalCount;
    }

    public LocalDate getStartDate() {
        return this.startDate;
    }

    public FinancialSubscription startDate(LocalDate startDate) {
        this.setStartDate(startDate);
        return this;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getNextExpectedDate() {
        return this.nextExpectedDate;
    }

    public FinancialSubscription nextExpectedDate(LocalDate nextExpectedDate) {
        this.setNextExpectedDate(nextExpectedDate);
        return this;
    }

    public void setNextExpectedDate(LocalDate nextExpectedDate) {
        this.nextExpectedDate = nextExpectedDate;
    }

    public LocalDate getEndDate() {
        return this.endDate;
    }

    public FinancialSubscription endDate(LocalDate endDate) {
        this.setEndDate(endDate);
        return this;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Boolean getAutomaticPayment() {
        return this.automaticPayment;
    }

    public FinancialSubscription automaticPayment(Boolean automaticPayment) {
        this.setAutomaticPayment(automaticPayment);
        return this;
    }

    public void setAutomaticPayment(Boolean automaticPayment) {
        this.automaticPayment = automaticPayment;
    }

    public String getNotes() {
        return this.notes;
    }

    public FinancialSubscription notes(String notes) {
        this.setNotes(notes);
        return this;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public FinancialSubscription createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public FinancialSubscription updatedAt(Instant updatedAt) {
        this.setUpdatedAt(updatedAt);
        return this;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public FinancialSubscription user(User user) {
        this.setUser(user);
        return this;
    }

    public FinancialAccount getAccount() {
        return this.account;
    }

    public void setAccount(FinancialAccount financialAccount) {
        this.account = financialAccount;
    }

    public FinancialSubscription account(FinancialAccount financialAccount) {
        this.setAccount(financialAccount);
        return this;
    }

    public Category getCategory() {
        return this.category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public FinancialSubscription category(Category category) {
        this.setCategory(category);
        return this;
    }

    public Set<Tag> getTags() {
        return this.tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public FinancialSubscription tags(Set<Tag> tags) {
        this.setTags(tags);
        return this;
    }

    public FinancialSubscription addTags(Tag tag) {
        this.tags.add(tag);
        return this;
    }

    public FinancialSubscription removeTags(Tag tag) {
        this.tags.remove(tag);
        return this;
    }

    public Set<FinancialTransaction> getFinancialTransactions() {
        return this.financialTransactions;
    }

    public void setFinancialTransactions(Set<FinancialTransaction> financialTransactions) {
        if (this.financialTransactions != null) {
            this.financialTransactions.forEach(i -> i.setFinancialSubscription(null));
        }
        if (financialTransactions != null) {
            financialTransactions.forEach(i -> i.setFinancialSubscription(this));
        }
        this.financialTransactions = financialTransactions;
    }

    public FinancialSubscription financialTransactions(Set<FinancialTransaction> financialTransactions) {
        this.setFinancialTransactions(financialTransactions);
        return this;
    }

    public FinancialSubscription addFinancialTransactions(FinancialTransaction financialTransaction) {
        this.financialTransactions.add(financialTransaction);
        financialTransaction.setFinancialSubscription(this);
        return this;
    }

    public FinancialSubscription removeFinancialTransactions(FinancialTransaction financialTransaction) {
        this.financialTransactions.remove(financialTransaction);
        financialTransaction.setFinancialSubscription(null);
        return this;
    }

    public Set<TransactionRule> getTransactionRules() {
        return this.transactionRules;
    }

    public void setTransactionRules(Set<TransactionRule> transactionRules) {
        if (this.transactionRules != null) {
            this.transactionRules.forEach(i -> i.setResultingFinancialSubscription(null));
        }
        if (transactionRules != null) {
            transactionRules.forEach(i -> i.setResultingFinancialSubscription(this));
        }
        this.transactionRules = transactionRules;
    }

    public FinancialSubscription transactionRules(Set<TransactionRule> transactionRules) {
        this.setTransactionRules(transactionRules);
        return this;
    }

    public FinancialSubscription addTransactionRules(TransactionRule transactionRule) {
        this.transactionRules.add(transactionRule);
        transactionRule.setResultingFinancialSubscription(this);
        return this;
    }

    public FinancialSubscription removeTransactionRules(TransactionRule transactionRule) {
        this.transactionRules.remove(transactionRule);
        transactionRule.setResultingFinancialSubscription(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FinancialSubscription)) {
            return false;
        }
        return getId() != null && getId().equals(((FinancialSubscription) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "FinancialSubscription{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", status='" + getStatus() + "'" +
            ", expectedAmount=" + getExpectedAmount() +
            ", amountTolerancePercentage=" + getAmountTolerancePercentage() +
            ", currency='" + getCurrency() + "'" +
            ", recurrenceUnit='" + getRecurrenceUnit() + "'" +
            ", intervalCount=" + getIntervalCount() +
            ", startDate='" + getStartDate() + "'" +
            ", nextExpectedDate='" + getNextExpectedDate() + "'" +
            ", endDate='" + getEndDate() + "'" +
            ", automaticPayment='" + getAutomaticPayment() + "'" +
            ", notes='" + getNotes() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            "}";
    }
}
