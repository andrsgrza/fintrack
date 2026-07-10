package com.fintrack.app.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A DTO for the {@link com.fintrack.app.domain.Tag} entity.
 */
@Schema(description = "A flexible, non-hierarchical classification label.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TagDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 50)
    private String name;

    @Size(max = 250)
    private String description;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    private String color;

    @NotNull
    private Boolean active;

    @NotNull
    private Instant createdAt;

    @NotNull
    private Instant updatedAt;

    private UserDTO user;

    private Set<FinancialTransactionDTO> financialTransactions = new HashSet<>();

    private Set<TransactionRuleDTO> transactionRules = new HashSet<>();

    private Set<FinancialSubscriptionDTO> subscriptions = new HashSet<>();

    private Set<BudgetDTO> budgets = new HashSet<>();

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

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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

    public UserDTO getUser() {
        return user;
    }

    public void setUser(UserDTO user) {
        this.user = user;
    }

    public Set<FinancialTransactionDTO> getFinancialTransactions() {
        return financialTransactions;
    }

    public void setFinancialTransactions(Set<FinancialTransactionDTO> financialTransactions) {
        this.financialTransactions = financialTransactions;
    }

    public Set<TransactionRuleDTO> getTransactionRules() {
        return transactionRules;
    }

    public void setTransactionRules(Set<TransactionRuleDTO> transactionRules) {
        this.transactionRules = transactionRules;
    }

    public Set<FinancialSubscriptionDTO> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(Set<FinancialSubscriptionDTO> subscriptions) {
        this.subscriptions = subscriptions;
    }

    public Set<BudgetDTO> getBudgets() {
        return budgets;
    }

    public void setBudgets(Set<BudgetDTO> budgets) {
        this.budgets = budgets;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TagDTO)) {
            return false;
        }

        TagDTO tagDTO = (TagDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, tagDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TagDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", color='" + getColor() + "'" +
            ", active='" + getActive() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", user=" + getUser() +
            ", financialTransactions=" + getFinancialTransactions() +
            ", transactionRules=" + getTransactionRules() +
            ", subscriptions=" + getSubscriptions() +
            ", budgets=" + getBudgets() +
            "}";
    }
}
