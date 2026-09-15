package com.fintrack.app.service.validation;

import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.TransactionFlow;

/**
 * Shared category-output compatibility rules for posted transactions and transaction candidates.
 *
 * This validator intentionally depends only on enums so product workflows can keep their source-specific
 * ownership, status, and command validation semantics.
 */
public final class CategoryFlowCompatibilityValidator {

    private CategoryFlowCompatibilityValidator() {}

    public static boolean isCompatible(CategoryType categoryType, TransactionFlow flow) {
        if (categoryType == null || flow == null) {
            return true;
        }
        if (categoryType == CategoryType.EXPENSE) {
            return flow == TransactionFlow.OUT;
        }
        if (categoryType == CategoryType.INCOME) {
            return flow == TransactionFlow.IN;
        }
        return true;
    }
}
