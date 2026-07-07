package com.fintrack.app.domain;

import static com.fintrack.app.domain.BudgetTestSamples.*;
import static com.fintrack.app.domain.CategoryTestSamples.*;
import static com.fintrack.app.domain.FinancialAccountTestSamples.*;
import static com.fintrack.app.domain.TagTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BudgetTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Budget.class);
        Budget budget1 = getBudgetSample1();
        Budget budget2 = new Budget();
        assertThat(budget1).isNotEqualTo(budget2);

        budget2.setId(budget1.getId());
        assertThat(budget1).isEqualTo(budget2);

        budget2 = getBudgetSample2();
        assertThat(budget1).isNotEqualTo(budget2);
    }

    @Test
    void accountsTest() {
        Budget budget = getBudgetRandomSampleGenerator();
        FinancialAccount financialAccountBack = getFinancialAccountRandomSampleGenerator();

        budget.addAccounts(financialAccountBack);
        assertThat(budget.getAccounts()).containsOnly(financialAccountBack);

        budget.removeAccounts(financialAccountBack);
        assertThat(budget.getAccounts()).doesNotContain(financialAccountBack);

        budget.accounts(new HashSet<>(Set.of(financialAccountBack)));
        assertThat(budget.getAccounts()).containsOnly(financialAccountBack);

        budget.setAccounts(new HashSet<>());
        assertThat(budget.getAccounts()).doesNotContain(financialAccountBack);
    }

    @Test
    void categoriesTest() {
        Budget budget = getBudgetRandomSampleGenerator();
        Category categoryBack = getCategoryRandomSampleGenerator();

        budget.addCategories(categoryBack);
        assertThat(budget.getCategories()).containsOnly(categoryBack);

        budget.removeCategories(categoryBack);
        assertThat(budget.getCategories()).doesNotContain(categoryBack);

        budget.categories(new HashSet<>(Set.of(categoryBack)));
        assertThat(budget.getCategories()).containsOnly(categoryBack);

        budget.setCategories(new HashSet<>());
        assertThat(budget.getCategories()).doesNotContain(categoryBack);
    }

    @Test
    void tagsTest() {
        Budget budget = getBudgetRandomSampleGenerator();
        Tag tagBack = getTagRandomSampleGenerator();

        budget.addTags(tagBack);
        assertThat(budget.getTags()).containsOnly(tagBack);

        budget.removeTags(tagBack);
        assertThat(budget.getTags()).doesNotContain(tagBack);

        budget.tags(new HashSet<>(Set.of(tagBack)));
        assertThat(budget.getTags()).containsOnly(tagBack);

        budget.setTags(new HashSet<>());
        assertThat(budget.getTags()).doesNotContain(tagBack);
    }
}
