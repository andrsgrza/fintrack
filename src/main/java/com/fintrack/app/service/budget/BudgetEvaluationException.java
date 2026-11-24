package com.fintrack.app.service.budget;

/** Safe read-boundary validation failures for Budget evaluation routes. */
public class BudgetEvaluationException extends RuntimeException {

    public enum Code {
        BUDGET_NOT_FOUND,
        INVALID_PERIOD,
        INVALID_RANGE,
        INVALID_TIME_ZONE,
    }

    private final Code code;

    public BudgetEvaluationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
