package com.fintrack.app.web.rest;

import com.fintrack.app.service.analytics.AnalyticsDiagnostic;
import com.fintrack.app.service.analytics.AnalyticsDiagnosticCode;
import com.fintrack.app.service.analytics.SemanticAnalyticsDslException;
import com.fintrack.app.service.analytics.SemanticAnalyticsValidationException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps malformed and invalid semantic-query requests out of the generic 500/error path. */
@RestControllerAdvice(assignableTypes = SemanticAnalyticsResource.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SemanticAnalyticsExceptionHandler {

    @ExceptionHandler(SemanticAnalyticsValidationException.class)
    public ResponseEntity<SemanticAnalyticsError> validation(SemanticAnalyticsValidationException exception) {
        return ResponseEntity.badRequest()
            .body(new SemanticAnalyticsError("SEMANTIC_ANALYTICS_VALIDATION_FAILED", exception.getDiagnostics()));
    }

    @ExceptionHandler(SemanticAnalyticsDslException.class)
    public ResponseEntity<SemanticAnalyticsError> dsl(SemanticAnalyticsDslException exception) {
        return ResponseEntity.badRequest().body(new SemanticAnalyticsError("SEMANTIC_ANALYTICS_DSL_INVALID", exception.getDiagnostics()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<SemanticAnalyticsError> malformed(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            new SemanticAnalyticsError(
                "SEMANTIC_ANALYTICS_MALFORMED_REQUEST",
                List.of(
                    new AnalyticsDiagnostic(
                        AnalyticsDiagnosticCode.MALFORMED_REQUEST,
                        "request",
                        "The semantic query request is malformed."
                    )
                )
            )
        );
    }
}
