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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    /**
     * Calculates snapshots for a known set of accessible accounts using batch reads. This intentionally avoids one
     * transaction query and one credit-detail query per account when rendering a product overview.
     */
    public Map<Long, FinancialAccountBalanceDTO> calculateBalances(Collection<FinancialAccount> accounts, LocalDate asOfDate) {
        if (accounts == null || accounts.isEmpty()) {
            return Collections.emptyMap();
        }

        LocalDate effectiveAsOfDate = asOfDate == null ? LocalDate.now() : asOfDate;
        List<FinancialAccount> accountList = List.copyOf(accounts);
        List<Long> accountIds = accountList.stream().map(FinancialAccount::getId).toList();
        Map<Long, FinancialAccount> accountsById = accountList
            .stream()
            .collect(Collectors.toMap(FinancialAccount::getId, Function.identity()));
        Map<Long, List<FinancialTransaction>> transactionsByAccountId = financialTransactionRepository
            .findByAccountIdInAndTransactionDateLessThanEqual(accountIds, effectiveAsOfDate)
            .stream()
            .filter(transaction -> {
                FinancialAccount account = transaction.getAccount();
                FinancialAccount overviewAccount = account == null ? null : accountsById.get(account.getId());
                return overviewAccount != null && !transaction.getTransactionDate().isBefore(overviewAccount.getInitialBalanceDate());
            })
            .collect(Collectors.groupingBy(transaction -> transaction.getAccount().getId()));
        Map<Long, CreditAccountDetails> creditDetailsByAccountId = creditAccountDetailsRepository
            .findAllByAccount_IdIn(accountIds)
            .stream()
            .collect(Collectors.toMap(details -> details.getAccount().getId(), Function.identity()));

        Map<Long, FinancialAccountBalanceDTO> balances = new LinkedHashMap<>();
        for (FinancialAccount account : accountList) {
            Optional<CreditAccountDetails> creditDetails = AccountType.CREDIT_CARD.equals(account.getAccountType())
                ? Optional.ofNullable(creditDetailsByAccountId.get(account.getId()))
                : Optional.empty();
            FinancialAccountBalanceDTO snapshot = calculators
                .stream()
                .filter(calculator -> calculator.supports(account.getAccountType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported account type"))
                .calculate(account, transactionsByAccountId.getOrDefault(account.getId(), List.of()), creditDetails, effectiveAsOfDate);
            balances.put(account.getId(), snapshot);
        }
        return balances;
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
