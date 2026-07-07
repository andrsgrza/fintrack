package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * A financial account owned by a user.
 *
 * The current balance is calculated and is not persisted.
 * Every transaction amount is expressed in this account's currency.
 */
@Entity
@Table(name = "financial_account")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class FinancialAccount implements Serializable {

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

    @Size(max = 100)
    @Column(name = "institution_name", length = 100)
    private String institutionName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private CurrencyCode currency;

    @NotNull
    @Column(name = "initial_balance", precision = 21, scale = 2, nullable = false)
    private BigDecimal initialBalance;

    @NotNull
    @Column(name = "initial_balance_date", nullable = false)
    private LocalDate initialBalanceDate;

    @Pattern(regexp = "^[0-9]{4}$")
    @Column(name = "last_four_digits")
    private String lastFourDigits;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

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

    @JsonIgnoreProperties(value = { "account" }, allowSetters = true)
    @OneToOne(fetch = FetchType.LAZY, mappedBy = "account")
    private CreditAccountDetails creditAccountDetails;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "account")
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

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "account")
    @JsonIgnoreProperties(
        value = { "user", "account", "category", "tags", "financialTransactions", "transactionRules" },
        allowSetters = true
    )
    private Set<FinancialSubscription> subscriptions = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "accounts")
    @JsonIgnoreProperties(value = { "user", "accounts", "categories", "tags" }, allowSetters = true)
    private Set<Budget> budgets = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "accounts")
    @JsonIgnoreProperties(value = { "accounts", "fileIngestion", "apiIngestion", "financialTransactions", "records" }, allowSetters = true)
    private Set<TransactionIngestion> transactionIngestions = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public FinancialAccount id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public FinancialAccount name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstitutionName() {
        return this.institutionName;
    }

    public FinancialAccount institutionName(String institutionName) {
        this.setInstitutionName(institutionName);
        return this;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public AccountType getAccountType() {
        return this.accountType;
    }

    public FinancialAccount accountType(AccountType accountType) {
        this.setAccountType(accountType);
        return this;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public CurrencyCode getCurrency() {
        return this.currency;
    }

    public FinancialAccount currency(CurrencyCode currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(CurrencyCode currency) {
        this.currency = currency;
    }

    public BigDecimal getInitialBalance() {
        return this.initialBalance;
    }

    public FinancialAccount initialBalance(BigDecimal initialBalance) {
        this.setInitialBalance(initialBalance);
        return this;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }

    public LocalDate getInitialBalanceDate() {
        return this.initialBalanceDate;
    }

    public FinancialAccount initialBalanceDate(LocalDate initialBalanceDate) {
        this.setInitialBalanceDate(initialBalanceDate);
        return this;
    }

    public void setInitialBalanceDate(LocalDate initialBalanceDate) {
        this.initialBalanceDate = initialBalanceDate;
    }

    public String getLastFourDigits() {
        return this.lastFourDigits;
    }

    public FinancialAccount lastFourDigits(String lastFourDigits) {
        this.setLastFourDigits(lastFourDigits);
        return this;
    }

    public void setLastFourDigits(String lastFourDigits) {
        this.lastFourDigits = lastFourDigits;
    }

    public String getDescription() {
        return this.description;
    }

    public FinancialAccount description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return this.color;
    }

    public FinancialAccount color(String color) {
        this.setColor(color);
        return this;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return this.icon;
    }

    public FinancialAccount icon(String icon) {
        this.setIcon(icon);
        return this;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Boolean getActive() {
        return this.active;
    }

    public FinancialAccount active(Boolean active) {
        this.setActive(active);
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public FinancialAccount createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public FinancialAccount updatedAt(Instant updatedAt) {
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

    public FinancialAccount user(User user) {
        this.setUser(user);
        return this;
    }

    public CreditAccountDetails getCreditAccountDetails() {
        return this.creditAccountDetails;
    }

    public void setCreditAccountDetails(CreditAccountDetails creditAccountDetails) {
        if (this.creditAccountDetails != null) {
            this.creditAccountDetails.setAccount(null);
        }
        if (creditAccountDetails != null) {
            creditAccountDetails.setAccount(this);
        }
        this.creditAccountDetails = creditAccountDetails;
    }

    public FinancialAccount creditAccountDetails(CreditAccountDetails creditAccountDetails) {
        this.setCreditAccountDetails(creditAccountDetails);
        return this;
    }

    public Set<FinancialTransaction> getFinancialTransactions() {
        return this.financialTransactions;
    }

    public void setFinancialTransactions(Set<FinancialTransaction> financialTransactions) {
        if (this.financialTransactions != null) {
            this.financialTransactions.forEach(i -> i.setAccount(null));
        }
        if (financialTransactions != null) {
            financialTransactions.forEach(i -> i.setAccount(this));
        }
        this.financialTransactions = financialTransactions;
    }

    public FinancialAccount financialTransactions(Set<FinancialTransaction> financialTransactions) {
        this.setFinancialTransactions(financialTransactions);
        return this;
    }

    public FinancialAccount addFinancialTransactions(FinancialTransaction financialTransaction) {
        this.financialTransactions.add(financialTransaction);
        financialTransaction.setAccount(this);
        return this;
    }

    public FinancialAccount removeFinancialTransactions(FinancialTransaction financialTransaction) {
        this.financialTransactions.remove(financialTransaction);
        financialTransaction.setAccount(null);
        return this;
    }

    public Set<FinancialSubscription> getSubscriptions() {
        return this.subscriptions;
    }

    public void setSubscriptions(Set<FinancialSubscription> financialSubscriptions) {
        if (this.subscriptions != null) {
            this.subscriptions.forEach(i -> i.setAccount(null));
        }
        if (financialSubscriptions != null) {
            financialSubscriptions.forEach(i -> i.setAccount(this));
        }
        this.subscriptions = financialSubscriptions;
    }

    public FinancialAccount subscriptions(Set<FinancialSubscription> financialSubscriptions) {
        this.setSubscriptions(financialSubscriptions);
        return this;
    }

    public FinancialAccount addSubscriptions(FinancialSubscription financialSubscription) {
        this.subscriptions.add(financialSubscription);
        financialSubscription.setAccount(this);
        return this;
    }

    public FinancialAccount removeSubscriptions(FinancialSubscription financialSubscription) {
        this.subscriptions.remove(financialSubscription);
        financialSubscription.setAccount(null);
        return this;
    }

    public Set<Budget> getBudgets() {
        return this.budgets;
    }

    public void setBudgets(Set<Budget> budgets) {
        if (this.budgets != null) {
            this.budgets.forEach(i -> i.removeAccounts(this));
        }
        if (budgets != null) {
            budgets.forEach(i -> i.addAccounts(this));
        }
        this.budgets = budgets;
    }

    public FinancialAccount budgets(Set<Budget> budgets) {
        this.setBudgets(budgets);
        return this;
    }

    public FinancialAccount addBudgets(Budget budget) {
        this.budgets.add(budget);
        budget.getAccounts().add(this);
        return this;
    }

    public FinancialAccount removeBudgets(Budget budget) {
        this.budgets.remove(budget);
        budget.getAccounts().remove(this);
        return this;
    }

    public Set<TransactionIngestion> getTransactionIngestions() {
        return this.transactionIngestions;
    }

    public void setTransactionIngestions(Set<TransactionIngestion> transactionIngestions) {
        if (this.transactionIngestions != null) {
            this.transactionIngestions.forEach(i -> i.removeAccounts(this));
        }
        if (transactionIngestions != null) {
            transactionIngestions.forEach(i -> i.addAccounts(this));
        }
        this.transactionIngestions = transactionIngestions;
    }

    public FinancialAccount transactionIngestions(Set<TransactionIngestion> transactionIngestions) {
        this.setTransactionIngestions(transactionIngestions);
        return this;
    }

    public FinancialAccount addTransactionIngestions(TransactionIngestion transactionIngestion) {
        this.transactionIngestions.add(transactionIngestion);
        transactionIngestion.getAccounts().add(this);
        return this;
    }

    public FinancialAccount removeTransactionIngestions(TransactionIngestion transactionIngestion) {
        this.transactionIngestions.remove(transactionIngestion);
        transactionIngestion.getAccounts().remove(this);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FinancialAccount)) {
            return false;
        }
        return getId() != null && getId().equals(((FinancialAccount) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "FinancialAccount{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", institutionName='" + getInstitutionName() + "'" +
            ", accountType='" + getAccountType() + "'" +
            ", currency='" + getCurrency() + "'" +
            ", initialBalance=" + getInitialBalance() +
            ", initialBalanceDate='" + getInitialBalanceDate() + "'" +
            ", lastFourDigits='" + getLastFourDigits() + "'" +
            ", description='" + getDescription() + "'" +
            ", color='" + getColor() + "'" +
            ", icon='" + getIcon() + "'" +
            ", active='" + getActive() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            "}";
    }
}
