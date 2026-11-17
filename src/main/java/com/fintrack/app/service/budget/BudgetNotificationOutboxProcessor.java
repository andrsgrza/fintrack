package com.fintrack.app.service.budget;

import com.fintrack.app.domain.BudgetNotification;
import com.fintrack.app.domain.BudgetNotificationOutbox;
import com.fintrack.app.domain.enumeration.BudgetNotificationOutboxState;
import com.fintrack.app.repository.BudgetNotificationOutboxRepository;
import com.fintrack.app.repository.BudgetNotificationRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One-event transactional materialization boundary, deliberately idempotent through the unique outbox reference. */
@Service
public class BudgetNotificationOutboxProcessor {

    private final BudgetNotificationOutboxRepository outboxRepository;
    private final BudgetNotificationRepository notificationRepository;
    private final Clock clock;

    public BudgetNotificationOutboxProcessor(
        BudgetNotificationOutboxRepository outboxRepository,
        BudgetNotificationRepository notificationRepository,
        Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean materialize(Long outboxId) {
        BudgetNotificationOutbox event = outboxRepository.findOneForProcessing(outboxId).orElse(null);
        if (event == null || event.getState() != BudgetNotificationOutboxState.PENDING) return false;
        if (notificationRepository.findOneByOutboxEventId(event.getId()).isEmpty()) {
            notificationRepository.save(BudgetNotification.materialize(event, Instant.now(clock)));
        }
        event.materialized(Instant.now(clock));
        return true;
    }
}
