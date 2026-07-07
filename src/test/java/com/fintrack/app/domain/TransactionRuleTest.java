package com.fintrack.app.domain;

import static com.fintrack.app.domain.CategoryTestSamples.*;
import static com.fintrack.app.domain.FinancialSubscriptionTestSamples.*;
import static com.fintrack.app.domain.TagTestSamples.*;
import static com.fintrack.app.domain.TransactionRuleConditionTestSamples.*;
import static com.fintrack.app.domain.TransactionRuleTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransactionRuleTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(TransactionRule.class);
        TransactionRule transactionRule1 = getTransactionRuleSample1();
        TransactionRule transactionRule2 = new TransactionRule();
        assertThat(transactionRule1).isNotEqualTo(transactionRule2);

        transactionRule2.setId(transactionRule1.getId());
        assertThat(transactionRule1).isEqualTo(transactionRule2);

        transactionRule2 = getTransactionRuleSample2();
        assertThat(transactionRule1).isNotEqualTo(transactionRule2);
    }

    @Test
    void resultingCategoryTest() {
        TransactionRule transactionRule = getTransactionRuleRandomSampleGenerator();
        Category categoryBack = getCategoryRandomSampleGenerator();

        transactionRule.setResultingCategory(categoryBack);
        assertThat(transactionRule.getResultingCategory()).isEqualTo(categoryBack);

        transactionRule.resultingCategory(null);
        assertThat(transactionRule.getResultingCategory()).isNull();
    }

    @Test
    void resultingFinancialSubscriptionTest() {
        TransactionRule transactionRule = getTransactionRuleRandomSampleGenerator();
        FinancialSubscription financialSubscriptionBack = getFinancialSubscriptionRandomSampleGenerator();

        transactionRule.setResultingFinancialSubscription(financialSubscriptionBack);
        assertThat(transactionRule.getResultingFinancialSubscription()).isEqualTo(financialSubscriptionBack);

        transactionRule.resultingFinancialSubscription(null);
        assertThat(transactionRule.getResultingFinancialSubscription()).isNull();
    }

    @Test
    void resultingTagsTest() {
        TransactionRule transactionRule = getTransactionRuleRandomSampleGenerator();
        Tag tagBack = getTagRandomSampleGenerator();

        transactionRule.addResultingTags(tagBack);
        assertThat(transactionRule.getResultingTags()).containsOnly(tagBack);

        transactionRule.removeResultingTags(tagBack);
        assertThat(transactionRule.getResultingTags()).doesNotContain(tagBack);

        transactionRule.resultingTags(new HashSet<>(Set.of(tagBack)));
        assertThat(transactionRule.getResultingTags()).containsOnly(tagBack);

        transactionRule.setResultingTags(new HashSet<>());
        assertThat(transactionRule.getResultingTags()).doesNotContain(tagBack);
    }

    @Test
    void conditionsTest() {
        TransactionRule transactionRule = getTransactionRuleRandomSampleGenerator();
        TransactionRuleCondition transactionRuleConditionBack = getTransactionRuleConditionRandomSampleGenerator();

        transactionRule.addConditions(transactionRuleConditionBack);
        assertThat(transactionRule.getConditions()).containsOnly(transactionRuleConditionBack);
        assertThat(transactionRuleConditionBack.getTransactionRule()).isEqualTo(transactionRule);

        transactionRule.removeConditions(transactionRuleConditionBack);
        assertThat(transactionRule.getConditions()).doesNotContain(transactionRuleConditionBack);
        assertThat(transactionRuleConditionBack.getTransactionRule()).isNull();

        transactionRule.conditions(new HashSet<>(Set.of(transactionRuleConditionBack)));
        assertThat(transactionRule.getConditions()).containsOnly(transactionRuleConditionBack);
        assertThat(transactionRuleConditionBack.getTransactionRule()).isEqualTo(transactionRule);

        transactionRule.setConditions(new HashSet<>());
        assertThat(transactionRule.getConditions()).doesNotContain(transactionRuleConditionBack);
        assertThat(transactionRuleConditionBack.getTransactionRule()).isNull();
    }
}
