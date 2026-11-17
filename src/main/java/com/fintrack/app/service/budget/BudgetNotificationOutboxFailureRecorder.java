package com.fintrack.app.service.budget;

import com.fintrack.app.domain.BudgetNotificationOutbox;
import com.fintrack.app.domain.enumeration.BudgetNotificationOutboxState;
import com.fintrack.app.repository.BudgetNotificationOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists a retry outcome in a fresh transaction after a materialization transaction rolls back. */
@Service
public class BudgetNotificationOutboxFailureRecorder {

    private static final int MAX_ATTEMPTS = 5;
    private final BudgetNotificationOutboxRepository outboxRepository;
    private final Clock clock;

    public BudgetNotificationOutboxFailureRecorder(BudgetNotificationOutboxRepository outboxRepository, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long outboxId, RuntimeException exception) {
        BudgetNotificationOutbox event = outboxRepository.findOneForProcessing(outboxId).orElse(null);
        if (event == null || event.getState() != BudgetNotificationOutboxState.PENDING) return;
        int nextAttempt = event.getAttemptCount() + 1;
        long seconds = Math.min(3600L, 1L << Math.min(10, nextAttempt));
        event.retry(clock.instant().plus(Duration.ofSeconds(seconds)), exception.getMessage(), nextAttempt >= MAX_ATTEMPTS);
    }
}
