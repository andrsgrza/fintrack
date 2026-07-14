package com.fintrack.app.service.balance;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import java.math.BigDecimal;
import java.time.LocalDate;

final class BalanceCalculatorTestSupport {

    private BalanceCalculatorTestSupport() {}

    static FinancialAccount account(AccountType accountType, String initialBalance) {
        FinancialAccount account = new FinancialAccount();
        account.setId(1L);
        account.setName("Account");
        account.setAccountType(accountType);
        account.setCurrency(CurrencyCode.MXN);
        account.setInitialBalance(new BigDecimal(initialBalance));
        account.setInitialBalanceDate(LocalDate.of(2026, 1, 1));
        return account;
    }

    static FinancialTransaction transaction(TransactionFlow flow, String amount) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setFlow(flow);
        transaction.setAmount(new BigDecimal(amount));
        return transaction;
    }
}
