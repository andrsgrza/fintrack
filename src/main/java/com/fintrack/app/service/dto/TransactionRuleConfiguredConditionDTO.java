package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

public class TransactionRuleConfiguredConditionDTO implements Serializable {

    private Long id;

    private TransactionRuleField field;

    private RuleOperator operator;

    @Size(max = 1000)
    private String value;

    @Size(max = 1000)
    private String secondValue;

    private Boolean caseSensitive;

    private Integer position;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransactionRuleField getField() {
        return field;
    }

    public void setField(TransactionRuleField field) {
        this.field = field;
    }

    public RuleOperator getOperator() {
        return operator;
    }

    public void setOperator(RuleOperator operator) {
        this.operator = operator;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getSecondValue() {
        return secondValue;
    }

    public void setSecondValue(String secondValue) {
        this.secondValue = secondValue;
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
