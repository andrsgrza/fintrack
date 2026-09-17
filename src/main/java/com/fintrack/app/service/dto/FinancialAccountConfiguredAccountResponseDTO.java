package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Product-safe parent account returned by the configured account workflow. */
public class FinancialAccountConfiguredAccountResponseDTO implements Serializable {

    private Long id;
    private String name;
    private String institutionName;
    private AccountType accountType;
    private CurrencyCode currency;
    private BigDecimal initialBalance;
    private LocalDate initialBalanceDate;
    private String lastFourDigits;
    private String description;
    private String color;
    private String icon;
    private Boolean active;

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
