package com.fintrack.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/** Immutable effective-period time-zone history for a Budget. */
@Entity
@Table(
    name = "budget_time_zone_assignment",
    uniqueConstraints = @UniqueConstraint(name = "ux_budget_time_zone_effective", columnNames = { "budget_id", "effective_period_start" })
)
public class BudgetTimeZoneAssignment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false, updatable = false)
    private Budget budget;

    @NotNull
    @Size(max = 64)
    @Column(name = "time_zone", length = 64, nullable = false, updatable = false)
    private String timeZone;

    @NotNull
    @Column(name = "effective_period_start", nullable = false, updatable = false)
    private LocalDate effectivePeriodStart;

    @NotNull
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected BudgetTimeZoneAssignment() {}

    public static BudgetTimeZoneAssignment record(Budget budget, String timeZone, LocalDate effectivePeriodStart, Instant recordedAt) {
        BudgetTimeZoneAssignment assignment = new BudgetTimeZoneAssignment();
        assignment.budget = budget;
        assignment.timeZone = timeZone;
        assignment.effectivePeriodStart = effectivePeriodStart;
        assignment.recordedAt = recordedAt;
        return assignment;
    }

    public Long getId() {
        return id;
    }

    public Budget getBudget() {
        return budget;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public LocalDate getEffectivePeriodStart() {
        return effectivePeriodStart;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
