package com.fintrack.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class TagCriteriaTest {

    @Test
    void newTagCriteriaHasAllFiltersNullTest() {
        var tagCriteria = new TagCriteria();
        assertThat(tagCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void tagCriteriaFluentMethodsCreatesFiltersTest() {
        var tagCriteria = new TagCriteria();

        setAllFilters(tagCriteria);

        assertThat(tagCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void tagCriteriaCopyCreatesNullFilterTest() {
        var tagCriteria = new TagCriteria();
        var copy = tagCriteria.copy();

        assertThat(tagCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(tagCriteria)
        );
    }

    @Test
    void tagCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var tagCriteria = new TagCriteria();
        setAllFilters(tagCriteria);

        var copy = tagCriteria.copy();

        assertThat(tagCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(tagCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var tagCriteria = new TagCriteria();

        assertThat(tagCriteria).hasToString("TagCriteria{}");
    }

    private static void setAllFilters(TagCriteria tagCriteria) {
        tagCriteria.id();
        tagCriteria.name();
        tagCriteria.description();
        tagCriteria.color();
        tagCriteria.active();
        tagCriteria.createdAt();
        tagCriteria.updatedAt();
        tagCriteria.userId();
        tagCriteria.financialTransactionsId();
        tagCriteria.transactionRulesId();
        tagCriteria.subscriptionsId();
        tagCriteria.budgetsId();
        tagCriteria.distinct();
    }

    private static Condition<TagCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getName()) &&
                condition.apply(criteria.getDescription()) &&
                condition.apply(criteria.getColor()) &&
                condition.apply(criteria.getActive()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt()) &&
                condition.apply(criteria.getUserId()) &&
                condition.apply(criteria.getFinancialTransactionsId()) &&
                condition.apply(criteria.getTransactionRulesId()) &&
                condition.apply(criteria.getSubscriptionsId()) &&
                condition.apply(criteria.getBudgetsId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<TagCriteria> copyFiltersAre(TagCriteria copy, BiFunction<Object, Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getName(), copy.getName()) &&
                condition.apply(criteria.getDescription(), copy.getDescription()) &&
                condition.apply(criteria.getColor(), copy.getColor()) &&
                condition.apply(criteria.getActive(), copy.getActive()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt(), copy.getUpdatedAt()) &&
                condition.apply(criteria.getUserId(), copy.getUserId()) &&
                condition.apply(criteria.getFinancialTransactionsId(), copy.getFinancialTransactionsId()) &&
                condition.apply(criteria.getTransactionRulesId(), copy.getTransactionRulesId()) &&
                condition.apply(criteria.getSubscriptionsId(), copy.getSubscriptionsId()) &&
                condition.apply(criteria.getBudgetsId(), copy.getBudgetsId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
