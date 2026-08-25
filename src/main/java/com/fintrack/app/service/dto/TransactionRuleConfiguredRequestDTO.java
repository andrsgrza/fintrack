package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TransactionRuleConfiguredRequestDTO implements Serializable {

    private String name;

    private String description;

    private RuleConditionLogic conditionLogic;

    private Boolean active;

    private CategoryDTO resultingCategory;

    private Set<TagDTO> resultingTags = new HashSet<>();

    private List<TransactionRuleConfiguredConditionDTO> conditions = new ArrayList<>();

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

    public RuleConditionLogic getConditionLogic() {
        return conditionLogic;
    }

    public void setConditionLogic(RuleConditionLogic conditionLogic) {
        this.conditionLogic = conditionLogic;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public CategoryDTO getResultingCategory() {
        return resultingCategory;
    }

    public void setResultingCategory(CategoryDTO resultingCategory) {
        this.resultingCategory = resultingCategory;
    }

    public Set<TagDTO> getResultingTags() {
        return resultingTags;
    }

    public void setResultingTags(Set<TagDTO> resultingTags) {
        this.resultingTags = resultingTags;
    }

    public List<TransactionRuleConfiguredConditionDTO> getConditions() {
        return conditions;
    }

    public void setConditions(List<TransactionRuleConfiguredConditionDTO> conditions) {
        this.conditions = conditions;
    }
}
