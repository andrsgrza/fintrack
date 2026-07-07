package com.fintrack.app.domain;

import static com.fintrack.app.domain.BudgetTestSamples.*;
import static com.fintrack.app.domain.FinancialSubscriptionTestSamples.*;
import static com.fintrack.app.domain.FinancialTransactionTestSamples.*;
import static com.fintrack.app.domain.TagTestSamples.*;
import static com.fintrack.app.domain.TransactionRuleTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TagTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Tag.class);
        Tag tag1 = getTagSample1();
        Tag tag2 = new Tag();
        assertThat(tag1).isNotEqualTo(tag2);

        tag2.setId(tag1.getId());
        assertThat(tag1).isEqualTo(tag2);

        tag2 = getTagSample2();
        assertThat(tag1).isNotEqualTo(tag2);
    }

    @Test
    void financialTransactionsTest() {
        Tag tag = getTagRandomSampleGenerator();
        FinancialTransaction financialTransactionBack = getFinancialTransactionRandomSampleGenerator();

        tag.addFinancialTransactions(financialTransactionBack);
        assertThat(tag.getFinancialTransactions()).containsOnly(financialTransactionBack);
        assertThat(financialTransactionBack.getTags()).containsOnly(tag);

        tag.removeFinancialTransactions(financialTransactionBack);
        assertThat(tag.getFinancialTransactions()).doesNotContain(financialTransactionBack);
        assertThat(financialTransactionBack.getTags()).doesNotContain(tag);

        tag.financialTransactions(new HashSet<>(Set.of(financialTransactionBack)));
        assertThat(tag.getFinancialTransactions()).containsOnly(financialTransactionBack);
        assertThat(financialTransactionBack.getTags()).containsOnly(tag);

        tag.setFinancialTransactions(new HashSet<>());
        assertThat(tag.getFinancialTransactions()).doesNotContain(financialTransactionBack);
        assertThat(financialTransactionBack.getTags()).doesNotContain(tag);
    }

    @Test
    void transactionRulesTest() {
        Tag tag = getTagRandomSampleGenerator();
        TransactionRule transactionRuleBack = getTransactionRuleRandomSampleGenerator();

        tag.addTransactionRules(transactionRuleBack);
        assertThat(tag.getTransactionRules()).containsOnly(transactionRuleBack);
        assertThat(transactionRuleBack.getResultingTags()).containsOnly(tag);

        tag.removeTransactionRules(transactionRuleBack);
        assertThat(tag.getTransactionRules()).doesNotContain(transactionRuleBack);
        assertThat(transactionRuleBack.getResultingTags()).doesNotContain(tag);

        tag.transactionRules(new HashSet<>(Set.of(transactionRuleBack)));
        assertThat(tag.getTransactionRules()).containsOnly(transactionRuleBack);
        assertThat(transactionRuleBack.getResultingTags()).containsOnly(tag);

        tag.setTransactionRules(new HashSet<>());
        assertThat(tag.getTransactionRules()).doesNotContain(transactionRuleBack);
        assertThat(transactionRuleBack.getResultingTags()).doesNotContain(tag);
    }

    @Test
    void subscriptionsTest() {
        Tag tag = getTagRandomSampleGenerator();
        FinancialSubscription financialSubscriptionBack = getFinancialSubscriptionRandomSampleGenerator();

        tag.addSubscriptions(financialSubscriptionBack);
        assertThat(tag.getSubscriptions()).containsOnly(financialSubscriptionBack);
        assertThat(financialSubscriptionBack.getTags()).containsOnly(tag);

        tag.removeSubscriptions(financialSubscriptionBack);
        assertThat(tag.getSubscriptions()).doesNotContain(financialSubscriptionBack);
        assertThat(financialSubscriptionBack.getTags()).doesNotContain(tag);

        tag.subscriptions(new HashSet<>(Set.of(financialSubscriptionBack)));
        assertThat(tag.getSubscriptions()).containsOnly(financialSubscriptionBack);
        assertThat(financialSubscriptionBack.getTags()).containsOnly(tag);

        tag.setSubscriptions(new HashSet<>());
        assertThat(tag.getSubscriptions()).doesNotContain(financialSubscriptionBack);
        assertThat(financialSubscriptionBack.getTags()).doesNotContain(tag);
    }

    @Test
    void budgetsTest() {
        Tag tag = getTagRandomSampleGenerator();
        Budget budgetBack = getBudgetRandomSampleGenerator();

        tag.addBudgets(budgetBack);
        assertThat(tag.getBudgets()).containsOnly(budgetBack);
        assertThat(budgetBack.getTags()).containsOnly(tag);

        tag.removeBudgets(budgetBack);
        assertThat(tag.getBudgets()).doesNotContain(budgetBack);
        assertThat(budgetBack.getTags()).doesNotContain(tag);

        tag.budgets(new HashSet<>(Set.of(budgetBack)));
        assertThat(tag.getBudgets()).containsOnly(budgetBack);
        assertThat(budgetBack.getTags()).containsOnly(tag);

        tag.setBudgets(new HashSet<>());
        assertThat(tag.getBudgets()).doesNotContain(budgetBack);
        assertThat(budgetBack.getTags()).doesNotContain(tag);
    }
}
