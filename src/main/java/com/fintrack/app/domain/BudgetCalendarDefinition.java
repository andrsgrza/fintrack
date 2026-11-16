package com.fintrack.app.domain;

import com.fintrack.app.domain.enumeration.*;
import java.time.LocalDate;

/** Calendar-only configuration with no persistence or financial-query dependency. */
public record BudgetCalendarDefinition(
    int intervalCount,
    BudgetIntervalUnit intervalUnit,
    BudgetAlignment alignment,
    LocalDate anchorDate,
    Integer customDayOfMonth,
    BudgetRecurrenceMode recurrenceMode,
    BudgetRecurrenceTermination termination,
    Integer terminationPeriodCount,
    LocalDate recurrenceEndDate
) {
    /** Compatibility constructor for anchored and natural definitions without a custom recurrence rule. */
    public BudgetCalendarDefinition(
        int intervalCount,
        BudgetIntervalUnit intervalUnit,
        BudgetAlignment alignment,
        LocalDate anchorDate,
        BudgetRecurrenceMode recurrenceMode,
        BudgetRecurrenceTermination termination,
        Integer terminationPeriodCount,
        LocalDate recurrenceEndDate
    ) {
        this(
            intervalCount,
            intervalUnit,
            alignment,
            anchorDate,
            null,
            recurrenceMode,
            termination,
            terminationPeriodCount,
            recurrenceEndDate
        );
    }
}
