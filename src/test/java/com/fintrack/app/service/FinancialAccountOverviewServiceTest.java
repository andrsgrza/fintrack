package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.repository.CreditAccountDetailsRepository;
import com.fintrack.app.service.dto.FinancialAccountBalanceDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialAccountOverviewServiceTest {

    @Mock
    private FinancialAccountService financialAccountService;

    @Mock
    private FinancialAccountBalanceService financialAccountBalanceService;

    @Mock
    private CreditAccountDetailsRepository creditAccountDetailsRepository;

    private FinancialAccountOverviewService financialAccountOverviewService;

    @BeforeEach
    void setUp() {
        financialAccountOverviewService = new FinancialAccountOverviewService(
            financialAccountService,
            financialAccountBalanceService,
            creditAccountDetailsRepository
        );
    }

    @Test
    void returnsProductSummariesIncludingAnIncompleteCreditCardWithoutPersistingAnything() {
        FinancialAccount debit = account(1L, "Daily account", AccountType.DEBIT, true);
        debit.setColor("#2463A5");
        FinancialAccount creditCard = account(2L, "Travel card", AccountType.CREDIT_CARD, false);
        FinancialAccount incompleteCard = account(3L, "Legacy card", AccountType.CREDIT_CARD, true);
        CreditAccountDetails creditDetails = new CreditAccountDetails();
        creditDetails.setAccount(creditCard);
        creditDetails.setCreditLimit(new BigDecimal("5000.00"));
        creditDetails.setStatementDay(15);
        creditDetails.setPaymentDueDay(5);
        creditDetails.setAnnualInterestRate(new BigDecimal("65.00"));

        when(financialAccountService.findAllAccessibleAccountEntities()).thenReturn(List.of(debit, creditCard, incompleteCard));
        when(
            financialAccountBalanceService.calculateBalances(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any())
        ).thenReturn(
            Map.of(
                1L,
                balance("850.00", null, null, null, false),
                2L,
                balance(null, "1200.00", "5000.00", "3800.00", false),
                3L,
                balance(null, "250.00", null, null, true)
            )
        );
        when(creditAccountDetailsRepository.findAllByAccount_IdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(creditDetails));

        var result = financialAccountOverviewService.getOverview();

        assertThat(result).hasSize(3);
        var debitSummary = result.stream().filter(summary -> summary.getId().equals(1L)).findFirst().orElseThrow();
        assertThat(debitSummary.getCurrentBalance()).isEqualByComparingTo("850.00");
        assertThat(debitSummary.getColor()).isEqualTo("#2463A5");
        assertThat(debitSummary.getCurrentDebt()).isNull();
        assertThat(debitSummary.getActive()).isTrue();
        var creditSummary = result.stream().filter(summary -> summary.getId().equals(2L)).findFirst().orElseThrow();
        assertThat(creditSummary.getCurrentDebt()).isEqualByComparingTo("1200.00");
        assertThat(creditSummary.getAvailableCredit()).isEqualByComparingTo("3800.00");
        assertThat(creditSummary.getStatementDay()).isEqualTo(15);
        assertThat(creditSummary.getPaymentDueDay()).isEqualTo(5);
        assertThat(creditSummary.getAnnualInterestRate()).isEqualByComparingTo("65.00");
        assertThat(creditSummary.getActive()).isFalse();
        var incompleteSummary = result.stream().filter(summary -> summary.getId().equals(3L)).findFirst().orElseThrow();
        assertThat(incompleteSummary.getCurrentDebt()).isEqualByComparingTo("250.00");
        assertThat(incompleteSummary.getMissingCreditDetails()).isTrue();
        assertThat(incompleteSummary.getCreditLimit()).isNull();
        assertThat(incompleteSummary.getStatementDay()).isNull();

        verify(financialAccountService).findAllAccessibleAccountEntities();
        verify(financialAccountBalanceService).calculateBalances(
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.any(LocalDate.class)
        );
        verify(creditAccountDetailsRepository).findAllByAccount_IdIn(List.of(1L, 2L, 3L));
    }

    @Test
    void returnsEmptyOverviewWithoutBalanceOrCreditDetailQueries() {
        when(financialAccountService.findAllAccessibleAccountEntities()).thenReturn(List.of());

        assertThat(financialAccountOverviewService.getOverview()).isEmpty();
    }

    private FinancialAccount account(Long id, String name, AccountType accountType, boolean active) {
        FinancialAccount account = new FinancialAccount();
        account.setId(id);
        account.setName(name);
        account.setAccountType(accountType);
        account.setCurrency(CurrencyCode.MXN);
        account.setActive(active);
        account.setLastFourDigits("1234");
        return account;
    }

    private FinancialAccountBalanceDTO balance(
        String currentBalance,
        String currentDebt,
        String creditLimit,
        String availableCredit,
        boolean missingCreditDetails
    ) {
        FinancialAccountBalanceDTO balance = new FinancialAccountBalanceDTO();
        balance.setCurrentBalance(currentBalance == null ? null : new BigDecimal(currentBalance));
        balance.setCurrentDebt(currentDebt == null ? null : new BigDecimal(currentDebt));
        balance.setCreditLimit(creditLimit == null ? null : new BigDecimal(creditLimit));
        balance.setAvailableCredit(availableCredit == null ? null : new BigDecimal(availableCredit));
        balance.setMissingCreditDetails(missingCreditDetails);
        return balance;
    }
}
