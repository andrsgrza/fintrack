package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetRevision;
import com.fintrack.app.domain.BudgetRevisionConfiguration;
import com.fintrack.app.domain.BudgetRevisionSchedule;
import com.fintrack.app.domain.BudgetTimeZoneAssignment;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.BudgetLifecycleEventRepository;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.BudgetRevisionScheduleRepository;
import com.fintrack.app.repository.BudgetTimeZoneAssignmentRepository;
import com.fintrack.app.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BudgetTimeZoneServiceTest {

    @Mock
    private BudgetTimeZoneAssignmentRepository assignmentRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private BudgetRevisionScheduleRepository scheduleRepository;

    @Mock
    private BudgetLifecycleEventRepository lifecycleEventRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void resolves_each_future_assignment_using_the_preceding_effective_zone() {
        User owner = new User();
        owner.setLogin("owner");
        Budget budget = Budget.create(
            owner,
            "Food",
            null,
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            "request",
            "hash",
            Instant.parse("2026-01-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(budget, "id", 42L);
        when(assignmentRepository.findAllByBudgetIdOrderByEffectivePeriodStartAsc(42L)).thenReturn(
            List.of(
                BudgetTimeZoneAssignment.record(budget, "America/Los_Angeles", LocalDate.of(2026, 3, 1), Instant.EPOCH),
                BudgetTimeZoneAssignment.record(budget, "Europe/Madrid", LocalDate.of(2026, 3, 10), Instant.EPOCH),
                BudgetTimeZoneAssignment.record(budget, "Asia/Tokyo", LocalDate.of(2026, 3, 15), Instant.EPOCH)
            )
        );
        BudgetTimeZoneService service = new BudgetTimeZoneService(
            assignmentRepository,
            budgetRepository,
            scheduleRepository,
            lifecycleEventRepository,
            userRepository,
            Clock.fixed(Instant.parse("2026-03-15T00:30:00Z"), ZoneOffset.UTC)
        );

        assertThat(service.resolveCurrentZone(budget)).hasValueSatisfying(zone -> assertThat(zone.getId()).isEqualTo("Asia/Tokyo"));
    }

    @Test
    void rejects_invalid_initial_zone_and_never_falls_back_when_assignment_is_missing() {
        User owner = new User();
        owner.setLogin("owner");
        Budget budget = Budget.create(
            owner,
            "Food",
            null,
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            "request-invalid-zone",
            "hash",
            Instant.parse("2026-01-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(budget, "id", 43L);
        BudgetTimeZoneService service = new BudgetTimeZoneService(
            assignmentRepository,
            budgetRepository,
            scheduleRepository,
            lifecycleEventRepository,
            userRepository,
            Clock.fixed(Instant.parse("2026-03-15T00:30:00Z"), ZoneOffset.UTC)
        );
        when(assignmentRepository.findAllByBudgetIdOrderByEffectivePeriodStartAsc(43L)).thenReturn(List.of());

        assertThat(service.resolveCurrentZone(budget)).isEmpty();
        assertThatThrownBy(() -> service.recordInitial(budget, "not/a-zone", LocalDate.of(2026, 3, 1), Instant.EPOCH))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("timeZone must be a valid IANA zone identifier.");
        verifyNoInteractions(budgetRepository, scheduleRepository, lifecycleEventRepository, userRepository);
    }

    @Test
    void resolves_historical_periods_from_their_own_assignment_not_the_current_profile() {
        Budget budget = budget("owner", "Food", BudgetRecurrenceMode.RECURRING);
        when(assignmentRepository.findAllByBudgetIdOrderByEffectivePeriodStartAsc(budget.getId())).thenReturn(
            List.of(
                BudgetTimeZoneAssignment.record(budget, "America/Mexico_City", LocalDate.of(2026, 3, 1), Instant.EPOCH),
                BudgetTimeZoneAssignment.record(budget, "America/New_York", LocalDate.of(2026, 4, 1), Instant.EPOCH)
            )
        );

        BudgetTimeZoneService service = service(Instant.parse("2026-05-01T12:00:00Z"));

        assertThat(service.resolveZoneForPeriod(budget, LocalDate.of(2026, 3, 1))).hasValueSatisfying(zone ->
            assertThat(zone.getId()).isEqualTo("America/Mexico_City")
        );
        assertThat(service.resolveZoneForPeriod(budget, LocalDate.of(2026, 4, 1))).hasValueSatisfying(zone ->
            assertThat(zone.getId()).isEqualTo("America/New_York")
        );
    }

    @Test
    void supersedes_a_pending_assignment_but_leaves_the_current_period_unchanged() {
        User owner = new User();
        owner.setLogin("owner");
        owner.setTimeZone("America/New_York");
        Budget budget = budget(owner, "Food", BudgetRecurrenceMode.RECURRING);
        BudgetRevisionSchedule initial = initialSchedule(budget, LocalDate.of(2026, 3, 1));
        BudgetTimeZoneAssignment current = BudgetTimeZoneAssignment.record(
            budget,
            "America/Mexico_City",
            LocalDate.of(2026, 3, 1),
            Instant.EPOCH
        );
        BudgetTimeZoneAssignment pending = BudgetTimeZoneAssignment.record(
            budget,
            "America/New_York",
            LocalDate.of(2026, 4, 1),
            Instant.EPOCH
        );
        when(userRepository.findOneForBudgetNotificationMutationByLogin("owner")).thenReturn(java.util.Optional.of(owner));
        when(budgetRepository.findAllForOverviewByUserLogin("owner")).thenReturn(List.of(budget));
        when(scheduleRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId())).thenReturn(List.of(initial));
        when(lifecycleEventRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId())).thenReturn(List.of());
        when(assignmentRepository.findAllByBudgetIdOrderByEffectivePeriodStartAsc(budget.getId())).thenReturn(List.of(current, pending));

        List<BudgetTimeZoneService.EffectiveChange> changes = service(Instant.parse("2026-03-15T18:00:00Z")).scheduleProfileTimeZoneChange(
            "owner",
            "Asia/Tokyo"
        );

        assertThat(owner.getTimeZone()).isEqualTo("Asia/Tokyo");
        assertThat(changes)
            .singleElement()
            .satisfies(change -> assertThat(change.effectivePeriodStart()).isEqualTo(LocalDate.of(2026, 4, 1)));
        verify(assignmentRepository).delete(pending);
        verify(assignmentRepository).flush();
        org.mockito.ArgumentCaptor<BudgetTimeZoneAssignment> saved = org.mockito.ArgumentCaptor.forClass(BudgetTimeZoneAssignment.class);
        verify(assignmentRepository).save(saved.capture());
        assertThat(saved.getValue().getEffectivePeriodStart()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(saved.getValue().getTimeZone()).isEqualTo("Asia/Tokyo");
    }

    @Test
    void terminal_once_budgets_do_not_block_a_profile_change_or_get_fictional_future_assignments() {
        User owner = new User();
        owner.setLogin("owner");
        owner.setTimeZone("America/Mexico_City");
        Budget budget = budget(owner, "One time", BudgetRecurrenceMode.ONCE);
        BudgetRevisionSchedule initial = initialSchedule(budget, LocalDate.of(2026, 3, 1));
        when(userRepository.findOneForBudgetNotificationMutationByLogin("owner")).thenReturn(java.util.Optional.of(owner));
        when(budgetRepository.findAllForOverviewByUserLogin("owner")).thenReturn(List.of(budget));
        when(scheduleRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId())).thenReturn(List.of(initial));
        when(lifecycleEventRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId())).thenReturn(List.of());
        when(assignmentRepository.findAllByBudgetIdOrderByEffectivePeriodStartAsc(budget.getId())).thenReturn(
            List.of(BudgetTimeZoneAssignment.record(budget, "America/Mexico_City", LocalDate.of(2026, 3, 1), Instant.EPOCH))
        );

        assertThat(service(Instant.parse("2026-03-15T18:00:00Z")).scheduleProfileTimeZoneChange("owner", "UTC")).isEmpty();
        assertThat(owner.getTimeZone()).isEqualTo("UTC");
        verifyNoInteractionsWithAssignmentWrites();
    }

    private BudgetTimeZoneService service(Instant now) {
        return new BudgetTimeZoneService(
            assignmentRepository,
            budgetRepository,
            scheduleRepository,
            lifecycleEventRepository,
            userRepository,
            Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private Budget budget(String login, String name, BudgetRecurrenceMode recurrence) {
        User owner = new User();
        owner.setLogin(login);
        return budget(owner, name, recurrence);
    }

    private Budget budget(User owner, String name, BudgetRecurrenceMode recurrence) {
        Budget value = Budget.create(owner, name, null, BudgetType.EXPENSE, CurrencyCode.MXN, "request-" + name, "hash", Instant.EPOCH);
        ReflectionTestUtils.setField(value, "id", name.equals("Food") ? 44L : 45L);
        BudgetRevision revision = BudgetRevision.initial(
            new BudgetRevisionConfiguration(
                java.math.BigDecimal.TEN,
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.NATURAL,
                LocalDate.of(2026, 3, 1),
                recurrence,
                BudgetRecurrenceTermination.NONE,
                null,
                null,
                BudgetAccountScopeMode.ALL,
                BudgetCategoryScopeMode.ALL,
                BudgetTagScopeMode.NONE,
                BudgetTagMatchMode.ANY
            ),
            Set.of(),
            Set.of(),
            Set.of(),
            Instant.EPOCH
        );
        value.addInitialRevision(revision);
        return value;
    }

    private BudgetRevisionSchedule initialSchedule(Budget budget, LocalDate start) {
        return BudgetRevisionSchedule.scheduled(budget, budget.getRevisions().get(0), start, 1, "INITIAL", Instant.EPOCH);
    }

    private void verifyNoInteractionsWithAssignmentWrites() {
        org.mockito.Mockito.verify(assignmentRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(assignmentRepository, org.mockito.Mockito.never()).delete(any());
    }
}
