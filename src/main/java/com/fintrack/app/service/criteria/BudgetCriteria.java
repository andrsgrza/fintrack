package com.fintrack.app.service.criteria;

import com.fintrack.app.domain.enumeration.BudgetPeriod;
import com.fintrack.app.domain.enumeration.BudgetStatus;
import com.fintrack.app.domain.enumeration.TagMatchMode;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.fintrack.app.domain.Budget} entity. This class is used
 * in {@link com.fintrack.app.web.rest.BudgetResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /budgets?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class BudgetCriteria implements Serializable, Criteria {

    /**
     * Class for filtering BudgetPeriod
     */
    public static class BudgetPeriodFilter extends Filter<BudgetPeriod> {

        public BudgetPeriodFilter() {}

        public BudgetPeriodFilter(BudgetPeriodFilter filter) {
            super(filter);
        }

        @Override
        public BudgetPeriodFilter copy() {
            return new BudgetPeriodFilter(this);
        }
    }

    /**
     * Class for filtering BudgetStatus
     */
    public static class BudgetStatusFilter extends Filter<BudgetStatus> {

        public BudgetStatusFilter() {}

        public BudgetStatusFilter(BudgetStatusFilter filter) {
            super(filter);
        }

        @Override
        public BudgetStatusFilter copy() {
            return new BudgetStatusFilter(this);
        }
    }

    /**
     * Class for filtering TagMatchMode
     */
    public static class TagMatchModeFilter extends Filter<TagMatchMode> {

        public TagMatchModeFilter() {}

        public TagMatchModeFilter(TagMatchModeFilter filter) {
            super(filter);
        }

        @Override
        public TagMatchModeFilter copy() {
            return new TagMatchModeFilter(this);
        }
    }

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter name;

    private BigDecimalFilter amount;

    private StringFilter currency;

    private BudgetPeriodFilter period;

    private LocalDateFilter startDate;

    private LocalDateFilter endDate;

    private BudgetStatusFilter status;

    private TagMatchModeFilter tagMatchMode;

    private BigDecimalFilter warningPercentage;

    private InstantFilter createdAt;

    private InstantFilter updatedAt;

    private LongFilter userId;

    private LongFilter accountsId;

    private LongFilter categoriesId;

    private LongFilter tagsId;

    private Boolean distinct;

    public BudgetCriteria() {}

    public BudgetCriteria(BudgetCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.name = other.optionalName().map(StringFilter::copy).orElse(null);
        this.amount = other.optionalAmount().map(BigDecimalFilter::copy).orElse(null);
        this.currency = other.optionalCurrency().map(StringFilter::copy).orElse(null);
        this.period = other.optionalPeriod().map(BudgetPeriodFilter::copy).orElse(null);
        this.startDate = other.optionalStartDate().map(LocalDateFilter::copy).orElse(null);
        this.endDate = other.optionalEndDate().map(LocalDateFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(BudgetStatusFilter::copy).orElse(null);
        this.tagMatchMode = other.optionalTagMatchMode().map(TagMatchModeFilter::copy).orElse(null);
        this.warningPercentage = other.optionalWarningPercentage().map(BigDecimalFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.updatedAt = other.optionalUpdatedAt().map(InstantFilter::copy).orElse(null);
        this.userId = other.optionalUserId().map(LongFilter::copy).orElse(null);
        this.accountsId = other.optionalAccountsId().map(LongFilter::copy).orElse(null);
        this.categoriesId = other.optionalCategoriesId().map(LongFilter::copy).orElse(null);
        this.tagsId = other.optionalTagsId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public BudgetCriteria copy() {
        return new BudgetCriteria(this);
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

    public BigDecimalFilter getAmount() {
        return amount;
    }

    public Optional<BigDecimalFilter> optionalAmount() {
        return Optional.ofNullable(amount);
    }

    public BigDecimalFilter amount() {
        if (amount == null) {
            setAmount(new BigDecimalFilter());
        }
        return amount;
    }

    public void setAmount(BigDecimalFilter amount) {
        this.amount = amount;
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

    public BudgetPeriodFilter getPeriod() {
        return period;
    }

    public Optional<BudgetPeriodFilter> optionalPeriod() {
        return Optional.ofNullable(period);
    }

    public BudgetPeriodFilter period() {
        if (period == null) {
            setPeriod(new BudgetPeriodFilter());
        }
        return period;
    }

    public void setPeriod(BudgetPeriodFilter period) {
        this.period = period;
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

    public BudgetStatusFilter getStatus() {
        return status;
    }

    public Optional<BudgetStatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public BudgetStatusFilter status() {
        if (status == null) {
            setStatus(new BudgetStatusFilter());
        }
        return status;
    }

    public void setStatus(BudgetStatusFilter status) {
        this.status = status;
    }

    public TagMatchModeFilter getTagMatchMode() {
        return tagMatchMode;
    }

    public Optional<TagMatchModeFilter> optionalTagMatchMode() {
        return Optional.ofNullable(tagMatchMode);
    }

    public TagMatchModeFilter tagMatchMode() {
        if (tagMatchMode == null) {
            setTagMatchMode(new TagMatchModeFilter());
        }
        return tagMatchMode;
    }

    public void setTagMatchMode(TagMatchModeFilter tagMatchMode) {
        this.tagMatchMode = tagMatchMode;
    }

    public BigDecimalFilter getWarningPercentage() {
        return warningPercentage;
    }

    public Optional<BigDecimalFilter> optionalWarningPercentage() {
        return Optional.ofNullable(warningPercentage);
    }

    public BigDecimalFilter warningPercentage() {
        if (warningPercentage == null) {
            setWarningPercentage(new BigDecimalFilter());
        }
        return warningPercentage;
    }

    public void setWarningPercentage(BigDecimalFilter warningPercentage) {
        this.warningPercentage = warningPercentage;
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

    public LongFilter getAccountsId() {
        return accountsId;
    }

    public Optional<LongFilter> optionalAccountsId() {
        return Optional.ofNullable(accountsId);
    }

    public LongFilter accountsId() {
        if (accountsId == null) {
            setAccountsId(new LongFilter());
        }
        return accountsId;
    }

    public void setAccountsId(LongFilter accountsId) {
        this.accountsId = accountsId;
    }

    public LongFilter getCategoriesId() {
        return categoriesId;
    }

    public Optional<LongFilter> optionalCategoriesId() {
        return Optional.ofNullable(categoriesId);
    }

    public LongFilter categoriesId() {
        if (categoriesId == null) {
            setCategoriesId(new LongFilter());
        }
        return categoriesId;
    }

    public void setCategoriesId(LongFilter categoriesId) {
        this.categoriesId = categoriesId;
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
        final BudgetCriteria that = (BudgetCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(name, that.name) &&
            Objects.equals(amount, that.amount) &&
            Objects.equals(currency, that.currency) &&
            Objects.equals(period, that.period) &&
            Objects.equals(startDate, that.startDate) &&
            Objects.equals(endDate, that.endDate) &&
            Objects.equals(status, that.status) &&
            Objects.equals(tagMatchMode, that.tagMatchMode) &&
            Objects.equals(warningPercentage, that.warningPercentage) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(updatedAt, that.updatedAt) &&
            Objects.equals(userId, that.userId) &&
            Objects.equals(accountsId, that.accountsId) &&
            Objects.equals(categoriesId, that.categoriesId) &&
            Objects.equals(tagsId, that.tagsId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            name,
            amount,
            currency,
            period,
            startDate,
            endDate,
            status,
            tagMatchMode,
            warningPercentage,
            createdAt,
            updatedAt,
            userId,
            accountsId,
            categoriesId,
            tagsId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "BudgetCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalAmount().map(f -> "amount=" + f + ", ").orElse("") +
            optionalCurrency().map(f -> "currency=" + f + ", ").orElse("") +
            optionalPeriod().map(f -> "period=" + f + ", ").orElse("") +
            optionalStartDate().map(f -> "startDate=" + f + ", ").orElse("") +
            optionalEndDate().map(f -> "endDate=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalTagMatchMode().map(f -> "tagMatchMode=" + f + ", ").orElse("") +
            optionalWarningPercentage().map(f -> "warningPercentage=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalUpdatedAt().map(f -> "updatedAt=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalAccountsId().map(f -> "accountsId=" + f + ", ").orElse("") +
            optionalCategoriesId().map(f -> "categoriesId=" + f + ", ").orElse("") +
            optionalTagsId().map(f -> "tagsId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
