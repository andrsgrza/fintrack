package com.fintrack.app.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetNotification;
import com.fintrack.app.domain.BudgetNotificationOutbox;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.BudgetNotificationType;
import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.repository.BudgetNotificationOutboxRepository;
import com.fintrack.app.repository.BudgetNotificationRepository;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.service.budget.BudgetEvaluationStatus;
import com.fintrack.app.service.budget.BudgetPeriodCloseNotificationSnapshot;
import com.fintrack.app.service.budget.BudgetThresholdNotificationSnapshot;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL-backed REST proof for BUDGET-6B notification delivery. These tests exercise the owner-scoped HTTP
 * boundary and inspect persisted rows, complementing the BUDGET-6A outbox-engine tests.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = BudgetNotificationResourceIT.OWNER)
@Import(BudgetNotificationResourceIT.FixedClockConfiguration.class)
class BudgetNotificationResourceIT {

    static final String OWNER = "user";
    private static final String OTHER_OWNER = "notification-other-owner";
    private static final Instant NOW = Instant.parse("2026-03-15T18:00:00Z");

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private BudgetNotificationOutboxRepository outboxRepository;

    @Autowired
    private BudgetNotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void listsOnlyTheOwnersImmutableNotificationsWithStableServerPaginationAndUnreadFiltering() throws Exception {
        Budget budget = budget(owner(), "notification-page");
        BudgetNotification oldest = notification(owner(), budget, "oldest", NOW.minusSeconds(120), NOW.minusSeconds(120));
        BudgetNotification middle = notification(owner(), budget, "middle", NOW.minusSeconds(60), NOW.minusSeconds(60));
        BudgetNotification newest = notification(owner(), budget, "newest", NOW, NOW);
        newest.markRead(NOW);
        notificationRepository.saveAndFlush(newest);

        restMockMvc
            .perform(get("/api/notifications").param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.notifications[0].id").value(newest.getId()))
            .andExpect(jsonPath("$.notifications[0].snapshot.budgetName").value("Notification budget notification-page"))
            .andExpect(jsonPath("$.notifications[0].snapshot.newlyCrossedPercentages[0]").value(80.0));

        restMockMvc
            .perform(get("/api/notifications").param("page", "1").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notifications[0].id").value(middle.getId()));

        restMockMvc
            .perform(get("/api/notifications").param("page", "0").param("size", "20").param("unreadOnly", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.notifications[0].id").value(middle.getId()))
            .andExpect(jsonPath("$.notifications[1].id").value(oldest.getId()));

        restMockMvc
            .perform(get("/api/notifications/unread-count"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(2));
    }

    @Test
    @Transactional
    void marksExactlyOneOwnedNotificationOnceAndDoesNotExposeForeignIds() throws Exception {
        BudgetNotification own = notification(
            owner(),
            budget(owner(), "notification-read"),
            "owned",
            NOW.minusSeconds(30),
            NOW.minusSeconds(30)
        );
        User other = otherOwner();
        BudgetNotification foreign = notification(other, budget(other, "notification-foreign"), "foreign", NOW, NOW);

        restMockMvc
            .perform(patch("/api/notifications/{id}/read", own.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(own.getId()))
            .andExpect(jsonPath("$.readAt").value(NOW.toString()));
        entityManager.flush();
        Instant firstReadAt = notificationRepository.findById(own.getId()).orElseThrow().getReadAt();
        assertThat(firstReadAt).isEqualTo(NOW);

        restMockMvc
            .perform(patch("/api/notifications/{id}/read", own.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readAt").value(NOW.toString()));
        entityManager.flush();
        assertThat(notificationRepository.findById(own.getId()).orElseThrow().getReadAt()).isEqualTo(firstReadAt);

        restMockMvc.perform(patch("/api/notifications/{id}/read", foreign.getId())).andExpect(status().isNotFound());
        assertThat(notificationRepository.findById(foreign.getId()).orElseThrow().getReadAt()).isNull();
    }

    @Test
    @Transactional
    void returns_mixed_threshold_and_period_close_snapshots_without_rewriting_the_closing_financial_evidence() throws Exception {
        Budget budget = budget(owner(), "notification-mixed-types");
        notification(owner(), budget, "threshold", NOW.minusSeconds(30), NOW.minusSeconds(30));
        BudgetNotification closing = periodCloseNotification(owner(), budget, NOW, NOW.plusSeconds(1));

        restMockMvc
            .perform(get("/api/notifications").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.notifications[0].notificationType").value("PERIOD_CLOSED"))
            .andExpect(jsonPath("$.notifications[0].snapshot.budgetName").value("Notification budget notification-mixed-types"))
            .andExpect(jsonPath("$.notifications[0].snapshot.periodStart").value("2026-02-01"))
            .andExpect(jsonPath("$.notifications[0].snapshot.periodEndExclusive").value("2026-03-01"))
            .andExpect(jsonPath("$.notifications[0].snapshot.actualAmount").value("60.00"))
            .andExpect(jsonPath("$.notifications[0].snapshot.targetAmount").value("100.00"))
            .andExpect(jsonPath("$.notifications[0].snapshot.progressPercentage").value("60.00"));

        restMockMvc.perform(patch("/api/notifications/{id}/read", closing.getId())).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        BudgetNotification persisted = notificationRepository.findById(closing.getId()).orElseThrow();
        assertThat(persisted.getSnapshotJson()).contains("\"actualAmount\":\"60.00\"").contains("\"targetAmount\":\"100.00\"");
        assertThat(persisted.getReadAt()).isEqualTo(NOW);
    }

    @Test
    @Transactional
    void marksOnlyExistingOwnedUnreadNotificationsAndKeepsLaterMaterializationsUnread() throws Exception {
        Budget ownerBudget = budget(owner(), "notification-mark-all");
        BudgetNotification eligible = notification(owner(), ownerBudget, "eligible", NOW.minusSeconds(30), NOW.minusSeconds(30));
        BudgetNotification alreadyRead = notification(owner(), ownerBudget, "read", NOW.minusSeconds(20), NOW.minusSeconds(20));
        alreadyRead.markRead(NOW.minusSeconds(10));
        notificationRepository.saveAndFlush(alreadyRead);
        BudgetNotification later = notification(owner(), ownerBudget, "later", NOW.minusSeconds(10), NOW.plusSeconds(1));
        BudgetNotification foreign = notification(otherOwner(), budget(otherOwner(), "notification-mark-all-foreign"), "foreign", NOW, NOW);

        restMockMvc
            .perform(post("/api/notifications/mark-all-read"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.markedCount").value(1));
        entityManager.flush();
        entityManager.clear();

        assertThat(notificationRepository.findById(eligible.getId()).orElseThrow().getReadAt()).isEqualTo(NOW);
        assertThat(notificationRepository.findById(alreadyRead.getId()).orElseThrow().getReadAt()).isEqualTo(NOW.minusSeconds(10));
        assertThat(notificationRepository.findById(later.getId()).orElseThrow().getReadAt()).isNull();
        assertThat(notificationRepository.findById(foreign.getId()).orElseThrow().getReadAt()).isNull();
    }

    @Test
    @Transactional
    void validatesPaginationRequiresAuthenticationAndCleansNotificationRowsWhenTheBudgetIsDeleted() throws Exception {
        Budget budget = budget(owner(), "notification-delete");
        BudgetNotification notification = notification(owner(), budget, "delete", NOW, NOW);

        restMockMvc.perform(get("/api/notifications").param("page", "-1")).andExpect(status().isBadRequest());
        restMockMvc.perform(get("/api/notifications").param("size", "101")).andExpect(status().isBadRequest());

        restMockMvc
            .perform(
                delete("/api/budgets/{id}", budget.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedVersion\":" + budget.getVersion() + "}")
            )
            .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertThat(notificationRepository.findById(notification.getId())).isEmpty();
        assertThat(outboxRepository.findAll().stream().filter(event -> event.getBudget().getId().equals(budget.getId()))).isEmpty();
    }

    @Test
    @Transactional
    @WithAnonymousUser
    void requiresAuthentication() throws Exception {
        restMockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        restMockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        restMockMvc.perform(patch("/api/notifications/{id}/read", 1)).andExpect(status().isUnauthorized());
        restMockMvc.perform(post("/api/notifications/mark-all-read")).andExpect(status().isUnauthorized());
    }

    private User owner() {
        return userRepository.findOneByLogin(OWNER).orElseThrow();
    }

    private User otherOwner() {
        return userRepository
            .findOneByLogin(OTHER_OWNER)
            .orElseGet(() -> {
                User user = new User();
                user.setLogin(OTHER_OWNER);
                user.setPassword("x".repeat(60));
                user.setActivated(true);
                user.setEmail("notification-other-owner@example.test");
                user.setLangKey("en");
                return userRepository.saveAndFlush(user);
            });
    }

    private Budget budget(User owner, String requestId) {
        return budgetRepository.saveAndFlush(
            Budget.create(
                owner,
                "Notification budget " + requestId,
                null,
                BudgetType.EXPENSE,
                CurrencyCode.MXN,
                requestId,
                "hash-" + requestId,
                NOW
            )
        );
    }

    private BudgetNotification notification(User owner, Budget budget, String key, Instant eventAt, Instant materializedAt)
        throws Exception {
        BudgetThresholdNotificationSnapshot snapshot = new BudgetThresholdNotificationSnapshot(
            owner.getId(),
            budget.getId(),
            budget.getName(),
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 4, 1),
            "America/Mexico_City",
            eventAt,
            new BigDecimal("40.00"),
            new BigDecimal("40.00"),
            new BigDecimal("80.00"),
            new BigDecimal("80.00"),
            new BigDecimal("100.00"),
            List.of(new BigDecimal("80.0000")),
            "notification-resource:" + key,
            null
        );
        BudgetNotificationOutbox outbox = outboxRepository.saveAndFlush(
            BudgetNotificationOutbox.pending(
                owner,
                budget,
                "notification-resource:" + key,
                objectMapper.writeValueAsString(snapshot),
                eventAt
            )
        );
        BudgetNotification notification = BudgetNotification.materialize(outbox, materializedAt);
        return notificationRepository.saveAndFlush(notification);
    }

    private BudgetNotification periodCloseNotification(User owner, Budget budget, Instant logicalClosedAt, Instant materializedAt)
        throws Exception {
        BudgetPeriodCloseNotificationSnapshot snapshot = new BudgetPeriodCloseNotificationSnapshot(
            budget.getId(),
            budget.getName(),
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 3, 1),
            "America/Mexico_City",
            7L,
            new BigDecimal("100.00"),
            new BigDecimal("60.00"),
            new BigDecimal("60.00"),
            BudgetEvaluationStatus.EVALUATED,
            logicalClosedAt,
            materializedAt,
            "PERIOD_CLOSED:" + budget.getId() + ":2026-02-01"
        );
        BudgetNotificationOutbox outbox = outboxRepository.saveAndFlush(
            BudgetNotificationOutbox.pending(
                owner,
                budget,
                BudgetNotificationType.PERIOD_CLOSED,
                snapshot.logicalEventKey(),
                objectMapper.writeValueAsString(snapshot),
                logicalClosedAt
            )
        );
        return notificationRepository.saveAndFlush(BudgetNotification.materialize(outbox, materializedAt));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock budgetNotificationResourceClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
