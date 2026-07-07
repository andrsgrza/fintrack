package com.fintrack.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class IngestionRecordCriteriaTest {

    @Test
    void newIngestionRecordCriteriaHasAllFiltersNullTest() {
        var ingestionRecordCriteria = new IngestionRecordCriteria();
        assertThat(ingestionRecordCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void ingestionRecordCriteriaFluentMethodsCreatesFiltersTest() {
        var ingestionRecordCriteria = new IngestionRecordCriteria();

        setAllFilters(ingestionRecordCriteria);

        assertThat(ingestionRecordCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void ingestionRecordCriteriaCopyCreatesNullFilterTest() {
        var ingestionRecordCriteria = new IngestionRecordCriteria();
        var copy = ingestionRecordCriteria.copy();

        assertThat(ingestionRecordCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(ingestionRecordCriteria)
        );
    }

    @Test
    void ingestionRecordCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var ingestionRecordCriteria = new IngestionRecordCriteria();
        setAllFilters(ingestionRecordCriteria);

        var copy = ingestionRecordCriteria.copy();

        assertThat(ingestionRecordCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(ingestionRecordCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var ingestionRecordCriteria = new IngestionRecordCriteria();

        assertThat(ingestionRecordCriteria).hasToString("IngestionRecordCriteria{}");
    }

    private static void setAllFilters(IngestionRecordCriteria ingestionRecordCriteria) {
        ingestionRecordCriteria.id();
        ingestionRecordCriteria.recordIndex();
        ingestionRecordCriteria.externalRecordId();
        ingestionRecordCriteria.status();
        ingestionRecordCriteria.errorCode();
        ingestionRecordCriteria.errorMessage();
        ingestionRecordCriteria.createdAt();
        ingestionRecordCriteria.financialTransactionId();
        ingestionRecordCriteria.transactionIngestionId();
        ingestionRecordCriteria.distinct();
    }

    private static Condition<IngestionRecordCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getRecordIndex()) &&
                condition.apply(criteria.getExternalRecordId()) &&
                condition.apply(criteria.getStatus()) &&
                condition.apply(criteria.getErrorCode()) &&
                condition.apply(criteria.getErrorMessage()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getFinancialTransactionId()) &&
                condition.apply(criteria.getTransactionIngestionId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<IngestionRecordCriteria> copyFiltersAre(
        IngestionRecordCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getRecordIndex(), copy.getRecordIndex()) &&
                condition.apply(criteria.getExternalRecordId(), copy.getExternalRecordId()) &&
                condition.apply(criteria.getStatus(), copy.getStatus()) &&
                condition.apply(criteria.getErrorCode(), copy.getErrorCode()) &&
                condition.apply(criteria.getErrorMessage(), copy.getErrorMessage()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getFinancialTransactionId(), copy.getFinancialTransactionId()) &&
                condition.apply(criteria.getTransactionIngestionId(), copy.getTransactionIngestionId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
