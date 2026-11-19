package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BudgetTargetAmountValidatorTest {

    @Test
    void acceptsStrictlyPositiveAmountsWithAtMostTwoSuppliedFractionDigits() {
        assertThat(BudgetTargetAmountValidator.requireValid(new BigDecimal("100"))).isEqualByComparingTo("100");
        assertThat(BudgetTargetAmountValidator.requireValid(new BigDecimal("100.5"))).isEqualByComparingTo("100.5");
        assertThat(BudgetTargetAmountValidator.requireValid(new BigDecimal("100.50"))).isEqualByComparingTo("100.50");
        assertThat(BudgetTargetAmountValidator.requireValid(new BigDecimal("0.01"))).isEqualByComparingTo("0.01");
        assertThat(BudgetTargetAmountValidator.requireValid(new BigDecimal("9999999999999999999.99"))).isEqualByComparingTo(
            "9999999999999999999.99"
        );
    }

    @Test
    void rejectsZeroNegativeExcessScaleAndAmountsOutsideDecimalTwentyOneTwo() {
        assertInvalid("0", "greater than zero");
        assertInvalid("-10.50", "greater than zero");
        assertInvalid("0.001", "at most two decimal places");
        assertInvalid("1.005", "at most two decimal places");
        assertInvalid("100.000", "at most two decimal places");
        assertInvalid("10000000000000000000.00", "supported precision");
    }

    private static void assertInvalid(String amount, String message) {
        assertThatThrownBy(() -> BudgetTargetAmountValidator.requireValid(new BigDecimal(amount)))
            .isInstanceOf(BudgetCommandException.class)
            .hasMessageContaining(message);
    }
}
