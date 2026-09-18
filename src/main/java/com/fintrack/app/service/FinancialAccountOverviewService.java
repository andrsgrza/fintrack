package com.fintrack.app.service;

import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.repository.CreditAccountDetailsRepository;
import com.fintrack.app.service.dto.FinancialAccountBalanceDTO;
import com.fintrack.app.service.dto.FinancialAccountOverviewDTO;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the read-only Financial Account product overview from accessible accounts and calculated balance snapshots.
 */
@Service
@Transactional(readOnly = true)
public class FinancialAccountOverviewService {

    private final FinancialAccountService financialAccountService;

    private final FinancialAccountBalanceService financialAccountBalanceService;

    private final CreditAccountDetailsRepository creditAccountDetailsRepository;

    public FinancialAccountOverviewService(
        FinancialAccountService financialAccountService,
        FinancialAccountBalanceService financialAccountBalanceService,
        CreditAccountDetailsRepository creditAccountDetailsRepository
    ) {
        this.financialAccountService = financialAccountService;
        this.financialAccountBalanceService = financialAccountBalanceService;
        this.creditAccountDetailsRepository = creditAccountDetailsRepository;
    }

    public List<FinancialAccountOverviewDTO> getOverview() {
        List<FinancialAccount> accounts = financialAccountService.findAllAccessibleAccountEntities();
        if (accounts.isEmpty()) {
            return List.of();
        }

        Map<Long, FinancialAccountBalanceDTO> balances = financialAccountBalanceService.calculateBalances(accounts, LocalDate.now());
        Collection<Long> accountIds = accounts.stream().map(FinancialAccount::getId).toList();
        Map<Long, CreditAccountDetails> creditDetailsByAccountId = creditAccountDetailsRepository
            .findAllByAccount_IdIn(accountIds)
            .stream()
            .collect(Collectors.toMap(details -> details.getAccount().getId(), Function.identity()));

        return accounts
            .stream()
            .map(account -> toOverview(account, balances.get(account.getId()), creditDetailsByAccountId.get(account.getId())))
            .toList();
    }

    private FinancialAccountOverviewDTO toOverview(
        FinancialAccount account,
        FinancialAccountBalanceDTO balance,
        CreditAccountDetails creditAccountDetails
    ) {
        FinancialAccountOverviewDTO overview = new FinancialAccountOverviewDTO();
        overview.setId(account.getId());
        overview.setName(account.getName());
        overview.setAccountType(account.getAccountType());
        overview.setCurrency(account.getCurrency());
        overview.setActive(account.getActive());
        overview.setLastFourDigits(account.getLastFourDigits());
        overview.setColor(account.getColor());

        if (balance != null) {
            overview.setCurrentBalance(balance.getCurrentBalance());
            overview.setCurrentDebt(balance.getCurrentDebt());
            overview.setCreditLimit(balance.getCreditLimit());
            overview.setAvailableCredit(balance.getAvailableCredit());
            overview.setMissingCreditDetails(balance.getMissingCreditDetails());
        }
        if (creditAccountDetails != null) {
            overview.setStatementDay(creditAccountDetails.getStatementDay());
            overview.setPaymentDueDay(creditAccountDetails.getPaymentDueDay());
            overview.setAnnualInterestRate(creditAccountDetails.getAnnualInterestRate());
        }
        return overview;
    }
}
