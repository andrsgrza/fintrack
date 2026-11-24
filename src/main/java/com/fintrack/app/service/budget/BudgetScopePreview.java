package com.fintrack.app.service.budget;

import java.util.List;

/**
 * Current category-tree expansion for review screens. The selected IDs remain the immutable Budget configuration;
 * this projection is intentionally dynamic because the financial semantic engine resolves descendants at execution.
 */
public record BudgetScopePreview(
    boolean unrestricted,
    List<Category> selectedCategories,
    List<Category> effectiveCategories,
    List<Long> missingCategoryIds,
    List<Long> incompatibleCategoryIds
) {
    public BudgetScopePreview {
        selectedCategories = List.copyOf(selectedCategories);
        effectiveCategories = List.copyOf(effectiveCategories);
        missingCategoryIds = List.copyOf(missingCategoryIds);
        incompatibleCategoryIds = List.copyOf(incompatibleCategoryIds);
    }

    public record Category(Long id, String name, String color, Boolean active, List<String> path, String categoryType) {
        public Category {
            path = List.copyOf(path);
        }
    }
}
