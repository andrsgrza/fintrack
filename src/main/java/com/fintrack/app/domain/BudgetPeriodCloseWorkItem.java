package com.fintrack.app.domain;

import com.fintrack.app.domain.enumeration.BudgetPeriodCloseWorkItemState;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/** Durable, inspectable evidence for processing exactly one logical Budget closing period. */
@Entity
@Table(
    name = "budget_period_close_work_item",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_budget_period_close_work_period", columnNames = { "budget_id", "period_start" }),
        @UniqueConstraint(name = "ux_budget_period_close_work_outbox", columnNames = "outbox_event_id"),
    },
    indexes = {
        @Index(name = "ix_budget_period_close_work_due", columnList = "state,next_attempt_at,logical_closed_at,id"),
        @Index(name = "ix_budget_period_close_work_lease", columnList = "state,lease_until,id"),
        @Index(name = "ix_budget_period_close_work_budget_period", columnList = "budget_id,period_start"),
    }
)
public class BudgetPeriodCloseWorkItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false, updatable = false)
    private Budget budget;

    @NotNull
    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @NotNull
    @Column(name = "period_end_exclusive", nullable = false, updatable = false)
    private LocalDate periodEndExclusive;

    @NotNull
    @Column(name = "period_time_zone", length = 64, nullable = false, updatable = false)
    private String periodTimeZone;

    @NotNull
    @Column(name = "logical_closed_at", nullable = false, updatable = false)
    private Instant logicalClosedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 24, nullable = false)
    private BudgetPeriodCloseWorkItemState state;

    @NotNull
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @NotNull
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @OneToOne(fetch = FetchType.LAZY)
    // This association is intentionally assigned only once by capture(), but it must remain ORM-updatable from
    // its initial null value when the atomic outbox evidence is created.
    @JoinColumn(name = "outbox_event_id", unique = true)
    private BudgetNotificationOutbox outboxEvent;

    protected BudgetPeriodCloseWorkItem() {}

    public static BudgetPeriodCloseWorkItem pending(
        Budget budget,
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        String periodTimeZone,
        Instant logicalClosedAt,
        Instant now
    ) {
        BudgetPeriodCloseWorkItem work = new BudgetPeriodCloseWorkItem();
        work.budget = budget;
        work.periodStart = periodStart;
        work.periodEndExclusive = periodEndExclusive;
        work.periodTimeZone = periodTimeZone;
        work.logicalClosedAt = logicalClosedAt;
        work.state = BudgetPeriodCloseWorkItemState.PENDING;
        work.nextAttemptAt = now;
        return work;
    }

    public boolean mayBeClaimedAt(Instant now) {
        return (
            (state == BudgetPeriodCloseWorkItemState.PENDING || state == BudgetPeriodCloseWorkItemState.RETRY_SCHEDULED) &&
            !nextAttemptAt.isAfter(now)
        );
    }

    public boolean leaseExpiredAt(Instant now) {
        return state == BudgetPeriodCloseWorkItemState.PROCESSING && leaseUntil != null && !leaseUntil.isAfter(now);
    }

    public void claim(Instant leaseUntil) {
        if (leaseUntil == null) throw new IllegalArgumentException("A lease expiry is required.");
        if (state != BudgetPeriodCloseWorkItemState.PENDING && state != BudgetPeriodCloseWorkItemState.RETRY_SCHEDULED) {
            throw new IllegalStateException("Only pending period-close work may be claimed.");
        }
        state = BudgetPeriodCloseWorkItemState.PROCESSING;
        this.leaseUntil = leaseUntil;
        lastError = null;
    }

    public void retry(Instant nextAttemptAt, String error) {
        if (state != BudgetPeriodCloseWorkItemState.PROCESSING) {
            throw new IllegalStateException("Only claimed period-close work may be retried.");
        }
        if (nextAttemptAt == null) throw new IllegalArgumentException("A retry instant is required.");
        attemptCount++;
        state = BudgetPeriodCloseWorkItemState.RETRY_SCHEDULED;
        this.nextAttemptAt = nextAttemptAt;
        leaseUntil = null;
        lastError = bounded(error);
    }

    public void releaseExpiredLease(Instant now) {
        if (!leaseExpiredAt(now)) return;
        state = BudgetPeriodCloseWorkItemState.PENDING;
        nextAttemptAt = now;
        leaseUntil = null;
        lastError = "The previous period-close lease expired before capture completed.";
    }

    public void capture(BudgetNotificationOutbox event, Instant now) {
        if (event == null || now == null) throw new IllegalArgumentException("Captured close evidence is required.");
        if (state != BudgetPeriodCloseWorkItemState.PROCESSING) {
            throw new IllegalStateException("Only claimed period-close work may be captured.");
        }
        state = BudgetPeriodCloseWorkItemState.CAPTURED;
        outboxEvent = event;
        capturedAt = now;
        leaseUntil = null;
        lastError = null;
    }

    public void block(String reason) {
        if (state != BudgetPeriodCloseWorkItemState.PROCESSING) {
            throw new IllegalStateException("Only claimed period-close work may be blocked.");
        }
        state = BudgetPeriodCloseWorkItemState.BLOCKED;
        leaseUntil = null;
        lastError = bounded(reason);
    }

    public void cancel(String reason) {
        if (state != BudgetPeriodCloseWorkItemState.PROCESSING) {
            throw new IllegalStateException("Only claimed period-close work may be cancelled.");
        }
        state = BudgetPeriodCloseWorkItemState.CANCELLED;
        leaseUntil = null;
        lastError = bounded(reason);
    }

    private static String bounded(String value) {
        return value == null ? null : value.substring(0, Math.min(1000, value.length()));
    }

    public Long getId() {
        return id;
    }

    public Budget getBudget() {
        return budget;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEndExclusive() {
        return periodEndExclusive;
    }

    public String getPeriodTimeZone() {
        return periodTimeZone;
    }

    public Instant getLogicalClosedAt() {
        return logicalClosedAt;
    }

    public BudgetPeriodCloseWorkItemState getState() {
        return state;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public BudgetNotificationOutbox getOutboxEvent() {
        return outboxEvent;
    }
}
