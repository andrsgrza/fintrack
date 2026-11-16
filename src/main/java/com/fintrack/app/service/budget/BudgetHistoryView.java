package com.fintrack.app.service.budget;

import com.fintrack.app.domain.enumeration.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Read-only, product-oriented history. It intentionally contains configuration and operations, never financial evaluation. */
public record BudgetHistoryView(
    Long budgetId,
    Long version,
    List<Revision> revisions,
    List<Schedule> schedules,
    List<LifecycleEvent> lifecycleEvents
) {
    public record Revision(
        Long id,
        Integer number,
        Instant recordedAt,
        BigDecimal targetAmount,
        BudgetIntervalUnit intervalUnit,
        Integer intervalCount,
        BudgetAlignment alignment,
        LocalDate anchorDate,
        BudgetRecurrenceMode recurrenceMode,
        BudgetRecurrenceTermination termination,
        Integer terminationPeriodCount,
        LocalDate recurrenceEndDate,
        BudgetAccountScopeMode accountScopeMode,
        Set<Long> accountIds,
        BudgetCategoryScopeMode categoryScopeMode,
        Set<Long> categoryIds,
        BudgetTagScopeMode tagScopeMode,
        Set<Long> tagIds,
        BudgetTagMatchMode tagMatchMode
    ) {}

    public record Schedule(
        Long id,
        BudgetRevisionScheduleOperation operation,
        Long revisionId,
        Long targetScheduleId,
        LocalDate effectivePeriodStart,
        Long sequenceNumber,
        Instant recordedAt,
        boolean active
    ) {}

    public record LifecycleEvent(
        Long id,
        BudgetLifecycleEventType type,
        Long targetEventId,
        LocalDate effectivePeriodStart,
        Long sequenceNumber,
        Instant recordedAt
    ) {}
}
