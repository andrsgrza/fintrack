package com.fintrack.app.service.budget;

/** Stable boundary failures; the REST layer never exposes persistence implementation details. */
public class BudgetCommandException extends RuntimeException {

    public enum Code {
        INVALID_PAYLOAD,
        IDEMPOTENCY_CONFLICT,
    }

    private final Code code;

    public BudgetCommandException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
