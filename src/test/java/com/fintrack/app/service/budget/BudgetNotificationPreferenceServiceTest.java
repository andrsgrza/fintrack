package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetNotificationPreference;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.repository.BudgetLifecycleEventRepository;
import com.fintrack.app.repository.BudgetNotificationPreferenceRepository;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.BudgetRevisionScheduleRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.service.CurrentUserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
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
class BudgetNotificationPreferenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-15T18:00:00Z");

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private BudgetNotificationPreferenceRepository preferenceRepository;

    @Mock
    private BudgetRevisionScheduleRepository scheduleRepository;

    @Mock
    private BudgetLifecycleEventRepository lifecycleEventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private BudgetTimeZoneService budgetTimeZoneService;

    @Mock
    private BudgetPeriodCloseCursorService periodCloseCursorService;

    private BudgetNotificationPreferenceService service;
    private Budget budget;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(1L);
        owner.setLogin("owner");
        budget = Budget.create(owner, "Food", null, BudgetType.EXPENSE, CurrencyCode.MXN, "request", "hash", NOW);
        ReflectionTestUtils.setField(budget, "id", 12L);
        service = new BudgetNotificationPreferenceService(
            budgetRepository,
            preferenceRepository,
            scheduleRepository,
            lifecycleEventRepository,
            userRepository,
            currentUserService,
            budgetTimeZoneService,
            periodCloseCursorService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        lenient().when(currentUserService.getCurrentUserLogin()).thenReturn("owner");
        lenient().when(userRepository.findOneForBudgetNotificationMutationByLogin("owner")).thenReturn(Optional.of(owner));
        lenient().when(budgetRepository.findOneByIdAndUserLogin(12L, "owner")).thenReturn(Optional.of(budget));
    }

    @Test
    void creates_complete_preference_without_emitting_financial_work() {
        BudgetNotificationPreferenceUpdateCommand command = new BudgetNotificationPreferenceUpdateCommand(
            null,
            true,
            new LinkedHashSet<>(Set.of(new BigDecimal("50.0000"), new BigDecimal("125.5000"))),
            false
        );
        when(preferenceRepository.findOneForOwnedMutation(12L, "owner")).thenReturn(Optional.empty());
        when(preferenceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetNotificationPreferenceView result = service.update(12L, command);

        assertThat(result.configured()).isTrue();
        assertThat(result.thresholdPercentages()).containsExactly(new BigDecimal("50.0000"), new BigDecimal("125.5000"));
        ArgumentCaptor<BudgetNotificationPreference> saved = ArgumentCaptor.forClass(BudgetNotificationPreference.class);
        verify(preferenceRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isPercentageAlertsEnabled()).isTrue();
        assertThat(saved.getValue().isPeriodCloseEnabled()).isFalse();
    }

    @Test
    void rejects_duplicate_normalized_thresholds_before_persistence() {
        BudgetNotificationPreferenceUpdateCommand command = new BudgetNotificationPreferenceUpdateCommand(
            null,
            true,
            new LinkedHashSet<>(Set.of(new BigDecimal("50.0"), new BigDecimal("50.0000"))),
            false
        );

        assertThatThrownBy(() -> service.update(12L, command)).isInstanceOf(BudgetCommandException.class);

        verify(preferenceRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejects_stale_updates_with_optimistic_version_contract() {
        BudgetNotificationPreference existing = BudgetNotificationPreference.create(budget, true, false, NOW);
        existing.replace(true, false, Set.of(new BigDecimal("50.0000")), NOW);
        ReflectionTestUtils.setField(existing, "version", 4L);
        when(preferenceRepository.findOneForOwnedMutation(12L, "owner")).thenReturn(Optional.of(existing));
        BudgetNotificationPreferenceUpdateCommand command = new BudgetNotificationPreferenceUpdateCommand(
            3L,
            true,
            Set.of(new BigDecimal("80.0000")),
            false
        );

        assertThatThrownBy(() -> service.update(12L, command)).isInstanceOf(BudgetCommandException.class);

        verify(preferenceRepository, never()).saveAndFlush(any());
    }

    @Test
    void advancesTheAuditTimestampForThresholdOnlyUpdates() {
        BudgetNotificationPreference existing = BudgetNotificationPreference.create(budget, true, true, NOW);
        ReflectionTestUtils.setField(existing, "version", 4L);
        when(preferenceRepository.findOneForOwnedMutation(12L, "owner")).thenReturn(Optional.of(existing));
        when(preferenceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(
            12L,
            new BudgetNotificationPreferenceUpdateCommand(4L, true, Set.of(new BigDecimal("50.0000"), new BigDecimal("80.0000")), true)
        );

        assertThat(existing.getUpdatedAt()).isEqualTo(NOW.plusNanos(1));
    }

    @Test
    void startsAnImmediateCloseEpisodeOnlyOnAGenuineOffToOnTransition() {
        Instant originalEpisode = NOW.minusSeconds(3600);
        BudgetNotificationPreference existing = BudgetNotificationPreference.create(budget, false, true, originalEpisode);
        existing.replace(false, true, Set.of(), originalEpisode);
        ReflectionTestUtils.setField(existing, "version", 4L);
        when(preferenceRepository.findOneForOwnedMutation(12L, "owner")).thenReturn(Optional.of(existing));
        when(preferenceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetNotificationPreferenceView unchanged = service.update(
            12L,
            new BudgetNotificationPreferenceUpdateCommand(4L, true, Set.of(new BigDecimal("50.0000")), true)
        );
        assertThat(unchanged.periodCloseEnabledSince()).isEqualTo(originalEpisode);

        BudgetNotificationPreferenceView disabled = service.update(
            12L,
            new BudgetNotificationPreferenceUpdateCommand(4L, true, Set.of(new BigDecimal("80.0000")), false)
        );
        assertThat(disabled.periodCloseEnabledSince()).isNull();
        assertThat(existing.permitsPeriodCloseCapture(NOW.minusSeconds(1))).isFalse();

        BudgetNotificationPreferenceView reenabled = service.update(
            12L,
            new BudgetNotificationPreferenceUpdateCommand(4L, true, Set.of(new BigDecimal("80.0000")), true)
        );
        assertThat(reenabled.periodCloseEnabledSince()).isEqualTo(NOW);
        assertThat(existing.permitsPeriodCloseCapture(NOW.minusSeconds(1))).isFalse();
        assertThat(existing.permitsPeriodCloseCapture(NOW)).isTrue();
    }

    @Test
    void normalizesOptionalCreationPreferencesWithoutPersistingDefaults() {
        assertThat(service.normalizeForCreation(null)).isNull();

        BudgetNotificationPreferenceInput normalized = service.normalizeForCreation(
            new BudgetNotificationPreferenceInput(true, Set.of(new BigDecimal("80.5"), new BigDecimal("50.0000")), true)
        );
        assertThat(normalized.percentageAlertsEnabled()).isTrue();
        assertThat(normalized.periodCloseEnabled()).isTrue();
        assertThat(normalized.thresholdPercentages()).containsExactlyInAnyOrder(new BigDecimal("50.0000"), new BigDecimal("80.5000"));
    }
}
