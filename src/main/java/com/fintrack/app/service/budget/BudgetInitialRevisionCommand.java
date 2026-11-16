package com.fintrack.app.service.budget;

import com.fintrack.app.domain.enumeration.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** Transport input for the immutable first revision; reference IDs are resolved server-side. */
public record BudgetInitialRevisionCommand(
    @NotNull BigDecimal targetAmount,
    @NotNull Integer intervalCount,
    @NotNull BudgetIntervalUnit intervalUnit,
    @NotNull BudgetAlignment alignment,
    @NotNull LocalDate anchorDate,
    Integer customDayOfMonth,
    @NotNull BudgetRecurrenceMode recurrenceMode,
    @NotNull BudgetRecurrenceTermination termination,
    Integer terminationPeriodCount,
    LocalDate recurrenceEndDate,
    @NotNull BudgetAccountScopeMode accountScopeMode,
    Set<Long> accountIds,
    @NotNull BudgetCategoryScopeMode categoryScopeMode,
    Set<Long> categoryIds,
    @NotNull BudgetTagScopeMode tagScopeMode,
    Set<Long> tagIds,
    BudgetTagMatchMode tagMatchMode
) {
    public BudgetInitialRevisionCommand(
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
            accountIds,
            categoryScopeMode,
            categoryIds,
            tagScopeMode,
            tagIds,
            tagMatchMode
        );
    }
}
