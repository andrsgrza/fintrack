package com.fintrack.app.service.budget;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetLifecycleEvent;
import com.fintrack.app.domain.BudgetNotificationPreference;
import com.fintrack.app.domain.BudgetRevisionSchedule;
import com.fintrack.app.domain.enumeration.BudgetPeriodOperationalStatus;
import com.fintrack.app.repository.BudgetLifecycleEventRepository;
import com.fintrack.app.repository.BudgetNotificationPreferenceRepository;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.BudgetRevisionScheduleRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.service.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner-scoped optional notification configuration, deliberately independent from Budget revisions. */
@Service
@Transactional
public class BudgetNotificationPreferenceService {

    private static final int PERCENTAGE_SCALE = 4;
    private static final int MAX_THRESHOLD_COUNT = 20;

    private final BudgetRepository budgetRepository;
    private final BudgetNotificationPreferenceRepository preferenceRepository;
    private final BudgetRevisionScheduleRepository scheduleRepository;
    private final BudgetLifecycleEventRepository lifecycleEventRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final BudgetTimeZoneService budgetTimeZoneService;
    private final BudgetPeriodCloseCursorService periodCloseCursorService;
    private final Clock clock;

    public BudgetNotificationPreferenceService(
        BudgetRepository budgetRepository,
        BudgetNotificationPreferenceRepository preferenceRepository,
        BudgetRevisionScheduleRepository scheduleRepository,
        BudgetLifecycleEventRepository lifecycleEventRepository,
        UserRepository userRepository,
        CurrentUserService currentUserService,
        BudgetTimeZoneService budgetTimeZoneService,
        BudgetPeriodCloseCursorService periodCloseCursorService,
        Clock clock
    ) {
        this.budgetRepository = budgetRepository;
        this.preferenceRepository = preferenceRepository;
        this.scheduleRepository = scheduleRepository;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.budgetTimeZoneService = budgetTimeZoneService;
        this.periodCloseCursorService = periodCloseCursorService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BudgetNotificationPreferenceView get(Long budgetId) {
        String login = currentUserService.getCurrentUserLogin();
        Budget budget = budgetRepository.findOneByIdAndUserLogin(budgetId, login).orElseThrow(this::notFound);
        return preferenceRepository.findOneByBudgetId(budget.getId()).map(this::view).orElseGet(() -> absent(budget.getId()));
    }

    public BudgetNotificationPreferenceView update(Long budgetId, BudgetNotificationPreferenceUpdateCommand command) {
        if (command == null || command.percentageAlertsEnabled() == null || command.periodCloseEnabled() == null) {
            throw invalid("Complete notification settings are required.");
        }
        String login = currentUserService.getCurrentUserLogin();
        // The same owner lock as financial capture makes the preference that was current at an event's logical
        // occurrence unambiguous and prevents configuration updates from racing a crossing comparison.
        userRepository.findOneForBudgetNotificationMutationByLogin(login).orElseThrow(this::notFound);
        Budget budget = budgetRepository.findOneByIdAndUserLogin(budgetId, login).orElseThrow(this::notFound);
        requireEditable(budget);
        Set<BigDecimal> thresholds = normalize(command.thresholdPercentages());
        if (command.percentageAlertsEnabled() && thresholds.isEmpty()) {
            throw invalid("At least one threshold is required when percentage alerts are enabled.");
        }
        Instant now = Instant.now(clock);
        Optional<BudgetNotificationPreference> existing = preferenceRepository.findOneForOwnedMutation(budget.getId(), login);
        BudgetNotificationPreference preference;
        if (existing.isEmpty()) {
            if (command.expectedVersion() != null) throw conflict("Notification settings do not exist at that version.");
            preference = BudgetNotificationPreference.create(budget, command.percentageAlertsEnabled(), command.periodCloseEnabled(), now);
            preference.replace(command.percentageAlertsEnabled(), command.periodCloseEnabled(), thresholds, now);
        } else {
            preference = existing.orElseThrow();
            if (command.expectedVersion() == null || !command.expectedVersion().equals(preference.getVersion())) {
                throw conflict("Notification settings changed before they could be updated.");
            }
            preference.replace(command.percentageAlertsEnabled(), command.periodCloseEnabled(), thresholds, now);
        }
        BudgetNotificationPreference saved = preferenceRepository.saveAndFlush(preference);
        periodCloseCursorService.synchronize(budget);
        return view(saved);
    }

    /**
     * Normalizes the optional creation payload before it participates in the creation fingerprint. Nothing is
     * persisted for an omitted payload, so the familiar disabled defaults remain a genuinely absent configuration.
     */
    public BudgetNotificationPreferenceInput normalizeForCreation(BudgetNotificationPreferenceInput supplied) {
        if (supplied == null) return null;
        boolean percentageAlertsEnabled = Boolean.TRUE.equals(supplied.percentageAlertsEnabled());
        boolean periodCloseEnabled = Boolean.TRUE.equals(supplied.periodCloseEnabled());
        Set<BigDecimal> thresholds = normalize(supplied.thresholdPercentages());
        if (percentageAlertsEnabled && thresholds.isEmpty()) {
            throw invalid("At least one threshold is required when percentage alerts are enabled.");
        }
        return new BudgetNotificationPreferenceInput(percentageAlertsEnabled, Set.copyOf(thresholds), periodCloseEnabled);
    }

    /** Called only inside the Budget creation transaction, after the aggregate has an identifier. */
    public void createForBudget(Budget budget, BudgetNotificationPreferenceInput input, Instant now) {
        if (input == null) return;
        BudgetNotificationPreference preference = BudgetNotificationPreference.create(
            budget,
            Boolean.TRUE.equals(input.percentageAlertsEnabled()),
            Boolean.TRUE.equals(input.periodCloseEnabled()),
            now
        );
        preference.replace(
            Boolean.TRUE.equals(input.percentageAlertsEnabled()),
            Boolean.TRUE.equals(input.periodCloseEnabled()),
            normalize(input.thresholdPercentages()),
            now
        );
        preferenceRepository.saveAndFlush(preference);
        periodCloseCursorService.synchronize(budget);
    }

    private Set<BigDecimal> normalize(Set<BigDecimal> supplied) {
        Set<BigDecimal> values = new LinkedHashSet<>();
        if (supplied == null) return values;
        if (supplied.size() > MAX_THRESHOLD_COUNT) throw invalid("No more than " + MAX_THRESHOLD_COUNT + " thresholds may be configured.");
        for (BigDecimal value : supplied) {
            if (value == null || value.signum() <= 0) throw invalid("Threshold percentages must be positive.");
            final BigDecimal normalized;
            try {
                normalized = value.setScale(PERCENTAGE_SCALE, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException exception) {
                throw invalid("Threshold percentages may use at most " + PERCENTAGE_SCALE + " decimal places.");
            }
            if (normalized.precision() > 15) throw invalid("Threshold percentage is too large.");
            if (!values.add(normalized)) throw invalid("Threshold percentages must be unique.");
        }
        return values;
    }

    private BudgetNotificationPreferenceView absent(Long budgetId) {
        return new BudgetNotificationPreferenceView(budgetId, false, null, false, List.of(), false, null, null);
    }

    private BudgetNotificationPreferenceView view(BudgetNotificationPreference preference) {
        return new BudgetNotificationPreferenceView(
            preference.getBudget().getId(),
            true,
            preference.getVersion(),
            preference.isPercentageAlertsEnabled(),
            preference.getThresholds().stream().map(threshold -> threshold.getNormalizedPercentage()).sorted().toList(),
            preference.isPeriodCloseEnabled(),
            preference.getPeriodCloseEnabledSince(),
            preference.getUpdatedAt()
        );
    }

    /**
     * A scheduled future FINISH remains editable until it takes effect. Once the timeline says the current local
     * period is completed (including ONCE/finite recurrence completion), delivery preferences are terminal too.
     */
    private void requireEditable(Budget budget) {
        ZoneId zone = budgetTimeZoneService.resolveCurrentZone(budget).orElse(null);
        if (zone == null) return;
        List<BudgetRevisionSchedule> schedules = scheduleRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId());
        if (schedules.isEmpty()) return;
        List<BudgetLifecycleEvent> events = lifecycleEventRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId());
        BudgetTimeline timeline = new BudgetTimeline(schedules, events);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        timeline
            .periodContaining(today)
            .ifPresent(period -> {
                BudgetPeriodOperationalStatus status = timeline.periods(period.index(), period.index(), today).get(0).status();
                if (status == BudgetPeriodOperationalStatus.COMPLETED) {
                    throw invalid("Notification settings cannot be changed after this Budget has finished.");
                }
            });
    }

    private BudgetCommandException notFound() {
        // Existing Budget command endpoints intentionally use one owner-safe
        // invalid payload response for absent and foreign aggregates.
        return new BudgetCommandException(BudgetCommandException.Code.INVALID_PAYLOAD, "Budget not found.");
    }

    private BudgetCommandException invalid(String message) {
        return new BudgetCommandException(BudgetCommandException.Code.INVALID_PAYLOAD, message);
    }

    private BudgetCommandException conflict(String message) {
        return new BudgetCommandException(BudgetCommandException.Code.VERSION_CONFLICT, message);
    }
}
