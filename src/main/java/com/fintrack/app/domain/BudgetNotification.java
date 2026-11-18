package com.fintrack.app.domain;

import com.fintrack.app.domain.enumeration.BudgetNotificationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;

/** User-visible immutable notification materialized from exactly one transactional outbox event. */
@Entity
@Table(
    name = "budget_notification",
    uniqueConstraints = @UniqueConstraint(name = "ux_budget_notification_outbox", columnNames = "outbox_event_id"),
    indexes = @Index(name = "ix_budget_notification_owner_read", columnList = "user_id,read_at,event_at,id")
)
public class BudgetNotification implements Serializable {

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
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outbox_event_id", nullable = false, unique = true, updatable = false)
    private BudgetNotificationOutbox outboxEvent;

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
    @Column(name = "materialized_at", nullable = false, updatable = false)
    private Instant materializedAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected BudgetNotification() {}

    public static BudgetNotification materialize(BudgetNotificationOutbox event, Instant now) {
        BudgetNotification notification = new BudgetNotification();
        notification.user = event.getUser();
        notification.budget = event.getBudget();
        notification.outboxEvent = event;
        notification.notificationType = BudgetNotificationType.THRESHOLD_CROSSED;
        notification.snapshotJson = event.getSnapshotJson();
        notification.eventAt = event.getEventAt();
        notification.materializedAt = now;
        return notification;
    }

    public Long getId() {
        return id;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Budget getBudget() {
        return budget;
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

    public Instant getMaterializedAt() {
        return materializedAt;
    }

    /** Sets the first server-authoritative read time only; repeated browser requests are intentionally idempotent. */
    public void markRead(Instant readTime) {
        if (readAt == null) {
            readAt = readTime;
        }
    }
}
