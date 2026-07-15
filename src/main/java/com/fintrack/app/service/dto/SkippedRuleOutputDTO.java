package com.fintrack.app.service.dto;

import com.fintrack.app.service.rules.RuleOutputField;
import com.fintrack.app.service.rules.RuleOutputSkipReason;
import java.io.Serializable;

/**
 * REST-safe skipped rule output from TransactionRule evaluation.
 */
public class SkippedRuleOutputDTO implements Serializable {

    private RuleOutputField field;

    private Long sourceRuleId;

    private String sourceRuleName;

    private RuleOutputSkipReason reason;

    private Long valueId;

    private String valueLabel;

    public RuleOutputField getField() {
        return field;
    }

    public void setField(RuleOutputField field) {
        this.field = field;
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

    public RuleOutputSkipReason getReason() {
        return reason;
    }

    public void setReason(RuleOutputSkipReason reason) {
        this.reason = reason;
    }

    public Long getValueId() {
        return valueId;
    }

    public void setValueId(Long valueId) {
        this.valueId = valueId;
    }

    public String getValueLabel() {
        return valueLabel;
    }

    public void setValueLabel(String valueLabel) {
        this.valueLabel = valueLabel;
    }
}
