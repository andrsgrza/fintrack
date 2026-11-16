package com.fintrack.app.service.budget;

import com.fintrack.app.domain.BudgetCalendarDefinition;
import com.fintrack.app.domain.enumeration.BudgetAlignment;
import com.fintrack.app.domain.enumeration.BudgetIntervalUnit;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceMode;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceTermination;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure deterministic calendar math for a Budget revision. It intentionally knows nothing about accounts,
 * transactions, repositories, or the current clock.
 */
public final class BudgetPeriodCalculator {

    private static final long MAX_RANGE_SIZE = 100_000;

    private final BudgetCalendarDefinition definition;
    private final Long terminatedAfterPeriodCount;

    public BudgetPeriodCalculator(BudgetCalendarDefinition definition) {
        this.definition = requireValid(definition);
        this.terminatedAfterPeriodCount = resolveTerminationCount();
    }

    public BudgetPeriod period(long index) {
        if (index < 0) throw invalid("Period index must not be negative.");
        if (terminatedAfterPeriodCount != null && index >= terminatedAfterPeriodCount) {
            throw invalid("The requested period is outside this revision's recurrence termination.");
        }
        return new BudgetPeriod(index, boundary(index), boundary(nextIndex(index)));
    }

    /** Returns only the bounded requested materialization, never every historical or future period. */
    public List<BudgetPeriod> periods(long firstIndex, long lastIndex) {
        if (firstIndex < 0 || lastIndex < firstIndex) throw invalid("Invalid period range.");
        long size;
        try {
            size = Math.addExact(Math.subtractExact(lastIndex, firstIndex), 1);
        } catch (ArithmeticException exception) {
            throw invalid("Period range overflows.");
        }
        if (size > MAX_RANGE_SIZE) throw invalid("Requested period range is too large.");
        List<BudgetPeriod> periods = new ArrayList<>((int) size);
        for (long index = firstIndex; index <= lastIndex; index++) periods.add(period(index));
        return List.copyOf(periods);
    }

    /** Efficiently locates the one scheduled window containing the local reference date. */
    public Optional<BudgetPeriod> periodContaining(LocalDate referenceDate) {
        if (referenceDate == null || referenceDate.isBefore(definition.anchorDate())) return Optional.empty();
        long candidate = estimateIndex(referenceDate);
        while (candidate > 0 && referenceDate.isBefore(boundary(candidate))) candidate--;
        while (true) {
            if (terminatedAfterPeriodCount != null && candidate >= terminatedAfterPeriodCount) return Optional.empty();
            LocalDate start = boundary(candidate);
            LocalDate end = boundary(nextIndex(candidate));
            if (referenceDate.isBefore(start)) return Optional.empty();
            if (referenceDate.isBefore(end)) return Optional.of(new BudgetPeriod(candidate, start, end));
            candidate = nextIndex(candidate);
        }
    }

    public BudgetCalendarDefinition definition() {
        return definition;
    }

    private Long resolveTerminationCount() {
        if (definition.recurrenceMode() == BudgetRecurrenceMode.ONCE) return 1L;
        return switch (definition.termination()) {
            case NONE -> null;
            case AFTER_N_PERIODS -> definition.terminationPeriodCount().longValue();
            case END_DATE -> findBoundaryIndex(definition.recurrenceEndDate());
        };
    }

    private long findBoundaryIndex(LocalDate requestedBoundary) {
        if (requestedBoundary == null || !requestedBoundary.isAfter(definition.anchorDate())) {
            throw invalid("A recurrence end date must be after the anchor date.");
        }
        long estimate = estimateIndex(requestedBoundary);
        for (long index = Math.max(0, estimate - 2); index <= safeAdd(estimate, 2); index++) {
            if (boundary(index).equals(requestedBoundary)) return index;
        }
        throw invalid("Recurrence end date must be a complete period boundary.");
    }

    private long estimateIndex(LocalDate date) {
        try {
            return switch (definition.intervalUnit()) {
                case DAY -> Math.floorDiv(ChronoUnit.DAYS.between(definition.anchorDate(), date), definition.intervalCount());
                case WEEK -> Math.floorDiv(
                    ChronoUnit.DAYS.between(definition.anchorDate(), date),
                    Math.multiplyExact(7L, definition.intervalCount())
                );
                case MONTH -> Math.floorDiv(monthDistance(date), definition.intervalCount());
                case QUARTER -> Math.floorDiv(monthDistance(date), Math.multiplyExact(3, definition.intervalCount()));
                case YEAR -> Math.floorDiv((long) date.getYear() - definition.anchorDate().getYear(), definition.intervalCount());
            };
        } catch (ArithmeticException exception) {
            throw invalid("Period arithmetic overflows.");
        }
    }

    private long monthDistance(LocalDate date) {
        return ChronoUnit.MONTHS.between(YearMonth.from(definition.anchorDate()), YearMonth.from(date));
    }

    private LocalDate boundary(long index) {
        if (index < 0) throw invalid("Period index must not be negative.");
        try {
            return switch (definition.intervalUnit()) {
                case DAY -> definition.anchorDate().plusDays(Math.multiplyExact(index, definition.intervalCount()));
                case WEEK -> definition
                    .anchorDate()
                    .plusDays(Math.multiplyExact(index, Math.multiplyExact(7L, definition.intervalCount())));
                case MONTH -> monthlyBoundary(index, definition.intervalCount());
                case QUARTER -> monthlyBoundary(index, Math.multiplyExact(3, definition.intervalCount()));
                case YEAR -> yearlyBoundary(index);
            };
        } catch (ArithmeticException | DateTimeException exception) {
            throw invalid("Period arithmetic overflows.");
        }
    }

    private LocalDate monthlyBoundary(long index, int monthsPerInterval) {
        YearMonth target = YearMonth.from(definition.anchorDate()).plusMonths(Math.multiplyExact(index, (long) monthsPerInterval));
        if (definition.alignment() == BudgetAlignment.NATURAL) return target.atDay(1);
        return target.atDay(Math.min(requestedDayOfMonth(), target.lengthOfMonth()));
    }

    private LocalDate yearlyBoundary(long index) {
        int targetYear = Math.toIntExact(
            Math.addExact(definition.anchorDate().getYear(), Math.multiplyExact(index, definition.intervalCount()))
        );
        if (definition.alignment() == BudgetAlignment.NATURAL) return LocalDate.of(targetYear, 1, 1);
        YearMonth target = YearMonth.of(targetYear, definition.anchorDate().getMonth());
        return target.atDay(Math.min(requestedDayOfMonth(), target.lengthOfMonth()));
    }

    /**
     * Custom calendars retain this user-selected value separately from the first materialized boundary. That is what
     * lets a rule for day 31 start in February and recover March 31 instead of drifting permanently to February 28.
     */
    private int requestedDayOfMonth() {
        return definition.alignment() == BudgetAlignment.CUSTOM ? definition.customDayOfMonth() : definition.anchorDate().getDayOfMonth();
    }

    private static BudgetCalendarDefinition requireValid(BudgetCalendarDefinition definition) {
        if (definition == null) throw invalid("Budget calendar definition is required.");
        if (
            definition.intervalCount() <= 0 ||
            definition.intervalUnit() == null ||
            definition.alignment() == null ||
            definition.anchorDate() == null
        ) {
            throw invalid("A positive interval, unit, alignment, and anchor date are required.");
        }
        if (definition.recurrenceMode() == null || definition.termination() == null) throw invalid(
            "Recurrence mode and termination are required."
        );
        if (definition.recurrenceMode() == BudgetRecurrenceMode.ONCE) {
            if (
                definition.termination() != BudgetRecurrenceTermination.NONE ||
                definition.terminationPeriodCount() != null ||
                definition.recurrenceEndDate() != null
            ) {
                throw invalid("A one-time budget cannot carry a recurrence termination.");
            }
        } else if (definition.termination() == BudgetRecurrenceTermination.NONE) {
            if (definition.terminationPeriodCount() != null || definition.recurrenceEndDate() != null) throw invalid(
                "NONE termination cannot carry a value."
            );
        } else if (definition.termination() == BudgetRecurrenceTermination.AFTER_N_PERIODS) {
            if (
                definition.terminationPeriodCount() == null ||
                definition.terminationPeriodCount() <= 0 ||
                definition.recurrenceEndDate() != null
            ) {
                throw invalid("AFTER_N_PERIODS requires one positive period count only.");
            }
        } else if (definition.termination() == BudgetRecurrenceTermination.END_DATE) {
            if (definition.recurrenceEndDate() == null || definition.terminationPeriodCount() != null) {
                throw invalid("END_DATE requires one complete end boundary only.");
            }
        }
        validateNaturalAlignment(definition);
        validateCustomAlignment(definition);
        return definition;
    }

    private static void validateNaturalAlignment(BudgetCalendarDefinition definition) {
        if (definition.alignment() != BudgetAlignment.NATURAL) return;
        LocalDate anchor = definition.anchorDate();
        boolean valid =
            switch (definition.intervalUnit()) {
                case DAY -> false;
                case WEEK -> anchor.getDayOfWeek() == DayOfWeek.MONDAY;
                case MONTH -> anchor.getDayOfMonth() == 1;
                case QUARTER -> anchor.getDayOfMonth() == 1 && ((anchor.getMonthValue() - 1) % 3 == 0);
                case YEAR -> anchor.getMonthValue() == 1 && anchor.getDayOfMonth() == 1;
            };
        if (!valid) throw invalid("Anchor date is not a natural boundary for the selected interval.");
    }

    private static void validateCustomAlignment(BudgetCalendarDefinition definition) {
        if (definition.alignment() != BudgetAlignment.CUSTOM) {
            if (definition.customDayOfMonth() != null) throw invalid("A custom day of month is allowed only for a custom calendar.");
            return;
        }
        if (definition.intervalUnit() == BudgetIntervalUnit.DAY) {
            throw invalid("Custom calendar mode is not supported for day intervals.");
        }
        if (definition.intervalUnit() == BudgetIntervalUnit.WEEK) {
            if (definition.customDayOfMonth() != null) throw invalid("Custom weeks use the selected weekday, not a day of month.");
            return;
        }
        Integer requested = definition.customDayOfMonth();
        if (requested == null || requested < 1 || requested > 31) {
            throw invalid("A custom calendar requires a day of month from 1 through 31.");
        }
        YearMonth firstMonth = YearMonth.from(definition.anchorDate());
        int expectedFirstDay = Math.min(requested, firstMonth.lengthOfMonth());
        if (definition.anchorDate().getDayOfMonth() != expectedFirstDay) {
            throw invalid("The first custom calendar cycle must begin on the selected recurring boundary.");
        }
    }

    private static long nextIndex(long index) {
        try {
            return Math.addExact(index, 1);
        } catch (ArithmeticException exception) {
            throw invalid("Period index overflows.");
        }
    }

    private static long safeAdd(long value, long increment) {
        try {
            return Math.addExact(value, increment);
        } catch (ArithmeticException exception) {
            throw invalid("Period index overflows.");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
