package com.fintrack.app.service;

import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.repository.CreditAccountDetailsRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.service.balance.AccountBalanceCalculator;
import com.fintrack.app.service.dto.FinancialAccountBalanceDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FinancialAccountBalanceService {

    private final FinancialAccountService financialAccountService;

    private final FinancialTransactionRepository financialTransactionRepository;

    private final CreditAccountDetailsRepository creditAccountDetailsRepository;

    private final List<AccountBalanceCalculator> calculators;

    public FinancialAccountBalanceService(
        FinancialAccountService financialAccountService,
        FinancialTransactionRepository financialTransactionRepository,
        CreditAccountDetailsRepository creditAccountDetailsRepository,
        List<AccountBalanceCalculator> calculators
    ) {
        this.financialAccountService = financialAccountService;
        this.financialTransactionRepository = financialTransactionRepository;
        this.creditAccountDetailsRepository = creditAccountDetailsRepository;
        this.calculators = calculators;
    }

    public Optional<FinancialAccountBalanceDTO> calculateBalance(Long accountId, LocalDate asOfDate) {
        LocalDate effectiveAsOfDate = asOfDate == null ? LocalDate.now() : asOfDate;

        return financialAccountService
            .findAccessibleAccountEntity(accountId)
            .map(account -> calculateAccessibleBalance(account, effectiveAsOfDate));
    }

    private FinancialAccountBalanceDTO calculateAccessibleBalance(FinancialAccount account, LocalDate asOfDate) {
        List<FinancialTransaction> transactions = financialTransactionRepository.findByAccountIdAndTransactionDateBetween(
            account.getId(),
            account.getInitialBalanceDate(),
            asOfDate
        );
        Optional<CreditAccountDetails> creditAccountDetails = AccountType.CREDIT_CARD.equals(account.getAccountType())
            ? creditAccountDetailsRepository.findOneWithEagerRelationshipsByAccountId(account.getId())
            : Optional.empty();

        return calculators
            .stream()
            .filter(calculator -> calculator.supports(account.getAccountType()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported account type"))
            .calculate(account, transactions, creditAccountDetails, asOfDate);
    }
}
