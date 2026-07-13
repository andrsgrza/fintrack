package com.fintrack.app.service.balance;

import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.service.dto.FinancialAccountBalanceDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CashBalanceCalculator implements AccountBalanceCalculator {

    @Override
    public boolean supports(AccountType accountType) {
        return AccountType.CASH.equals(accountType);
    }

    @Override
    public FinancialAccountBalanceDTO calculate(
        FinancialAccount account,
        List<FinancialTransaction> transactions,
        Optional<CreditAccountDetails> creditAccountDetails,
        LocalDate asOfDate
    ) {
        FinancialAccountBalanceDTO snapshot = baseSnapshot(account, transactions, asOfDate);
        snapshot.setCurrentBalance(account.getInitialBalance().add(snapshot.getInflowTotal()).subtract(snapshot.getOutflowTotal()));
        snapshot.setMissingCreditDetails(false);
        return snapshot;
    }
}
