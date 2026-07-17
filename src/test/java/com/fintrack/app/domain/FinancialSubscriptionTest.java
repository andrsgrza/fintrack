package com.fintrack.app.domain;

import static com.fintrack.app.domain.CategoryTestSamples.*;
import static com.fintrack.app.domain.FinancialAccountTestSamples.*;
import static com.fintrack.app.domain.FinancialSubscriptionTestSamples.*;
import static com.fintrack.app.domain.FinancialTransactionTestSamples.*;
import static com.fintrack.app.domain.TagTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FinancialSubscriptionTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(FinancialSubscription.class);
        FinancialSubscription financialSubscription1 = getFinancialSubscriptionSample1();
        FinancialSubscription financialSubscription2 = new FinancialSubscription();
        assertThat(financialSubscription1).isNotEqualTo(financialSubscription2);

        financialSubscription2.setId(financialSubscription1.getId());
        assertThat(financialSubscription1).isEqualTo(financialSubscription2);

        financialSubscription2 = getFinancialSubscriptionSample2();
        assertThat(financialSubscription1).isNotEqualTo(financialSubscription2);
    }

    @Test
    void accountTest() {
        FinancialSubscription financialSubscription = getFinancialSubscriptionRandomSampleGenerator();
        FinancialAccount financialAccountBack = getFinancialAccountRandomSampleGenerator();

        financialSubscription.setAccount(financialAccountBack);
        assertThat(financialSubscription.getAccount()).isEqualTo(financialAccountBack);

        financialSubscription.account(null);
        assertThat(financialSubscription.getAccount()).isNull();
    }

    @Test
    void categoryTest() {
        FinancialSubscription financialSubscription = getFinancialSubscriptionRandomSampleGenerator();
        Category categoryBack = getCategoryRandomSampleGenerator();

        financialSubscription.setCategory(categoryBack);
        assertThat(financialSubscription.getCategory()).isEqualTo(categoryBack);

        financialSubscription.category(null);
        assertThat(financialSubscription.getCategory()).isNull();
    }

    @Test
    void tagsTest() {
        FinancialSubscription financialSubscription = getFinancialSubscriptionRandomSampleGenerator();
        Tag tagBack = getTagRandomSampleGenerator();

        financialSubscription.addTags(tagBack);
        assertThat(financialSubscription.getTags()).containsOnly(tagBack);

        financialSubscription.removeTags(tagBack);
        assertThat(financialSubscription.getTags()).doesNotContain(tagBack);

        financialSubscription.tags(new HashSet<>(Set.of(tagBack)));
        assertThat(financialSubscription.getTags()).containsOnly(tagBack);

        financialSubscription.setTags(new HashSet<>());
        assertThat(financialSubscription.getTags()).doesNotContain(tagBack);
    }

    @Test
    void financialTransactionsTest() {
        FinancialSubscription financialSubscription = getFinancialSubscriptionRandomSampleGenerator();
        FinancialTransaction financialTransactionBack = getFinancialTransactionRandomSampleGenerator();

        financialSubscription.addFinancialTransactions(financialTransactionBack);
        assertThat(financialSubscription.getFinancialTransactions()).containsOnly(financialTransactionBack);
        assertThat(financialTransactionBack.getFinancialSubscription()).isEqualTo(financialSubscription);

        financialSubscription.removeFinancialTransactions(financialTransactionBack);
        assertThat(financialSubscription.getFinancialTransactions()).doesNotContain(financialTransactionBack);
        assertThat(financialTransactionBack.getFinancialSubscription()).isNull();

        financialSubscription.financialTransactions(new HashSet<>(Set.of(financialTransactionBack)));
        assertThat(financialSubscription.getFinancialTransactions()).containsOnly(financialTransactionBack);
        assertThat(financialTransactionBack.getFinancialSubscription()).isEqualTo(financialSubscription);

        financialSubscription.setFinancialTransactions(new HashSet<>());
        assertThat(financialSubscription.getFinancialTransactions()).doesNotContain(financialTransactionBack);
        assertThat(financialTransactionBack.getFinancialSubscription()).isNull();
    }
}
