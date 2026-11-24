package com.fintrack.app.service.budget;

import com.fintrack.app.repository.BudgetPeriodCloseWorkItemRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/** Operational, aggregate-only instrumentation for the durable close worker. */
@Service
public class BudgetPeriodCloseMetrics {

    private final Counter dueCursors;
    private final Counter createdWork;
    private final Counter capturedWork;
    private final Counter retriedWork;
    private final Counter blockedWork;
    private final Counter cancelledWork;
    private final Counter failures;
    private final Timer captureDuration;
    private final AtomicLong oldestOutstandingAgeSeconds = new AtomicLong();
    private final BudgetPeriodCloseWorkItemRepository workRepository;
    private final Clock clock;

    public BudgetPeriodCloseMetrics(BudgetPeriodCloseWorkItemRepository workRepository, Clock clock, MeterRegistry registry) {
        this.workRepository = workRepository;
        this.clock = clock;
        dueCursors = counter(registry, "cursors.processed", "Due Budget close cursors processed.");
        createdWork = counter(registry, "work.created", "Budget close work items created.");
        capturedWork = counter(registry, "work.captured", "Budget close work items captured atomically with an outbox event.");
        retriedWork = counter(registry, "work.retried", "Recoverable Budget close work retries scheduled.");
        blockedWork = counter(registry, "work.blocked", "Budget close work blocked by permanent configuration failures.");
        cancelledWork = counter(registry, "work.cancelled", "Budget close work cancelled after eligibility changed.");
        failures = counter(registry, "failures", "Unexpected Budget close processing failures.");
        captureDuration = Timer.builder("budget.period-close.capture.duration")
            .description("Duration of one Budget close capture attempt.")
            .register(registry);
        Gauge.builder("budget.period-close.oldest-outstanding.age", oldestOutstandingAgeSeconds, AtomicLong::get)
            .baseUnit("seconds")
            .description("Age of the oldest pending, retrying, or leased Budget close work item.")
            .register(registry);
    }

    public void recordDiscovery(int processedCursors, int createdWorkItems) {
        if (processedCursors > 0) dueCursors.increment(processedCursors);
        if (createdWorkItems > 0) createdWork.increment(createdWorkItems);
    }

    public Timer.Sample startCapture() {
        return Timer.start();
    }

    public void stopCapture(Timer.Sample sample) {
        if (sample != null) sample.stop(captureDuration);
    }

    public void captured() {
        capturedWork.increment();
    }

    public void retried() {
        retriedWork.increment();
    }

    public void blocked() {
        blockedWork.increment();
    }

    public void cancelled() {
        cancelledWork.increment();
    }

    public void failed() {
        failures.increment();
    }

    /** Refreshes a gauge without retaining Budget, user, or financial information in metrics. */
    public void refreshOldestOutstandingAge() {
        Instant oldest = workRepository.findOldestOutstandingLogicalClosedAt().orElse(null);
        long seconds = oldest == null ? 0L : Math.max(0L, Duration.between(oldest, clock.instant()).toSeconds());
        oldestOutstandingAgeSeconds.set(seconds);
    }

    private Counter counter(MeterRegistry registry, String suffix, String description) {
        return Counter.builder("budget.period-close." + suffix).description(description).register(registry);
    }
}
