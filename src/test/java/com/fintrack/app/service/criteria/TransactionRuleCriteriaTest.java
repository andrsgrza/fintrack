package com.fintrack.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class TransactionRuleCriteriaTest {

    @Test
    void newTransactionRuleCriteriaHasAllFiltersNullTest() {
        var transactionRuleCriteria = new TransactionRuleCriteria();
        assertThat(transactionRuleCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void transactionRuleCriteriaFluentMethodsCreatesFiltersTest() {
        var transactionRuleCriteria = new TransactionRuleCriteria();

        setAllFilters(transactionRuleCriteria);

        assertThat(transactionRuleCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void transactionRuleCriteriaCopyCreatesNullFilterTest() {
        var transactionRuleCriteria = new TransactionRuleCriteria();
        var copy = transactionRuleCriteria.copy();

        assertThat(transactionRuleCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(transactionRuleCriteria)
        );
    }

    @Test
    void transactionRuleCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var transactionRuleCriteria = new TransactionRuleCriteria();
        setAllFilters(transactionRuleCriteria);

        var copy = transactionRuleCriteria.copy();

        assertThat(transactionRuleCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(transactionRuleCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var transactionRuleCriteria = new TransactionRuleCriteria();

        assertThat(transactionRuleCriteria).hasToString("TransactionRuleCriteria{}");
    }

    private static void setAllFilters(TransactionRuleCriteria transactionRuleCriteria) {
        transactionRuleCriteria.id();
        transactionRuleCriteria.name();
        transactionRuleCriteria.description();
        transactionRuleCriteria.priority();
        transactionRuleCriteria.conditionLogic();
        transactionRuleCriteria.resultingDescription();
        transactionRuleCriteria.active();
        transactionRuleCriteria.createdAt();
        transactionRuleCriteria.updatedAt();
        transactionRuleCriteria.userId();
        transactionRuleCriteria.resultingCategoryId();
        transactionRuleCriteria.resultingFinancialSubscriptionId();
        transactionRuleCriteria.resultingTagsId();
        transactionRuleCriteria.conditionsId();
        transactionRuleCriteria.distinct();
    }

    private static Condition<TransactionRuleCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getName()) &&
                condition.apply(criteria.getDescription()) &&
                condition.apply(criteria.getPriority()) &&
                condition.apply(criteria.getConditionLogic()) &&
                condition.apply(criteria.getResultingDescription()) &&
                condition.apply(criteria.getActive()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt()) &&
                condition.apply(criteria.getUserId()) &&
                condition.apply(criteria.getResultingCategoryId()) &&
                condition.apply(criteria.getResultingFinancialSubscriptionId()) &&
                condition.apply(criteria.getResultingTagsId()) &&
                condition.apply(criteria.getConditionsId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<TransactionRuleCriteria> copyFiltersAre(
        TransactionRuleCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getName(), copy.getName()) &&
                condition.apply(criteria.getDescription(), copy.getDescription()) &&
                condition.apply(criteria.getPriority(), copy.getPriority()) &&
                condition.apply(criteria.getConditionLogic(), copy.getConditionLogic()) &&
                condition.apply(criteria.getResultingDescription(), copy.getResultingDescription()) &&
                condition.apply(criteria.getActive(), copy.getActive()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt(), copy.getUpdatedAt()) &&
                condition.apply(criteria.getUserId(), copy.getUserId()) &&
                condition.apply(criteria.getResultingCategoryId(), copy.getResultingCategoryId()) &&
                condition.apply(criteria.getResultingFinancialSubscriptionId(), copy.getResultingFinancialSubscriptionId()) &&
                condition.apply(criteria.getResultingTagsId(), copy.getResultingTagsId()) &&
                condition.apply(criteria.getConditionsId(), copy.getConditionsId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
