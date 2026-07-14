package com.fintrack.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class FinancialSubscriptionCriteriaTest {

    @Test
    void newFinancialSubscriptionCriteriaHasAllFiltersNullTest() {
        var financialSubscriptionCriteria = new FinancialSubscriptionCriteria();
        assertThat(financialSubscriptionCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void financialSubscriptionCriteriaFluentMethodsCreatesFiltersTest() {
        var financialSubscriptionCriteria = new FinancialSubscriptionCriteria();

        setAllFilters(financialSubscriptionCriteria);

        assertThat(financialSubscriptionCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void financialSubscriptionCriteriaCopyCreatesNullFilterTest() {
        var financialSubscriptionCriteria = new FinancialSubscriptionCriteria();
        var copy = financialSubscriptionCriteria.copy();

        assertThat(financialSubscriptionCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(financialSubscriptionCriteria)
        );
    }

    @Test
    void financialSubscriptionCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var financialSubscriptionCriteria = new FinancialSubscriptionCriteria();
        setAllFilters(financialSubscriptionCriteria);

        var copy = financialSubscriptionCriteria.copy();

        assertThat(financialSubscriptionCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(financialSubscriptionCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var financialSubscriptionCriteria = new FinancialSubscriptionCriteria();

        assertThat(financialSubscriptionCriteria).hasToString("FinancialSubscriptionCriteria{}");
    }

    private static void setAllFilters(FinancialSubscriptionCriteria financialSubscriptionCriteria) {
        financialSubscriptionCriteria.id();
        financialSubscriptionCriteria.name();
        financialSubscriptionCriteria.description();
        financialSubscriptionCriteria.status();
        financialSubscriptionCriteria.expectedAmount();
        financialSubscriptionCriteria.amountTolerancePercentage();
        financialSubscriptionCriteria.currency();
        financialSubscriptionCriteria.recurrenceUnit();
        financialSubscriptionCriteria.intervalCount();
        financialSubscriptionCriteria.startDate();
        financialSubscriptionCriteria.nextExpectedDate();
        financialSubscriptionCriteria.endDate();
        financialSubscriptionCriteria.automaticPayment();
        financialSubscriptionCriteria.notes();
        financialSubscriptionCriteria.createdAt();
        financialSubscriptionCriteria.updatedAt();
        financialSubscriptionCriteria.userId();
        financialSubscriptionCriteria.accountId();
        financialSubscriptionCriteria.categoryId();
        financialSubscriptionCriteria.tagsId();
        financialSubscriptionCriteria.financialTransactionsId();
        financialSubscriptionCriteria.distinct();
    }

    private static Condition<FinancialSubscriptionCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getName()) &&
                condition.apply(criteria.getDescription()) &&
                condition.apply(criteria.getStatus()) &&
                condition.apply(criteria.getExpectedAmount()) &&
                condition.apply(criteria.getAmountTolerancePercentage()) &&
                condition.apply(criteria.getCurrency()) &&
                condition.apply(criteria.getRecurrenceUnit()) &&
                condition.apply(criteria.getIntervalCount()) &&
                condition.apply(criteria.getStartDate()) &&
                condition.apply(criteria.getNextExpectedDate()) &&
                condition.apply(criteria.getEndDate()) &&
                condition.apply(criteria.getAutomaticPayment()) &&
                condition.apply(criteria.getNotes()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt()) &&
                condition.apply(criteria.getUserId()) &&
                condition.apply(criteria.getAccountId()) &&
                condition.apply(criteria.getCategoryId()) &&
                condition.apply(criteria.getTagsId()) &&
                condition.apply(criteria.getFinancialTransactionsId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<FinancialSubscriptionCriteria> copyFiltersAre(
        FinancialSubscriptionCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getName(), copy.getName()) &&
                condition.apply(criteria.getDescription(), copy.getDescription()) &&
                condition.apply(criteria.getStatus(), copy.getStatus()) &&
                condition.apply(criteria.getExpectedAmount(), copy.getExpectedAmount()) &&
                condition.apply(criteria.getAmountTolerancePercentage(), copy.getAmountTolerancePercentage()) &&
                condition.apply(criteria.getCurrency(), copy.getCurrency()) &&
                condition.apply(criteria.getRecurrenceUnit(), copy.getRecurrenceUnit()) &&
                condition.apply(criteria.getIntervalCount(), copy.getIntervalCount()) &&
                condition.apply(criteria.getStartDate(), copy.getStartDate()) &&
                condition.apply(criteria.getNextExpectedDate(), copy.getNextExpectedDate()) &&
                condition.apply(criteria.getEndDate(), copy.getEndDate()) &&
                condition.apply(criteria.getAutomaticPayment(), copy.getAutomaticPayment()) &&
                condition.apply(criteria.getNotes(), copy.getNotes()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt(), copy.getUpdatedAt()) &&
                condition.apply(criteria.getUserId(), copy.getUserId()) &&
                condition.apply(criteria.getAccountId(), copy.getAccountId()) &&
                condition.apply(criteria.getCategoryId(), copy.getCategoryId()) &&
                condition.apply(criteria.getTagsId(), copy.getTagsId()) &&
                condition.apply(criteria.getFinancialTransactionsId(), copy.getFinancialTransactionsId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
