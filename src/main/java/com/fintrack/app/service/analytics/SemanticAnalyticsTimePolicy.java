package com.fintrack.app.service.analytics;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Component;

/** Product-owned time resolution for semantic query execution; browser and host time zones are never authoritative. */
@Component
public class SemanticAnalyticsTimePolicy {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Mexico_City");
    private static final List<String> RELATIVE_PERIODS = List.of(
        "TODAY",
        "THIS_MONTH",
        "PREVIOUS_MONTH",
        "LAST_30_DAYS",
        "LAST_6_CALENDAR_MONTHS",
        "THIS_YEAR"
    );

    private final Clock clock;

    public SemanticAnalyticsTimePolicy() {
        this(Clock.systemUTC());
    }

    public SemanticAnalyticsTimePolicy(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(BUSINESS_ZONE));
    }

    public List<String> supportedRelativePeriods() {
        return RELATIVE_PERIODS;
    }

    public AnalyticsDateRange resolveRelative(String relativePeriod) {
        if (!RELATIVE_PERIODS.contains(relativePeriod)) {
            throw new IllegalArgumentException("Unsupported relative period: " + relativePeriod);
        }
        LocalDate today = today();
        return switch (relativePeriod) {
            case "TODAY" -> new AnalyticsDateRange(today, today);
            case "THIS_MONTH" -> new AnalyticsDateRange(today.withDayOfMonth(1), today);
            case "PREVIOUS_MONTH" -> {
                YearMonth previousMonth = YearMonth.from(today).minusMonths(1);
                yield new AnalyticsDateRange(previousMonth.atDay(1), previousMonth.atEndOfMonth());
            }
            case "LAST_30_DAYS" -> new AnalyticsDateRange(today.minusDays(29), today);
            case "LAST_6_CALENDAR_MONTHS" -> new AnalyticsDateRange(today.withDayOfMonth(1).minusMonths(5), today);
            case "THIS_YEAR" -> new AnalyticsDateRange(today.withDayOfYear(1), today);
            default -> throw new IllegalStateException("Supported relative period was not handled: " + relativePeriod);
        };
    }
}
