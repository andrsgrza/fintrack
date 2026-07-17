package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.CategoryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * The primary classification assigned to a transaction.
 * Categories can form a hierarchy.
 */
@Entity
@Table(name = "category")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 1, max = 80)
    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Size(max = 300)
    @Column(name = "description", length = 300)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false)
    private CategoryType categoryType;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    @Column(name = "color")
    private String color;

    @Size(max = 50)
    @Column(name = "icon", length = 50)
    private String icon;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(
        value = { "user", "parentCategory", "financialTransactions", "childCategories", "transactionRules", "subscriptions", "budgets" },
        allowSetters = true
    )
    private Category parentCategory;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "category")
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

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "parentCategory")
    @JsonIgnoreProperties(
        value = { "user", "parentCategory", "financialTransactions", "childCategories", "transactionRules", "subscriptions", "budgets" },
        allowSetters = true
    )
    private Set<Category> childCategories = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "resultingCategory")
    @JsonIgnoreProperties(value = { "user", "resultingCategory", "resultingTags", "conditions" }, allowSetters = true)
    private Set<TransactionRule> transactionRules = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "category")
    @JsonIgnoreProperties(
        value = { "user", "account", "category", "tags", "financialTransactions", "transactionRules" },
        allowSetters = true
    )
    private Set<FinancialSubscription> subscriptions = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "categories")
    @JsonIgnoreProperties(value = { "user", "accounts", "categories", "tags" }, allowSetters = true)
    private Set<Budget> budgets = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Category id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public Category name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public Category description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CategoryType getCategoryType() {
        return this.categoryType;
    }

    public Category categoryType(CategoryType categoryType) {
        this.setCategoryType(categoryType);
        return this;
    }

    public void setCategoryType(CategoryType categoryType) {
        this.categoryType = categoryType;
    }

    public String getColor() {
        return this.color;
    }

    public Category color(String color) {
        this.setColor(color);
        return this;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return this.icon;
    }

    public Category icon(String icon) {
        this.setIcon(icon);
        return this;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Boolean getActive() {
        return this.active;
    }

    public Category active(Boolean active) {
        this.setActive(active);
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public Category createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public Category updatedAt(Instant updatedAt) {
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

    public Category user(User user) {
        this.setUser(user);
        return this;
    }

    public Category getParentCategory() {
        return this.parentCategory;
    }

    public void setParentCategory(Category category) {
        this.parentCategory = category;
    }

    public Category parentCategory(Category category) {
        this.setParentCategory(category);
        return this;
    }

    public Set<FinancialTransaction> getFinancialTransactions() {
        return this.financialTransactions;
    }

    public void setFinancialTransactions(Set<FinancialTransaction> financialTransactions) {
        if (this.financialTransactions != null) {
            this.financialTransactions.forEach(i -> i.setCategory(null));
        }
        if (financialTransactions != null) {
            financialTransactions.forEach(i -> i.setCategory(this));
        }
        this.financialTransactions = financialTransactions;
    }

    public Category financialTransactions(Set<FinancialTransaction> financialTransactions) {
        this.setFinancialTransactions(financialTransactions);
        return this;
    }

    public Category addFinancialTransactions(FinancialTransaction financialTransaction) {
        this.financialTransactions.add(financialTransaction);
        financialTransaction.setCategory(this);
        return this;
    }

    public Category removeFinancialTransactions(FinancialTransaction financialTransaction) {
        this.financialTransactions.remove(financialTransaction);
        financialTransaction.setCategory(null);
        return this;
    }

    public Set<Category> getChildCategories() {
        return this.childCategories;
    }

    public void setChildCategories(Set<Category> categories) {
        if (this.childCategories != null) {
            this.childCategories.forEach(i -> i.setParentCategory(null));
        }
        if (categories != null) {
            categories.forEach(i -> i.setParentCategory(this));
        }
        this.childCategories = categories;
    }

    public Category childCategories(Set<Category> categories) {
        this.setChildCategories(categories);
        return this;
    }

    public Category addChildCategories(Category category) {
        this.childCategories.add(category);
        category.setParentCategory(this);
        return this;
    }

    public Category removeChildCategories(Category category) {
        this.childCategories.remove(category);
        category.setParentCategory(null);
        return this;
    }

    public Set<TransactionRule> getTransactionRules() {
        return this.transactionRules;
    }

    public void setTransactionRules(Set<TransactionRule> transactionRules) {
        if (this.transactionRules != null) {
            this.transactionRules.forEach(i -> i.setResultingCategory(null));
        }
        if (transactionRules != null) {
            transactionRules.forEach(i -> i.setResultingCategory(this));
        }
        this.transactionRules = transactionRules;
    }

    public Category transactionRules(Set<TransactionRule> transactionRules) {
        this.setTransactionRules(transactionRules);
        return this;
    }

    public Category addTransactionRules(TransactionRule transactionRule) {
        this.transactionRules.add(transactionRule);
        transactionRule.setResultingCategory(this);
        return this;
    }

    public Category removeTransactionRules(TransactionRule transactionRule) {
        this.transactionRules.remove(transactionRule);
        transactionRule.setResultingCategory(null);
        return this;
    }

    public Set<FinancialSubscription> getSubscriptions() {
        return this.subscriptions;
    }

    public void setSubscriptions(Set<FinancialSubscription> financialSubscriptions) {
        if (this.subscriptions != null) {
            this.subscriptions.forEach(i -> i.setCategory(null));
        }
        if (financialSubscriptions != null) {
            financialSubscriptions.forEach(i -> i.setCategory(this));
        }
        this.subscriptions = financialSubscriptions;
    }

    public Category subscriptions(Set<FinancialSubscription> financialSubscriptions) {
        this.setSubscriptions(financialSubscriptions);
        return this;
    }

    public Category addSubscriptions(FinancialSubscription financialSubscription) {
        this.subscriptions.add(financialSubscription);
        financialSubscription.setCategory(this);
        return this;
    }

    public Category removeSubscriptions(FinancialSubscription financialSubscription) {
        this.subscriptions.remove(financialSubscription);
        financialSubscription.setCategory(null);
        return this;
    }

    public Set<Budget> getBudgets() {
        return this.budgets;
    }

    public void setBudgets(Set<Budget> budgets) {
        if (this.budgets != null) {
            this.budgets.forEach(i -> i.removeCategories(this));
        }
        if (budgets != null) {
            budgets.forEach(i -> i.addCategories(this));
        }
        this.budgets = budgets;
    }

    public Category budgets(Set<Budget> budgets) {
        this.setBudgets(budgets);
        return this;
    }

    public Category addBudgets(Budget budget) {
        this.budgets.add(budget);
        budget.getCategories().add(this);
        return this;
    }

    public Category removeBudgets(Budget budget) {
        this.budgets.remove(budget);
        budget.getCategories().remove(this);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Category)) {
            return false;
        }
        return getId() != null && getId().equals(((Category) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Category{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", categoryType='" + getCategoryType() + "'" +
            ", color='" + getColor() + "'" +
            ", icon='" + getIcon() + "'" +
            ", active='" + getActive() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            "}";
    }
}
