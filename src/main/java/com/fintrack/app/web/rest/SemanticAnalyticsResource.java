package com.fintrack.app.web.rest;

import com.fintrack.app.service.analytics.SemanticAnalyticsCatalog;
import com.fintrack.app.service.analytics.SemanticAnalyticsCatalogService;
import com.fintrack.app.service.analytics.SemanticAnalyticsDslFormatResult;
import com.fintrack.app.service.analytics.SemanticAnalyticsDslParseRequest;
import com.fintrack.app.service.analytics.SemanticAnalyticsDslParseResult;
import com.fintrack.app.service.analytics.SemanticAnalyticsDslService;
import com.fintrack.app.service.analytics.SemanticAnalyticsExecutionRequest;
import com.fintrack.app.service.analytics.SemanticAnalyticsExecutionService;
import com.fintrack.app.service.analytics.SemanticAnalyticsResult;
import com.fintrack.app.service.analytics.SemanticAnalyticsTimePolicy;
import com.fintrack.app.service.analytics.SemanticQueryDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The sole public boundary for the governed composable semantic engine. It never accepts a metric ID, physical SQL
 * or caller-supplied owner.
 */
@RestController
@RequestMapping("/api/analytics/semantic")
public class SemanticAnalyticsResource {

    private final SemanticAnalyticsCatalogService catalogService;
    private final SemanticAnalyticsTimePolicy timePolicy;
    private final SemanticAnalyticsExecutionService executionService;
    private final SemanticAnalyticsDslService dslService;

    public SemanticAnalyticsResource(
        SemanticAnalyticsCatalogService catalogService,
        SemanticAnalyticsTimePolicy timePolicy,
        SemanticAnalyticsExecutionService executionService,
        SemanticAnalyticsDslService dslService
    ) {
        this.catalogService = catalogService;
        this.timePolicy = timePolicy;
        this.executionService = executionService;
        this.dslService = dslService;
    }

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SemanticAnalyticsCatalog> catalog() {
        return ResponseEntity.ok(catalogService.catalog(timePolicy));
    }

    @PostMapping("/queries/execute")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SemanticAnalyticsResult> execute(@RequestBody SemanticAnalyticsExecutionRequest request) {
        return ResponseEntity.ok(executionService.execute(request));
    }

    @PostMapping("/queries/parse")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SemanticAnalyticsDslParseResult> parse(@RequestBody SemanticAnalyticsDslParseRequest request) {
        return ResponseEntity.ok(dslService.parse(request == null ? null : request.text()));
    }

    @PostMapping("/queries/format")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SemanticAnalyticsDslFormatResult> format(@RequestBody SemanticQueryDefinition definition) {
        return ResponseEntity.ok(new SemanticAnalyticsDslFormatResult(dslService.format(definition)));
    }
}
