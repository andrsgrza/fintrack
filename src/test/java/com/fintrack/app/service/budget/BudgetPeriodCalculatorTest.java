package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.app.domain.BudgetCalendarDefinition;
import com.fintrack.app.domain.enumeration.BudgetAlignment;
import com.fintrack.app.domain.enumeration.BudgetIntervalUnit;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceMode;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceTermination;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BudgetPeriodCalculatorTest {

    @Test
    void keepsTheOriginalDayOfMonthAcrossShortMonthsWithoutDrifting() {
        BudgetPeriodCalculator calculator = calculator(
            1,
            BudgetIntervalUnit.MONTH,
            BudgetAlignment.ANCHORED,
            LocalDate.of(2027, 1, 31),
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE,
            null,
            null
        );

        assertThat(calculator.period(0)).isEqualTo(new BudgetPeriod(0, LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28)));
        assertThat(calculator.period(1)).isEqualTo(new BudgetPeriod(1, LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)));
        assertThat(calculator.period(2)).isEqualTo(new BudgetPeriod(2, LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30)));
    }

    @Test
    void restoresFebruaryTwentyNinthInTheNextLeapYear() {
        BudgetPeriodCalculator calculator = calculator(
            1,
            BudgetIntervalUnit.YEAR,
            BudgetAlignment.ANCHORED,
            LocalDate.of(2024, 2, 29),
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE,
            null,
            null
        );

        assertThat(calculator.period(1).startInclusive()).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(calculator.period(4).startInclusive()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void alignsNaturalWeekToIsoMondayAndUsesExclusiveEndBoundaries() {
        BudgetPeriodCalculator calculator = calculator(
            2,
            BudgetIntervalUnit.WEEK,
            BudgetAlignment.NATURAL,
            LocalDate.of(2026, 1, 5),
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE,
            null,
            null
        );

        assertThat(calculator.period(0)).isEqualTo(new BudgetPeriod(0, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 19)));
        assertThat(calculator.periodContaining(LocalDate.of(2026, 1, 18))).contains(calculator.period(0));
        assertThat(calculator.periodContaining(LocalDate.of(2026, 1, 19))).contains(calculator.period(1));
    }

    @Test
    void rejectsAClaimedNaturalBoundaryThatIsNotNatural() {
        assertThatThrownBy(() ->
            calculator(
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.NATURAL,
                LocalDate.of(2026, 1, 2),
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("natural boundary");
    }

    @Test
    void rejectsDayAsANaturalCalendarInterval() {
        assertThatThrownBy(() ->
            calculator(
                1,
                BudgetIntervalUnit.DAY,
                BudgetAlignment.NATURAL,
                LocalDate.of(2026, 1, 1),
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("natural boundary");
    }

    @Test
    void supportsACustomTuesdayWeekWithoutChangingItsPhase() {
        BudgetPeriodCalculator calculator = new BudgetPeriodCalculator(
            new BudgetCalendarDefinition(
                1,
                BudgetIntervalUnit.WEEK,
                BudgetAlignment.CUSTOM,
                LocalDate.of(2026, 11, 17),
                null,
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null
            )
        );

        assertThat(calculator.period(0)).isEqualTo(new BudgetPeriod(0, LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 24)));
        assertThat(calculator.period(1)).isEqualTo(new BudgetPeriod(1, LocalDate.of(2026, 11, 24), LocalDate.of(2026, 12, 1)));
    }

    @Test
    void retainsCustomDayThirtyOneWhenTheFirstCycleIsClampedInFebruary() {
        BudgetPeriodCalculator calculator = new BudgetPeriodCalculator(
            new BudgetCalendarDefinition(
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.CUSTOM,
                LocalDate.of(2027, 2, 28),
                31,
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null
            )
        );

        assertThat(calculator.period(0)).isEqualTo(new BudgetPeriod(0, LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)));
        assertThat(calculator.period(1)).isEqualTo(new BudgetPeriod(1, LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30)));
    }

    @Test
    void supportsOrdinaryAndMonthEndCustomMonthlyDaysWithoutDrift() {
        BudgetPeriodCalculator dayThirteen = new BudgetPeriodCalculator(
            new BudgetCalendarDefinition(
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.CUSTOM,
                LocalDate.of(2027, 1, 13),
                13,
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null
            )
        );
        BudgetPeriodCalculator dayTwentyNine = new BudgetPeriodCalculator(
            new BudgetCalendarDefinition(
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.CUSTOM,
                LocalDate.of(2027, 2, 28),
                29,
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null
            )
        );
        BudgetPeriodCalculator dayThirty = new BudgetPeriodCalculator(
            new BudgetCalendarDefinition(
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.CUSTOM,
                LocalDate.of(2027, 2, 28),
                30,
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null
            )
        );

        assertThat(dayThirteen.period(1).startInclusive()).isEqualTo(LocalDate.of(2027, 2, 13));
        assertThat(dayTwentyNine.period(1).startInclusive()).isEqualTo(LocalDate.of(2027, 3, 29));
        assertThat(dayThirty.period(1).startInclusive()).isEqualTo(LocalDate.of(2027, 3, 30));
        assertThat(dayThirty.period(2).startInclusive()).isEqualTo(LocalDate.of(2027, 4, 30));
    }

    @Test
    void supportsCustomQuarterAndCustomLeapYearAnniversaries() {
        BudgetPeriodCalculator quarter = new BudgetPeriodCalculator(
            new BudgetCalendarDefinition(
                1,
                BudgetIntervalUnit.QUARTER,
                BudgetAlignment.CUSTOM,
                LocalDate.of(2027, 2, 28),
                31,
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null
            )
        );
        BudgetPeriodCalculator leapYear = new BudgetPeriodCalculator(
            new BudgetCalendarDefinition(
                1,
                BudgetIntervalUnit.YEAR,
                BudgetAlignment.CUSTOM,
                LocalDate.of(2027, 2, 28),
                29,
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null
            )
        );

        assertThat(quarter.period(1).startInclusive()).isEqualTo(LocalDate.of(2027, 5, 31));
        assertThat(quarter.period(2).startInclusive()).isEqualTo(LocalDate.of(2027, 8, 31));
        assertThat(leapYear.period(1).startInclusive()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(leapYear.period(5).startInclusive()).isEqualTo(LocalDate.of(2032, 2, 29));
    }

    @Test
    void supportsNaturalQuarterBoundariesAndRejectsUnsupportedCustomDays() {
        BudgetPeriodCalculator naturalQuarter = calculator(
            1,
            BudgetIntervalUnit.QUARTER,
            BudgetAlignment.NATURAL,
            LocalDate.of(2026, 4, 1),
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE,
            null,
            null
        );
        assertThat(naturalQuarter.period(0)).isEqualTo(new BudgetPeriod(0, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 1)));
        assertThatThrownBy(() ->
            new BudgetPeriodCalculator(
                new BudgetCalendarDefinition(
                    4,
                    BudgetIntervalUnit.DAY,
                    BudgetAlignment.CUSTOM,
                    LocalDate.of(2026, 1, 1),
                    null,
                    BudgetRecurrenceMode.RECURRING,
                    BudgetRecurrenceTermination.NONE,
                    null,
                    null
                )
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not supported for day");
    }

    @Test
    void supportsNaturalYearsAndCustomAnnualAnniversaries() {
        BudgetPeriodCalculator naturalYear = calculator(
            2,
            BudgetIntervalUnit.YEAR,
            BudgetAlignment.NATURAL,
            LocalDate.of(2026, 1, 1),
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE,
            null,
            null
        );
        BudgetPeriodCalculator customAnniversary = new BudgetPeriodCalculator(
            new BudgetCalendarDefinition(
                1,
                BudgetIntervalUnit.YEAR,
                BudgetAlignment.CUSTOM,
                LocalDate.of(2026, 11, 13),
                13,
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null
            )
        );

        assertThat(naturalYear.period(1)).isEqualTo(new BudgetPeriod(1, LocalDate.of(2028, 1, 1), LocalDate.of(2030, 1, 1)));
        assertThat(customAnniversary.period(1)).isEqualTo(new BudgetPeriod(1, LocalDate.of(2027, 11, 13), LocalDate.of(2028, 11, 13)));
    }

    @Test
    void enforcesBothRecurrenceTerminationFormsWithoutCreatingPartialPeriods() {
        BudgetPeriodCalculator afterTwo = calculator(
            1,
            BudgetIntervalUnit.MONTH,
            BudgetAlignment.ANCHORED,
            LocalDate.of(2026, 1, 15),
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.AFTER_N_PERIODS,
            2,
            null
        );
        assertThat(afterTwo.periods(0, 1)).hasSize(2);
        assertThat(afterTwo.periodContaining(LocalDate.of(2026, 3, 15))).isEmpty();

        BudgetPeriodCalculator throughMarch = calculator(
            1,
            BudgetIntervalUnit.MONTH,
            BudgetAlignment.ANCHORED,
            LocalDate.of(2026, 1, 15),
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.END_DATE,
            null,
            LocalDate.of(2026, 3, 15)
        );
        assertThat(throughMarch.periods(0, 1)).hasSize(2);
        assertThat(throughMarch.periodContaining(LocalDate.of(2026, 3, 15))).isEmpty();
        assertThatThrownBy(() ->
            calculator(
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.ANCHORED,
                LocalDate.of(2026, 1, 15),
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.END_DATE,
                null,
                LocalDate.of(2026, 3, 14)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("complete period boundary");
    }

    @Test
    void modelsAOneTimeBudgetAsOneCompletePeriodOnly() {
        BudgetPeriodCalculator calculator = calculator(
            10,
            BudgetIntervalUnit.DAY,
            BudgetAlignment.ANCHORED,
            LocalDate.of(2026, 5, 10),
            BudgetRecurrenceMode.ONCE,
            BudgetRecurrenceTermination.NONE,
            null,
            null
        );

        assertThat(calculator.period(0)).isEqualTo(new BudgetPeriod(0, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 20)));
        assertThat(calculator.periodContaining(LocalDate.of(2026, 5, 20))).isEmpty();
        assertThatThrownBy(() -> calculator.period(1)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("termination");
    }

    private static BudgetPeriodCalculator calculator(
        int intervalCount,
        BudgetIntervalUnit unit,
        BudgetAlignment alignment,
        LocalDate anchor,
        BudgetRecurrenceMode recurrenceMode,
        BudgetRecurrenceTermination termination,
        Integer terminationCount,
        LocalDate endDate
    ) {
        return new BudgetPeriodCalculator(
            new BudgetCalendarDefinition(intervalCount, unit, alignment, anchor, recurrenceMode, termination, terminationCount, endDate)
        );
    }
}
