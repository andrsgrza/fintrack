package com.fintrack.app.service.budget;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Single bounded Spring scheduling entry point for Budget close discovery and processing. */
@Service
public class BudgetPeriodCloseScheduler {

    private final BudgetPeriodCloseCursorService cursorService;
    private final BudgetPeriodCloseProcessor processor;
    private final BudgetPeriodCloseMetrics metrics;

    public BudgetPeriodCloseScheduler(
        BudgetPeriodCloseCursorService cursorService,
        BudgetPeriodCloseProcessor processor,
        BudgetPeriodCloseMetrics metrics
    ) {
        this.cursorService = cursorService;
        this.processor = processor;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${application.budget-period-close.fixed-delay:PT60S}")
    public void scheduledProcess() {
        processDue();
    }

    /** Testable operational boundary. Discovery and processing are independently bounded durable phases. */
    public BudgetPeriodCloseRunResult processDue() {
        BudgetPeriodCloseCursorService.DiscoveryResult discovery = cursorService.discoverDueWork();
        int captured = processor.processDueWork();
        metrics.recordDiscovery(discovery.dueCursorsProcessed(), discovery.workItemsCreated());
        metrics.refreshOldestOutstandingAge();
        return new BudgetPeriodCloseRunResult(discovery.workItemsCreated(), captured);
    }

    public record BudgetPeriodCloseRunResult(int discovered, int captured) {}
}
