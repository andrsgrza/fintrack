package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetNotification;
import com.fintrack.app.domain.BudgetNotificationOutbox;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.BudgetNotificationOutboxState;
import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.repository.BudgetNotificationOutboxRepository;
import com.fintrack.app.repository.BudgetNotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BudgetNotificationOutboxProcessorTest {

    private static final Instant NOW = Instant.parse("2026-03-15T18:00:00Z");

    @Mock
    private BudgetNotificationOutboxRepository outboxRepository;

    @Mock
    private BudgetNotificationRepository notificationRepository;

    private BudgetNotificationOutboxProcessor processor;
    private BudgetNotificationOutbox event;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(1L);
        owner.setLogin("owner");
        Budget budget = Budget.create(owner, "Food", null, BudgetType.EXPENSE, CurrencyCode.MXN, "request", "hash", NOW);
        ReflectionTestUtils.setField(budget, "id", 8L);
        event = BudgetNotificationOutbox.pending(owner, budget, "FINANCIAL_TRANSACTION_CREATE:9", "{\"immutable\":true}", NOW);
        ReflectionTestUtils.setField(event, "id", 31L);
        processor = new BudgetNotificationOutboxProcessor(outboxRepository, notificationRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void materializes_once_and_marks_the_event_materialized() {
        when(outboxRepository.findOneForProcessing(31L)).thenReturn(Optional.of(event));
        when(notificationRepository.findOneByOutboxEventId(31L)).thenReturn(Optional.empty());

        assertThat(processor.materialize(31L)).isTrue();

        verify(notificationRepository).save(any(BudgetNotification.class));
        assertThat(event.getState()).isEqualTo(BudgetNotificationOutboxState.MATERIALIZED);
    }

    @Test
    void is_idempotent_after_materialization() {
        when(outboxRepository.findOneForProcessing(31L)).thenReturn(Optional.of(event));
        when(notificationRepository.findOneByOutboxEventId(31L)).thenReturn(Optional.empty());
        processor.materialize(31L);

        assertThat(processor.materialize(31L)).isFalse();

        verify(notificationRepository, times(1)).save(any(BudgetNotification.class));
    }
}
