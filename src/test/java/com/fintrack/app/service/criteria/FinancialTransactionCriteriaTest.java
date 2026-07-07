package com.fintrack.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class FinancialTransactionCriteriaTest {

    @Test
    void newFinancialTransactionCriteriaHasAllFiltersNullTest() {
        var financialTransactionCriteria = new FinancialTransactionCriteria();
        assertThat(financialTransactionCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void financialTransactionCriteriaFluentMethodsCreatesFiltersTest() {
        var financialTransactionCriteria = new FinancialTransactionCriteria();

        setAllFilters(financialTransactionCriteria);

        assertThat(financialTransactionCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void financialTransactionCriteriaCopyCreatesNullFilterTest() {
        var financialTransactionCriteria = new FinancialTransactionCriteria();
        var copy = financialTransactionCriteria.copy();

        assertThat(financialTransactionCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(financialTransactionCriteria)
        );
    }

    @Test
    void financialTransactionCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var financialTransactionCriteria = new FinancialTransactionCriteria();
        setAllFilters(financialTransactionCriteria);

        var copy = financialTransactionCriteria.copy();

        assertThat(financialTransactionCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(financialTransactionCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var financialTransactionCriteria = new FinancialTransactionCriteria();

        assertThat(financialTransactionCriteria).hasToString("FinancialTransactionCriteria{}");
    }

    private static void setAllFilters(FinancialTransactionCriteria financialTransactionCriteria) {
        financialTransactionCriteria.id();
        financialTransactionCriteria.transactionDate();
        financialTransactionCriteria.postingDate();
        financialTransactionCriteria.description();
        financialTransactionCriteria.amount();
        financialTransactionCriteria.flow();
        financialTransactionCriteria.origin();
        financialTransactionCriteria.externalReference();
        financialTransactionCriteria.notes();
        financialTransactionCriteria.createdAt();
        financialTransactionCriteria.updatedAt();
        financialTransactionCriteria.accountId();
        financialTransactionCriteria.categoryId();
        financialTransactionCriteria.financialSubscriptionId();
        financialTransactionCriteria.transactionIngestionId();
        financialTransactionCriteria.tagsId();
        financialTransactionCriteria.outgoingInternalTransferId();
        financialTransactionCriteria.incomingInternalTransferId();
        financialTransactionCriteria.ingestionRecordId();
        financialTransactionCriteria.distinct();
    }

    private static Condition<FinancialTransactionCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getTransactionDate()) &&
                condition.apply(criteria.getPostingDate()) &&
                condition.apply(criteria.getDescription()) &&
                condition.apply(criteria.getAmount()) &&
                condition.apply(criteria.getFlow()) &&
                condition.apply(criteria.getOrigin()) &&
                condition.apply(criteria.getExternalReference()) &&
                condition.apply(criteria.getNotes()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt()) &&
                condition.apply(criteria.getAccountId()) &&
                condition.apply(criteria.getCategoryId()) &&
                condition.apply(criteria.getFinancialSubscriptionId()) &&
                condition.apply(criteria.getTransactionIngestionId()) &&
                condition.apply(criteria.getTagsId()) &&
                condition.apply(criteria.getOutgoingInternalTransferId()) &&
                condition.apply(criteria.getIncomingInternalTransferId()) &&
                condition.apply(criteria.getIngestionRecordId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<FinancialTransactionCriteria> copyFiltersAre(
        FinancialTransactionCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getTransactionDate(), copy.getTransactionDate()) &&
                condition.apply(criteria.getPostingDate(), copy.getPostingDate()) &&
                condition.apply(criteria.getDescription(), copy.getDescription()) &&
                condition.apply(criteria.getAmount(), copy.getAmount()) &&
                condition.apply(criteria.getFlow(), copy.getFlow()) &&
                condition.apply(criteria.getOrigin(), copy.getOrigin()) &&
                condition.apply(criteria.getExternalReference(), copy.getExternalReference()) &&
                condition.apply(criteria.getNotes(), copy.getNotes()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt(), copy.getUpdatedAt()) &&
                condition.apply(criteria.getAccountId(), copy.getAccountId()) &&
                condition.apply(criteria.getCategoryId(), copy.getCategoryId()) &&
                condition.apply(criteria.getFinancialSubscriptionId(), copy.getFinancialSubscriptionId()) &&
                condition.apply(criteria.getTransactionIngestionId(), copy.getTransactionIngestionId()) &&
                condition.apply(criteria.getTagsId(), copy.getTagsId()) &&
                condition.apply(criteria.getOutgoingInternalTransferId(), copy.getOutgoingInternalTransferId()) &&
                condition.apply(criteria.getIncomingInternalTransferId(), copy.getIncomingInternalTransferId()) &&
                condition.apply(criteria.getIngestionRecordId(), copy.getIngestionRecordId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
