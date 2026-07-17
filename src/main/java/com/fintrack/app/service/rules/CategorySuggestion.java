package com.fintrack.app.service.rules;

public record CategorySuggestion(
    Long categoryId,
    String categoryName,
    Long sourceRuleId,
    String sourceRuleName,
    boolean conflictsWithCurrentValue,
    Long currentCategoryId,
    String currentCategoryName
) {}
