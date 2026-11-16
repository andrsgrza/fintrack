package com.fintrack.app.web.rest;

import com.fintrack.app.service.budget.BudgetCommandException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps Budget command failures safe and transport-stable. */
@RestControllerAdvice(assignableTypes = BudgetResource.class)
public class BudgetExceptionHandler {

    @ExceptionHandler(BudgetCommandException.class)
    public ResponseEntity<BudgetError> command(BudgetCommandException exception) {
        HttpStatus status =
            switch (exception.getCode()) {
                case IDEMPOTENCY_CONFLICT, VERSION_CONFLICT -> HttpStatus.CONFLICT;
                case INVALID_PAYLOAD -> HttpStatus.BAD_REQUEST;
            };
        return ResponseEntity.status(status).body(new BudgetError(exception.getCode().name(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BudgetError> malformed() {
        return ResponseEntity.badRequest().body(new BudgetError("INVALID_PAYLOAD", "The request payload is not valid."));
    }

    /** A database-level race is the same client action as a stale expectedVersion: reload before trying again. */
    @ExceptionHandler({ ObjectOptimisticLockingFailureException.class, OptimisticLockException.class })
    public ResponseEntity<BudgetError> concurrentMutation() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            new BudgetError("VERSION_CONFLICT", "This Budget was updated elsewhere. Reload it before saving.")
        );
    }

    public record BudgetError(String code, String message) {}
}
