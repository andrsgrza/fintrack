package com.fintrack.app.service.balance;

import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.service.dto.FinancialAccountBalanceDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AccountBalanceCalculator {
    boolean supports(AccountType accountType);

    FinancialAccountBalanceDTO calculate(
        FinancialAccount account,
        List<FinancialTransaction> transactions,
        Optional<CreditAccountDetails> creditAccountDetails,
        LocalDate asOfDate
    );

    default FinancialAccountBalanceDTO baseSnapshot(FinancialAccount account, List<FinancialTransaction> transactions, LocalDate asOfDate) {
        FinancialAccountBalanceDTO snapshot = new FinancialAccountBalanceDTO();
        snapshot.setAccountId(account.getId());
        snapshot.setAccountName(account.getName());
        snapshot.setAccountType(account.getAccountType());
        snapshot.setCurrency(account.getCurrency());
        snapshot.setInitialBalance(account.getInitialBalance());
        snapshot.setInitialBalanceDate(account.getInitialBalanceDate());
        snapshot.setAsOfDate(asOfDate);
        snapshot.setInflowTotal(sumByFlow(transactions, TransactionFlow.IN));
        snapshot.setOutflowTotal(sumByFlow(transactions, TransactionFlow.OUT));
        return snapshot;
    }

    private BigDecimal sumByFlow(List<FinancialTransaction> transactions, TransactionFlow flow) {
        return transactions
            .stream()
            .filter(transaction -> flow.equals(transaction.getFlow()))
            .map(FinancialTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
