package com.fintrack.app.service.dto;

import java.io.Serializable;

/**
 * REST-safe tag suggestion from TransactionRule evaluation.
 */
public class TagSuggestionDTO implements Serializable {

    private Long tagId;

    private String tagName;

    private Long sourceRuleId;

    private String sourceRuleName;

    private boolean alreadyPresent;

    private boolean duplicateOfEarlierSuggestion;

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
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

    public boolean isAlreadyPresent() {
        return alreadyPresent;
    }

    public void setAlreadyPresent(boolean alreadyPresent) {
        this.alreadyPresent = alreadyPresent;
    }

    public boolean isDuplicateOfEarlierSuggestion() {
        return duplicateOfEarlierSuggestion;
    }

    public void setDuplicateOfEarlierSuggestion(boolean duplicateOfEarlierSuggestion) {
        this.duplicateOfEarlierSuggestion = duplicateOfEarlierSuggestion;
    }
}
