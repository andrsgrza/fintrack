package com.fintrack.app.service.budget;

import com.fintrack.app.domain.enumeration.BudgetAlignment;
import com.fintrack.app.domain.enumeration.BudgetIntervalUnit;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceMode;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceTermination;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Read-only calendar draft. It deliberately carries no Budget identity or financial scope. */
public record BudgetCalendarPreviewCommand(
    @NotBlank String timeZone,
    @NotNull Integer intervalCount,
    @NotNull BudgetIntervalUnit intervalUnit,
    @NotNull BudgetAlignment alignment,
    @NotNull LocalDate anchorDate,
    Integer customDayOfMonth,
    @NotNull BudgetRecurrenceMode recurrenceMode,
    @NotNull BudgetRecurrenceTermination termination,
    Integer terminationPeriodCount,
    LocalDate recurrenceEndDate
) {
    public BudgetCalendarPreviewCommand(
        String timeZone,
        Integer intervalCount,
        BudgetIntervalUnit intervalUnit,
        BudgetAlignment alignment,
        LocalDate anchorDate,
        BudgetRecurrenceMode recurrenceMode,
        BudgetRecurrenceTermination termination,
        Integer terminationPeriodCount,
        LocalDate recurrenceEndDate
    ) {
        this(
            timeZone,
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
