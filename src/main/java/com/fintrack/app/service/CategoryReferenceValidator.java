package com.fintrack.app.service;

import com.fintrack.app.domain.Category;

/**
 * Guards new Category assignments. Inactive categories are historical references only: they remain valid on records
 * that already hold them, but cannot be selected for new work.
 */
public final class CategoryReferenceValidator {

    private CategoryReferenceValidator() {}

    public static void validateActiveForNewReference(Category category) {
        if (category != null && !Boolean.TRUE.equals(category.getActive())) {
            throw new IllegalArgumentException("Inactive category cannot be selected for a new reference");
        }
    }
}
