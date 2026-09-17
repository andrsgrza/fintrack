package com.fintrack.app.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;

/** Product-owned credit-card fields accepted only through the parent account workflow. */
public class CreditAccountDetailsConfiguredDTO implements Serializable {

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal creditLimit;

    @NotNull
    @Min(1)
    @Max(31)
    private Integer statementDay;

    @NotNull
    @Min(1)
    @Max(31)
    private Integer paymentDueDay;

    @DecimalMin(value = "0")
    private BigDecimal annualInterestRate;

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
