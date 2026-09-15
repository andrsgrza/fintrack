package com.fintrack.app.service.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import org.junit.jupiter.api.Test;

class CategoryFlowCompatibilityValidatorTest {

    @Test
    void expenseAcceptsOnlyOut() {
        assertThat(CategoryFlowCompatibilityValidator.isCompatible(CategoryType.EXPENSE, TransactionFlow.OUT)).isTrue();
        assertThat(CategoryFlowCompatibilityValidator.isCompatible(CategoryType.EXPENSE, TransactionFlow.IN)).isFalse();
    }

    @Test
    void incomeAcceptsOnlyIn() {
        assertThat(CategoryFlowCompatibilityValidator.isCompatible(CategoryType.INCOME, TransactionFlow.IN)).isTrue();
        assertThat(CategoryFlowCompatibilityValidator.isCompatible(CategoryType.INCOME, TransactionFlow.OUT)).isFalse();
    }

    @Test
    void bothAcceptsInAndOut() {
        assertThat(CategoryFlowCompatibilityValidator.isCompatible(CategoryType.BOTH, TransactionFlow.IN)).isTrue();
        assertThat(CategoryFlowCompatibilityValidator.isCompatible(CategoryType.BOTH, TransactionFlow.OUT)).isTrue();
    }

    @Test
    void nullCategoryTypeOrFlowPreservesNoValidationBehavior() {
        assertThat(CategoryFlowCompatibilityValidator.isCompatible(null, TransactionFlow.IN)).isTrue();
        assertThat(CategoryFlowCompatibilityValidator.isCompatible(CategoryType.EXPENSE, null)).isTrue();
    }
}
