package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted DescriptionNormalizationRule returned by the atomic configured create command.
 */
public class DescriptionNormalizationRuleConfiguredResponseDTO implements Serializable {

    private Long id;
    private String name;
    private String description;
    private Boolean active;
    private Integer priority;
    private RuleConditionLogic conditionOperator;
    private String resultingDescription;
    private Instant createdAt;
    private Instant updatedAt;
    private List<DescriptionNormalizationRuleConfiguredConditionDTO> conditions = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<DescriptionNormalizationRuleConfiguredConditionDTO> getConditions() {
        return conditions;
    }

    public void setConditions(List<DescriptionNormalizationRuleConfiguredConditionDTO> conditions) {
        this.conditions = conditions;
    }
}
