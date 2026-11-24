package com.fintrack.app.service.budget;

import com.fintrack.app.domain.BudgetPeriodCloseWorkItem;
import com.fintrack.app.repository.BudgetPeriodCloseWorkItemRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Retains recoverable closing failures indefinitely with bounded backoff instead of silently discarding them. */
@Service
public class BudgetPeriodCloseFailureRecorder {

    private final BudgetPeriodCloseWorkItemRepository workRepository;
    private final Clock clock;
    private final BudgetPeriodCloseMetrics metrics;

    public BudgetPeriodCloseFailureRecorder(
        BudgetPeriodCloseWorkItemRepository workRepository,
        Clock clock,
        BudgetPeriodCloseMetrics metrics
    ) {
        this.workRepository = workRepository;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRecoverableFailure(Long workId, RuntimeException exception) {
        BudgetPeriodCloseWorkItem work = workRepository.findOneForProcessing(workId).orElse(null);
        // Another authoritative transaction may have cancelled or captured the item after the processor threw.
        // Only a still-held lease is eligible to be converted into a recoverable retry.
        if (work == null || work.getState() != com.fintrack.app.domain.enumeration.BudgetPeriodCloseWorkItemState.PROCESSING) return;
        int nextAttempt = work.getAttemptCount() + 1;
        long seconds = Math.min(Duration.ofHours(6).toSeconds(), 1L << Math.min(14, nextAttempt));
        work.retry(clock.instant().plusSeconds(seconds), exception == null ? "Period-close capture failed." : exception.getMessage());
        metrics.retried();
    }
}
