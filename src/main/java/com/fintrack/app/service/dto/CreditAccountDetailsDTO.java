package com.fintrack.app.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.fintrack.app.domain.CreditAccountDetails} entity.
 */
@Schema(description = "Additional data that exists only for CREDIT_CARD accounts.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CreditAccountDetailsDTO implements Serializable {

    private Long id;

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal creditLimit;

    @NotNull
    @Min(value = 1)
    @Max(value = 31)
    private Integer statementDay;

    @NotNull
    @Min(value = 1)
    @Max(value = 31)
    private Integer paymentDueDay;

    @DecimalMin(value = "0")
    private BigDecimal annualInterestRate;

    private Instant createdAt;

    private Instant updatedAt;

    @NotNull
    private FinancialAccountDTO account;

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

    public FinancialAccountDTO getAccount() {
        return account;
    }

    public void setAccount(FinancialAccountDTO account) {
        this.account = account;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CreditAccountDetailsDTO)) {
            return false;
        }

        CreditAccountDetailsDTO creditAccountDetailsDTO = (CreditAccountDetailsDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, creditAccountDetailsDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CreditAccountDetailsDTO{" +
            "id=" + getId() +
            ", creditLimit=" + getCreditLimit() +
            ", statementDay=" + getStatementDay() +
            ", paymentDueDay=" + getPaymentDueDay() +
            ", annualInterestRate=" + getAnnualInterestRate() +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", account=" + getAccount() +
            "}";
    }
}
