package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Atomic create payload for a DescriptionNormalizationRule and its ordered conditions.
 */
public class DescriptionNormalizationRuleConfiguredRequestDTO implements Serializable {

    private String name;

    private String description;

    private Boolean active;

    private RuleConditionLogic conditionOperator;

    private String resultingDescription;

    private List<DescriptionNormalizationRuleConfiguredConditionDTO> conditions = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public RuleConditionLogic getConditionOperator() {
        return conditionOperator;
    }

    public void setConditionOperator(RuleConditionLogic conditionOperator) {
        this.conditionOperator = conditionOperator;
    }

    public String getResultingDescription() {
        return resultingDescription;
    }

    public void setResultingDescription(String resultingDescription) {
        this.resultingDescription = resultingDescription;
    }

    public List<DescriptionNormalizationRuleConfiguredConditionDTO> getConditions() {
        return conditions;
    }

    public void setConditions(List<DescriptionNormalizationRuleConfiguredConditionDTO> conditions) {
        this.conditions = conditions;
    }
}
