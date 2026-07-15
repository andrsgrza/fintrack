package com.fintrack.app.service.dto;

import java.io.Serializable;

/**
 * REST-safe category suggestion from TransactionRule evaluation.
 */
public class CategorySuggestionDTO implements Serializable {

    private Long categoryId;

    private String categoryName;

    private Long sourceRuleId;

    private String sourceRuleName;

    private boolean conflictsWithCurrentValue;

    private Long currentCategoryId;

    private String currentCategoryName;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getSourceRuleId() {
        return sourceRuleId;
    }

    public void setSourceRuleId(Long sourceRuleId) {
        this.sourceRuleId = sourceRuleId;
    }

    public String getSourceRuleName() {
        return sourceRuleName;
    }

    public void setSourceRuleName(String sourceRuleName) {
        this.sourceRuleName = sourceRuleName;
    }

    public boolean isConflictsWithCurrentValue() {
        return conflictsWithCurrentValue;
    }

    public void setConflictsWithCurrentValue(boolean conflictsWithCurrentValue) {
        this.conflictsWithCurrentValue = conflictsWithCurrentValue;
    }

    public Long getCurrentCategoryId() {
        return currentCategoryId;
    }

    public void setCurrentCategoryId(Long currentCategoryId) {
        this.currentCategoryId = currentCategoryId;
    }

    public String getCurrentCategoryName() {
        return currentCategoryName;
    }

    public void setCurrentCategoryName(String currentCategoryName) {
        this.currentCategoryName = currentCategoryName;
    }
}
