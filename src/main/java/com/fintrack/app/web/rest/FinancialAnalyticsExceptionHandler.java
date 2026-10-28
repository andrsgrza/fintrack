package com.fintrack.app.web.rest;

import com.fintrack.app.service.analytics.AnalyticsDiagnostic;
import com.fintrack.app.service.analytics.AnalyticsDiagnosticCode;
import com.fintrack.app.service.analytics.FinancialAnalyticsValidationException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps malformed and semantically invalid Analytics requests out of the generic 500/error path. */
@RestControllerAdvice(assignableTypes = FinancialAnalyticsExecutionResource.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FinancialAnalyticsExceptionHandler {

    @ExceptionHandler(FinancialAnalyticsValidationException.class)
    public ResponseEntity<FinancialAnalyticsError> validation(FinancialAnalyticsValidationException exception) {
        return ResponseEntity.badRequest().body(new FinancialAnalyticsError("ANALYTICS_VALIDATION_FAILED", exception.getDiagnostics()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<FinancialAnalyticsError> malformed(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            new FinancialAnalyticsError(
                "ANALYTICS_MALFORMED_REQUEST",
                List.of(
                    new AnalyticsDiagnostic(AnalyticsDiagnosticCode.MALFORMED_REQUEST, "request", "The Analytics request is malformed.")
                )
            )
        );
    }
}
