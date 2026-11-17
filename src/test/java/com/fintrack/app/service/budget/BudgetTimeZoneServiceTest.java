package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetTimeZoneAssignment;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
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
}
