package com.fintrack.app.domain;

import static com.fintrack.app.domain.BudgetTestSamples.*;
import static com.fintrack.app.domain.CategoryTestSamples.*;
import static com.fintrack.app.domain.CategoryTestSamples.*;
import static com.fintrack.app.domain.FinancialSubscriptionTestSamples.*;
import static com.fintrack.app.domain.FinancialTransactionTestSamples.*;
import static com.fintrack.app.domain.TransactionRuleTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Category.class);
        Category category1 = getCategorySample1();
        Category category2 = new Category();
        assertThat(category1).isNotEqualTo(category2);

        category2.setId(category1.getId());
        assertThat(category1).isEqualTo(category2);

        category2 = getCategorySample2();
        assertThat(category1).isNotEqualTo(category2);
    }

    @Test
    void parentCategoryTest() {
        Category category = getCategoryRandomSampleGenerator();
        Category categoryBack = getCategoryRandomSampleGenerator();

        category.setParentCategory(categoryBack);
        assertThat(category.getParentCategory()).isEqualTo(categoryBack);

        category.parentCategory(null);
        assertThat(category.getParentCategory()).isNull();
    }

    @Test
    void financialTransactionsTest() {
        Category category = getCategoryRandomSampleGenerator();
        FinancialTransaction financialTransactionBack = getFinancialTransactionRandomSampleGenerator();

        category.addFinancialTransactions(financialTransactionBack);
        assertThat(category.getFinancialTransactions()).containsOnly(financialTransactionBack);
        assertThat(financialTransactionBack.getCategory()).isEqualTo(category);

        category.removeFinancialTransactions(financialTransactionBack);
        assertThat(category.getFinancialTransactions()).doesNotContain(financialTransactionBack);
        assertThat(financialTransactionBack.getCategory()).isNull();

        category.financialTransactions(new HashSet<>(Set.of(financialTransactionBack)));
        assertThat(category.getFinancialTransactions()).containsOnly(financialTransactionBack);
        assertThat(financialTransactionBack.getCategory()).isEqualTo(category);

        category.setFinancialTransactions(new HashSet<>());
        assertThat(category.getFinancialTransactions()).doesNotContain(financialTransactionBack);
        assertThat(financialTransactionBack.getCategory()).isNull();
    }

    @Test
    void childCategoriesTest() {
        Category category = getCategoryRandomSampleGenerator();
        Category categoryBack = getCategoryRandomSampleGenerator();

        category.addChildCategories(categoryBack);
        assertThat(category.getChildCategories()).containsOnly(categoryBack);
        assertThat(categoryBack.getParentCategory()).isEqualTo(category);

        category.removeChildCategories(categoryBack);
        assertThat(category.getChildCategories()).doesNotContain(categoryBack);
        assertThat(categoryBack.getParentCategory()).isNull();

        category.childCategories(new HashSet<>(Set.of(categoryBack)));
        assertThat(category.getChildCategories()).containsOnly(categoryBack);
        assertThat(categoryBack.getParentCategory()).isEqualTo(category);

        category.setChildCategories(new HashSet<>());
        assertThat(category.getChildCategories()).doesNotContain(categoryBack);
        assertThat(categoryBack.getParentCategory()).isNull();
    }

    @Test
    void transactionRulesTest() {
        Category category = getCategoryRandomSampleGenerator();
        TransactionRule transactionRuleBack = getTransactionRuleRandomSampleGenerator();

        category.addTransactionRules(transactionRuleBack);
        assertThat(category.getTransactionRules()).containsOnly(transactionRuleBack);
        assertThat(transactionRuleBack.getResultingCategory()).isEqualTo(category);

        category.removeTransactionRules(transactionRuleBack);
        assertThat(category.getTransactionRules()).doesNotContain(transactionRuleBack);
        assertThat(transactionRuleBack.getResultingCategory()).isNull();

        category.transactionRules(new HashSet<>(Set.of(transactionRuleBack)));
        assertThat(category.getTransactionRules()).containsOnly(transactionRuleBack);
        assertThat(transactionRuleBack.getResultingCategory()).isEqualTo(category);

        category.setTransactionRules(new HashSet<>());
        assertThat(category.getTransactionRules()).doesNotContain(transactionRuleBack);
        assertThat(transactionRuleBack.getResultingCategory()).isNull();
    }

    @Test
    void subscriptionsTest() {
        Category category = getCategoryRandomSampleGenerator();
        FinancialSubscription financialSubscriptionBack = getFinancialSubscriptionRandomSampleGenerator();

        category.addSubscriptions(financialSubscriptionBack);
        assertThat(category.getSubscriptions()).containsOnly(financialSubscriptionBack);
        assertThat(financialSubscriptionBack.getCategory()).isEqualTo(category);

        category.removeSubscriptions(financialSubscriptionBack);
        assertThat(category.getSubscriptions()).doesNotContain(financialSubscriptionBack);
        assertThat(financialSubscriptionBack.getCategory()).isNull();

        category.subscriptions(new HashSet<>(Set.of(financialSubscriptionBack)));
        assertThat(category.getSubscriptions()).containsOnly(financialSubscriptionBack);
        assertThat(financialSubscriptionBack.getCategory()).isEqualTo(category);

        category.setSubscriptions(new HashSet<>());
        assertThat(category.getSubscriptions()).doesNotContain(financialSubscriptionBack);
        assertThat(financialSubscriptionBack.getCategory()).isNull();
    }

    @Test
    void budgetsTest() {
        Category category = getCategoryRandomSampleGenerator();
        Budget budgetBack = getBudgetRandomSampleGenerator();

        category.addBudgets(budgetBack);
        assertThat(category.getBudgets()).containsOnly(budgetBack);
        assertThat(budgetBack.getCategories()).containsOnly(category);

        category.removeBudgets(budgetBack);
        assertThat(category.getBudgets()).doesNotContain(budgetBack);
        assertThat(budgetBack.getCategories()).doesNotContain(category);

        category.budgets(new HashSet<>(Set.of(budgetBack)));
        assertThat(category.getBudgets()).containsOnly(budgetBack);
        assertThat(budgetBack.getCategories()).containsOnly(category);

        category.setBudgets(new HashSet<>());
        assertThat(category.getBudgets()).doesNotContain(budgetBack);
        assertThat(budgetBack.getCategories()).doesNotContain(category);
    }
}
