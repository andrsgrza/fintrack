package com.fintrack.app.service.balance;

import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.service.dto.FinancialAccountBalanceDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CreditCardBalanceCalculator implements AccountBalanceCalculator {

    @Override
    public boolean supports(AccountType accountType) {
        return AccountType.CREDIT_CARD.equals(accountType);
    }

    @Override
    public FinancialAccountBalanceDTO calculate(
        FinancialAccount account,
        List<FinancialTransaction> transactions,
        Optional<CreditAccountDetails> creditAccountDetails,
        LocalDate asOfDate
    ) {
        FinancialAccountBalanceDTO snapshot = baseSnapshot(account, transactions, asOfDate);
        BigDecimal currentDebt = account.getInitialBalance().add(snapshot.getOutflowTotal()).subtract(snapshot.getInflowTotal());
        snapshot.setCurrentDebt(currentDebt);

        if (creditAccountDetails.isPresent()) {
            BigDecimal creditLimit = creditAccountDetails.orElseThrow().getCreditLimit();
            snapshot.setCreditLimit(creditLimit);
            snapshot.setAvailableCredit(creditLimit == null ? null : creditLimit.subtract(currentDebt));
            snapshot.setMissingCreditDetails(false);
        } else {
            snapshot.setMissingCreditDetails(true);
        }

        return snapshot;
    }
}
