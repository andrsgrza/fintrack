package com.fintrack.app.web.rest;

import com.fintrack.app.service.budget.BudgetCommandException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps Budget command failures safe and transport-stable. */
@RestControllerAdvice(assignableTypes = BudgetResource.class)
public class BudgetExceptionHandler {

    @ExceptionHandler(BudgetCommandException.class)
    public ResponseEntity<BudgetError> command(BudgetCommandException exception) {
        HttpStatus status = exception.getCode() == BudgetCommandException.Code.IDEMPOTENCY_CONFLICT
            ? HttpStatus.CONFLICT
            : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new BudgetError(exception.getCode().name(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BudgetError> malformed() {
        return ResponseEntity.badRequest().body(new BudgetError("INVALID_PAYLOAD", "The request payload is not valid."));
    }

    public record BudgetError(String code, String message) {}
}
