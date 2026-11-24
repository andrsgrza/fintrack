package com.fintrack.app.domain;

import com.fintrack.app.domain.enumeration.BudgetNotificationOutboxState;
import com.fintrack.app.domain.enumeration.BudgetNotificationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;

/** Immutable event evidence captured with a financial mutation before any asynchronous materialization. */
@Entity
@Table(
    name = "budget_notification_outbox",
    indexes = {
        @Index(name = "ix_budget_notification_outbox_pending", columnList = "state,next_attempt_at,event_at,id"),
        @Index(name = "ix_budget_notification_outbox_owner_budget", columnList = "user_id,budget_id,event_at,id"),
    }
)
public class BudgetNotificationOutbox implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false, updatable = false)
    private Budget budget;

    @NotNull
    @Column(name = "source_mutation_key", length = 200, nullable = false, updatable = false)
    private String sourceMutationKey;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", length = 40, nullable = false, updatable = false)
    private BudgetNotificationType notificationType;

    @NotNull
    @Column(name = "snapshot_json", columnDefinition = "text", nullable = false, updatable = false)
    private String snapshotJson;

    @NotNull
    @Column(name = "event_at", nullable = false, updatable = false)
    private Instant eventAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 20, nullable = false)
    private BudgetNotificationOutboxState state;

    @NotNull
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @NotNull
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "materialized_at")
    private Instant materializedAt;

    protected BudgetNotificationOutbox() {}

    public static BudgetNotificationOutbox pending(
        User user,
        Budget budget,
        String sourceMutationKey,
        String snapshotJson,
        Instant eventAt
    ) {
        return pending(user, budget, BudgetNotificationType.THRESHOLD_CROSSED, sourceMutationKey, snapshotJson, eventAt);
    }

    public static BudgetNotificationOutbox pending(
        User user,
        Budget budget,
        BudgetNotificationType notificationType,
        String sourceMutationKey,
        String snapshotJson,
        Instant eventAt
    ) {
        if (notificationType == null) throw new IllegalArgumentException("A Budget notification type is required.");
        BudgetNotificationOutbox event = new BudgetNotificationOutbox();
        event.user = user;
        event.budget = budget;
        event.notificationType = notificationType;
        event.sourceMutationKey = sourceMutationKey;
        event.snapshotJson = snapshotJson;
        event.eventAt = eventAt;
        event.state = BudgetNotificationOutboxState.PENDING;
        event.nextAttemptAt = eventAt;
        return event;
    }

    public void materialized(Instant now) {
        state = BudgetNotificationOutboxState.MATERIALIZED;
        materializedAt = now;
        lastError = null;
    }

    public void retry(Instant nextAttemptAt, String error, boolean exhausted) {
        attemptCount++;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = error == null ? null : error.substring(0, Math.min(1000, error.length()));
        this.state = exhausted ? BudgetNotificationOutboxState.FAILED : BudgetNotificationOutboxState.PENDING;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Budget getBudget() {
        return budget;
    }

    public String getSourceMutationKey() {
        return sourceMutationKey;
    }

    public BudgetNotificationType getNotificationType() {
        return notificationType;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public Instant getEventAt() {
        return eventAt;
    }

    public BudgetNotificationOutboxState getState() {
        return state;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}
