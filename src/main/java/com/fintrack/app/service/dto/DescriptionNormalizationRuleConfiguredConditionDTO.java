package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.DescriptionNormalizationRuleOperator;
import java.io.Serializable;

/**
 * One condition supplied with an atomic DescriptionNormalizationRule create request.
 */
public class DescriptionNormalizationRuleConfiguredConditionDTO implements Serializable {

    private DescriptionNormalizationRuleOperator operator;

    private String value;

    private Boolean caseSensitive;

    private Integer position;

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
}
