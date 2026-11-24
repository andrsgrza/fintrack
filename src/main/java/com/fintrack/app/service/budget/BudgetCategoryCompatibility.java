package com.fintrack.app.service.budget;

import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CategoryType;

/** Shared Budget rule for category references; scope previews never relax the compiler's financial compatibility. */
public final class BudgetCategoryCompatibility {

    private BudgetCategoryCompatibility() {}

    public static boolean supports(CategoryType categoryType, BudgetType budgetType) {
        return (
            categoryType == CategoryType.BOTH ||
            (budgetType == BudgetType.EXPENSE && categoryType == CategoryType.EXPENSE) ||
            (budgetType == BudgetType.INCOME && categoryType == CategoryType.INCOME)
        );
    }
}
