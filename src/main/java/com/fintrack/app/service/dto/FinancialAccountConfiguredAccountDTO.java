package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Product-owned FinancialAccount fields accepted by the configured account workflow.
 * Ownership, identifiers, and audit timestamps are intentionally server-owned.
 */
public class FinancialAccountConfiguredAccountDTO implements Serializable {

    @NotNull
    @Size(min = 1, max = 100)
    private String name;

    @Size(max = 100)
    private String institutionName;

    @NotNull
    private AccountType accountType;

    @NotNull
    private CurrencyCode currency;

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

    public CurrencyCode getCurrency() {
        return currency;
    }

    public void setCurrency(CurrencyCode currency) {
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
}
