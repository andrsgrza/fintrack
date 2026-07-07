package com.fintrack.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class BudgetCriteriaTest {

    @Test
    void newBudgetCriteriaHasAllFiltersNullTest() {
        var budgetCriteria = new BudgetCriteria();
        assertThat(budgetCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void budgetCriteriaFluentMethodsCreatesFiltersTest() {
        var budgetCriteria = new BudgetCriteria();

        setAllFilters(budgetCriteria);

        assertThat(budgetCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void budgetCriteriaCopyCreatesNullFilterTest() {
        var budgetCriteria = new BudgetCriteria();
        var copy = budgetCriteria.copy();

        assertThat(budgetCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(budgetCriteria)
        );
    }

    @Test
    void budgetCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var budgetCriteria = new BudgetCriteria();
        setAllFilters(budgetCriteria);

        var copy = budgetCriteria.copy();

        assertThat(budgetCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(budgetCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var budgetCriteria = new BudgetCriteria();

        assertThat(budgetCriteria).hasToString("BudgetCriteria{}");
    }

    private static void setAllFilters(BudgetCriteria budgetCriteria) {
        budgetCriteria.id();
        budgetCriteria.name();
        budgetCriteria.amount();
        budgetCriteria.currency();
        budgetCriteria.period();
        budgetCriteria.startDate();
        budgetCriteria.endDate();
        budgetCriteria.status();
        budgetCriteria.tagMatchMode();
        budgetCriteria.warningPercentage();
        budgetCriteria.createdAt();
        budgetCriteria.updatedAt();
        budgetCriteria.userId();
        budgetCriteria.accountsId();
        budgetCriteria.categoriesId();
        budgetCriteria.tagsId();
        budgetCriteria.distinct();
    }

    private static Condition<BudgetCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getName()) &&
                condition.apply(criteria.getAmount()) &&
                condition.apply(criteria.getCurrency()) &&
                condition.apply(criteria.getPeriod()) &&
                condition.apply(criteria.getStartDate()) &&
                condition.apply(criteria.getEndDate()) &&
                condition.apply(criteria.getStatus()) &&
                condition.apply(criteria.getTagMatchMode()) &&
                condition.apply(criteria.getWarningPercentage()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt()) &&
                condition.apply(criteria.getUserId()) &&
                condition.apply(criteria.getAccountsId()) &&
                condition.apply(criteria.getCategoriesId()) &&
                condition.apply(criteria.getTagsId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<BudgetCriteria> copyFiltersAre(BudgetCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getName(), copy.getName()) &&
                condition.apply(criteria.getAmount(), copy.getAmount()) &&
                condition.apply(criteria.getCurrency(), copy.getCurrency()) &&
                condition.apply(criteria.getPeriod(), copy.getPeriod()) &&
                condition.apply(criteria.getStartDate(), copy.getStartDate()) &&
                condition.apply(criteria.getEndDate(), copy.getEndDate()) &&
                condition.apply(criteria.getStatus(), copy.getStatus()) &&
                condition.apply(criteria.getTagMatchMode(), copy.getTagMatchMode()) &&
                condition.apply(criteria.getWarningPercentage(), copy.getWarningPercentage()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt(), copy.getUpdatedAt()) &&
                condition.apply(criteria.getUserId(), copy.getUserId()) &&
                condition.apply(criteria.getAccountsId(), copy.getAccountsId()) &&
                condition.apply(criteria.getCategoriesId(), copy.getCategoriesId()) &&
                condition.apply(criteria.getTagsId(), copy.getTagsId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
