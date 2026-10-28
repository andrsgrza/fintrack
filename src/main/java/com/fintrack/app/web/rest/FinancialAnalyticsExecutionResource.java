package com.fintrack.app.web.rest;

import com.fintrack.app.service.analytics.FinancialAnalyticsExecutionRequest;
import com.fintrack.app.service.analytics.FinancialAnalyticsExecutionService;
import com.fintrack.app.service.analytics.FinancialAnalyticsResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Canonical R3 Analytics execution boundary. It accepts only the versioned semantic request contract. */
@RestController
@RequestMapping("/api/analytics/queries")
public class FinancialAnalyticsExecutionResource {

    private final FinancialAnalyticsExecutionService executionService;

    public FinancialAnalyticsExecutionResource(FinancialAnalyticsExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/execute")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FinancialAnalyticsResult> execute(@RequestBody FinancialAnalyticsExecutionRequest request) {
        return ResponseEntity.ok(executionService.execute(request));
    }
}
