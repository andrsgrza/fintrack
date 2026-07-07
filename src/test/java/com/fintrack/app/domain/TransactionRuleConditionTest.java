package com.fintrack.app.domain;

import static com.fintrack.app.domain.TransactionRuleConditionTestSamples.*;
import static com.fintrack.app.domain.TransactionRuleTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class TransactionRuleConditionTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(TransactionRuleCondition.class);
        TransactionRuleCondition transactionRuleCondition1 = getTransactionRuleConditionSample1();
        TransactionRuleCondition transactionRuleCondition2 = new TransactionRuleCondition();
        assertThat(transactionRuleCondition1).isNotEqualTo(transactionRuleCondition2);

        transactionRuleCondition2.setId(transactionRuleCondition1.getId());
        assertThat(transactionRuleCondition1).isEqualTo(transactionRuleCondition2);

        transactionRuleCondition2 = getTransactionRuleConditionSample2();
        assertThat(transactionRuleCondition1).isNotEqualTo(transactionRuleCondition2);
    }

    @Test
    void transactionRuleTest() {
        TransactionRuleCondition transactionRuleCondition = getTransactionRuleConditionRandomSampleGenerator();
        TransactionRule transactionRuleBack = getTransactionRuleRandomSampleGenerator();

        transactionRuleCondition.setTransactionRule(transactionRuleBack);
        assertThat(transactionRuleCondition.getTransactionRule()).isEqualTo(transactionRuleBack);

        transactionRuleCondition.transactionRule(null);
        assertThat(transactionRuleCondition.getTransactionRule()).isNull();
    }
}
