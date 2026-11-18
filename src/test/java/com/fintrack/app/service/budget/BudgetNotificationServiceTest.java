package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetNotification;
import com.fintrack.app.domain.BudgetNotificationOutbox;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.repository.BudgetNotificationRepository;
import com.fintrack.app.service.CurrentUserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BudgetNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-15T18:00:00Z");

    @Mock
    private BudgetNotificationRepository notificationRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ObjectMapper objectMapper;

    private BudgetNotificationService service;
    private BudgetNotification notification;

    @BeforeEach
    void setUp() throws Exception {
        User owner = new User();
        owner.setId(1L);
        owner.setLogin("owner");
        Budget budget = Budget.create(owner, "Food", null, BudgetType.EXPENSE, CurrencyCode.MXN, "request", "hash", NOW);
        ReflectionTestUtils.setField(budget, "id", 8L);
        BudgetNotificationOutbox outbox = BudgetNotificationOutbox.pending(owner, budget, "transaction:1", "{}", NOW);
        notification = BudgetNotification.materialize(outbox, NOW);
        ReflectionTestUtils.setField(notification, "id", 13L);
        when(currentUserService.getCurrentUserLogin()).thenReturn("owner");
        lenient().when(objectMapper.readTree("{}")).thenReturn(JsonNodeFactory.instance.objectNode().put("budgetName", "Food"));
        service = new BudgetNotificationService(notificationRepository, currentUserService, objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void marks_an_owned_notification_once_and_preserves_the_first_server_timestamp() {
        when(notificationRepository.findOneOwnedForReadMutation(13L, "owner")).thenReturn(Optional.of(notification));

        service.markRead(13L);
        service.markRead(13L);

        assertThat(notification.getReadAt()).isEqualTo(NOW);
        verify(notificationRepository, times(2)).findOneOwnedForReadMutation(13L, "owner");
    }

    @Test
    void lists_only_owned_unread_notifications_in_stable_page_shape() {
        when(notificationRepository.findAllByUserLoginAndReadAtIsNullOrderByEventAtDescIdDesc(eq("owner"), any())).thenReturn(
            new PageImpl<>(List.of(notification))
        );

        BudgetNotificationPage page = service.list(0, 20, true);

        assertThat(page.notifications())
            .singleElement()
            .satisfies(view -> {
                assertThat(view.budgetId()).isEqualTo(8L);
                assertThat(view.snapshot().path("budgetName").asText()).isEqualTo("Food");
                assertThat(view.readAt()).isNull();
            });
    }

    @Test
    void mark_all_uses_a_materialization_cutoff_so_later_events_are_not_read() {
        when(notificationRepository.markAllExistingUnreadAsRead(eq("owner"), eq(NOW), eq(NOW))).thenReturn(2);

        assertThat(service.markAllRead()).isEqualTo(2);

        verify(notificationRepository).markAllExistingUnreadAsRead("owner", NOW, NOW);
    }
}
