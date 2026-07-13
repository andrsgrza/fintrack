package com.fintrack.app.service.balance;

import static com.fintrack.app.service.balance.BalanceCalculatorTestSupport.account;
import static com.fintrack.app.service.balance.BalanceCalculatorTestSupport.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CreditCardBalanceCalculatorTest {

    private final CreditCardBalanceCalculator calculator = new CreditCardBalanceCalculator();

    @Test
    void supportsCreditCardAccounts() {
        assertThat(calculator.supports(AccountType.CREDIT_CARD)).isTrue();
        assertThat(calculator.supports(AccountType.DEBIT)).isFalse();
    }

    @Test
    void outflowIncreasesAndInflowDecreasesCurrentDebt() {
        var snapshot = calculator.calculate(
            account(AccountType.CREDIT_CARD, "1000.00"),
            List.of(
                transaction(TransactionFlow.OUT, "500.00"),
                transaction(TransactionFlow.OUT, "100.00"),
                transaction(TransactionFlow.IN, "250.00")
            ),
            Optional.of(details("5000.00")),
            LocalDate.of(2026, 1, 31)
        );

        assertThat(snapshot.getInflowTotal()).isEqualByComparingTo("250.00");
        assertThat(snapshot.getOutflowTotal()).isEqualByComparingTo("600.00");
        assertThat(snapshot.getCurrentDebt()).isEqualByComparingTo("1350.00");
        assertThat(snapshot.getCurrentBalance()).isNull();
    }

    @Test
    void negativeInitialBalanceMeansCreditBalance() {
        var snapshot = calculator.calculate(
            account(AccountType.CREDIT_CARD, "-200.00"),
            List.of(transaction(TransactionFlow.OUT, "50.00")),
            Optional.of(details("1000.00")),
            LocalDate.of(2026, 1, 31)
        );

        assertThat(snapshot.getCurrentDebt()).isEqualByComparingTo("-150.00");
        assertThat(snapshot.getAvailableCredit()).isEqualByComparingTo("1150.00");
    }

    @Test
    void creditLimitProducesAvailableCredit() {
        var snapshot = calculator.calculate(
            account(AccountType.CREDIT_CARD, "1000.00"),
            List.of(transaction(TransactionFlow.OUT, "500.00")),
            Optional.of(details("5000.00")),
            LocalDate.of(2026, 1, 31)
        );

        assertThat(snapshot.getCreditLimit()).isEqualByComparingTo("5000.00");
        assertThat(snapshot.getAvailableCredit()).isEqualByComparingTo("3500.00");
        assertThat(snapshot.getMissingCreditDetails()).isFalse();
    }

    @Test
    void availableCreditCanBeNegative() {
        var snapshot = calculator.calculate(
            account(AccountType.CREDIT_CARD, "6000.00"),
            List.of(transaction(TransactionFlow.OUT, "500.00")),
            Optional.of(details("5000.00")),
            LocalDate.of(2026, 1, 31)
        );

        assertThat(snapshot.getCurrentDebt()).isEqualByComparingTo("6500.00");
        assertThat(snapshot.getAvailableCredit()).isEqualByComparingTo("-1500.00");
    }

    @Test
    void missingDetailsReturnsMissingCreditDetailsAndNullCreditFields() {
        var snapshot = calculator.calculate(
            account(AccountType.CREDIT_CARD, "1000.00"),
            List.of(transaction(TransactionFlow.OUT, "500.00")),
            Optional.empty(),
            LocalDate.of(2026, 1, 31)
        );

        assertThat(snapshot.getCurrentDebt()).isEqualByComparingTo("1500.00");
        assertThat(snapshot.getCreditLimit()).isNull();
        assertThat(snapshot.getAvailableCredit()).isNull();
        assertThat(snapshot.getMissingCreditDetails()).isTrue();
    }

    private CreditAccountDetails details(String creditLimit) {
        CreditAccountDetails details = new CreditAccountDetails();
        details.setCreditLimit(new BigDecimal(creditLimit));
        return details;
    }
}
