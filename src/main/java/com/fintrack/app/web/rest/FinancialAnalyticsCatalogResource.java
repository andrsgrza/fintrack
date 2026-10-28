package com.fintrack.app.web.rest;

import com.fintrack.app.service.analytics.FinancialAnalyticsCatalog;
import com.fintrack.app.service.analytics.FinancialMetricTimePolicy;
import com.fintrack.app.service.analytics.FinancialSemanticCatalog;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only capabilities of the same canonical Analytics semantic model used by R3 execution. */
@RestController
@RequestMapping("/api/analytics")
public class FinancialAnalyticsCatalogResource {

    private final FinancialSemanticCatalog catalog;
    private final FinancialMetricTimePolicy timePolicy;

    public FinancialAnalyticsCatalogResource(FinancialSemanticCatalog catalog, FinancialMetricTimePolicy timePolicy) {
        this.catalog = catalog;
        this.timePolicy = timePolicy;
    }

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FinancialAnalyticsCatalog> getCatalog() {
        return ResponseEntity.ok(catalog.catalog(timePolicy));
    }
}
