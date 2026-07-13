package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read model for a calculated financial account balance snapshot.
 *
 * Balances are calculated on demand and are not persisted.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class FinancialAccountBalanceDTO implements Serializable {

    private Long accountId;

    private String accountName;

    private AccountType accountType;

    private CurrencyCode currency;

    private BigDecimal initialBalance;

    private LocalDate initialBalanceDate;

    private LocalDate asOfDate;

    private BigDecimal inflowTotal;

    private BigDecimal outflowTotal;

    private BigDecimal currentBalance;

    private BigDecimal currentDebt;

    private BigDecimal creditLimit;

    private BigDecimal availableCredit;

    private Boolean missingCreditDetails;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
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

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }

    public BigDecimal getInflowTotal() {
        return inflowTotal;
    }

    public void setInflowTotal(BigDecimal inflowTotal) {
        this.inflowTotal = inflowTotal;
    }

    public BigDecimal getOutflowTotal() {
        return outflowTotal;
    }

    public void setOutflowTotal(BigDecimal outflowTotal) {
        this.outflowTotal = outflowTotal;
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

    public Boolean getMissingCreditDetails() {
        return missingCreditDetails;
    }

    public void setMissingCreditDetails(Boolean missingCreditDetails) {
        this.missingCreditDetails = missingCreditDetails;
    }
}
