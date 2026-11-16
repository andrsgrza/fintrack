package com.fintrack.app.service.budget;

import com.fintrack.app.domain.BudgetCalendarDefinition;
import com.fintrack.app.service.budget.BudgetCommandException.Code;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sole API adapter for previewing a creation-calendar draft. It invokes the same pure Java calculator used by
 * Budget creation rather than maintaining a browser-side copy of date arithmetic.
 */
@Service
@Transactional(readOnly = true)
public class BudgetCalendarPreviewService {

    private static final int PREVIEW_PERIOD_LIMIT = 6;

    private final Clock clock;

    public BudgetCalendarPreviewService(Clock clock) {
        this.clock = clock;
    }

    public BudgetCalendarPreview preview(BudgetCalendarPreviewCommand command) {
        if (command == null) throw invalid("A calendar draft is required.");
        ZoneId zone = zone(command.timeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        BudgetCalendarDefinition definition = new BudgetCalendarDefinition(
            command.intervalCount(),
            command.intervalUnit(),
            command.alignment(),
            command.anchorDate(),
            command.customDayOfMonth(),
            command.recurrenceMode(),
            command.termination(),
            command.terminationPeriodCount(),
            command.recurrenceEndDate()
        );
        final BudgetPeriodCalculator calculator;
        try {
            calculator = new BudgetPeriodCalculator(definition);
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
        List<BudgetPeriod> periods = new ArrayList<>(PREVIEW_PERIOD_LIMIT);
        for (long index = 0; index < PREVIEW_PERIOD_LIMIT; index++) {
            try {
                periods.add(calculator.period(index));
            } catch (IllegalArgumentException completed) {
                break;
            }
        }
        return new BudgetCalendarPreview(today, List.copyOf(periods));
    }

    private ZoneId zone(String value) {
        try {
            if (value == null || value.isBlank()) throw invalid("timeZone must be a valid IANA zone identifier.");
            return ZoneId.of(value.trim());
        } catch (DateTimeException exception) {
            throw invalid("timeZone must be a valid IANA zone identifier.");
        }
    }

    private static BudgetCommandException invalid(String message) {
        return new BudgetCommandException(Code.INVALID_PAYLOAD, message);
    }
}
