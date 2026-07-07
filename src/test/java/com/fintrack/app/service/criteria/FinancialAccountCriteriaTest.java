package com.fintrack.app.service.criteria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class FinancialAccountCriteriaTest {

    @Test
    void newFinancialAccountCriteriaHasAllFiltersNullTest() {
        var financialAccountCriteria = new FinancialAccountCriteria();
        assertThat(financialAccountCriteria).is(criteriaFiltersAre(Objects::isNull));
    }

    @Test
    void financialAccountCriteriaFluentMethodsCreatesFiltersTest() {
        var financialAccountCriteria = new FinancialAccountCriteria();

        setAllFilters(financialAccountCriteria);

        assertThat(financialAccountCriteria).is(criteriaFiltersAre(Objects::nonNull));
    }

    @Test
    void financialAccountCriteriaCopyCreatesNullFilterTest() {
        var financialAccountCriteria = new FinancialAccountCriteria();
        var copy = financialAccountCriteria.copy();

        assertThat(financialAccountCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::isNull)),
            criteria -> assertThat(criteria).isEqualTo(financialAccountCriteria)
        );
    }

    @Test
    void financialAccountCriteriaCopyDuplicatesEveryExistingFilterTest() {
        var financialAccountCriteria = new FinancialAccountCriteria();
        setAllFilters(financialAccountCriteria);

        var copy = financialAccountCriteria.copy();

        assertThat(financialAccountCriteria).satisfies(
            criteria ->
                assertThat(criteria).is(
                    copyFiltersAre(copy, (a, b) -> (a == null || a instanceof Boolean) ? a == b : (a != b && a.equals(b)))
                ),
            criteria -> assertThat(criteria).isEqualTo(copy),
            criteria -> assertThat(criteria).hasSameHashCodeAs(copy)
        );

        assertThat(copy).satisfies(
            criteria -> assertThat(criteria).is(criteriaFiltersAre(Objects::nonNull)),
            criteria -> assertThat(criteria).isEqualTo(financialAccountCriteria)
        );
    }

    @Test
    void toStringVerifier() {
        var financialAccountCriteria = new FinancialAccountCriteria();

        assertThat(financialAccountCriteria).hasToString("FinancialAccountCriteria{}");
    }

    private static void setAllFilters(FinancialAccountCriteria financialAccountCriteria) {
        financialAccountCriteria.id();
        financialAccountCriteria.name();
        financialAccountCriteria.institutionName();
        financialAccountCriteria.accountType();
        financialAccountCriteria.currency();
        financialAccountCriteria.initialBalance();
        financialAccountCriteria.initialBalanceDate();
        financialAccountCriteria.lastFourDigits();
        financialAccountCriteria.description();
        financialAccountCriteria.color();
        financialAccountCriteria.icon();
        financialAccountCriteria.active();
        financialAccountCriteria.includeInNetWorth();
        financialAccountCriteria.createdAt();
        financialAccountCriteria.updatedAt();
        financialAccountCriteria.userId();
        financialAccountCriteria.creditAccountDetailsId();
        financialAccountCriteria.financialTransactionsId();
        financialAccountCriteria.subscriptionsId();
        financialAccountCriteria.budgetsId();
        financialAccountCriteria.transactionIngestionsId();
        financialAccountCriteria.apiAccessTokensId();
        financialAccountCriteria.distinct();
    }

    private static Condition<FinancialAccountCriteria> criteriaFiltersAre(Function<Object, Boolean> condition) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId()) &&
                condition.apply(criteria.getName()) &&
                condition.apply(criteria.getInstitutionName()) &&
                condition.apply(criteria.getAccountType()) &&
                condition.apply(criteria.getCurrency()) &&
                condition.apply(criteria.getInitialBalance()) &&
                condition.apply(criteria.getInitialBalanceDate()) &&
                condition.apply(criteria.getLastFourDigits()) &&
                condition.apply(criteria.getDescription()) &&
                condition.apply(criteria.getColor()) &&
                condition.apply(criteria.getIcon()) &&
                condition.apply(criteria.getActive()) &&
                condition.apply(criteria.getIncludeInNetWorth()) &&
                condition.apply(criteria.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt()) &&
                condition.apply(criteria.getUserId()) &&
                condition.apply(criteria.getCreditAccountDetailsId()) &&
                condition.apply(criteria.getFinancialTransactionsId()) &&
                condition.apply(criteria.getSubscriptionsId()) &&
                condition.apply(criteria.getBudgetsId()) &&
                condition.apply(criteria.getTransactionIngestionsId()) &&
                condition.apply(criteria.getApiAccessTokensId()) &&
                condition.apply(criteria.getDistinct()),
            "every filter matches"
        );
    }

    private static Condition<FinancialAccountCriteria> copyFiltersAre(
        FinancialAccountCriteria copy,
        BiFunction<Object, Object, Boolean> condition
    ) {
        return new Condition<>(
            criteria ->
                condition.apply(criteria.getId(), copy.getId()) &&
                condition.apply(criteria.getName(), copy.getName()) &&
                condition.apply(criteria.getInstitutionName(), copy.getInstitutionName()) &&
                condition.apply(criteria.getAccountType(), copy.getAccountType()) &&
                condition.apply(criteria.getCurrency(), copy.getCurrency()) &&
                condition.apply(criteria.getInitialBalance(), copy.getInitialBalance()) &&
                condition.apply(criteria.getInitialBalanceDate(), copy.getInitialBalanceDate()) &&
                condition.apply(criteria.getLastFourDigits(), copy.getLastFourDigits()) &&
                condition.apply(criteria.getDescription(), copy.getDescription()) &&
                condition.apply(criteria.getColor(), copy.getColor()) &&
                condition.apply(criteria.getIcon(), copy.getIcon()) &&
                condition.apply(criteria.getActive(), copy.getActive()) &&
                condition.apply(criteria.getIncludeInNetWorth(), copy.getIncludeInNetWorth()) &&
                condition.apply(criteria.getCreatedAt(), copy.getCreatedAt()) &&
                condition.apply(criteria.getUpdatedAt(), copy.getUpdatedAt()) &&
                condition.apply(criteria.getUserId(), copy.getUserId()) &&
                condition.apply(criteria.getCreditAccountDetailsId(), copy.getCreditAccountDetailsId()) &&
                condition.apply(criteria.getFinancialTransactionsId(), copy.getFinancialTransactionsId()) &&
                condition.apply(criteria.getSubscriptionsId(), copy.getSubscriptionsId()) &&
                condition.apply(criteria.getBudgetsId(), copy.getBudgetsId()) &&
                condition.apply(criteria.getTransactionIngestionsId(), copy.getTransactionIngestionsId()) &&
                condition.apply(criteria.getApiAccessTokensId(), copy.getApiAccessTokensId()) &&
                condition.apply(criteria.getDistinct(), copy.getDistinct()),
            "every filter matches"
        );
    }
}
