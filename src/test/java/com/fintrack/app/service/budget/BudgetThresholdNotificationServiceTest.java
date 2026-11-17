package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.domain.*;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.*;
import com.fintrack.app.service.analytics.SemanticResultAvailability;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BudgetThresholdNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-15T18:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

    @Mock
    private UserRepository userRepository;

    @Mock
    private BudgetNotificationPreferenceRepository preferenceRepository;

    @Mock
    private BudgetThresholdClaimRepository claimRepository;

    @Mock
    private BudgetNotificationOutboxRepository outboxRepository;

    @Mock
    private BudgetEvaluationService evaluationService;

    @Mock
    private BudgetTimeZoneService timeZoneService;

    @Mock
    private EntityManager entityManager;

    private BudgetThresholdNotificationService service;
    private User owner;
    private Budget budget;
    private BudgetNotificationPreference preference;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setLogin("user");
        budget = Budget.create(owner, "Food", null, BudgetType.EXPENSE, CurrencyCode.MXN, "request", "hash", NOW);
        ReflectionTestUtils.setField(budget, "id", 11L);
        preference = BudgetNotificationPreference.create(budget, true, false, NOW);
        preference.replace(true, false, Set.of(new BigDecimal("50.0000"), new BigDecimal("80.0000"), new BigDecimal("100.0000")), NOW);
        service = new BudgetThresholdNotificationService(
            userRepository,
            preferenceRepository,
            claimRepository,
            outboxRepository,
            evaluationService,
            timeZoneService,
            new ObjectMapper().findAndRegisterModules(),
            entityManager,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(userRepository.findOneForBudgetNotificationMutationByLogin("user")).thenReturn(Optional.of(owner));
        when(preferenceRepository.findAllEnabledForOwnerEvaluation("user")).thenReturn(List.of(preference));
        when(timeZoneService.resolveCurrentZone(budget)).thenReturn(Optional.of(ZONE));
        lenient().when(claimRepository.existsByBudgetIdAndPeriodStartAndNormalizedPercentage(anyLong(), any(), any())).thenReturn(false);
    }

    @Test
    void captures_below_to_equal_threshold_and_serializes_immutable_snapshot() throws Exception {
        when(evaluationService.evaluateForNotification(budget, ZONE)).thenReturn(
            evaluation("49.00", "49.00"),
            evaluation("50.00", "50.00")
        );

        String result = service.capture("user", "FINANCIAL_TRANSACTION_CREATE", () -> "transaction-90", value -> value);

        assertThat(result).isEqualTo("transaction-90");
        verify(claimRepository).save(any(BudgetThresholdClaim.class));
        ArgumentCaptor<BudgetNotificationOutbox> outbox = ArgumentCaptor.forClass(BudgetNotificationOutbox.class);
        verify(outboxRepository).save(outbox.capture());
        var json = new ObjectMapper().findAndRegisterModules().readTree(outbox.getValue().getSnapshotJson());
        assertThat(json.path("budgetName").asText()).isEqualTo("Food");
        assertThat(json.path("newlyCrossedPercentages")).hasSize(1);
        assertThat(json.path("newlyCrossedPercentages").get(0).decimalValue()).isEqualByComparingTo("50.0000");
        verify(entityManager).flush();
    }

    @Test
    void consolidates_multiple_crossed_thresholds_in_one_outbox_event() throws Exception {
        when(evaluationService.evaluateForNotification(budget, ZONE)).thenReturn(
            evaluation("40.00", "40.00"),
            evaluation("120.00", "120.00")
        );

        service.capture("user", "FINANCIAL_TRANSACTION_CREATE", () -> "transaction-91", value -> value);

        verify(claimRepository, times(3)).save(any(BudgetThresholdClaim.class));
        ArgumentCaptor<BudgetNotificationOutbox> outbox = ArgumentCaptor.forClass(BudgetNotificationOutbox.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(
            new ObjectMapper().findAndRegisterModules().readTree(outbox.getValue().getSnapshotJson()).path("newlyCrossedPercentages")
        ).hasSize(3);
    }

    @Test
    void does_not_realert_a_claimed_or_already_above_threshold() {
        when(evaluationService.evaluateForNotification(budget, ZONE)).thenReturn(
            evaluation("90.00", "90.00"),
            evaluation("110.00", "110.00")
        );
        when(
            claimRepository.existsByBudgetIdAndPeriodStartAndNormalizedPercentage(eq(11L), any(), eq(new BigDecimal("100.0000")))
        ).thenReturn(true);

        service.capture("user", "FINANCIAL_TRANSACTION_UPDATE", () -> "transaction-92", value -> value);

        verifyNoInteractions(outboxRepository);
        verify(claimRepository, never()).save(any());
    }

    @Test
    void does_not_alert_when_progress_moves_down_from_above_a_threshold() {
        when(evaluationService.evaluateForNotification(budget, ZONE)).thenReturn(
            evaluation("101.00", "101.00"),
            evaluation("10.00", "10.00")
        );

        service.capture("user", "FINANCIAL_TRANSACTION_DELETE", () -> "transaction-94", value -> value);

        verifyNoInteractions(outboxRepository);
        verify(claimRepository, never()).save(any());
    }

    @Test
    void compares_decimal_and_over_one_hundred_thresholds_without_floating_point_rounding() throws Exception {
        preference.replace(true, false, Set.of(new BigDecimal("33.3333"), new BigDecimal("125.5000")), NOW);
        when(evaluationService.evaluateForNotification(budget, ZONE)).thenReturn(
            evaluation("33.3332", "33.3332"),
            evaluation("126.00", "126.00")
        );

        service.capture("user", "FINANCIAL_TRANSACTION_CREATE", () -> "transaction-95", value -> value);

        verify(claimRepository, times(2)).save(any(BudgetThresholdClaim.class));
        ArgumentCaptor<BudgetNotificationOutbox> outbox = ArgumentCaptor.forClass(BudgetNotificationOutbox.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(
            new ObjectMapper().findAndRegisterModules().readTree(outbox.getValue().getSnapshotJson()).path("newlyCrossedPercentages")
        ).hasSize(2);
    }

    @Test
    void treats_a_zero_evaluation_as_authoritative_when_analytics_marks_it_applicable() {
        preference.replace(true, false, Set.of(new BigDecimal("1.0000")), NOW);
        when(evaluationService.evaluateForNotification(budget, ZONE)).thenReturn(evaluation("0.00", "0.00"), evaluation("1.00", "1.00"));

        service.capture("user", "FINANCIAL_TRANSACTION_CREATE", () -> "transaction-96", value -> value);

        verify(claimRepository).save(any(BudgetThresholdClaim.class));
        verify(outboxRepository).save(any(BudgetNotificationOutbox.class));
    }

    @Test
    void never_converts_an_unavailable_financial_result_into_zero_or_an_alert() {
        when(evaluationService.evaluateForNotification(budget, ZONE)).thenReturn(unavailable(), evaluation("200.00", "200.00"));

        service.capture("user", "FINANCIAL_TRANSACTION_CREATE", () -> "transaction-93", value -> value);

        verifyNoInteractions(outboxRepository);
        verify(claimRepository, never()).save(any());
    }

    private BudgetEvaluationResult evaluation(String actual, String progress) {
        return new BudgetEvaluationResult(
            11L,
            "Food",
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            2,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 4, 1),
            7L,
            new BigDecimal("100.00"),
            BudgetPeriodOperationalStatus.ACTIVE,
            BudgetEvaluationPeriodTiming.CURRENT,
            true,
            new BigDecimal(actual),
            new BigDecimal(progress),
            BigDecimal.ZERO,
            BudgetGoalStatus.WITHIN_LIMIT,
            BudgetEvaluationStatus.EVALUATED,
            SemanticResultAvailability.COMPLETE,
            null,
            null
        );
    }

    private BudgetEvaluationResult unavailable() {
        return new BudgetEvaluationResult(
            11L,
            "Food",
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            2,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 4, 1),
            7L,
            new BigDecimal("100.00"),
            BudgetPeriodOperationalStatus.ACTIVE,
            BudgetEvaluationPeriodTiming.CURRENT,
            false,
            null,
            null,
            null,
            BudgetGoalStatus.UNKNOWN,
            BudgetEvaluationStatus.SEMANTIC_UNAVAILABLE,
            SemanticResultAvailability.UNAVAILABLE,
            BudgetEvaluationFailure.UNAVAILABLE_FINANCIAL_RESULT,
            "Unavailable"
        );
    }
}
