package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/** Product-safe contextual credit-card details returned by the configured account workflow. */
public class CreditAccountDetailsConfiguredResponseDTO implements Serializable {

    private Long id;
    private BigDecimal creditLimit;
    private Integer statementDay;
    private Integer paymentDueDay;
    private BigDecimal annualInterestRate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
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
}
