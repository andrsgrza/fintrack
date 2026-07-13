package com.fintrack.app.service.balance;

import static com.fintrack.app.service.balance.BalanceCalculatorTestSupport.account;
import static com.fintrack.app.service.balance.BalanceCalculatorTestSupport.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DebitBalanceCalculatorTest {

    private final DebitBalanceCalculator calculator = new DebitBalanceCalculator();

    @Test
    void supportsDebitAccounts() {
        assertThat(calculator.supports(AccountType.DEBIT)).isTrue();
        assertThat(calculator.supports(AccountType.CASH)).isFalse();
    }

    @Test
    void calculatesPositiveInitialBalance() {
        var snapshot = calculator.calculate(account(AccountType.DEBIT, "100.00"), List.of(), Optional.empty(), LocalDate.of(2026, 1, 31));

        assertThat(snapshot.getCurrentBalance()).isEqualByComparingTo("100.00");
        assertThat(snapshot.getInflowTotal()).isEqualByComparingTo("0");
        assertThat(snapshot.getOutflowTotal()).isEqualByComparingTo("0");
    }

    @Test
    void calculatesZeroInitialBalance() {
        var snapshot = calculator.calculate(account(AccountType.DEBIT, "0.00"), List.of(), Optional.empty(), LocalDate.of(2026, 1, 31));

        assertThat(snapshot.getCurrentBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void calculatesNegativeInitialBalance() {
        var snapshot = calculator.calculate(account(AccountType.DEBIT, "-50.00"), List.of(), Optional.empty(), LocalDate.of(2026, 1, 31));

        assertThat(snapshot.getCurrentBalance()).isEqualByComparingTo("-50.00");
    }

    @Test
    void inflowIncreasesAndOutflowDecreasesBalance() {
        var snapshot = calculator.calculate(
            account(AccountType.DEBIT, "100.00"),
            List.of(
                transaction(TransactionFlow.IN, "25.00"),
                transaction(TransactionFlow.IN, "5.00"),
                transaction(TransactionFlow.OUT, "40.00")
            ),
            Optional.empty(),
            LocalDate.of(2026, 1, 31)
        );

        assertThat(snapshot.getInflowTotal()).isEqualByComparingTo("30.00");
        assertThat(snapshot.getOutflowTotal()).isEqualByComparingTo("40.00");
        assertThat(snapshot.getCurrentBalance()).isEqualByComparingTo("90.00");
        assertThat(snapshot.getCurrentDebt()).isNull();
        assertThat(snapshot.getMissingCreditDetails()).isFalse();
    }
}
