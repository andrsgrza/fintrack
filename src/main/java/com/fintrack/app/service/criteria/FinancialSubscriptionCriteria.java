package com.fintrack.app.service.criteria;

import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.RecurrenceUnit;
import com.fintrack.app.domain.enumeration.SubscriptionStatus;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.fintrack.app.domain.FinancialSubscription} entity. This class is used
 * in {@link com.fintrack.app.web.rest.FinancialSubscriptionResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /financial-subscriptions?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class FinancialSubscriptionCriteria implements Serializable, Criteria {

    /**
     * Class for filtering SubscriptionStatus
     */
    public static class SubscriptionStatusFilter extends Filter<SubscriptionStatus> {

        public SubscriptionStatusFilter() {}

        public SubscriptionStatusFilter(SubscriptionStatusFilter filter) {
            super(filter);
        }

        @Override
        public SubscriptionStatusFilter copy() {
            return new SubscriptionStatusFilter(this);
        }
    }

    /**
     * Class for filtering CurrencyCode
     */
    public static class CurrencyCodeFilter extends Filter<CurrencyCode> {

        public CurrencyCodeFilter() {}

        public CurrencyCodeFilter(CurrencyCodeFilter filter) {
            super(filter);
        }

        @Override
        public CurrencyCodeFilter copy() {
            return new CurrencyCodeFilter(this);
        }
    }

    /**
     * Class for filtering RecurrenceUnit
     */
    public static class RecurrenceUnitFilter extends Filter<RecurrenceUnit> {

        public RecurrenceUnitFilter() {}

        public RecurrenceUnitFilter(RecurrenceUnitFilter filter) {
            super(filter);
        }

        @Override
        public RecurrenceUnitFilter copy() {
            return new RecurrenceUnitFilter(this);
        }
    }

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter name;

    private StringFilter description;

    private SubscriptionStatusFilter status;

    private BigDecimalFilter expectedAmount;

    private BigDecimalFilter amountTolerancePercentage;

    private CurrencyCodeFilter currency;

    private RecurrenceUnitFilter recurrenceUnit;

    private IntegerFilter intervalCount;

    private LocalDateFilter startDate;

    private LocalDateFilter nextExpectedDate;

    private LocalDateFilter endDate;

    private BooleanFilter automaticPayment;

    private StringFilter notes;

    private InstantFilter createdAt;

    private InstantFilter updatedAt;

    private LongFilter userId;

    private LongFilter accountId;

    private LongFilter categoryId;

    private LongFilter tagsId;

    private LongFilter financialTransactionsId;

    private LongFilter transactionRulesId;

    private Boolean distinct;

    public FinancialSubscriptionCriteria() {}

    public FinancialSubscriptionCriteria(FinancialSubscriptionCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.name = other.optionalName().map(StringFilter::copy).orElse(null);
        this.description = other.optionalDescription().map(StringFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(SubscriptionStatusFilter::copy).orElse(null);
        this.expectedAmount = other.optionalExpectedAmount().map(BigDecimalFilter::copy).orElse(null);
        this.amountTolerancePercentage = other.optionalAmountTolerancePercentage().map(BigDecimalFilter::copy).orElse(null);
        this.currency = other.optionalCurrency().map(CurrencyCodeFilter::copy).orElse(null);
        this.recurrenceUnit = other.optionalRecurrenceUnit().map(RecurrenceUnitFilter::copy).orElse(null);
        this.intervalCount = other.optionalIntervalCount().map(IntegerFilter::copy).orElse(null);
        this.startDate = other.optionalStartDate().map(LocalDateFilter::copy).orElse(null);
        this.nextExpectedDate = other.optionalNextExpectedDate().map(LocalDateFilter::copy).orElse(null);
        this.endDate = other.optionalEndDate().map(LocalDateFilter::copy).orElse(null);
        this.automaticPayment = other.optionalAutomaticPayment().map(BooleanFilter::copy).orElse(null);
        this.notes = other.optionalNotes().map(StringFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.updatedAt = other.optionalUpdatedAt().map(InstantFilter::copy).orElse(null);
        this.userId = other.optionalUserId().map(LongFilter::copy).orElse(null);
        this.accountId = other.optionalAccountId().map(LongFilter::copy).orElse(null);
        this.categoryId = other.optionalCategoryId().map(LongFilter::copy).orElse(null);
        this.tagsId = other.optionalTagsId().map(LongFilter::copy).orElse(null);
        this.financialTransactionsId = other.optionalFinancialTransactionsId().map(LongFilter::copy).orElse(null);
        this.transactionRulesId = other.optionalTransactionRulesId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public FinancialSubscriptionCriteria copy() {
        return new FinancialSubscriptionCriteria(this);
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

    public SubscriptionStatusFilter getStatus() {
        return status;
    }

    public Optional<SubscriptionStatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public SubscriptionStatusFilter status() {
        if (status == null) {
            setStatus(new SubscriptionStatusFilter());
        }
        return status;
    }

    public void setStatus(SubscriptionStatusFilter status) {
        this.status = status;
    }

    public BigDecimalFilter getExpectedAmount() {
        return expectedAmount;
    }

    public Optional<BigDecimalFilter> optionalExpectedAmount() {
        return Optional.ofNullable(expectedAmount);
    }

    public BigDecimalFilter expectedAmount() {
        if (expectedAmount == null) {
            setExpectedAmount(new BigDecimalFilter());
        }
        return expectedAmount;
    }

    public void setExpectedAmount(BigDecimalFilter expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public BigDecimalFilter getAmountTolerancePercentage() {
        return amountTolerancePercentage;
    }

    public Optional<BigDecimalFilter> optionalAmountTolerancePercentage() {
        return Optional.ofNullable(amountTolerancePercentage);
    }

    public BigDecimalFilter amountTolerancePercentage() {
        if (amountTolerancePercentage == null) {
            setAmountTolerancePercentage(new BigDecimalFilter());
        }
        return amountTolerancePercentage;
    }

    public void setAmountTolerancePercentage(BigDecimalFilter amountTolerancePercentage) {
        this.amountTolerancePercentage = amountTolerancePercentage;
    }

    public CurrencyCodeFilter getCurrency() {
        return currency;
    }

    public Optional<CurrencyCodeFilter> optionalCurrency() {
        return Optional.ofNullable(currency);
    }

    public CurrencyCodeFilter currency() {
        if (currency == null) {
            setCurrency(new CurrencyCodeFilter());
        }
        return currency;
    }

    public void setCurrency(CurrencyCodeFilter currency) {
        this.currency = currency;
    }

    public RecurrenceUnitFilter getRecurrenceUnit() {
        return recurrenceUnit;
    }

    public Optional<RecurrenceUnitFilter> optionalRecurrenceUnit() {
        return Optional.ofNullable(recurrenceUnit);
    }

    public RecurrenceUnitFilter recurrenceUnit() {
        if (recurrenceUnit == null) {
            setRecurrenceUnit(new RecurrenceUnitFilter());
        }
        return recurrenceUnit;
    }

    public void setRecurrenceUnit(RecurrenceUnitFilter recurrenceUnit) {
        this.recurrenceUnit = recurrenceUnit;
    }

    public IntegerFilter getIntervalCount() {
        return intervalCount;
    }

    public Optional<IntegerFilter> optionalIntervalCount() {
        return Optional.ofNullable(intervalCount);
    }

    public IntegerFilter intervalCount() {
        if (intervalCount == null) {
            setIntervalCount(new IntegerFilter());
        }
        return intervalCount;
    }

    public void setIntervalCount(IntegerFilter intervalCount) {
        this.intervalCount = intervalCount;
    }

    public LocalDateFilter getStartDate() {
        return startDate;
    }

    public Optional<LocalDateFilter> optionalStartDate() {
        return Optional.ofNullable(startDate);
    }

    public LocalDateFilter startDate() {
        if (startDate == null) {
            setStartDate(new LocalDateFilter());
        }
        return startDate;
    }

    public void setStartDate(LocalDateFilter startDate) {
        this.startDate = startDate;
    }

    public LocalDateFilter getNextExpectedDate() {
        return nextExpectedDate;
    }

    public Optional<LocalDateFilter> optionalNextExpectedDate() {
        return Optional.ofNullable(nextExpectedDate);
    }

    public LocalDateFilter nextExpectedDate() {
        if (nextExpectedDate == null) {
            setNextExpectedDate(new LocalDateFilter());
        }
        return nextExpectedDate;
    }

    public void setNextExpectedDate(LocalDateFilter nextExpectedDate) {
        this.nextExpectedDate = nextExpectedDate;
    }

    public LocalDateFilter getEndDate() {
        return endDate;
    }

    public Optional<LocalDateFilter> optionalEndDate() {
        return Optional.ofNullable(endDate);
    }

    public LocalDateFilter endDate() {
        if (endDate == null) {
            setEndDate(new LocalDateFilter());
        }
        return endDate;
    }

    public void setEndDate(LocalDateFilter endDate) {
        this.endDate = endDate;
    }

    public BooleanFilter getAutomaticPayment() {
        return automaticPayment;
    }

    public Optional<BooleanFilter> optionalAutomaticPayment() {
        return Optional.ofNullable(automaticPayment);
    }

    public BooleanFilter automaticPayment() {
        if (automaticPayment == null) {
            setAutomaticPayment(new BooleanFilter());
        }
        return automaticPayment;
    }

    public void setAutomaticPayment(BooleanFilter automaticPayment) {
        this.automaticPayment = automaticPayment;
    }

    public StringFilter getNotes() {
        return notes;
    }

    public Optional<StringFilter> optionalNotes() {
        return Optional.ofNullable(notes);
    }

    public StringFilter notes() {
        if (notes == null) {
            setNotes(new StringFilter());
        }
        return notes;
    }

    public void setNotes(StringFilter notes) {
        this.notes = notes;
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

    public LongFilter getAccountId() {
        return accountId;
    }

    public Optional<LongFilter> optionalAccountId() {
        return Optional.ofNullable(accountId);
    }

    public LongFilter accountId() {
        if (accountId == null) {
            setAccountId(new LongFilter());
        }
        return accountId;
    }

    public void setAccountId(LongFilter accountId) {
        this.accountId = accountId;
    }

    public LongFilter getCategoryId() {
        return categoryId;
    }

    public Optional<LongFilter> optionalCategoryId() {
        return Optional.ofNullable(categoryId);
    }

    public LongFilter categoryId() {
        if (categoryId == null) {
            setCategoryId(new LongFilter());
        }
        return categoryId;
    }

    public void setCategoryId(LongFilter categoryId) {
        this.categoryId = categoryId;
    }

    public LongFilter getTagsId() {
        return tagsId;
    }

    public Optional<LongFilter> optionalTagsId() {
        return Optional.ofNullable(tagsId);
    }

    public LongFilter tagsId() {
        if (tagsId == null) {
            setTagsId(new LongFilter());
        }
        return tagsId;
    }

    public void setTagsId(LongFilter tagsId) {
        this.tagsId = tagsId;
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

    public LongFilter getTransactionRulesId() {
        return transactionRulesId;
    }

    public Optional<LongFilter> optionalTransactionRulesId() {
        return Optional.ofNullable(transactionRulesId);
    }

    public LongFilter transactionRulesId() {
        if (transactionRulesId == null) {
            setTransactionRulesId(new LongFilter());
        }
        return transactionRulesId;
    }

    public void setTransactionRulesId(LongFilter transactionRulesId) {
        this.transactionRulesId = transactionRulesId;
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
        final FinancialSubscriptionCriteria that = (FinancialSubscriptionCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(name, that.name) &&
            Objects.equals(description, that.description) &&
            Objects.equals(status, that.status) &&
            Objects.equals(expectedAmount, that.expectedAmount) &&
            Objects.equals(amountTolerancePercentage, that.amountTolerancePercentage) &&
            Objects.equals(currency, that.currency) &&
            Objects.equals(recurrenceUnit, that.recurrenceUnit) &&
            Objects.equals(intervalCount, that.intervalCount) &&
            Objects.equals(startDate, that.startDate) &&
            Objects.equals(nextExpectedDate, that.nextExpectedDate) &&
            Objects.equals(endDate, that.endDate) &&
            Objects.equals(automaticPayment, that.automaticPayment) &&
            Objects.equals(notes, that.notes) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(updatedAt, that.updatedAt) &&
            Objects.equals(userId, that.userId) &&
            Objects.equals(accountId, that.accountId) &&
            Objects.equals(categoryId, that.categoryId) &&
            Objects.equals(tagsId, that.tagsId) &&
            Objects.equals(financialTransactionsId, that.financialTransactionsId) &&
            Objects.equals(transactionRulesId, that.transactionRulesId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            name,
            description,
            status,
            expectedAmount,
            amountTolerancePercentage,
            currency,
            recurrenceUnit,
            intervalCount,
            startDate,
            nextExpectedDate,
            endDate,
            automaticPayment,
            notes,
            createdAt,
            updatedAt,
            userId,
            accountId,
            categoryId,
            tagsId,
            financialTransactionsId,
            transactionRulesId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "FinancialSubscriptionCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalExpectedAmount().map(f -> "expectedAmount=" + f + ", ").orElse("") +
            optionalAmountTolerancePercentage().map(f -> "amountTolerancePercentage=" + f + ", ").orElse("") +
            optionalCurrency().map(f -> "currency=" + f + ", ").orElse("") +
            optionalRecurrenceUnit().map(f -> "recurrenceUnit=" + f + ", ").orElse("") +
            optionalIntervalCount().map(f -> "intervalCount=" + f + ", ").orElse("") +
            optionalStartDate().map(f -> "startDate=" + f + ", ").orElse("") +
            optionalNextExpectedDate().map(f -> "nextExpectedDate=" + f + ", ").orElse("") +
            optionalEndDate().map(f -> "endDate=" + f + ", ").orElse("") +
            optionalAutomaticPayment().map(f -> "automaticPayment=" + f + ", ").orElse("") +
            optionalNotes().map(f -> "notes=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalUpdatedAt().map(f -> "updatedAt=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalAccountId().map(f -> "accountId=" + f + ", ").orElse("") +
            optionalCategoryId().map(f -> "categoryId=" + f + ", ").orElse("") +
            optionalTagsId().map(f -> "tagsId=" + f + ", ").orElse("") +
            optionalFinancialTransactionsId().map(f -> "financialTransactionsId=" + f + ", ").orElse("") +
            optionalTransactionRulesId().map(f -> "transactionRulesId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
