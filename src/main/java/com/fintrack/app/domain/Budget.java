package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.BudgetPeriod;
import com.fintrack.app.domain.enumeration.BudgetStatus;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TagMatchMode;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * A spending limit for a recurring or custom period.
 *
 * Empty account/category/tag relations have domain-specific meanings:
 * - no accounts: all active accounts of the user in the budget currency
 * - no categories: any category
 * - no tags: no tag filtering
 */
@Entity
@Table(name = "budget")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Budget implements Serializable {

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

    @NotNull
    @DecimalMin(value = "0")
    @Column(name = "amount", precision = 21, scale = 2, nullable = false)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private CurrencyCode currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false)
    private BudgetPeriod period;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BudgetStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tag_match_mode", nullable = false)
    private TagMatchMode tagMatchMode;

    @DecimalMin(value = "0")
    @DecimalMax(value = "100")
    @Column(name = "warning_percentage", precision = 21, scale = 2)
    private BigDecimal warningPercentage;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(optional = false)
    @NotNull
    private User user;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rel_budget__accounts",
        joinColumns = @JoinColumn(name = "budget_id"),
        inverseJoinColumns = @JoinColumn(name = "accounts_id")
    )
    @JsonIgnoreProperties(
        value = {
            "user", "creditAccountDetails", "financialTransactions", "subscriptions", "budgets", "transactionIngestions", "apiAccessTokens",
        },
        allowSetters = true
    )
    private Set<FinancialAccount> accounts = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rel_budget__categories",
        joinColumns = @JoinColumn(name = "budget_id"),
        inverseJoinColumns = @JoinColumn(name = "categories_id")
    )
    @JsonIgnoreProperties(
        value = { "user", "parentCategory", "financialTransactions", "childCategories", "transactionRules", "subscriptions", "budgets" },
        allowSetters = true
    )
    private Set<Category> categories = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "rel_budget__tags", joinColumns = @JoinColumn(name = "budget_id"), inverseJoinColumns = @JoinColumn(name = "tags_id"))
    @JsonIgnoreProperties(value = { "user", "financialTransactions", "transactionRules", "subscriptions", "budgets" }, allowSetters = true)
    private Set<Tag> tags = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Budget id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public Budget name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAmount() {
        return this.amount;
    }

    public Budget amount(BigDecimal amount) {
        this.setAmount(amount);
        return this;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public CurrencyCode getCurrency() {
        return this.currency;
    }

    public Budget currency(CurrencyCode currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(CurrencyCode currency) {
        this.currency = currency;
    }

    public BudgetPeriod getPeriod() {
        return this.period;
    }

    public Budget period(BudgetPeriod period) {
        this.setPeriod(period);
        return this;
    }

    public void setPeriod(BudgetPeriod period) {
        this.period = period;
    }

    public LocalDate getStartDate() {
        return this.startDate;
    }

    public Budget startDate(LocalDate startDate) {
        this.setStartDate(startDate);
        return this;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return this.endDate;
    }

    public Budget endDate(LocalDate endDate) {
        this.setEndDate(endDate);
        return this;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BudgetStatus getStatus() {
        return this.status;
    }

    public Budget status(BudgetStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(BudgetStatus status) {
        this.status = status;
    }

    public TagMatchMode getTagMatchMode() {
        return this.tagMatchMode;
    }

    public Budget tagMatchMode(TagMatchMode tagMatchMode) {
        this.setTagMatchMode(tagMatchMode);
        return this;
    }

    public void setTagMatchMode(TagMatchMode tagMatchMode) {
        this.tagMatchMode = tagMatchMode;
    }

    public BigDecimal getWarningPercentage() {
        return this.warningPercentage;
    }

    public Budget warningPercentage(BigDecimal warningPercentage) {
        this.setWarningPercentage(warningPercentage);
        return this;
    }

    public void setWarningPercentage(BigDecimal warningPercentage) {
        this.warningPercentage = warningPercentage;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public Budget createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public Budget updatedAt(Instant updatedAt) {
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

    public Budget user(User user) {
        this.setUser(user);
        return this;
    }

    public Set<FinancialAccount> getAccounts() {
        return this.accounts;
    }

    public void setAccounts(Set<FinancialAccount> financialAccounts) {
        this.accounts = financialAccounts;
    }

    public Budget accounts(Set<FinancialAccount> financialAccounts) {
        this.setAccounts(financialAccounts);
        return this;
    }

    public Budget addAccounts(FinancialAccount financialAccount) {
        this.accounts.add(financialAccount);
        return this;
    }

    public Budget removeAccounts(FinancialAccount financialAccount) {
        this.accounts.remove(financialAccount);
        return this;
    }

    public Set<Category> getCategories() {
        return this.categories;
    }

    public void setCategories(Set<Category> categories) {
        this.categories = categories;
    }

    public Budget categories(Set<Category> categories) {
        this.setCategories(categories);
        return this;
    }

    public Budget addCategories(Category category) {
        this.categories.add(category);
        return this;
    }

    public Budget removeCategories(Category category) {
        this.categories.remove(category);
        return this;
    }

    public Set<Tag> getTags() {
        return this.tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public Budget tags(Set<Tag> tags) {
        this.setTags(tags);
        return this;
    }

    public Budget addTags(Tag tag) {
        this.tags.add(tag);
        return this;
    }

    public Budget removeTags(Tag tag) {
        this.tags.remove(tag);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Budget)) {
            return false;
        }
        return getId() != null && getId().equals(((Budget) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Budget{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", amount=" + getAmount() +
            ", currency='" + getCurrency() + "'" +
            ", period='" + getPeriod() + "'" +
            ", startDate='" + getStartDate() + "'" +
            ", endDate='" + getEndDate() + "'" +
            ", status='" + getStatus() + "'" +
            ", tagMatchMode='" + getTagMatchMode() + "'" +
            ", warningPercentage=" + getWarningPercentage() +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            "}";
    }
}
