package com.fintrack.app.domain;

import static com.fintrack.app.domain.ApiAccessTokenTestSamples.*;
import static com.fintrack.app.domain.BudgetTestSamples.*;
import static com.fintrack.app.domain.CreditAccountDetailsTestSamples.*;
import static com.fintrack.app.domain.FinancialAccountTestSamples.*;
import static com.fintrack.app.domain.FinancialSubscriptionTestSamples.*;
import static com.fintrack.app.domain.FinancialTransactionTestSamples.*;
import static com.fintrack.app.domain.TransactionIngestionTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FinancialAccountTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(FinancialAccount.class);
        FinancialAccount financialAccount1 = getFinancialAccountSample1();
        FinancialAccount financialAccount2 = new FinancialAccount();
        assertThat(financialAccount1).isNotEqualTo(financialAccount2);

        financialAccount2.setId(financialAccount1.getId());
        assertThat(financialAccount1).isEqualTo(financialAccount2);

        financialAccount2 = getFinancialAccountSample2();
        assertThat(financialAccount1).isNotEqualTo(financialAccount2);
    }

    @Test
    void creditAccountDetailsTest() {
        FinancialAccount financialAccount = getFinancialAccountRandomSampleGenerator();
        CreditAccountDetails creditAccountDetailsBack = getCreditAccountDetailsRandomSampleGenerator();

        financialAccount.setCreditAccountDetails(creditAccountDetailsBack);
        assertThat(financialAccount.getCreditAccountDetails()).isEqualTo(creditAccountDetailsBack);
        assertThat(creditAccountDetailsBack.getAccount()).isEqualTo(financialAccount);

        financialAccount.creditAccountDetails(null);
        assertThat(financialAccount.getCreditAccountDetails()).isNull();
        assertThat(creditAccountDetailsBack.getAccount()).isNull();
    }

    @Test
    void financialTransactionsTest() {
        FinancialAccount financialAccount = getFinancialAccountRandomSampleGenerator();
        FinancialTransaction financialTransactionBack = getFinancialTransactionRandomSampleGenerator();

        financialAccount.addFinancialTransactions(financialTransactionBack);
        assertThat(financialAccount.getFinancialTransactions()).containsOnly(financialTransactionBack);
        assertThat(financialTransactionBack.getAccount()).isEqualTo(financialAccount);

        financialAccount.removeFinancialTransactions(financialTransactionBack);
        assertThat(financialAccount.getFinancialTransactions()).doesNotContain(financialTransactionBack);
        assertThat(financialTransactionBack.getAccount()).isNull();

        financialAccount.financialTransactions(new HashSet<>(Set.of(financialTransactionBack)));
        assertThat(financialAccount.getFinancialTransactions()).containsOnly(financialTransactionBack);
        assertThat(financialTransactionBack.getAccount()).isEqualTo(financialAccount);

        financialAccount.setFinancialTransactions(new HashSet<>());
        assertThat(financialAccount.getFinancialTransactions()).doesNotContain(financialTransactionBack);
        assertThat(financialTransactionBack.getAccount()).isNull();
    }

    @Test
    void subscriptionsTest() {
        FinancialAccount financialAccount = getFinancialAccountRandomSampleGenerator();
        FinancialSubscription financialSubscriptionBack = getFinancialSubscriptionRandomSampleGenerator();

        financialAccount.addSubscriptions(financialSubscriptionBack);
        assertThat(financialAccount.getSubscriptions()).containsOnly(financialSubscriptionBack);
        assertThat(financialSubscriptionBack.getAccount()).isEqualTo(financialAccount);

        financialAccount.removeSubscriptions(financialSubscriptionBack);
        assertThat(financialAccount.getSubscriptions()).doesNotContain(financialSubscriptionBack);
        assertThat(financialSubscriptionBack.getAccount()).isNull();

        financialAccount.subscriptions(new HashSet<>(Set.of(financialSubscriptionBack)));
        assertThat(financialAccount.getSubscriptions()).containsOnly(financialSubscriptionBack);
        assertThat(financialSubscriptionBack.getAccount()).isEqualTo(financialAccount);

        financialAccount.setSubscriptions(new HashSet<>());
        assertThat(financialAccount.getSubscriptions()).doesNotContain(financialSubscriptionBack);
        assertThat(financialSubscriptionBack.getAccount()).isNull();
    }

    @Test
    void budgetsTest() {
        FinancialAccount financialAccount = getFinancialAccountRandomSampleGenerator();
        Budget budgetBack = getBudgetRandomSampleGenerator();

        financialAccount.addBudgets(budgetBack);
        assertThat(financialAccount.getBudgets()).containsOnly(budgetBack);
        assertThat(budgetBack.getAccounts()).containsOnly(financialAccount);

        financialAccount.removeBudgets(budgetBack);
        assertThat(financialAccount.getBudgets()).doesNotContain(budgetBack);
        assertThat(budgetBack.getAccounts()).doesNotContain(financialAccount);

        financialAccount.budgets(new HashSet<>(Set.of(budgetBack)));
        assertThat(financialAccount.getBudgets()).containsOnly(budgetBack);
        assertThat(budgetBack.getAccounts()).containsOnly(financialAccount);

        financialAccount.setBudgets(new HashSet<>());
        assertThat(financialAccount.getBudgets()).doesNotContain(budgetBack);
        assertThat(budgetBack.getAccounts()).doesNotContain(financialAccount);
    }

    @Test
    void transactionIngestionsTest() {
        FinancialAccount financialAccount = getFinancialAccountRandomSampleGenerator();
        TransactionIngestion transactionIngestionBack = getTransactionIngestionRandomSampleGenerator();

        financialAccount.addTransactionIngestions(transactionIngestionBack);
        assertThat(financialAccount.getTransactionIngestions()).containsOnly(transactionIngestionBack);
        assertThat(transactionIngestionBack.getAccounts()).containsOnly(financialAccount);

        financialAccount.removeTransactionIngestions(transactionIngestionBack);
        assertThat(financialAccount.getTransactionIngestions()).doesNotContain(transactionIngestionBack);
        assertThat(transactionIngestionBack.getAccounts()).doesNotContain(financialAccount);

        financialAccount.transactionIngestions(new HashSet<>(Set.of(transactionIngestionBack)));
        assertThat(financialAccount.getTransactionIngestions()).containsOnly(transactionIngestionBack);
        assertThat(transactionIngestionBack.getAccounts()).containsOnly(financialAccount);

        financialAccount.setTransactionIngestions(new HashSet<>());
        assertThat(financialAccount.getTransactionIngestions()).doesNotContain(transactionIngestionBack);
        assertThat(transactionIngestionBack.getAccounts()).doesNotContain(financialAccount);
    }

    @Test
    void apiAccessTokensTest() {
        FinancialAccount financialAccount = getFinancialAccountRandomSampleGenerator();
        ApiAccessToken apiAccessTokenBack = getApiAccessTokenRandomSampleGenerator();

        financialAccount.addApiAccessTokens(apiAccessTokenBack);
        assertThat(financialAccount.getApiAccessTokens()).containsOnly(apiAccessTokenBack);
        assertThat(apiAccessTokenBack.getAccounts()).containsOnly(financialAccount);

        financialAccount.removeApiAccessTokens(apiAccessTokenBack);
        assertThat(financialAccount.getApiAccessTokens()).doesNotContain(apiAccessTokenBack);
        assertThat(apiAccessTokenBack.getAccounts()).doesNotContain(financialAccount);

        financialAccount.apiAccessTokens(new HashSet<>(Set.of(apiAccessTokenBack)));
        assertThat(financialAccount.getApiAccessTokens()).containsOnly(apiAccessTokenBack);
        assertThat(apiAccessTokenBack.getAccounts()).containsOnly(financialAccount);

        financialAccount.setApiAccessTokens(new HashSet<>());
        assertThat(financialAccount.getApiAccessTokens()).doesNotContain(apiAccessTokenBack);
        assertThat(apiAccessTokenBack.getAccounts()).doesNotContain(financialAccount);
    }
}
