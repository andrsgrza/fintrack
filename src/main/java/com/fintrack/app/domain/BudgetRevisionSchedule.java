package com.fintrack.app.domain;

import com.fintrack.app.domain.enumeration.BudgetRevisionScheduleOperation;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable operational history for when a revision becomes effective. Configuration itself belongs exclusively to
 * {@link BudgetRevision}; cancellation is another row, never a mutation or deletion of a scheduled row.
 */
@Entity
@Table(
    name = "budget_revision_schedule",
    uniqueConstraints = @UniqueConstraint(name = "ux_budget_revision_schedule_sequence", columnNames = { "budget_id", "sequence_number" })
)
public class BudgetRevisionSchedule implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false, updatable = false)
    private Budget budget;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id", updatable = false)
    private BudgetRevision revision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_schedule_id", updatable = false)
    private BudgetRevisionSchedule targetSchedule;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", length = 16, nullable = false, updatable = false)
    private BudgetRevisionScheduleOperation operation;

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

    protected BudgetRevisionSchedule() {}

    public static BudgetRevisionSchedule scheduled(
        Budget budget,
        BudgetRevision revision,
        LocalDate effectivePeriodStart,
        long sequenceNumber,
        String clientRequestId,
        Instant recordedAt
    ) {
        BudgetRevisionSchedule schedule = base(
            budget,
            BudgetRevisionScheduleOperation.SCHEDULED,
            effectivePeriodStart,
            sequenceNumber,
            clientRequestId,
            recordedAt
        );
        schedule.revision = revision;
        return schedule;
    }

    public static BudgetRevisionSchedule cancelled(
        Budget budget,
        BudgetRevisionSchedule target,
        long sequenceNumber,
        String clientRequestId,
        Instant recordedAt
    ) {
        BudgetRevisionSchedule cancellation = base(
            budget,
            BudgetRevisionScheduleOperation.CANCELLED,
            target.getEffectivePeriodStart(),
            sequenceNumber,
            clientRequestId,
            recordedAt
        );
        cancellation.targetSchedule = target;
        return cancellation;
    }

    private static BudgetRevisionSchedule base(
        Budget budget,
        BudgetRevisionScheduleOperation operation,
        LocalDate effectivePeriodStart,
        long sequenceNumber,
        String clientRequestId,
        Instant recordedAt
    ) {
        BudgetRevisionSchedule schedule = new BudgetRevisionSchedule();
        schedule.budget = budget;
        schedule.operation = operation;
        schedule.effectivePeriodStart = effectivePeriodStart;
        schedule.sequenceNumber = sequenceNumber;
        schedule.clientRequestId = clientRequestId;
        schedule.recordedAt = recordedAt;
        return schedule;
    }

    public Long getId() {
        return id;
    }

    public Budget getBudget() {
        return budget;
    }

    public BudgetRevision getRevision() {
        return revision;
    }

    public BudgetRevisionSchedule getTargetSchedule() {
        return targetSchedule;
    }

    public BudgetRevisionScheduleOperation getOperation() {
        return operation;
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
