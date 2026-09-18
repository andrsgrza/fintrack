package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Read-only product summary for the Financial Accounts overview.
 *
 * <p>Balances are calculated when the overview is requested and are never persisted here.</p>
 */
public class FinancialAccountOverviewDTO implements Serializable {

    private Long id;

    private String name;

    private AccountType accountType;

    private CurrencyCode currency;

    private Boolean active;

    private String lastFourDigits;

    /**
     * Product identity accent for the account overview. This is account metadata only; it does not affect balance calculation.
     */
    private String color;

    private BigDecimal currentBalance;

    private BigDecimal currentDebt;

    private BigDecimal creditLimit;

    private BigDecimal availableCredit;

    private Integer statementDay;

    private Integer paymentDueDay;

    private BigDecimal annualInterestRate;

    private Boolean missingCreditDetails;

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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getLastFourDigits() {
        return lastFourDigits;
    }

    public void setLastFourDigits(String lastFourDigits) {
        this.lastFourDigits = lastFourDigits;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }

    public BigDecimal getCurrentDebt() {
        return currentDebt;
    }

    public void setCurrentDebt(BigDecimal currentDebt) {
        this.currentDebt = currentDebt;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public BigDecimal getAvailableCredit() {
        return availableCredit;
    }

    public void setAvailableCredit(BigDecimal availableCredit) {
        this.availableCredit = availableCredit;
    }

    public Integer getStatementDay() {
        return statementDay;
    }

    public void setStatementDay(Integer statementDay) {
        this.statementDay = statementDay;
    }

    public Integer getPaymentDueDay() {
        return paymentDueDay;
    }

    public void setPaymentDueDay(Integer paymentDueDay) {
        this.paymentDueDay = paymentDueDay;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public void setAnnualInterestRate(BigDecimal annualInterestRate) {
        this.annualInterestRate = annualInterestRate;
    }

    public Boolean getMissingCreditDetails() {
        return missingCreditDetails;
    }

    public void setMissingCreditDetails(Boolean missingCreditDetails) {
        this.missingCreditDetails = missingCreditDetails;
    }
}
