package com.fintrack.app.service.budget;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.domain.*;
import com.fintrack.app.domain.enumeration.BudgetPeriodOperationalStatus;
import com.fintrack.app.repository.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures threshold crossings inside the same database transaction as a logical financial mutation. The owner row
 * lock establishes one commit-consistent ordering across manual changes, transfer commands, Batch publication and
 * preference edits. The lock order is: owner, financial accounts, financial transactions, Budget preference data.
 */
@Service
@Transactional
public class BudgetThresholdNotificationService {

    private final UserRepository userRepository;
    private final BudgetNotificationPreferenceRepository preferenceRepository;
    private final BudgetThresholdClaimRepository claimRepository;
    private final BudgetNotificationOutboxRepository outboxRepository;
    private final BudgetEvaluationService evaluationService;
    private final BudgetTimeZoneService timeZoneService;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final Clock clock;

    public BudgetThresholdNotificationService(
        UserRepository userRepository,
        BudgetNotificationPreferenceRepository preferenceRepository,
        BudgetThresholdClaimRepository claimRepository,
        BudgetNotificationOutboxRepository outboxRepository,
        BudgetEvaluationService evaluationService,
        BudgetTimeZoneService timeZoneService,
        ObjectMapper objectMapper,
        EntityManager entityManager,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.claimRepository = claimRepository;
        this.outboxRepository = outboxRepository;
        this.evaluationService = evaluationService;
        this.timeZoneService = timeZoneService;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * Executes {@code mutation} exactly once. With no enabled preference or no persisted valid user time zone it is
     * deliberately a low-cost pass-through: existing development budgets must not begin generating alerts.
     */
    public <T> T capture(String ownerLogin, String mutationKind, Supplier<T> mutation, Function<T, String> sourceKey) {
        Objects.requireNonNull(mutation, "mutation is required");
        Objects.requireNonNull(sourceKey, "sourceKey is required");
        if (ownerLogin == null || ownerLogin.isBlank()) return mutation.get();

        User owner = userRepository.findOneForBudgetNotificationMutationByLogin(ownerLogin).orElse(null);
        if (owner == null) return mutation.get();
        List<BudgetNotificationPreference> preferences = preferenceRepository.findAllEnabledForOwnerEvaluation(ownerLogin);
        if (preferences.isEmpty()) return mutation.get();
        Instant eventAt = clock.instant();
        List<BeforeEvaluation> before = preferences
            .stream()
            .map(preference ->
                new BeforeEvaluation(preference, timeZoneService.resolveCurrentZone(preference.getBudget()).orElse(null), null)
            )
            .filter(item -> item.zone() != null)
            .map(item -> new BeforeEvaluation(item.preference(), item.zone(), safelyEvaluate(item.preference().getBudget(), item.zone())))
            .toList();

        T result = mutation.get();
        // Force the final persisted economic state before the canonical Analytics executor issues its aggregate SQL.
        entityManager.flush();
        String resolvedSourceKey = boundedSourceKey(mutationKind, sourceKey.apply(result));
        for (BeforeEvaluation item : before) {
            BudgetEvaluationResult after = safelyEvaluate(item.preference().getBudget(), item.zone());
            captureCrossings(owner, item.preference(), item.before(), after, item.zone(), eventAt, resolvedSourceKey);
        }
        return result;
    }

    /** Acquires the documented first lock for compound publishers before they take Batch or account locks. */
    public void lockOwner(String ownerLogin) {
        if (ownerLogin == null || ownerLogin.isBlank()) return;
        userRepository
            .findOneForBudgetNotificationMutationByLogin(ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Financial mutation owner is not available."));
    }

    private void captureCrossings(
        User owner,
        BudgetNotificationPreference preference,
        BudgetEvaluationResult before,
        BudgetEvaluationResult after,
        ZoneId zone,
        Instant eventAt,
        String sourceKey
    ) {
        if (!eligible(before) || !eligible(after)) return;
        // A timeline or revision transition may make a different period current; it is never valid to infer a crossing
        // across two period scopes.
        if (!Objects.equals(before.startInclusive(), after.startInclusive())) return;
        List<BigDecimal> crossed = preference
            .getThresholds()
            .stream()
            .map(BudgetNotificationThreshold::getNormalizedPercentage)
            .filter(threshold -> before.progressPercentage().compareTo(threshold) < 0)
            .filter(threshold -> after.progressPercentage().compareTo(threshold) >= 0)
            .filter(threshold ->
                !claimRepository.existsByBudgetIdAndPeriodStartAndNormalizedPercentage(after.budgetId(), after.startInclusive(), threshold)
            )
            .sorted()
            .toList();
        if (crossed.isEmpty()) return;

        Budget budget = preference.getBudget();
        List<BigDecimal> claimed = new ArrayList<>();
        for (BigDecimal threshold : crossed) {
            // The unique key remains the final race-safe authority even though the owner lock serializes normal writes.
            claimRepository.save(BudgetThresholdClaim.create(budget, after.startInclusive(), threshold, eventAt, sourceKey));
            claimed.add(threshold);
        }
        BudgetThresholdNotificationSnapshot snapshot = new BudgetThresholdNotificationSnapshot(
            owner.getId(),
            budget.getId(),
            budget.getName(),
            budget.getType(),
            budget.getCurrency(),
            after.startInclusive(),
            after.endExclusive(),
            zone.getId(),
            eventAt,
            before.actualAmount(),
            before.progressPercentage(),
            after.actualAmount(),
            after.progressPercentage(),
            after.targetAmount(),
            List.copyOf(claimed),
            sourceKey,
            after.revisionId()
        );
        outboxRepository.save(BudgetNotificationOutbox.pending(owner, budget, sourceKey, serialize(snapshot), eventAt));
    }

    private BudgetEvaluationResult safelyEvaluate(Budget budget, ZoneId zone) {
        try {
            return evaluationService.evaluateForNotification(budget, zone);
        } catch (RuntimeException ignored) {
            // The notification engine never creates an alert from an unavailable, malformed or otherwise non-authoritative
            // evaluation. The live Budget view remains the place where the condition is surfaced.
            return null;
        }
    }

    private boolean eligible(BudgetEvaluationResult result) {
        return (
            result != null &&
            result.evaluationApplicable() &&
            result.actualAmount() != null &&
            result.progressPercentage() != null &&
            result.periodTiming() == BudgetEvaluationPeriodTiming.CURRENT &&
            result.operationalStatus() == BudgetPeriodOperationalStatus.ACTIVE
        );
    }

    private String serialize(BudgetThresholdNotificationSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Budget notification snapshot could not be serialized.", exception);
        }
    }

    private String boundedSourceKey(String kind, String identity) {
        String key = (kind == null ? "FINANCIAL_MUTATION" : kind) + ":" + (identity == null ? "unknown" : identity);
        return key.substring(0, Math.min(200, key.length()));
    }

    private record BeforeEvaluation(BudgetNotificationPreference preference, ZoneId zone, BudgetEvaluationResult before) {}
}
