package com.fintrack.app.service.dashboard;

/** Explicit, transport-safe command failures. The REST boundary deliberately never exposes internal exception text. */
public class PersonalDashboardCommandException extends RuntimeException {

    public enum Code {
        INVALID_PAYLOAD,
        INVALID_QUERY,
        INVALID_LAYOUT,
        NOT_FOUND,
        DUPLICATE_NAME,
        VERSION_CONFLICT,
        IDEMPOTENCY_CONFLICT,
        LIMIT_EXCEEDED,
    }

    private final Code code;

    public PersonalDashboardCommandException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
