package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.DescriptionNormalizationRuleOperator;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * One text condition inside a description normalization rule.
 */
@Entity
@Table(name = "description_normalization_rule_condition")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DescriptionNormalizationRuleCondition implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false)
    private DescriptionNormalizationRuleOperator operator;

    @NotNull
    @Size(min = 1, max = 1000)
    @Column(name = "value", length = 1000, nullable = false)
    private String value;

    @NotNull
    @Column(name = "case_sensitive", nullable = false)
    private Boolean caseSensitive;

    @NotNull
    @Min(0)
    @Column(name = "position", nullable = false)
    private Integer position;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "user", "conditions" }, allowSetters = true)
    private DescriptionNormalizationRule descriptionNormalizationRule;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DescriptionNormalizationRuleCondition id(Long id) {
        this.setId(id);
        return this;
    }

    public DescriptionNormalizationRuleOperator getOperator() {
        return operator;
    }

    public void setOperator(DescriptionNormalizationRuleOperator operator) {
        this.operator = operator;
    }

    public DescriptionNormalizationRuleCondition operator(DescriptionNormalizationRuleOperator operator) {
        this.setOperator(operator);
        return this;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public DescriptionNormalizationRuleCondition value(String value) {
        this.setValue(value);
        return this;
    }

    public Boolean getCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(Boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public DescriptionNormalizationRuleCondition caseSensitive(Boolean caseSensitive) {
        this.setCaseSensitive(caseSensitive);
        return this;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public DescriptionNormalizationRuleCondition position(Integer position) {
        this.setPosition(position);
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public DescriptionNormalizationRuleCondition createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public DescriptionNormalizationRuleCondition updatedAt(Instant updatedAt) {
        this.setUpdatedAt(updatedAt);
        return this;
    }

    public DescriptionNormalizationRule getDescriptionNormalizationRule() {
        return descriptionNormalizationRule;
    }

    public void setDescriptionNormalizationRule(DescriptionNormalizationRule descriptionNormalizationRule) {
        this.descriptionNormalizationRule = descriptionNormalizationRule;
    }

    public DescriptionNormalizationRuleCondition descriptionNormalizationRule(DescriptionNormalizationRule descriptionNormalizationRule) {
        this.setDescriptionNormalizationRule(descriptionNormalizationRule);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DescriptionNormalizationRuleCondition)) {
            return false;
        }
        return getId() != null && getId().equals(((DescriptionNormalizationRuleCondition) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "DescriptionNormalizationRuleCondition{" +
            "id=" +
            getId() +
            ", operator='" +
            getOperator() +
            "'" +
            ", value='" +
            getValue() +
            "'" +
            ", caseSensitive='" +
            getCaseSensitive() +
            "'" +
            ", position=" +
            getPosition() +
            "}"
        );
    }
}
