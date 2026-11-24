package com.fintrack.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Replaceable scheduling projection for one Budget. Immutable work evidence is stored separately in
 * {@link BudgetPeriodCloseWorkItem}; this row only says which logical close should be discovered next.
 */
@Entity
@Table(
    name = "budget_period_close_cursor",
    uniqueConstraints = @UniqueConstraint(name = "ux_budget_period_close_cursor_budget", columnNames = "budget_id"),
    indexes = @Index(name = "ix_budget_period_close_cursor_due", columnList = "next_due_at,budget_id")
)
public class BudgetPeriodCloseCursor implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false, unique = true, updatable = false)
    private Budget budget;

    @NotNull
    @Column(name = "next_period_start", nullable = false)
    private LocalDate nextPeriodStart;

    @NotNull
    @Column(name = "next_due_at", nullable = false)
    private Instant nextDueAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BudgetPeriodCloseCursor() {}

    public static BudgetPeriodCloseCursor schedule(Budget budget, LocalDate periodStart, Instant dueAt, Instant now) {
        BudgetPeriodCloseCursor cursor = new BudgetPeriodCloseCursor();
        cursor.budget = budget;
        cursor.reschedule(periodStart, dueAt, now);
        return cursor;
    }

    public void reschedule(LocalDate periodStart, Instant dueAt, Instant now) {
        if (periodStart == null || dueAt == null || now == null) throw new IllegalArgumentException(
            "Budget close cursor timing is required."
        );
        nextPeriodStart = periodStart;
        nextDueAt = dueAt;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Budget getBudget() {
        return budget;
    }

    public LocalDate getNextPeriodStart() {
        return nextPeriodStart;
    }

    public Instant getNextDueAt() {
        return nextDueAt;
    }
}
