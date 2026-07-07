package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A DTO for the {@link com.fintrack.app.domain.TransactionRule} entity.
 */
@Schema(
    description = "A user-owned rule evaluated only when a transaction is created.\n\nHigher numeric priority wins for category, subscription and description.\nTags from every matching rule are accumulated without duplicates."
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionRuleDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    @NotNull
    @Min(value = 0)
    private Integer priority;

    @NotNull
    private RuleConditionLogic conditionLogic;

    @Size(max = 500)
    private String resultingDescription;

    @NotNull
    private Boolean active;

    @NotNull
    private Instant createdAt;

    @NotNull
    private Instant updatedAt;

    @NotNull
    private UserDTO user;

    private CategoryDTO resultingCategory;

    private FinancialSubscriptionDTO resultingFinancialSubscription;

    private Set<TagDTO> resultingTags = new HashSet<>();

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

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public RuleConditionLogic getConditionLogic() {
        return conditionLogic;
    }

    public void setConditionLogic(RuleConditionLogic conditionLogic) {
        this.conditionLogic = conditionLogic;
    }

    public String getResultingDescription() {
        return resultingDescription;
    }

    public void setResultingDescription(String resultingDescription) {
        this.resultingDescription = resultingDescription;
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

    public CategoryDTO getResultingCategory() {
        return resultingCategory;
    }

    public void setResultingCategory(CategoryDTO resultingCategory) {
        this.resultingCategory = resultingCategory;
    }

    public FinancialSubscriptionDTO getResultingFinancialSubscription() {
        return resultingFinancialSubscription;
    }

    public void setResultingFinancialSubscription(FinancialSubscriptionDTO resultingFinancialSubscription) {
        this.resultingFinancialSubscription = resultingFinancialSubscription;
    }

    public Set<TagDTO> getResultingTags() {
        return resultingTags;
    }

    public void setResultingTags(Set<TagDTO> resultingTags) {
        this.resultingTags = resultingTags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionRuleDTO)) {
            return false;
        }

        TransactionRuleDTO transactionRuleDTO = (TransactionRuleDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, transactionRuleDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionRuleDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", priority=" + getPriority() +
            ", conditionLogic='" + getConditionLogic() + "'" +
            ", resultingDescription='" + getResultingDescription() + "'" +
            ", active='" + getActive() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", user=" + getUser() +
            ", resultingCategory=" + getResultingCategory() +
            ", resultingFinancialSubscription=" + getResultingFinancialSubscription() +
            ", resultingTags=" + getResultingTags() +
            "}";
    }
}
