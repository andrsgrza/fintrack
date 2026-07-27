package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * User-owned rule for normalizing imported transaction descriptions before review/import.
 */
@Entity
@Table(name = "description_normalization_rule")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DescriptionNormalizationRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 1, max = 100)
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active;

    @NotNull
    @Min(0)
    @Column(name = "priority", nullable = false)
    private Integer priority;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "condition_operator", nullable = false)
    private RuleConditionLogic conditionOperator;

    @NotNull
    @Size(min = 1, max = 500)
    @Column(name = "resulting_description", length = 500, nullable = false)
    private String resultingDescription;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(optional = false)
    @NotNull
    private User user;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "descriptionNormalizationRule")
    @JsonIgnoreProperties(value = { "descriptionNormalizationRule" }, allowSetters = true)
    private Set<DescriptionNormalizationRuleCondition> conditions = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DescriptionNormalizationRule id(Long id) {
        this.setId(id);
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DescriptionNormalizationRule name(String name) {
        this.setName(name);
        return this;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DescriptionNormalizationRule description(String description) {
        this.setDescription(description);
        return this;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public DescriptionNormalizationRule active(Boolean active) {
        this.setActive(active);
        return this;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public DescriptionNormalizationRule priority(Integer priority) {
        this.setPriority(priority);
        return this;
    }

    public RuleConditionLogic getConditionOperator() {
        return conditionOperator;
    }

    public void setConditionOperator(RuleConditionLogic conditionOperator) {
        this.conditionOperator = conditionOperator;
    }

    public DescriptionNormalizationRule conditionOperator(RuleConditionLogic conditionOperator) {
        this.setConditionOperator(conditionOperator);
        return this;
    }

    public String getResultingDescription() {
        return resultingDescription;
    }

    public void setResultingDescription(String resultingDescription) {
        this.resultingDescription = resultingDescription;
    }

    public DescriptionNormalizationRule resultingDescription(String resultingDescription) {
        this.setResultingDescription(resultingDescription);
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public DescriptionNormalizationRule createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public DescriptionNormalizationRule updatedAt(Instant updatedAt) {
        this.setUpdatedAt(updatedAt);
        return this;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public DescriptionNormalizationRule user(User user) {
        this.setUser(user);
        return this;
    }

    public Set<DescriptionNormalizationRuleCondition> getConditions() {
        return conditions;
    }

    public void setConditions(Set<DescriptionNormalizationRuleCondition> conditions) {
        if (this.conditions != null) {
            this.conditions.forEach(condition -> condition.setDescriptionNormalizationRule(null));
        }
        if (conditions != null) {
            conditions.forEach(condition -> condition.setDescriptionNormalizationRule(this));
        }
        this.conditions = conditions;
    }

    public DescriptionNormalizationRule conditions(Set<DescriptionNormalizationRuleCondition> conditions) {
        this.setConditions(conditions);
        return this;
    }

    public DescriptionNormalizationRule addConditions(DescriptionNormalizationRuleCondition condition) {
        this.conditions.add(condition);
        condition.setDescriptionNormalizationRule(this);
        return this;
    }

    public DescriptionNormalizationRule removeConditions(DescriptionNormalizationRuleCondition condition) {
        this.conditions.remove(condition);
        condition.setDescriptionNormalizationRule(null);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DescriptionNormalizationRule)) {
            return false;
        }
        return getId() != null && getId().equals(((DescriptionNormalizationRule) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "DescriptionNormalizationRule{" +
            "id=" +
            getId() +
            ", name='" +
            getName() +
            "'" +
            ", active='" +
            getActive() +
            "'" +
            ", priority=" +
            getPriority() +
            ", conditionOperator='" +
            getConditionOperator() +
            "'" +
            ", resultingDescription='" +
            getResultingDescription() +
            "'" +
            "}"
        );
    }
}
