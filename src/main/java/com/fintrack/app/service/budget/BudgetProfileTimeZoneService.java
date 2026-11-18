package com.fintrack.app.service.budget;

import com.fintrack.app.domain.User;
import com.fintrack.app.service.CurrentUserService;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The only authenticated write path for a profile zone that also preserves Budget-effective assignments. */
@Service
public class BudgetProfileTimeZoneService {

    private final CurrentUserService currentUserService;
    private final BudgetTimeZoneService budgetTimeZoneService;

    public BudgetProfileTimeZoneService(CurrentUserService currentUserService, BudgetTimeZoneService budgetTimeZoneService) {
        this.currentUserService = currentUserService;
        this.budgetTimeZoneService = budgetTimeZoneService;
    }

    @Transactional(readOnly = true)
    public BudgetProfileTimeZoneView current() {
        User user = currentUserService.getCurrentUser();
        String persisted = validZoneIdOrNull(user.getTimeZone());
        return new BudgetProfileTimeZoneView(persisted, persisted == null, List.of());
    }

    @Transactional
    public BudgetProfileTimeZoneView update(BudgetProfileTimeZoneCommand command) {
        if (command == null) throw invalid();
        String requested = canonicalZone(command.timeZone());
        List<BudgetProfileTimeZoneView.EffectiveChange> changes = budgetTimeZoneService
            .scheduleProfileTimeZoneChange(currentUserService.getCurrentUserLogin(), requested)
            .stream()
            .map(change ->
                new BudgetProfileTimeZoneView.EffectiveChange(change.budgetId(), change.budgetName(), change.effectivePeriodStart())
            )
            .toList();
        return new BudgetProfileTimeZoneView(requested, false, changes);
    }

    private String canonicalZone(String value) {
        String zone = validZoneIdOrNull(value);
        if (zone == null) throw invalid();
        return zone;
    }

    private String validZoneIdOrNull(String value) {
        try {
            return value == null || value.isBlank() ? null : ZoneId.of(value.trim()).getId();
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("timeZone must be a valid IANA zone identifier.");
    }
}
