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
 * A user-owned rule evaluated only when a transaction is created.
 *
 * Higher numeric priority wins for category.
 * Tags from every matching rule are accumulated without duplicates.
 */
@Entity
@Table(name = "transaction_rule")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionRule implements Serializable {

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
    @Min(value = 0)
    @Column(name = "priority", nullable = false)
    private Integer priority;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "condition_logic", nullable = false)
    private RuleConditionLogic conditionLogic;

    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(optional = false)
    @NotNull
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(
        value = { "user", "parentCategory", "financialTransactions", "childCategories", "transactionRules", "subscriptions", "budgets" },
        allowSetters = true
    )
    private Category resultingCategory;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rel_transaction_rule__resulting_tags",
        joinColumns = @JoinColumn(name = "transaction_rule_id"),
        inverseJoinColumns = @JoinColumn(name = "resulting_tags_id")
    )
    @JsonIgnoreProperties(value = { "user", "financialTransactions", "transactionRules", "subscriptions", "budgets" }, allowSetters = true)
    private Set<Tag> resultingTags = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "transactionRule")
    @JsonIgnoreProperties(value = { "transactionRule" }, allowSetters = true)
    private Set<TransactionRuleCondition> conditions = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public TransactionRule id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public TransactionRule name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public TransactionRule description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getPriority() {
        return this.priority;
    }

    public TransactionRule priority(Integer priority) {
        this.setPriority(priority);
        return this;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public RuleConditionLogic getConditionLogic() {
        return this.conditionLogic;
    }

    public TransactionRule conditionLogic(RuleConditionLogic conditionLogic) {
        this.setConditionLogic(conditionLogic);
        return this;
    }

    public void setConditionLogic(RuleConditionLogic conditionLogic) {
        this.conditionLogic = conditionLogic;
    }

    public Boolean getActive() {
        return this.active;
    }

    public TransactionRule active(Boolean active) {
        this.setActive(active);
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public TransactionRule createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public TransactionRule updatedAt(Instant updatedAt) {
        this.setUpdatedAt(updatedAt);
        return this;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public TransactionRule user(User user) {
        this.setUser(user);
        return this;
    }

    public Category getResultingCategory() {
        return this.resultingCategory;
    }

    public void setResultingCategory(Category category) {
        this.resultingCategory = category;
    }

    public TransactionRule resultingCategory(Category category) {
        this.setResultingCategory(category);
        return this;
    }

    public Set<Tag> getResultingTags() {
        return this.resultingTags;
    }

    public void setResultingTags(Set<Tag> tags) {
        this.resultingTags = tags;
    }

    public TransactionRule resultingTags(Set<Tag> tags) {
        this.setResultingTags(tags);
        return this;
    }

    public TransactionRule addResultingTags(Tag tag) {
        this.resultingTags.add(tag);
        return this;
    }

    public TransactionRule removeResultingTags(Tag tag) {
        this.resultingTags.remove(tag);
        return this;
    }

    public Set<TransactionRuleCondition> getConditions() {
        return this.conditions;
    }

    public void setConditions(Set<TransactionRuleCondition> transactionRuleConditions) {
        if (this.conditions != null) {
            this.conditions.forEach(i -> i.setTransactionRule(null));
        }
        if (transactionRuleConditions != null) {
            transactionRuleConditions.forEach(i -> i.setTransactionRule(this));
        }
        this.conditions = transactionRuleConditions;
    }

    public TransactionRule conditions(Set<TransactionRuleCondition> transactionRuleConditions) {
        this.setConditions(transactionRuleConditions);
        return this;
    }

    public TransactionRule addConditions(TransactionRuleCondition transactionRuleCondition) {
        this.conditions.add(transactionRuleCondition);
        transactionRuleCondition.setTransactionRule(this);
        return this;
    }

    public TransactionRule removeConditions(TransactionRuleCondition transactionRuleCondition) {
        this.conditions.remove(transactionRuleCondition);
        transactionRuleCondition.setTransactionRule(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionRule)) {
            return false;
        }
        return getId() != null && getId().equals(((TransactionRule) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionRule{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", priority=" + getPriority() +
            ", conditionLogic='" + getConditionLogic() + "'" +
            ", active='" + getActive() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            "}";
    }
}
