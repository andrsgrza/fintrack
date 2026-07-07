package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.AccountType;
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
 * A DTO for the {@link com.fintrack.app.domain.FinancialAccount} entity.
 */
@Schema(
    description = "A financial account owned by a user.\n\nThe current balance is calculated and is not persisted.\nEvery transaction amount is expressed in this account's currency."
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class FinancialAccountDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 100)
    private String name;

    @Size(max = 100)
    private String institutionName;

    @NotNull
    private AccountType accountType;

    @NotNull
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;

    @NotNull
    private BigDecimal initialBalance;

    @NotNull
    private LocalDate initialBalanceDate;

    @Pattern(regexp = "^[0-9]{4}$")
    private String lastFourDigits;

    @Size(max = 500)
    private String description;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    private String color;

    @Size(max = 50)
    private String icon;

    @NotNull
    private Boolean active;

    @NotNull
    private Boolean includeInNetWorth;

    @NotNull
    private Instant createdAt;

    @NotNull
    private Instant updatedAt;

    @NotNull
    private UserDTO user;

    private Set<BudgetDTO> budgets = new HashSet<>();

    private Set<TransactionIngestionDTO> transactionIngestions = new HashSet<>();

    private Set<ApiAccessTokenDTO> apiAccessTokens = new HashSet<>();

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

    public String getInstitutionName() {
        return institutionName;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public void setInitialBalance(BigDecimal initialBalance) {
        this.initialBalance = initialBalance;
    }

    public LocalDate getInitialBalanceDate() {
        return initialBalanceDate;
    }

    public void setInitialBalanceDate(LocalDate initialBalanceDate) {
        this.initialBalanceDate = initialBalanceDate;
    }

    public String getLastFourDigits() {
        return lastFourDigits;
    }

    public void setLastFourDigits(String lastFourDigits) {
        this.lastFourDigits = lastFourDigits;
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

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getIncludeInNetWorth() {
        return includeInNetWorth;
    }

    public void setIncludeInNetWorth(Boolean includeInNetWorth) {
        this.includeInNetWorth = includeInNetWorth;
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

    public Set<BudgetDTO> getBudgets() {
        return budgets;
    }

    public void setBudgets(Set<BudgetDTO> budgets) {
        this.budgets = budgets;
    }

    public Set<TransactionIngestionDTO> getTransactionIngestions() {
        return transactionIngestions;
    }

    public void setTransactionIngestions(Set<TransactionIngestionDTO> transactionIngestions) {
        this.transactionIngestions = transactionIngestions;
    }

    public Set<ApiAccessTokenDTO> getApiAccessTokens() {
        return apiAccessTokens;
    }

    public void setApiAccessTokens(Set<ApiAccessTokenDTO> apiAccessTokens) {
        this.apiAccessTokens = apiAccessTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FinancialAccountDTO)) {
            return false;
        }

        FinancialAccountDTO financialAccountDTO = (FinancialAccountDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, financialAccountDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "FinancialAccountDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", institutionName='" + getInstitutionName() + "'" +
            ", accountType='" + getAccountType() + "'" +
            ", currency='" + getCurrency() + "'" +
            ", initialBalance=" + getInitialBalance() +
            ", initialBalanceDate='" + getInitialBalanceDate() + "'" +
            ", lastFourDigits='" + getLastFourDigits() + "'" +
            ", description='" + getDescription() + "'" +
            ", color='" + getColor() + "'" +
            ", icon='" + getIcon() + "'" +
            ", active='" + getActive() + "'" +
            ", includeInNetWorth='" + getIncludeInNetWorth() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", user=" + getUser() +
            ", budgets=" + getBudgets() +
            ", transactionIngestions=" + getTransactionIngestions() +
            ", apiAccessTokens=" + getApiAccessTokens() +
            "}";
    }
}
