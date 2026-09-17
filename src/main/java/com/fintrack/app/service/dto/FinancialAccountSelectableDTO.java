package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import java.io.Serializable;

/**
 * Product-safe account read model for assigning a FinancialAccount to new work.
 *
 * <p>It deliberately omits ownership, timestamps, balances, and other technical entity fields.
 */
public class FinancialAccountSelectableDTO implements Serializable {

    private Long id;

    private String name;

    private AccountType accountType;

    private CurrencyCode currency;

    private String lastFourDigits;

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

    public String getLastFourDigits() {
        return lastFourDigits;
    }

    public void setLastFourDigits(String lastFourDigits) {
        this.lastFourDigits = lastFourDigits;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
