package com.fintrack.app.service.dto;

import com.fintrack.app.service.rules.RuleOutputConflictReason;
import com.fintrack.app.service.rules.RuleOutputField;
import java.io.Serializable;

/**
 * REST-safe rule output conflict from TransactionRule evaluation.
 */
public class RuleOutputConflictDTO implements Serializable {

    private RuleOutputField field;

    private Long currentValueId;

    private String currentValueLabel;

    private Long suggestedValueId;

    private String suggestedValueLabel;

    private Long sourceRuleId;

    private String sourceRuleName;

    private RuleOutputConflictReason reason;

    public RuleOutputField getField() {
        return field;
    }

    public void setField(RuleOutputField field) {
        this.field = field;
    }

    public Long getCurrentValueId() {
        return currentValueId;
    }

    public void setCurrentValueId(Long currentValueId) {
        this.currentValueId = currentValueId;
    }

    public String getCurrentValueLabel() {
        return currentValueLabel;
    }

    public void setCurrentValueLabel(String currentValueLabel) {
        this.currentValueLabel = currentValueLabel;
    }

    public Long getSuggestedValueId() {
        return suggestedValueId;
    }

    public void setSuggestedValueId(Long suggestedValueId) {
        this.suggestedValueId = suggestedValueId;
    }

    public String getSuggestedValueLabel() {
        return suggestedValueLabel;
    }

    public void setSuggestedValueLabel(String suggestedValueLabel) {
        this.suggestedValueLabel = suggestedValueLabel;
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

    public RuleOutputConflictReason getReason() {
        return reason;
    }

    public void setReason(RuleOutputConflictReason reason) {
        this.reason = reason;
    }
}
