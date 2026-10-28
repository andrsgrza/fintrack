package com.fintrack.app.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FinancialMetricTimePolicyTest {

    private final FinancialMetricTimePolicy policy = new FinancialMetricTimePolicy(
        Clock.fixed(Instant.parse("2026-03-01T01:30:00Z"), ZoneOffset.UTC)
    );

    @Test
    void resolvesRelativePeriodsInTheProductTimezoneWithInclusiveBoundaries() {
        assertThat(policy.today()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(policy.resolveRelative("TODAY")).isEqualTo(new AnalyticsDateRange(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 2, 28)));
        assertThat(policy.resolveRelative("THIS_MONTH")).isEqualTo(
            new AnalyticsDateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))
        );
        assertThat(policy.resolveRelative("PREVIOUS_MONTH")).isEqualTo(
            new AnalyticsDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        );
        assertThat(policy.resolveRelative("LAST_30_DAYS")).isEqualTo(
            new AnalyticsDateRange(LocalDate.of(2026, 1, 30), LocalDate.of(2026, 2, 28))
        );
        assertThat(policy.resolveRelative("LAST_6_CALENDAR_MONTHS")).isEqualTo(
            new AnalyticsDateRange(LocalDate.of(2025, 9, 1), LocalDate.of(2026, 2, 28))
        );
        assertThat(policy.resolveRelative("THIS_YEAR")).isEqualTo(
            new AnalyticsDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28))
        );
    }
}
