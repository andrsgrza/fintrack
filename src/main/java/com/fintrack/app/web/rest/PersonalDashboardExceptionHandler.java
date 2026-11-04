package com.fintrack.app.web.rest;

import com.fintrack.app.service.dashboard.PersonalDashboardCommandException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps personal-dashboard validation and conflict details stable without returning parser/JPA implementation text. */
@RestControllerAdvice(assignableTypes = PersonalDashboardResource.class)
public class PersonalDashboardExceptionHandler {

    @ExceptionHandler(PersonalDashboardCommandException.class)
    public ResponseEntity<PersonalDashboardError> command(PersonalDashboardCommandException exception) {
        HttpStatus status =
            switch (exception.getCode()) {
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case DUPLICATE_NAME, VERSION_CONFLICT, IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
                case INVALID_PAYLOAD, INVALID_QUERY, INVALID_LAYOUT, LIMIT_EXCEEDED -> HttpStatus.BAD_REQUEST;
            };
        return ResponseEntity.status(status).body(new PersonalDashboardError(exception.getCode().name(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<PersonalDashboardError> malformed() {
        return ResponseEntity.badRequest().body(new PersonalDashboardError("INVALID_PAYLOAD", "The request payload is not valid."));
    }
}
