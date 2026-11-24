package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.domain.enumeration.BudgetAlignment;
import com.fintrack.app.domain.enumeration.BudgetIntervalUnit;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceMode;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceTermination;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BudgetCalendarPreviewServiceTest {

    private final BudgetCalendarPreviewService service = new BudgetCalendarPreviewService(
        Clock.fixed(Instant.parse("2026-01-15T18:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void previews_exact_day_anchored_month_end_and_natural_calendar_boundaries() {
        BudgetCalendarPreview fortnight = service.preview(
            command(14, BudgetIntervalUnit.DAY, BudgetAlignment.ANCHORED, LocalDate.of(2026, 1, 15))
        );
        assertThat(fortnight.periods())
            .extracting(BudgetPeriod::startInclusive)
            .containsExactly(
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 29),
                LocalDate.of(2026, 2, 12),
                LocalDate.of(2026, 2, 26),
                LocalDate.of(2026, 3, 12),
                LocalDate.of(2026, 3, 26)
            );

        BudgetCalendarPreview monthEnd = service.preview(
            command(1, BudgetIntervalUnit.MONTH, BudgetAlignment.ANCHORED, LocalDate.of(2026, 1, 31))
        );
        assertThat(monthEnd.periods())
            .extracting(BudgetPeriod::startInclusive)
            .containsExactly(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 6, 30)
            );

        BudgetCalendarPreview natural = service.preview(
            command(1, BudgetIntervalUnit.MONTH, BudgetAlignment.NATURAL, LocalDate.of(2026, 2, 1))
        );
        assertThat(natural.periods().get(1).startInclusive()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void previews_past_starts_and_bounded_finite_recurrence_without_persisting() {
        BudgetCalendarPreview finite = service.preview(
            new BudgetCalendarPreviewCommand(
                "America/Mexico_City",
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.NATURAL,
                LocalDate.of(2026, 2, 1),
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.AFTER_N_PERIODS,
                2,
                null
            )
        );
        assertThat(finite.localToday()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(finite.periods()).hasSize(2);
        assertThat(finite.periods().get(1).endExclusive()).isEqualTo(LocalDate.of(2026, 4, 1));

        BudgetCalendarPreview historical = service.preview(
            command(1, BudgetIntervalUnit.MONTH, BudgetAlignment.ANCHORED, LocalDate.of(2025, 11, 1))
        );
        assertThat(historical.periods().get(0).startInclusive()).isEqualTo(LocalDate.of(2025, 11, 1));
    }

    @Test
    void interprets_today_in_the_browser_iana_zone_near_a_utc_day_boundary() {
        BudgetCalendarPreviewService boundaryService = new BudgetCalendarPreviewService(
            Clock.fixed(Instant.parse("2026-01-15T01:30:00Z"), ZoneOffset.UTC)
        );
        BudgetCalendarPreview mexico = boundaryService.preview(
            new BudgetCalendarPreviewCommand(
                "America/Mexico_City",
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.ANCHORED,
                LocalDate.of(2026, 1, 14),
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null
            )
        );
        assertThat(mexico.localToday()).isEqualTo(LocalDate.of(2026, 1, 14));

        BudgetCalendarPreview tokyo = boundaryService.preview(
            new BudgetCalendarPreviewCommand(
                "Asia/Tokyo",
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.ANCHORED,
                LocalDate.of(2026, 1, 14),
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null
            )
        );
        assertThat(tokyo.localToday()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(tokyo.periods().get(0).startInclusive()).isEqualTo(LocalDate.of(2026, 1, 14));
    }

    @Test
    void previewsAndCarriesTheCustomDayOfMonthRule() {
        BudgetCalendarPreview custom = service.preview(
            new BudgetCalendarPreviewCommand(
                "America/Mexico_City",
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
        assertThat(custom.periods())
            .extracting(BudgetPeriod::startInclusive)
            .startsWith(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30));
    }

    private BudgetCalendarPreviewCommand command(int count, BudgetIntervalUnit unit, BudgetAlignment alignment, LocalDate anchor) {
        return new BudgetCalendarPreviewCommand(
            "America/Mexico_City",
            count,
            unit,
            alignment,
            anchor,
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE,
            null,
            null
        );
    }
}
