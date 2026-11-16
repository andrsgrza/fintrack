package com.fintrack.app.domain;

import com.fintrack.app.domain.enumeration.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Validated immutable input used to create the first persisted revision. */
public record BudgetRevisionConfiguration(
    BigDecimal targetAmount,
    Integer intervalCount,
    BudgetIntervalUnit intervalUnit,
    BudgetAlignment alignment,
    LocalDate anchorDate,
    Integer customDayOfMonth,
    BudgetRecurrenceMode recurrenceMode,
    BudgetRecurrenceTermination termination,
    Integer terminationPeriodCount,
    LocalDate recurrenceEndDate,
    BudgetAccountScopeMode accountScopeMode,
    BudgetCategoryScopeMode categoryScopeMode,
    BudgetTagScopeMode tagScopeMode,
    BudgetTagMatchMode tagMatchMode
) {
    public BudgetRevisionConfiguration(
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
        BudgetCategoryScopeMode categoryScopeMode,
        BudgetTagScopeMode tagScopeMode,
        BudgetTagMatchMode tagMatchMode
    ) {
        this(
            targetAmount,
            intervalCount,
            intervalUnit,
            alignment,
            anchorDate,
            null,
            recurrenceMode,
            termination,
            terminationPeriodCount,
            recurrenceEndDate,
            accountScopeMode,
            categoryScopeMode,
            tagScopeMode,
            tagMatchMode
        );
    }

    public BudgetCalendarDefinition calendarDefinition() {
        return new BudgetCalendarDefinition(
            intervalCount,
            intervalUnit,
            alignment,
            anchorDate,
            customDayOfMonth,
            recurrenceMode,
            termination,
            terminationPeriodCount,
            recurrenceEndDate
        );
    }
}
