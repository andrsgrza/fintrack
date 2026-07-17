package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A flexible, non-hierarchical classification label.
 */
@Entity
@Table(name = "tag")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Tag implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 1, max = 50)
    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Size(max = 250)
    @Column(name = "description", length = 250)
    private String description;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    @Column(name = "color")
    private String color;

    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(optional = false)
    @NotNull
    private User user;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "tags")
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

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "resultingTags")
    @JsonIgnoreProperties(value = { "user", "resultingCategory", "resultingTags", "conditions" }, allowSetters = true)
    private Set<TransactionRule> transactionRules = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "tags")
    @JsonIgnoreProperties(
        value = { "user", "account", "category", "tags", "financialTransactions", "transactionRules" },
        allowSetters = true
    )
    private Set<FinancialSubscription> subscriptions = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "tags")
    @JsonIgnoreProperties(value = { "user", "accounts", "categories", "tags" }, allowSetters = true)
    private Set<Budget> budgets = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Tag id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public Tag name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public Tag description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return this.color;
    }

    public Tag color(String color) {
        this.setColor(color);
        return this;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Boolean getActive() {
        return this.active;
    }

    public Tag active(Boolean active) {
        this.setActive(active);
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public Tag createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public Tag updatedAt(Instant updatedAt) {
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

    public Tag user(User user) {
        this.setUser(user);
        return this;
    }

    public Set<FinancialTransaction> getFinancialTransactions() {
        return this.financialTransactions;
    }

    public void setFinancialTransactions(Set<FinancialTransaction> financialTransactions) {
        if (this.financialTransactions != null) {
            this.financialTransactions.forEach(i -> i.removeTags(this));
        }
        if (financialTransactions != null) {
            financialTransactions.forEach(i -> i.addTags(this));
        }
        this.financialTransactions = financialTransactions;
    }

    public Tag financialTransactions(Set<FinancialTransaction> financialTransactions) {
        this.setFinancialTransactions(financialTransactions);
        return this;
    }

    public Tag addFinancialTransactions(FinancialTransaction financialTransaction) {
        this.financialTransactions.add(financialTransaction);
        financialTransaction.getTags().add(this);
        return this;
    }

    public Tag removeFinancialTransactions(FinancialTransaction financialTransaction) {
        this.financialTransactions.remove(financialTransaction);
        financialTransaction.getTags().remove(this);
        return this;
    }

    public Set<TransactionRule> getTransactionRules() {
        return this.transactionRules;
    }

    public void setTransactionRules(Set<TransactionRule> transactionRules) {
        if (this.transactionRules != null) {
            this.transactionRules.forEach(i -> i.removeResultingTags(this));
        }
        if (transactionRules != null) {
            transactionRules.forEach(i -> i.addResultingTags(this));
        }
        this.transactionRules = transactionRules;
    }

    public Tag transactionRules(Set<TransactionRule> transactionRules) {
        this.setTransactionRules(transactionRules);
        return this;
    }

    public Tag addTransactionRules(TransactionRule transactionRule) {
        this.transactionRules.add(transactionRule);
        transactionRule.getResultingTags().add(this);
        return this;
    }

    public Tag removeTransactionRules(TransactionRule transactionRule) {
        this.transactionRules.remove(transactionRule);
        transactionRule.getResultingTags().remove(this);
        return this;
    }

    public Set<FinancialSubscription> getSubscriptions() {
        return this.subscriptions;
    }

    public void setSubscriptions(Set<FinancialSubscription> financialSubscriptions) {
        if (this.subscriptions != null) {
            this.subscriptions.forEach(i -> i.removeTags(this));
        }
        if (financialSubscriptions != null) {
            financialSubscriptions.forEach(i -> i.addTags(this));
        }
        this.subscriptions = financialSubscriptions;
    }

    public Tag subscriptions(Set<FinancialSubscription> financialSubscriptions) {
        this.setSubscriptions(financialSubscriptions);
        return this;
    }

    public Tag addSubscriptions(FinancialSubscription financialSubscription) {
        this.subscriptions.add(financialSubscription);
        financialSubscription.getTags().add(this);
        return this;
    }

    public Tag removeSubscriptions(FinancialSubscription financialSubscription) {
        this.subscriptions.remove(financialSubscription);
        financialSubscription.getTags().remove(this);
        return this;
    }

    public Set<Budget> getBudgets() {
        return this.budgets;
    }

    public void setBudgets(Set<Budget> budgets) {
        if (this.budgets != null) {
            this.budgets.forEach(i -> i.removeTags(this));
        }
        if (budgets != null) {
            budgets.forEach(i -> i.addTags(this));
        }
        this.budgets = budgets;
    }

    public Tag budgets(Set<Budget> budgets) {
        this.setBudgets(budgets);
        return this;
    }

    public Tag addBudgets(Budget budget) {
        this.budgets.add(budget);
        budget.getTags().add(this);
        return this;
    }

    public Tag removeBudgets(Budget budget) {
        this.budgets.remove(budget);
        budget.getTags().remove(this);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Tag)) {
            return false;
        }
        return getId() != null && getId().equals(((Tag) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Tag{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", color='" + getColor() + "'" +
            ", active='" + getActive() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            "}";
    }
}
