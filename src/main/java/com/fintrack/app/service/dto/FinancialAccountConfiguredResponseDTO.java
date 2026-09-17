package com.fintrack.app.service.dto;

import java.io.Serializable;

/** Result of the atomic FinancialAccount configured workflow. */
public class FinancialAccountConfiguredResponseDTO implements Serializable {

    private FinancialAccountConfiguredAccountResponseDTO financialAccount;

    private CreditAccountDetailsConfiguredResponseDTO creditAccountDetails;

    public FinancialAccountConfiguredAccountResponseDTO getFinancialAccount() {
        return financialAccount;
    }

    public void setFinancialAccount(FinancialAccountConfiguredAccountResponseDTO financialAccount) {
        this.financialAccount = financialAccount;
    }

    public CreditAccountDetailsConfiguredResponseDTO getCreditAccountDetails() {
        return creditAccountDetails;
    }

    public void setCreditAccountDetails(CreditAccountDetailsConfiguredResponseDTO creditAccountDetails) {
        this.creditAccountDetails = creditAccountDetails;
    }
}
