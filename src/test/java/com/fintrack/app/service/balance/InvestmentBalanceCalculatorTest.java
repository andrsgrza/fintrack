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

class InvestmentBalanceCalculatorTest {

    private final InvestmentBalanceCalculator calculator = new InvestmentBalanceCalculator();

    @Test
    void supportsInvestmentAccounts() {
        assertThat(calculator.supports(AccountType.INVESTMENT)).isTrue();
        assertThat(calculator.supports(AccountType.DEBIT)).isFalse();
    }

    @Test
    void calculatesCurrentBalanceWithValuationDeferred() {
        var snapshot = calculator.calculate(
            account(AccountType.INVESTMENT, "1000.00"),
            List.of(transaction(TransactionFlow.IN, "250.00"), transaction(TransactionFlow.OUT, "100.00")),
            Optional.empty(),
            LocalDate.of(2026, 1, 31)
        );

        assertThat(snapshot.getInflowTotal()).isEqualByComparingTo("250.00");
        assertThat(snapshot.getOutflowTotal()).isEqualByComparingTo("100.00");
        assertThat(snapshot.getCurrentBalance()).isEqualByComparingTo("1150.00");
        assertThat(snapshot.getCurrentDebt()).isNull();
        assertThat(snapshot.getMissingCreditDetails()).isFalse();
    }
}
