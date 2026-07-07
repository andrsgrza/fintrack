package com.fintrack.app.service.criteria;

import com.fintrack.app.domain.enumeration.AccountType;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.fintrack.app.domain.FinancialAccount} entity. This class is used
 * in {@link com.fintrack.app.web.rest.FinancialAccountResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /financial-accounts?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class FinancialAccountCriteria implements Serializable, Criteria {

    /**
     * Class for filtering AccountType
     */
    public static class AccountTypeFilter extends Filter<AccountType> {

        public AccountTypeFilter() {}

        public AccountTypeFilter(AccountTypeFilter filter) {
            super(filter);
        }

        @Override
        public AccountTypeFilter copy() {
            return new AccountTypeFilter(this);
        }
    }

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter name;

    private StringFilter institutionName;

    private AccountTypeFilter accountType;

    private StringFilter currency;

    private BigDecimalFilter initialBalance;

    private LocalDateFilter initialBalanceDate;

    private StringFilter lastFourDigits;

    private StringFilter description;

    private StringFilter color;

    private StringFilter icon;

    private BooleanFilter active;

    private BooleanFilter includeInNetWorth;

    private InstantFilter createdAt;

    private InstantFilter updatedAt;

    private LongFilter userId;

    private LongFilter creditAccountDetailsId;

    private LongFilter financialTransactionsId;

    private LongFilter subscriptionsId;

    private LongFilter budgetsId;

    private LongFilter transactionIngestionsId;

    private LongFilter apiAccessTokensId;

    private Boolean distinct;

    public FinancialAccountCriteria() {}

    public FinancialAccountCriteria(FinancialAccountCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.name = other.optionalName().map(StringFilter::copy).orElse(null);
        this.institutionName = other.optionalInstitutionName().map(StringFilter::copy).orElse(null);
        this.accountType = other.optionalAccountType().map(AccountTypeFilter::copy).orElse(null);
        this.currency = other.optionalCurrency().map(StringFilter::copy).orElse(null);
        this.initialBalance = other.optionalInitialBalance().map(BigDecimalFilter::copy).orElse(null);
        this.initialBalanceDate = other.optionalInitialBalanceDate().map(LocalDateFilter::copy).orElse(null);
        this.lastFourDigits = other.optionalLastFourDigits().map(StringFilter::copy).orElse(null);
        this.description = other.optionalDescription().map(StringFilter::copy).orElse(null);
        this.color = other.optionalColor().map(StringFilter::copy).orElse(null);
        this.icon = other.optionalIcon().map(StringFilter::copy).orElse(null);
        this.active = other.optionalActive().map(BooleanFilter::copy).orElse(null);
        this.includeInNetWorth = other.optionalIncludeInNetWorth().map(BooleanFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.updatedAt = other.optionalUpdatedAt().map(InstantFilter::copy).orElse(null);
        this.userId = other.optionalUserId().map(LongFilter::copy).orElse(null);
        this.creditAccountDetailsId = other.optionalCreditAccountDetailsId().map(LongFilter::copy).orElse(null);
        this.financialTransactionsId = other.optionalFinancialTransactionsId().map(LongFilter::copy).orElse(null);
        this.subscriptionsId = other.optionalSubscriptionsId().map(LongFilter::copy).orElse(null);
        this.budgetsId = other.optionalBudgetsId().map(LongFilter::copy).orElse(null);
        this.transactionIngestionsId = other.optionalTransactionIngestionsId().map(LongFilter::copy).orElse(null);
        this.apiAccessTokensId = other.optionalApiAccessTokensId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public FinancialAccountCriteria copy() {
        return new FinancialAccountCriteria(this);
    }

    public LongFilter getId() {
        return id;
    }

    public Optional<LongFilter> optionalId() {
        return Optional.ofNullable(id);
    }

    public LongFilter id() {
        if (id == null) {
            setId(new LongFilter());
        }
        return id;
    }

    public void setId(LongFilter id) {
        this.id = id;
    }

    public StringFilter getName() {
        return name;
    }

    public Optional<StringFilter> optionalName() {
        return Optional.ofNullable(name);
    }

    public StringFilter name() {
        if (name == null) {
            setName(new StringFilter());
        }
        return name;
    }

    public void setName(StringFilter name) {
        this.name = name;
    }

    public StringFilter getInstitutionName() {
        return institutionName;
    }

    public Optional<StringFilter> optionalInstitutionName() {
        return Optional.ofNullable(institutionName);
    }

    public StringFilter institutionName() {
        if (institutionName == null) {
            setInstitutionName(new StringFilter());
        }
        return institutionName;
    }

    public void setInstitutionName(StringFilter institutionName) {
        this.institutionName = institutionName;
    }

    public AccountTypeFilter getAccountType() {
        return accountType;
    }

    public Optional<AccountTypeFilter> optionalAccountType() {
        return Optional.ofNullable(accountType);
    }

    public AccountTypeFilter accountType() {
        if (accountType == null) {
            setAccountType(new AccountTypeFilter());
        }
        return accountType;
    }

    public void setAccountType(AccountTypeFilter accountType) {
        this.accountType = accountType;
    }

    public StringFilter getCurrency() {
        return currency;
    }

    public Optional<StringFilter> optionalCurrency() {
        return Optional.ofNullable(currency);
    }

    public StringFilter currency() {
        if (currency == null) {
            setCurrency(new StringFilter());
        }
        return currency;
    }

    public void setCurrency(StringFilter currency) {
        this.currency = currency;
    }

    public BigDecimalFilter getInitialBalance() {
        return initialBalance;
    }

    public Optional<BigDecimalFilter> optionalInitialBalance() {
        return Optional.ofNullable(initialBalance);
    }

    public BigDecimalFilter initialBalance() {
        if (initialBalance == null) {
            setInitialBalance(new BigDecimalFilter());
        }
        return initialBalance;
    }

    public void setInitialBalance(BigDecimalFilter initialBalance) {
        this.initialBalance = initialBalance;
    }

    public LocalDateFilter getInitialBalanceDate() {
        return initialBalanceDate;
    }

    public Optional<LocalDateFilter> optionalInitialBalanceDate() {
        return Optional.ofNullable(initialBalanceDate);
    }

    public LocalDateFilter initialBalanceDate() {
        if (initialBalanceDate == null) {
            setInitialBalanceDate(new LocalDateFilter());
        }
        return initialBalanceDate;
    }

    public void setInitialBalanceDate(LocalDateFilter initialBalanceDate) {
        this.initialBalanceDate = initialBalanceDate;
    }

    public StringFilter getLastFourDigits() {
        return lastFourDigits;
    }

    public Optional<StringFilter> optionalLastFourDigits() {
        return Optional.ofNullable(lastFourDigits);
    }

    public StringFilter lastFourDigits() {
        if (lastFourDigits == null) {
            setLastFourDigits(new StringFilter());
        }
        return lastFourDigits;
    }

    public void setLastFourDigits(StringFilter lastFourDigits) {
        this.lastFourDigits = lastFourDigits;
    }

    public StringFilter getDescription() {
        return description;
    }

    public Optional<StringFilter> optionalDescription() {
        return Optional.ofNullable(description);
    }

    public StringFilter description() {
        if (description == null) {
            setDescription(new StringFilter());
        }
        return description;
    }

    public void setDescription(StringFilter description) {
        this.description = description;
    }

    public StringFilter getColor() {
        return color;
    }

    public Optional<StringFilter> optionalColor() {
        return Optional.ofNullable(color);
    }

    public StringFilter color() {
        if (color == null) {
            setColor(new StringFilter());
        }
        return color;
    }

    public void setColor(StringFilter color) {
        this.color = color;
    }

    public StringFilter getIcon() {
        return icon;
    }

    public Optional<StringFilter> optionalIcon() {
        return Optional.ofNullable(icon);
    }

    public StringFilter icon() {
        if (icon == null) {
            setIcon(new StringFilter());
        }
        return icon;
    }

    public void setIcon(StringFilter icon) {
        this.icon = icon;
    }

    public BooleanFilter getActive() {
        return active;
    }

    public Optional<BooleanFilter> optionalActive() {
        return Optional.ofNullable(active);
    }

    public BooleanFilter active() {
        if (active == null) {
            setActive(new BooleanFilter());
        }
        return active;
    }

    public void setActive(BooleanFilter active) {
        this.active = active;
    }

    public BooleanFilter getIncludeInNetWorth() {
        return includeInNetWorth;
    }

    public Optional<BooleanFilter> optionalIncludeInNetWorth() {
        return Optional.ofNullable(includeInNetWorth);
    }

    public BooleanFilter includeInNetWorth() {
        if (includeInNetWorth == null) {
            setIncludeInNetWorth(new BooleanFilter());
        }
        return includeInNetWorth;
    }

    public void setIncludeInNetWorth(BooleanFilter includeInNetWorth) {
        this.includeInNetWorth = includeInNetWorth;
    }

    public InstantFilter getCreatedAt() {
        return createdAt;
    }

    public Optional<InstantFilter> optionalCreatedAt() {
        return Optional.ofNullable(createdAt);
    }

    public InstantFilter createdAt() {
        if (createdAt == null) {
            setCreatedAt(new InstantFilter());
        }
        return createdAt;
    }

    public void setCreatedAt(InstantFilter createdAt) {
        this.createdAt = createdAt;
    }

    public InstantFilter getUpdatedAt() {
        return updatedAt;
    }

    public Optional<InstantFilter> optionalUpdatedAt() {
        return Optional.ofNullable(updatedAt);
    }

    public InstantFilter updatedAt() {
        if (updatedAt == null) {
            setUpdatedAt(new InstantFilter());
        }
        return updatedAt;
    }

    public void setUpdatedAt(InstantFilter updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LongFilter getUserId() {
        return userId;
    }

    public Optional<LongFilter> optionalUserId() {
        return Optional.ofNullable(userId);
    }

    public LongFilter userId() {
        if (userId == null) {
            setUserId(new LongFilter());
        }
        return userId;
    }

    public void setUserId(LongFilter userId) {
        this.userId = userId;
    }

    public LongFilter getCreditAccountDetailsId() {
        return creditAccountDetailsId;
    }

    public Optional<LongFilter> optionalCreditAccountDetailsId() {
        return Optional.ofNullable(creditAccountDetailsId);
    }

    public LongFilter creditAccountDetailsId() {
        if (creditAccountDetailsId == null) {
            setCreditAccountDetailsId(new LongFilter());
        }
        return creditAccountDetailsId;
    }

    public void setCreditAccountDetailsId(LongFilter creditAccountDetailsId) {
        this.creditAccountDetailsId = creditAccountDetailsId;
    }

    public LongFilter getFinancialTransactionsId() {
        return financialTransactionsId;
    }

    public Optional<LongFilter> optionalFinancialTransactionsId() {
        return Optional.ofNullable(financialTransactionsId);
    }

    public LongFilter financialTransactionsId() {
        if (financialTransactionsId == null) {
            setFinancialTransactionsId(new LongFilter());
        }
        return financialTransactionsId;
    }

    public void setFinancialTransactionsId(LongFilter financialTransactionsId) {
        this.financialTransactionsId = financialTransactionsId;
    }

    public LongFilter getSubscriptionsId() {
        return subscriptionsId;
    }

    public Optional<LongFilter> optionalSubscriptionsId() {
        return Optional.ofNullable(subscriptionsId);
    }

    public LongFilter subscriptionsId() {
        if (subscriptionsId == null) {
            setSubscriptionsId(new LongFilter());
        }
        return subscriptionsId;
    }

    public void setSubscriptionsId(LongFilter subscriptionsId) {
        this.subscriptionsId = subscriptionsId;
    }

    public LongFilter getBudgetsId() {
        return budgetsId;
    }

    public Optional<LongFilter> optionalBudgetsId() {
        return Optional.ofNullable(budgetsId);
    }

    public LongFilter budgetsId() {
        if (budgetsId == null) {
            setBudgetsId(new LongFilter());
        }
        return budgetsId;
    }

    public void setBudgetsId(LongFilter budgetsId) {
        this.budgetsId = budgetsId;
    }

    public LongFilter getTransactionIngestionsId() {
        return transactionIngestionsId;
    }

    public Optional<LongFilter> optionalTransactionIngestionsId() {
        return Optional.ofNullable(transactionIngestionsId);
    }

    public LongFilter transactionIngestionsId() {
        if (transactionIngestionsId == null) {
            setTransactionIngestionsId(new LongFilter());
        }
        return transactionIngestionsId;
    }

    public void setTransactionIngestionsId(LongFilter transactionIngestionsId) {
        this.transactionIngestionsId = transactionIngestionsId;
    }

    public LongFilter getApiAccessTokensId() {
        return apiAccessTokensId;
    }

    public Optional<LongFilter> optionalApiAccessTokensId() {
        return Optional.ofNullable(apiAccessTokensId);
    }

    public LongFilter apiAccessTokensId() {
        if (apiAccessTokensId == null) {
            setApiAccessTokensId(new LongFilter());
        }
        return apiAccessTokensId;
    }

    public void setApiAccessTokensId(LongFilter apiAccessTokensId) {
        this.apiAccessTokensId = apiAccessTokensId;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FinancialAccountCriteria that = (FinancialAccountCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(name, that.name) &&
            Objects.equals(institutionName, that.institutionName) &&
            Objects.equals(accountType, that.accountType) &&
            Objects.equals(currency, that.currency) &&
            Objects.equals(initialBalance, that.initialBalance) &&
            Objects.equals(initialBalanceDate, that.initialBalanceDate) &&
            Objects.equals(lastFourDigits, that.lastFourDigits) &&
            Objects.equals(description, that.description) &&
            Objects.equals(color, that.color) &&
            Objects.equals(icon, that.icon) &&
            Objects.equals(active, that.active) &&
            Objects.equals(includeInNetWorth, that.includeInNetWorth) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(updatedAt, that.updatedAt) &&
            Objects.equals(userId, that.userId) &&
            Objects.equals(creditAccountDetailsId, that.creditAccountDetailsId) &&
            Objects.equals(financialTransactionsId, that.financialTransactionsId) &&
            Objects.equals(subscriptionsId, that.subscriptionsId) &&
            Objects.equals(budgetsId, that.budgetsId) &&
            Objects.equals(transactionIngestionsId, that.transactionIngestionsId) &&
            Objects.equals(apiAccessTokensId, that.apiAccessTokensId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            name,
            institutionName,
            accountType,
            currency,
            initialBalance,
            initialBalanceDate,
            lastFourDigits,
            description,
            color,
            icon,
            active,
            includeInNetWorth,
            createdAt,
            updatedAt,
            userId,
            creditAccountDetailsId,
            financialTransactionsId,
            subscriptionsId,
            budgetsId,
            transactionIngestionsId,
            apiAccessTokensId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "FinancialAccountCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalInstitutionName().map(f -> "institutionName=" + f + ", ").orElse("") +
            optionalAccountType().map(f -> "accountType=" + f + ", ").orElse("") +
            optionalCurrency().map(f -> "currency=" + f + ", ").orElse("") +
            optionalInitialBalance().map(f -> "initialBalance=" + f + ", ").orElse("") +
            optionalInitialBalanceDate().map(f -> "initialBalanceDate=" + f + ", ").orElse("") +
            optionalLastFourDigits().map(f -> "lastFourDigits=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalColor().map(f -> "color=" + f + ", ").orElse("") +
            optionalIcon().map(f -> "icon=" + f + ", ").orElse("") +
            optionalActive().map(f -> "active=" + f + ", ").orElse("") +
            optionalIncludeInNetWorth().map(f -> "includeInNetWorth=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalUpdatedAt().map(f -> "updatedAt=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalCreditAccountDetailsId().map(f -> "creditAccountDetailsId=" + f + ", ").orElse("") +
            optionalFinancialTransactionsId().map(f -> "financialTransactionsId=" + f + ", ").orElse("") +
            optionalSubscriptionsId().map(f -> "subscriptionsId=" + f + ", ").orElse("") +
            optionalBudgetsId().map(f -> "budgetsId=" + f + ", ").orElse("") +
            optionalTransactionIngestionsId().map(f -> "transactionIngestionsId=" + f + ", ").orElse("") +
            optionalApiAccessTokensId().map(f -> "apiAccessTokensId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
