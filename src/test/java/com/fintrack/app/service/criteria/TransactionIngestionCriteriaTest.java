package com.fintrack.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class TransactionIngestionCriteriaTest {

    @Test
    void newTransactionIngestionCriteriaHasAllFiltersNullTest() {
        var transactionIngestionCriteria = new TransactionIngestionCriteria();
        assertThat(transactionIngestionCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void transactionIngestionCriteriaFluentMethodsCreatesFiltersTest() {
        var transactionIngestionCriteria = new TransactionIngestionCriteria();

        setAllFilters(transactionIngestionCriteria);

        assertThat(transactionIngestionCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void transactionIngestionCriteriaCopyCreatesNullFilterTest() {
        var transactionIngestionCriteria = new TransactionIngestionCriteria();
        var copy = transactionIngestionCriteria.copy();

        assertThat(transactionIngestionCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(transactionIngestionCriteria)
        );
    }

    @Test
    void transactionIngestionCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var transactionIngestionCriteria = new TransactionIngestionCriteria();
        setAllFilters(transactionIngestionCriteria);

        var copy = transactionIngestionCriteria.copy();

        assertThat(transactionIngestionCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(transactionIngestionCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var transactionIngestionCriteria = new TransactionIngestionCriteria();

        assertThat(transactionIngestionCriteria).hasToString("TransactionIngestionCriteria{}");
    }

    private static void setAllFilters(TransactionIngestionCriteria transactionIngestionCriteria) {
        transactionIngestionCriteria.id();
        transactionIngestionCriteria.ingestionType();
        transactionIngestionCriteria.status();
        transactionIngestionCriteria.sourceLabel();
        transactionIngestionCriteria.startedAt();
        transactionIngestionCriteria.completedAt();
        transactionIngestionCriteria.recordsReceived();
        transactionIngestionCriteria.recordsCreated();
        transactionIngestionCriteria.recordsSkipped();
        transactionIngestionCriteria.recordsRejected();
        transactionIngestionCriteria.errorMessage();
        transactionIngestionCriteria.createdAt();
        transactionIngestionCriteria.accountsId();
        transactionIngestionCriteria.fileIngestionId();
        transactionIngestionCriteria.apiIngestionId();
        transactionIngestionCriteria.financialTransactionsId();
        transactionIngestionCriteria.recordsId();
        transactionIngestionCriteria.distinct();
    }

    private static Condition<TransactionIngestionCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getIngestionType()) &&
                condition.apply(criteria.getStatus()) &&
                condition.apply(criteria.getSourceLabel()) &&
                condition.apply(criteria.getStartedAt()) &&
                condition.apply(criteria.getCompletedAt()) &&
                condition.apply(criteria.getRecordsReceived()) &&
                condition.apply(criteria.getRecordsCreated()) &&
                condition.apply(criteria.getRecordsSkipped()) &&
                condition.apply(criteria.getRecordsRejected()) &&
                condition.apply(criteria.getErrorMessage()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getAccountsId()) &&
                condition.apply(criteria.getFileIngestionId()) &&
                condition.apply(criteria.getApiIngestionId()) &&
                condition.apply(criteria.getFinancialTransactionsId()) &&
                condition.apply(criteria.getRecordsId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<TransactionIngestionCriteria> copyFiltersAre(
        TransactionIngestionCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getIngestionType(), copy.getIngestionType()) &&
                condition.apply(criteria.getStatus(), copy.getStatus()) &&
                condition.apply(criteria.getSourceLabel(), copy.getSourceLabel()) &&
                condition.apply(criteria.getStartedAt(), copy.getStartedAt()) &&
                condition.apply(criteria.getCompletedAt(), copy.getCompletedAt()) &&
                condition.apply(criteria.getRecordsReceived(), copy.getRecordsReceived()) &&
                condition.apply(criteria.getRecordsCreated(), copy.getRecordsCreated()) &&
                condition.apply(criteria.getRecordsSkipped(), copy.getRecordsSkipped()) &&
                condition.apply(criteria.getRecordsRejected(), copy.getRecordsRejected()) &&
                condition.apply(criteria.getErrorMessage(), copy.getErrorMessage()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getAccountsId(), copy.getAccountsId()) &&
                condition.apply(criteria.getFileIngestionId(), copy.getFileIngestionId()) &&
                condition.apply(criteria.getApiIngestionId(), copy.getApiIngestionId()) &&
                condition.apply(criteria.getFinancialTransactionsId(), copy.getFinancialTransactionsId()) &&
                condition.apply(criteria.getRecordsId(), copy.getRecordsId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
