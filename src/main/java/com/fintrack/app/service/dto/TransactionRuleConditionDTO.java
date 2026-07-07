package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A DTO for the {@link com.fintrack.app.domain.TransactionRuleCondition} entity.
 */
@Schema(
    description = "One condition inside a transaction rule.\n\nvalue and secondValue are stored as strings and interpreted according to\nfield and operator by the service layer."
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionRuleConditionDTO implements Serializable {

    private Long id;

    @NotNull
    private TransactionRuleField field;

    @NotNull
    private RuleOperator operator;

    @NotNull
    @Size(max = 1000)
    private String value;

    @Size(max = 1000)
    private String secondValue;

    @NotNull
    private Boolean caseSensitive;

    @NotNull
    @Min(value = 0)
    private Integer position;

    @NotNull
    private TransactionRuleDTO transactionRule;

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

    public TransactionRuleDTO getTransactionRule() {
        return transactionRule;
    }

    public void setTransactionRule(TransactionRuleDTO transactionRule) {
        this.transactionRule = transactionRule;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionRuleConditionDTO)) {
            return false;
        }

        TransactionRuleConditionDTO transactionRuleConditionDTO = (TransactionRuleConditionDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, transactionRuleConditionDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionRuleConditionDTO{" +
            "id=" + getId() +
            ", field='" + getField() + "'" +
            ", operator='" + getOperator() + "'" +
            ", value='" + getValue() + "'" +
            ", secondValue='" + getSecondValue() + "'" +
            ", caseSensitive='" + getCaseSensitive() + "'" +
            ", position=" + getPosition() +
            ", transactionRule=" + getTransactionRule() +
            "}";
    }
}
