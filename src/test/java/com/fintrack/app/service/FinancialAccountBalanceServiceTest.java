package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.repository.CreditAccountDetailsRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.service.balance.CashBalanceCalculator;
import com.fintrack.app.service.balance.CreditCardBalanceCalculator;
import com.fintrack.app.service.balance.DebitBalanceCalculator;
import com.fintrack.app.service.balance.InvestmentBalanceCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialAccountBalanceServiceTest {

    @Mock
    private FinancialAccountService financialAccountService;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private CreditAccountDetailsRepository creditAccountDetailsRepository;

    private FinancialAccountBalanceService financialAccountBalanceService;

    @BeforeEach
    void setUp() {
        financialAccountBalanceService = new FinancialAccountBalanceService(
            financialAccountService,
            financialTransactionRepository,
            creditAccountDetailsRepository,
            List.of(
                new DebitBalanceCalculator(),
                new CashBalanceCalculator(),
                new CreditCardBalanceCalculator(),
                new InvestmentBalanceCalculator()
            )
        );
    }

    @Test
    void ownAccessibleAccountResolves() {
        FinancialAccount account = account(AccountType.DEBIT, "100.00");

        when(financialAccountService.findAccessibleAccountEntity(1L)).thenReturn(Optional.of(account));
        when(
            financialTransactionRepository.findByAccountIdAndTransactionDateBetween(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        ).thenReturn(List.of(transaction(TransactionFlow.IN, "25.00")));

        var result = financialAccountBalanceService.calculateBalance(1L, LocalDate.of(2026, 1, 31));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getCurrentBalance()).isEqualByComparingTo("125.00");
    }

    @Test
    void normalUserCannotAccessForeignAccountWhenAccessibleLookupReturnsEmpty() {
        when(financialAccountService.findAccessibleAccountEntity(1L)).thenReturn(Optional.empty());

        var result = financialAccountBalanceService.calculateBalance(1L, LocalDate.of(2026, 1, 31));

        assertThat(result).isEmpty();
    }

    @Test
    void adminCanAccessForeignAccountWhenAccessibleLookupReturnsAccount() {
        FinancialAccount account = account(AccountType.DEBIT, "100.00");

        when(financialAccountService.findAccessibleAccountEntity(1L)).thenReturn(Optional.of(account));
        when(
            financialTransactionRepository.findByAccountIdAndTransactionDateBetween(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        ).thenReturn(List.of());

        var result = financialAccountBalanceService.calculateBalance(1L, LocalDate.of(2026, 1, 31));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getCurrentBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void noTransactionsReturnsInitialBalanceBasedResult() {
        FinancialAccount account = account(AccountType.DEBIT, "-25.00");

        when(financialAccountService.findAccessibleAccountEntity(1L)).thenReturn(Optional.of(account));
        when(
            financialTransactionRepository.findByAccountIdAndTransactionDateBetween(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        ).thenReturn(List.of());

        var result = financialAccountBalanceService.calculateBalance(1L, LocalDate.of(2026, 1, 31)).orElseThrow();

        assertThat(result.getInflowTotal()).isEqualByComparingTo("0");
        assertThat(result.getOutflowTotal()).isEqualByComparingTo("0");
        assertThat(result.getCurrentBalance()).isEqualByComparingTo("-25.00");
    }

    @Test
    void loadsTransactionsFromInitialBalanceDateThroughAsOfDate() {
        FinancialAccount account = account(AccountType.DEBIT, "100.00");

        when(financialAccountService.findAccessibleAccountEntity(1L)).thenReturn(Optional.of(account));
        when(
            financialTransactionRepository.findByAccountIdAndTransactionDateBetween(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15))
        ).thenReturn(List.of(transaction(TransactionFlow.OUT, "10.00")));

        var result = financialAccountBalanceService.calculateBalance(1L, LocalDate.of(2026, 1, 15)).orElseThrow();

        assertThat(result.getCurrentBalance()).isEqualByComparingTo("90.00");
        verify(financialTransactionRepository).findByAccountIdAndTransactionDateBetween(
            1L,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 15)
        );
    }

    @Test
    void inactiveAccountStillCalculates() {
        FinancialAccount account = account(AccountType.DEBIT, "100.00");
        account.setActive(false);

        when(financialAccountService.findAccessibleAccountEntity(1L)).thenReturn(Optional.of(account));
        when(
            financialTransactionRepository.findByAccountIdAndTransactionDateBetween(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        ).thenReturn(List.of(transaction(TransactionFlow.OUT, "10.00")));

        var result = financialAccountBalanceService.calculateBalance(1L, LocalDate.of(2026, 1, 31)).orElseThrow();

        assertThat(result.getCurrentBalance()).isEqualByComparingTo("90.00");
    }

    @Test
    void creditCardWithoutDetailsReturnsCurrentDebtAndMissingCreditDetails() {
        FinancialAccount account = account(AccountType.CREDIT_CARD, "1000.00");

        when(financialAccountService.findAccessibleAccountEntity(1L)).thenReturn(Optional.of(account));
        when(
            financialTransactionRepository.findByAccountIdAndTransactionDateBetween(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        ).thenReturn(List.of(transaction(TransactionFlow.OUT, "500.00")));
        when(creditAccountDetailsRepository.findOneWithEagerRelationshipsByAccountId(1L)).thenReturn(Optional.empty());

        var result = financialAccountBalanceService.calculateBalance(1L, LocalDate.of(2026, 1, 31)).orElseThrow();

        assertThat(result.getCurrentDebt()).isEqualByComparingTo("1500.00");
        assertThat(result.getMissingCreditDetails()).isTrue();
        assertThat(result.getCreditLimit()).isNull();
        assertThat(result.getAvailableCredit()).isNull();
    }

    @Test
    void creditCardWithDetailsReturnsCreditLimitAndAvailableCredit() {
        FinancialAccount account = account(AccountType.CREDIT_CARD, "1000.00");
        CreditAccountDetails details = new CreditAccountDetails();
        details.setCreditLimit(new BigDecimal("5000.00"));

        when(financialAccountService.findAccessibleAccountEntity(1L)).thenReturn(Optional.of(account));
        when(
            financialTransactionRepository.findByAccountIdAndTransactionDateBetween(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        ).thenReturn(List.of(transaction(TransactionFlow.OUT, "500.00")));
        when(creditAccountDetailsRepository.findOneWithEagerRelationshipsByAccountId(1L)).thenReturn(Optional.of(details));

        var result = financialAccountBalanceService.calculateBalance(1L, LocalDate.of(2026, 1, 31)).orElseThrow();

        assertThat(result.getCurrentDebt()).isEqualByComparingTo("1500.00");
        assertThat(result.getCreditLimit()).isEqualByComparingTo("5000.00");
        assertThat(result.getAvailableCredit()).isEqualByComparingTo("3500.00");
        assertThat(result.getMissingCreditDetails()).isFalse();
    }

    private FinancialAccount account(AccountType accountType, String initialBalance) {
        FinancialAccount account = new FinancialAccount();
        account.setId(1L);
        account.setName("Account");
        account.setAccountType(accountType);
        account.setCurrency(CurrencyCode.MXN);
        account.setInitialBalance(new BigDecimal(initialBalance));
        account.setInitialBalanceDate(LocalDate.of(2026, 1, 1));
        account.setActive(true);
        return account;
    }

    private FinancialTransaction transaction(TransactionFlow flow, String amount) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setFlow(flow);
        transaction.setAmount(new BigDecimal(amount));
        return transaction;
    }
}
