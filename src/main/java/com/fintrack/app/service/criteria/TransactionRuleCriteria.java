package com.fintrack.app.service.criteria;

import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.fintrack.app.domain.TransactionRule} entity. This class is used
 * in {@link com.fintrack.app.web.rest.TransactionRuleResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /transaction-rules?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionRuleCriteria implements Serializable, Criteria {

    /**
     * Class for filtering RuleConditionLogic
     */
    public static class RuleConditionLogicFilter extends Filter<RuleConditionLogic> {

        public RuleConditionLogicFilter() {}

        public RuleConditionLogicFilter(RuleConditionLogicFilter filter) {
            super(filter);
        }

        @Override
        public RuleConditionLogicFilter copy() {
            return new RuleConditionLogicFilter(this);
        }
    }

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private StringFilter name;

    private StringFilter description;

    private IntegerFilter priority;

    private RuleConditionLogicFilter conditionLogic;

    private StringFilter resultingDescription;

    private BooleanFilter active;

    private InstantFilter createdAt;

    private InstantFilter updatedAt;

    private LongFilter userId;

    private LongFilter resultingCategoryId;

    private LongFilter resultingFinancialSubscriptionId;

    private LongFilter resultingTagsId;

    private LongFilter conditionsId;

    private Boolean distinct;

    public TransactionRuleCriteria() {}

    public TransactionRuleCriteria(TransactionRuleCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.name = other.optionalName().map(StringFilter::copy).orElse(null);
        this.description = other.optionalDescription().map(StringFilter::copy).orElse(null);
        this.priority = other.optionalPriority().map(IntegerFilter::copy).orElse(null);
        this.conditionLogic = other.optionalConditionLogic().map(RuleConditionLogicFilter::copy).orElse(null);
        this.resultingDescription = other.optionalResultingDescription().map(StringFilter::copy).orElse(null);
        this.active = other.optionalActive().map(BooleanFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.updatedAt = other.optionalUpdatedAt().map(InstantFilter::copy).orElse(null);
        this.userId = other.optionalUserId().map(LongFilter::copy).orElse(null);
        this.resultingCategoryId = other.optionalResultingCategoryId().map(LongFilter::copy).orElse(null);
        this.resultingFinancialSubscriptionId = other.optionalResultingFinancialSubscriptionId().map(LongFilter::copy).orElse(null);
        this.resultingTagsId = other.optionalResultingTagsId().map(LongFilter::copy).orElse(null);
        this.conditionsId = other.optionalConditionsId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public TransactionRuleCriteria copy() {
        return new TransactionRuleCriteria(this);
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

    public IntegerFilter getPriority() {
        return priority;
    }

    public Optional<IntegerFilter> optionalPriority() {
        return Optional.ofNullable(priority);
    }

    public IntegerFilter priority() {
        if (priority == null) {
            setPriority(new IntegerFilter());
        }
        return priority;
    }

    public void setPriority(IntegerFilter priority) {
        this.priority = priority;
    }

    public RuleConditionLogicFilter getConditionLogic() {
        return conditionLogic;
    }

    public Optional<RuleConditionLogicFilter> optionalConditionLogic() {
        return Optional.ofNullable(conditionLogic);
    }

    public RuleConditionLogicFilter conditionLogic() {
        if (conditionLogic == null) {
            setConditionLogic(new RuleConditionLogicFilter());
        }
        return conditionLogic;
    }

    public void setConditionLogic(RuleConditionLogicFilter conditionLogic) {
        this.conditionLogic = conditionLogic;
    }

    public StringFilter getResultingDescription() {
        return resultingDescription;
    }

    public Optional<StringFilter> optionalResultingDescription() {
        return Optional.ofNullable(resultingDescription);
    }

    public StringFilter resultingDescription() {
        if (resultingDescription == null) {
            setResultingDescription(new StringFilter());
        }
        return resultingDescription;
    }

    public void setResultingDescription(StringFilter resultingDescription) {
        this.resultingDescription = resultingDescription;
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

    public LongFilter getResultingCategoryId() {
        return resultingCategoryId;
    }

    public Optional<LongFilter> optionalResultingCategoryId() {
        return Optional.ofNullable(resultingCategoryId);
    }

    public LongFilter resultingCategoryId() {
        if (resultingCategoryId == null) {
            setResultingCategoryId(new LongFilter());
        }
        return resultingCategoryId;
    }

    public void setResultingCategoryId(LongFilter resultingCategoryId) {
        this.resultingCategoryId = resultingCategoryId;
    }

    public LongFilter getResultingFinancialSubscriptionId() {
        return resultingFinancialSubscriptionId;
    }

    public Optional<LongFilter> optionalResultingFinancialSubscriptionId() {
        return Optional.ofNullable(resultingFinancialSubscriptionId);
    }

    public LongFilter resultingFinancialSubscriptionId() {
        if (resultingFinancialSubscriptionId == null) {
            setResultingFinancialSubscriptionId(new LongFilter());
        }
        return resultingFinancialSubscriptionId;
    }

    public void setResultingFinancialSubscriptionId(LongFilter resultingFinancialSubscriptionId) {
        this.resultingFinancialSubscriptionId = resultingFinancialSubscriptionId;
    }

    public LongFilter getResultingTagsId() {
        return resultingTagsId;
    }

    public Optional<LongFilter> optionalResultingTagsId() {
        return Optional.ofNullable(resultingTagsId);
    }

    public LongFilter resultingTagsId() {
        if (resultingTagsId == null) {
            setResultingTagsId(new LongFilter());
        }
        return resultingTagsId;
    }

    public void setResultingTagsId(LongFilter resultingTagsId) {
        this.resultingTagsId = resultingTagsId;
    }

    public LongFilter getConditionsId() {
        return conditionsId;
    }

    public Optional<LongFilter> optionalConditionsId() {
        return Optional.ofNullable(conditionsId);
    }

    public LongFilter conditionsId() {
        if (conditionsId == null) {
            setConditionsId(new LongFilter());
        }
        return conditionsId;
    }

    public void setConditionsId(LongFilter conditionsId) {
        this.conditionsId = conditionsId;
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
        final TransactionRuleCriteria that = (TransactionRuleCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(name, that.name) &&
            Objects.equals(description, that.description) &&
            Objects.equals(priority, that.priority) &&
            Objects.equals(conditionLogic, that.conditionLogic) &&
            Objects.equals(resultingDescription, that.resultingDescription) &&
            Objects.equals(active, that.active) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(updatedAt, that.updatedAt) &&
            Objects.equals(userId, that.userId) &&
            Objects.equals(resultingCategoryId, that.resultingCategoryId) &&
            Objects.equals(resultingFinancialSubscriptionId, that.resultingFinancialSubscriptionId) &&
            Objects.equals(resultingTagsId, that.resultingTagsId) &&
            Objects.equals(conditionsId, that.conditionsId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            name,
            description,
            priority,
            conditionLogic,
            resultingDescription,
            active,
            createdAt,
            updatedAt,
            userId,
            resultingCategoryId,
            resultingFinancialSubscriptionId,
            resultingTagsId,
            conditionsId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionRuleCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalPriority().map(f -> "priority=" + f + ", ").orElse("") +
            optionalConditionLogic().map(f -> "conditionLogic=" + f + ", ").orElse("") +
            optionalResultingDescription().map(f -> "resultingDescription=" + f + ", ").orElse("") +
            optionalActive().map(f -> "active=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalUpdatedAt().map(f -> "updatedAt=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalResultingCategoryId().map(f -> "resultingCategoryId=" + f + ", ").orElse("") +
            optionalResultingFinancialSubscriptionId().map(f -> "resultingFinancialSubscriptionId=" + f + ", ").orElse("") +
            optionalResultingTagsId().map(f -> "resultingTagsId=" + f + ", ").orElse("") +
            optionalConditionsId().map(f -> "conditionsId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
