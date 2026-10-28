package com.fintrack.app.web.rest;

import com.fintrack.app.service.analytics.FinancialAnalyticsDslParseResult;
import com.fintrack.app.service.analytics.FinancialAnalyticsDslService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated parser/formatter boundary for the non-executable financial definition language. */
@RestController
@RequestMapping("/api/analytics/queries")
public class FinancialAnalyticsDslResource {

    private final FinancialAnalyticsDslService dslService;

    public FinancialAnalyticsDslResource(FinancialAnalyticsDslService dslService) {
        this.dslService = dslService;
    }

    @PostMapping("/parse")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FinancialAnalyticsDslParseResult> parse(@RequestBody FinancialAnalyticsDslParseRequest request) {
        return ResponseEntity.ok(dslService.parse(request == null ? null : request.text()));
    }

    @PostMapping("/format")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FinancialAnalyticsDslFormatResult> format(@RequestBody FinancialAnalyticsDslFormatRequest request) {
        return ResponseEntity.ok(new FinancialAnalyticsDslFormatResult(dslService.format(request == null ? null : request.definition())));
    }
}
