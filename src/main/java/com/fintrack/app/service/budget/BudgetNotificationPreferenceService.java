package com.fintrack.app.service.budget;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetNotificationPreference;
import com.fintrack.app.repository.BudgetNotificationPreferenceRepository;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.service.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
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
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public BudgetNotificationPreferenceService(
        BudgetRepository budgetRepository,
        BudgetNotificationPreferenceRepository preferenceRepository,
        UserRepository userRepository,
        CurrentUserService currentUserService,
        Clock clock
    ) {
        this.budgetRepository = budgetRepository;
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
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
        return view(preferenceRepository.saveAndFlush(preference));
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
        return new BudgetNotificationPreferenceView(budgetId, false, null, false, List.of(), false, null);
    }

    private BudgetNotificationPreferenceView view(BudgetNotificationPreference preference) {
        return new BudgetNotificationPreferenceView(
            preference.getBudget().getId(),
            true,
            preference.getVersion(),
            preference.isPercentageAlertsEnabled(),
            preference.getThresholds().stream().map(threshold -> threshold.getNormalizedPercentage()).sorted().toList(),
            preference.isPeriodCloseEnabled(),
            preference.getUpdatedAt()
        );
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
