package com.fintrack.app.service.transfer;

/**
 * Deliberately carries only a stable code to the HTTP boundary. Detailed Java
 * messages are kept out of product responses so clients do not depend on them.
 */
public class InternalTransferCommandException extends RuntimeException {

    private final InternalTransferErrorCode code;

    public InternalTransferCommandException(InternalTransferErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public InternalTransferErrorCode getCode() {
        return code;
    }
}
