package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Additional data that exists only for CREDIT_CARD accounts.
 */
@Entity
@Table(name = "credit_account_details")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CreditAccountDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @DecimalMin(value = "0")
    @Column(name = "credit_limit", precision = 21, scale = 2, nullable = false)
    private BigDecimal creditLimit;

    @NotNull
    @Min(value = 1)
    @Max(value = 31)
    @Column(name = "statement_day", nullable = false)
    private Integer statementDay;

    @NotNull
    @Min(value = 1)
    @Max(value = 31)
    @Column(name = "payment_due_day", nullable = false)
    private Integer paymentDueDay;

    @DecimalMin(value = "0")
    @Column(name = "annual_interest_rate", precision = 21, scale = 2)
    private BigDecimal annualInterestRate;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @JsonIgnoreProperties(
        value = { "user", "creditAccountDetails", "financialTransactions", "subscriptions", "budgets", "transactionIngestions" },
        allowSetters = true
    )
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @NotNull
    @JoinColumn(unique = true)
    private FinancialAccount account;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public CreditAccountDetails id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getCreditLimit() {
        return this.creditLimit;
    }

    public CreditAccountDetails creditLimit(BigDecimal creditLimit) {
        this.setCreditLimit(creditLimit);
        return this;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public Integer getStatementDay() {
        return this.statementDay;
    }

    public CreditAccountDetails statementDay(Integer statementDay) {
        this.setStatementDay(statementDay);
        return this;
    }

    public void setStatementDay(Integer statementDay) {
        this.statementDay = statementDay;
    }

    public Integer getPaymentDueDay() {
        return this.paymentDueDay;
    }

    public CreditAccountDetails paymentDueDay(Integer paymentDueDay) {
        this.setPaymentDueDay(paymentDueDay);
        return this;
    }

    public void setPaymentDueDay(Integer paymentDueDay) {
        this.paymentDueDay = paymentDueDay;
    }

    public BigDecimal getAnnualInterestRate() {
        return this.annualInterestRate;
    }

    public CreditAccountDetails annualInterestRate(BigDecimal annualInterestRate) {
        this.setAnnualInterestRate(annualInterestRate);
        return this;
    }

    public void setAnnualInterestRate(BigDecimal annualInterestRate) {
        this.annualInterestRate = annualInterestRate;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public CreditAccountDetails createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public CreditAccountDetails updatedAt(Instant updatedAt) {
        this.setUpdatedAt(updatedAt);
        return this;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public FinancialAccount getAccount() {
        return this.account;
    }

    public void setAccount(FinancialAccount financialAccount) {
        this.account = financialAccount;
    }

    public CreditAccountDetails account(FinancialAccount financialAccount) {
        this.setAccount(financialAccount);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CreditAccountDetails)) {
            return false;
        }
        return getId() != null && getId().equals(((CreditAccountDetails) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CreditAccountDetails{" +
            "id=" + getId() +
            ", creditLimit=" + getCreditLimit() +
            ", statementDay=" + getStatementDay() +
            ", paymentDueDay=" + getPaymentDueDay() +
            ", annualInterestRate=" + getAnnualInterestRate() +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            "}";
    }
}
