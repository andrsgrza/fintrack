package com.fintrack.app.domain;

import com.fintrack.app.domain.enumeration.BudgetLifecycleEventType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/** Immutable lifecycle intent. Current state is derived from this history and the calendar, never stored separately. */
@Entity
@Table(
    name = "budget_lifecycle_event",
    uniqueConstraints = @UniqueConstraint(name = "ux_budget_lifecycle_event_sequence", columnNames = { "budget_id", "sequence_number" })
)
public class BudgetLifecycleEvent implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false, updatable = false)
    private Budget budget;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_event_id", updatable = false)
    private BudgetLifecycleEvent targetEvent;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 24, nullable = false, updatable = false)
    private BudgetLifecycleEventType eventType;

    @NotNull
    @Column(name = "effective_period_start", nullable = false, updatable = false)
    private LocalDate effectivePeriodStart;

    @NotNull
    @Column(name = "sequence_number", nullable = false, updatable = false)
    private Long sequenceNumber;

    @NotNull
    @Size(max = 100)
    @Column(name = "client_request_id", length = 100, nullable = false, updatable = false)
    private String clientRequestId;

    @NotNull
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected BudgetLifecycleEvent() {}

    public static BudgetLifecycleEvent record(
        Budget budget,
        BudgetLifecycleEventType eventType,
        LocalDate effectivePeriodStart,
        long sequenceNumber,
        String clientRequestId,
        Instant recordedAt,
        BudgetLifecycleEvent targetEvent
    ) {
        BudgetLifecycleEvent event = new BudgetLifecycleEvent();
        event.budget = budget;
        event.eventType = eventType;
        event.effectivePeriodStart = effectivePeriodStart;
        event.sequenceNumber = sequenceNumber;
        event.clientRequestId = clientRequestId;
        event.recordedAt = recordedAt;
        event.targetEvent = targetEvent;
        return event;
    }

    public Long getId() {
        return id;
    }

    public Budget getBudget() {
        return budget;
    }

    public BudgetLifecycleEvent getTargetEvent() {
        return targetEvent;
    }

    public BudgetLifecycleEventType getEventType() {
        return eventType;
    }

    public LocalDate getEffectivePeriodStart() {
        return effectivePeriodStart;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
