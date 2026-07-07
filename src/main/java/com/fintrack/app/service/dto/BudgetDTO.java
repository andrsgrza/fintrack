package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.BudgetPeriod;
import com.fintrack.app.domain.enumeration.BudgetStatus;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TagMatchMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A DTO for the {@link com.fintrack.app.domain.Budget} entity.
 */
@Schema(
    description = "A spending limit for a recurring or custom period.\n\nEmpty account/category/tag relations have domain-specific meanings:\n- no accounts: all active accounts of the user in the budget currency\n- no categories: any category\n- no tags: no tag filtering"
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class BudgetDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 100)
    private String name;

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal amount;

    @NotNull
    private CurrencyCode currency;

    @NotNull
    private BudgetPeriod period;

    @NotNull
    private LocalDate startDate;

    private LocalDate endDate;

    @NotNull
    private BudgetStatus status;

    @NotNull
    private TagMatchMode tagMatchMode;

    @DecimalMin(value = "0")
    @DecimalMax(value = "100")
    private BigDecimal warningPercentage;

    @NotNull
    private Instant createdAt;

    @NotNull
    private Instant updatedAt;

    @NotNull
    private UserDTO user;

    private Set<FinancialAccountDTO> accounts = new HashSet<>();

    private Set<CategoryDTO> categories = new HashSet<>();

    private Set<TagDTO> tags = new HashSet<>();

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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public void setCurrency(CurrencyCode currency) {
        this.currency = currency;
    }

    public BudgetPeriod getPeriod() {
        return period;
    }

    public void setPeriod(BudgetPeriod period) {
        this.period = period;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BudgetStatus getStatus() {
        return status;
    }

    public void setStatus(BudgetStatus status) {
        this.status = status;
    }

    public TagMatchMode getTagMatchMode() {
        return tagMatchMode;
    }

    public void setTagMatchMode(TagMatchMode tagMatchMode) {
        this.tagMatchMode = tagMatchMode;
    }

    public BigDecimal getWarningPercentage() {
        return warningPercentage;
    }

    public void setWarningPercentage(BigDecimal warningPercentage) {
        this.warningPercentage = warningPercentage;
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

    public Set<FinancialAccountDTO> getAccounts() {
        return accounts;
    }

    public void setAccounts(Set<FinancialAccountDTO> accounts) {
        this.accounts = accounts;
    }

    public Set<CategoryDTO> getCategories() {
        return categories;
    }

    public void setCategories(Set<CategoryDTO> categories) {
        this.categories = categories;
    }

    public Set<TagDTO> getTags() {
        return tags;
    }

    public void setTags(Set<TagDTO> tags) {
        this.tags = tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BudgetDTO)) {
            return false;
        }

        BudgetDTO budgetDTO = (BudgetDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, budgetDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "BudgetDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", amount=" + getAmount() +
            ", currency='" + getCurrency() + "'" +
            ", period='" + getPeriod() + "'" +
            ", startDate='" + getStartDate() + "'" +
            ", endDate='" + getEndDate() + "'" +
            ", status='" + getStatus() + "'" +
            ", tagMatchMode='" + getTagMatchMode() + "'" +
            ", warningPercentage=" + getWarningPercentage() +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", user=" + getUser() +
            ", accounts=" + getAccounts() +
            ", categories=" + getCategories() +
            ", tags=" + getTags() +
            "}";
    }
}
