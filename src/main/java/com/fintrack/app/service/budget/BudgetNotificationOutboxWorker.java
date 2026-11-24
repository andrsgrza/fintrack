package com.fintrack.app.service.budget;

import com.fintrack.app.domain.BudgetNotificationOutbox;
import com.fintrack.app.domain.enumeration.BudgetNotificationOutboxState;
import com.fintrack.app.repository.BudgetNotificationOutboxRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Small durable outbox worker. A failed delivery is retried at most five times with bounded exponential backoff;
 * materialization is local database persistence, so a permanent FAILED state remains inspectable rather than dropped.
 */
@Service
public class BudgetNotificationOutboxWorker {

    private static final int BATCH_SIZE = 25;
    private final BudgetNotificationOutboxRepository outboxRepository;
    private final BudgetNotificationOutboxProcessor processor;
    private final BudgetNotificationOutboxFailureRecorder failureRecorder;
    private final Clock clock;

    public BudgetNotificationOutboxWorker(
        BudgetNotificationOutboxRepository outboxRepository,
        BudgetNotificationOutboxProcessor processor,
        BudgetNotificationOutboxFailureRecorder failureRecorder,
        Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.processor = processor;
        this.failureRecorder = failureRecorder;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${application.budget-notifications.outbox-fixed-delay:PT30S}")
    public void scheduledProcess() {
        processPending();
    }

    /** Invocable from integration tests and operations; every event has its own transaction via the processor. */
    public int processPending() {
        List<Long> ids = outboxRepository.findPendingIdsForProcessing(
            BudgetNotificationOutboxState.PENDING,
            clock.instant(),
            PageRequest.of(0, BATCH_SIZE)
        );
        int materialized = 0;
        for (Long id : ids) {
            try {
                if (processor.materialize(id)) materialized++;
            } catch (RuntimeException exception) {
                failureRecorder.record(id, exception);
            }
        }
        return materialized;
    }
}
