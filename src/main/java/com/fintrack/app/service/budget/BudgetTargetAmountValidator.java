package com.fintrack.app.service.budget;

import java.math.BigDecimal;

/** Single authoritative validation for immutable Budget expense limits and income targets. */
public final class BudgetTargetAmountValidator {

    public static final int MAX_PRECISION = 21;
    public static final int MAX_SCALE = 2;
    public static final int MAX_INTEGER_DIGITS = MAX_PRECISION - MAX_SCALE;

    private BudgetTargetAmountValidator() {}

    /**
     * Validates without normalization. In particular, {@code 100.000} retains scale three and is rejected instead
     * of being silently changed into {@code 100.00}.
     */
    public static BigDecimal requireValid(BigDecimal amount) {
        if (amount == null) throw invalid("Budget target amount is required.");
        if (amount.signum() <= 0) throw invalid("Budget target amount must be greater than zero.");
        if (amount.scale() < 0 || amount.scale() > MAX_SCALE) {
            throw invalid("Budget target amount supports at most two decimal places.");
        }
        int integerDigits = Math.max(0, amount.precision() - amount.scale());
        if (amount.precision() > MAX_PRECISION || integerDigits > MAX_INTEGER_DIGITS) {
            throw invalid("Budget target amount exceeds the supported precision.");
        }
        return amount;
    }

    private static BudgetCommandException invalid(String message) {
        return new BudgetCommandException(BudgetCommandException.Code.INVALID_PAYLOAD, message);
    }
}
