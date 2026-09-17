package com.fintrack.app.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/** Atomic product command payload for a FinancialAccount and its contextual credit-card details. */
public class FinancialAccountConfiguredRequestDTO implements Serializable {

    @NotNull
    @Valid
    private FinancialAccountConfiguredAccountDTO financialAccount;

    @Valid
    private CreditAccountDetailsConfiguredDTO creditAccountDetails;

    public FinancialAccountConfiguredAccountDTO getFinancialAccount() {
        return financialAccount;
    }

    public void setFinancialAccount(FinancialAccountConfiguredAccountDTO financialAccount) {
        this.financialAccount = financialAccount;
    }

    public CreditAccountDetailsConfiguredDTO getCreditAccountDetails() {
        return creditAccountDetails;
    }

    public void setCreditAccountDetails(CreditAccountDetailsConfiguredDTO creditAccountDetails) {
        this.creditAccountDetails = creditAccountDetails;
    }
}
