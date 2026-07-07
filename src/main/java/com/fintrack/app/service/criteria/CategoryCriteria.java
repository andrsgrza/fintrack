package com.fintrack.app.service.criteria;

import com.fintrack.app.domain.enumeration.CategoryType;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.fintrack.app.domain.Category} entity. This class is used
 * in {@link com.fintrack.app.web.rest.CategoryResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /categories?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CategoryCriteria implements Serializable, Criteria {

    /**
     * Class for filtering CategoryType
     */
    public static class CategoryTypeFilter extends Filter<CategoryType> {

        public CategoryTypeFilter() {}

        public CategoryTypeFilter(CategoryTypeFilter filter) {
            super(filter);
        }

        @Override
        public CategoryTypeFilter copy() {
            return new CategoryTypeFilter(this);
        }
    }

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter name;

    private StringFilter description;

    private CategoryTypeFilter categoryType;

    private StringFilter color;

    private StringFilter icon;

    private BooleanFilter active;

    private InstantFilter createdAt;

    private InstantFilter updatedAt;

    private LongFilter userId;

    private LongFilter parentCategoryId;

    private LongFilter financialTransactionsId;

    private LongFilter childCategoriesId;

    private LongFilter transactionRulesId;

    private LongFilter subscriptionsId;

    private LongFilter budgetsId;

    private Boolean distinct;

    public CategoryCriteria() {}

    public CategoryCriteria(CategoryCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.name = other.optionalName().map(StringFilter::copy).orElse(null);
        this.description = other.optionalDescription().map(StringFilter::copy).orElse(null);
        this.categoryType = other.optionalCategoryType().map(CategoryTypeFilter::copy).orElse(null);
        this.color = other.optionalColor().map(StringFilter::copy).orElse(null);
        this.icon = other.optionalIcon().map(StringFilter::copy).orElse(null);
        this.active = other.optionalActive().map(BooleanFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.updatedAt = other.optionalUpdatedAt().map(InstantFilter::copy).orElse(null);
        this.userId = other.optionalUserId().map(LongFilter::copy).orElse(null);
        this.parentCategoryId = other.optionalParentCategoryId().map(LongFilter::copy).orElse(null);
        this.financialTransactionsId = other.optionalFinancialTransactionsId().map(LongFilter::copy).orElse(null);
        this.childCategoriesId = other.optionalChildCategoriesId().map(LongFilter::copy).orElse(null);
        this.transactionRulesId = other.optionalTransactionRulesId().map(LongFilter::copy).orElse(null);
        this.subscriptionsId = other.optionalSubscriptionsId().map(LongFilter::copy).orElse(null);
        this.budgetsId = other.optionalBudgetsId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public CategoryCriteria copy() {
        return new CategoryCriteria(this);
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

    public CategoryTypeFilter getCategoryType() {
        return categoryType;
    }

    public Optional<CategoryTypeFilter> optionalCategoryType() {
        return Optional.ofNullable(categoryType);
    }

    public CategoryTypeFilter categoryType() {
        if (categoryType == null) {
            setCategoryType(new CategoryTypeFilter());
        }
        return categoryType;
    }

    public void setCategoryType(CategoryTypeFilter categoryType) {
        this.categoryType = categoryType;
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

    public LongFilter getParentCategoryId() {
        return parentCategoryId;
    }

    public Optional<LongFilter> optionalParentCategoryId() {
        return Optional.ofNullable(parentCategoryId);
    }

    public LongFilter parentCategoryId() {
        if (parentCategoryId == null) {
            setParentCategoryId(new LongFilter());
        }
        return parentCategoryId;
    }

    public void setParentCategoryId(LongFilter parentCategoryId) {
        this.parentCategoryId = parentCategoryId;
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

    public LongFilter getChildCategoriesId() {
        return childCategoriesId;
    }

    public Optional<LongFilter> optionalChildCategoriesId() {
        return Optional.ofNullable(childCategoriesId);
    }

    public LongFilter childCategoriesId() {
        if (childCategoriesId == null) {
            setChildCategoriesId(new LongFilter());
        }
        return childCategoriesId;
    }

    public void setChildCategoriesId(LongFilter childCategoriesId) {
        this.childCategoriesId = childCategoriesId;
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
        final CategoryCriteria that = (CategoryCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(name, that.name) &&
            Objects.equals(description, that.description) &&
            Objects.equals(categoryType, that.categoryType) &&
            Objects.equals(color, that.color) &&
            Objects.equals(icon, that.icon) &&
            Objects.equals(active, that.active) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(updatedAt, that.updatedAt) &&
            Objects.equals(userId, that.userId) &&
            Objects.equals(parentCategoryId, that.parentCategoryId) &&
            Objects.equals(financialTransactionsId, that.financialTransactionsId) &&
            Objects.equals(childCategoriesId, that.childCategoriesId) &&
            Objects.equals(transactionRulesId, that.transactionRulesId) &&
            Objects.equals(subscriptionsId, that.subscriptionsId) &&
            Objects.equals(budgetsId, that.budgetsId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            name,
            description,
            categoryType,
            color,
            icon,
            active,
            createdAt,
            updatedAt,
            userId,
            parentCategoryId,
            financialTransactionsId,
            childCategoriesId,
            transactionRulesId,
            subscriptionsId,
            budgetsId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CategoryCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalCategoryType().map(f -> "categoryType=" + f + ", ").orElse("") +
            optionalColor().map(f -> "color=" + f + ", ").orElse("") +
            optionalIcon().map(f -> "icon=" + f + ", ").orElse("") +
            optionalActive().map(f -> "active=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalUpdatedAt().map(f -> "updatedAt=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalParentCategoryId().map(f -> "parentCategoryId=" + f + ", ").orElse("") +
            optionalFinancialTransactionsId().map(f -> "financialTransactionsId=" + f + ", ").orElse("") +
            optionalChildCategoriesId().map(f -> "childCategoriesId=" + f + ", ").orElse("") +
            optionalTransactionRulesId().map(f -> "transactionRulesId=" + f + ", ").orElse("") +
            optionalSubscriptionsId().map(f -> "subscriptionsId=" + f + ", ").orElse("") +
            optionalBudgetsId().map(f -> "budgetsId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
