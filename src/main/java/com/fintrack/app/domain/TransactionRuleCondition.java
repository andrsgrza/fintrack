package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;

/**
 * One condition inside a transaction rule.
 *
 * value and secondValue are stored as strings and interpreted according to
 * field and operator by the service layer.
 */
@Entity
@Table(name = "transaction_rule_condition")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionRuleCondition implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false)
    private TransactionRuleField field;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false)
    private RuleOperator operator;

    @NotNull
    @Size(max = 1000)
    @Column(name = "value", length = 1000, nullable = false)
    private String value;

    @Size(max = 1000)
    @Column(name = "second_value", length = 1000)
    private String secondValue;

    @NotNull
    @Column(name = "case_sensitive", nullable = false)
    private Boolean caseSensitive;

    @NotNull
    @Min(value = 0)
    @Column(name = "position", nullable = false)
    private Integer position;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "user", "resultingCategory", "resultingTags", "conditions" }, allowSetters = true)
    private TransactionRule transactionRule;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public TransactionRuleCondition id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransactionRuleField getField() {
        return this.field;
    }

    public TransactionRuleCondition field(TransactionRuleField field) {
        this.setField(field);
        return this;
    }

    public void setField(TransactionRuleField field) {
        this.field = field;
    }

    public RuleOperator getOperator() {
        return this.operator;
    }

    public TransactionRuleCondition operator(RuleOperator operator) {
        this.setOperator(operator);
        return this;
    }

    public void setOperator(RuleOperator operator) {
        this.operator = operator;
    }

    public String getValue() {
        return this.value;
    }

    public TransactionRuleCondition value(String value) {
        this.setValue(value);
        return this;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getSecondValue() {
        return this.secondValue;
    }

    public TransactionRuleCondition secondValue(String secondValue) {
        this.setSecondValue(secondValue);
        return this;
    }

    public void setSecondValue(String secondValue) {
        this.secondValue = secondValue;
    }

    public Boolean getCaseSensitive() {
        return this.caseSensitive;
    }

    public TransactionRuleCondition caseSensitive(Boolean caseSensitive) {
        this.setCaseSensitive(caseSensitive);
        return this;
    }

    public void setCaseSensitive(Boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public Integer getPosition() {
        return this.position;
    }

    public TransactionRuleCondition position(Integer position) {
        this.setPosition(position);
        return this;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public TransactionRule getTransactionRule() {
        return this.transactionRule;
    }

    public void setTransactionRule(TransactionRule transactionRule) {
        this.transactionRule = transactionRule;
    }

    public TransactionRuleCondition transactionRule(TransactionRule transactionRule) {
        this.setTransactionRule(transactionRule);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionRuleCondition)) {
            return false;
        }
        return getId() != null && getId().equals(((TransactionRuleCondition) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionRuleCondition{" +
            "id=" + getId() +
            ", field='" + getField() + "'" +
            ", operator='" + getOperator() + "'" +
            ", value='" + getValue() + "'" +
            ", secondValue='" + getSecondValue() + "'" +
            ", caseSensitive='" + getCaseSensitive() + "'" +
            ", position=" + getPosition() +
            "}";
    }
}
