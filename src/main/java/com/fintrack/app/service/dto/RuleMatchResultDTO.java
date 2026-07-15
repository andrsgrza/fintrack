package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import com.fintrack.app.service.rules.RuleOutputField;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * REST-safe matched rule summary from TransactionRule evaluation.
 */
public class RuleMatchResultDTO implements Serializable {

    private Long ruleId;

    private String ruleName;

    private Integer priority;

    private RuleConditionLogic conditionLogic;

    private Set<RuleOutputField> proposedOutputs = new HashSet<>();

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public RuleConditionLogic getConditionLogic() {
        return conditionLogic;
    }

    public void setConditionLogic(RuleConditionLogic conditionLogic) {
        this.conditionLogic = conditionLogic;
    }

    public Set<RuleOutputField> getProposedOutputs() {
        return proposedOutputs;
    }

    public void setProposedOutputs(Set<RuleOutputField> proposedOutputs) {
        this.proposedOutputs = proposedOutputs;
    }
}
