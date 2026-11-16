package com.fintrack.app.service.budget;

import com.fintrack.app.domain.enumeration.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/** Read representation returned by the only Budget-1 product command. */
public record BudgetCreated(
    Long id,
    Long version,
    String name,
    String description,
    BudgetType type,
    CurrencyCode currency,
    Instant createdAt,
    Instant updatedAt,
    InitialRevision initialRevision
) {
    public record InitialRevision(
        Long id,
        Integer revisionNumber,
        Instant recordedAt,
        BigDecimal targetAmount,
        Integer intervalCount,
        BudgetIntervalUnit intervalUnit,
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
}
