package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.DescriptionNormalizationRuleOperator;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.fintrack.app.domain.DescriptionNormalizationRuleCondition} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DescriptionNormalizationRuleConditionDTO implements Serializable {

    private Long id;

    @NotNull
    private DescriptionNormalizationRuleOperator operator;

    @NotNull
    @Size(max = 1000)
    private String value;

    @NotNull
    private Boolean caseSensitive;

    private Integer position;

    private Instant createdAt;

    private Instant updatedAt;

    @NotNull
    private DescriptionNormalizationRuleDTO descriptionNormalizationRule;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DescriptionNormalizationRuleOperator getOperator() {
        return operator;
    }

    public void setOperator(DescriptionNormalizationRuleOperator operator) {
        this.operator = operator;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Boolean getCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(Boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
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

    public DescriptionNormalizationRuleDTO getDescriptionNormalizationRule() {
        return descriptionNormalizationRule;
    }

    public void setDescriptionNormalizationRule(DescriptionNormalizationRuleDTO descriptionNormalizationRule) {
        this.descriptionNormalizationRule = descriptionNormalizationRule;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DescriptionNormalizationRuleConditionDTO)) {
            return false;
        }
        DescriptionNormalizationRuleConditionDTO that = (DescriptionNormalizationRuleConditionDTO) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
