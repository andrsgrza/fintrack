package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.User;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.service.CurrentUserService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BudgetProfileTimeZoneServiceTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private BudgetTimeZoneService budgetTimeZoneService;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private BudgetPeriodCloseCursorService periodCloseCursorService;

    @Test
    void reports_missing_profile_configuration_without_persisting_a_browser_suggestion() {
        User user = new User();
        user.setLogin("owner");
        when(currentUserService.getCurrentUser()).thenReturn(user);

        BudgetProfileTimeZoneView view = service().current();

        assertThat(view.timeZone()).isNull();
        assertThat(view.configurationRequired()).isTrue();
        assertThat(view.effectiveChanges()).isEmpty();
    }

    @Test
    void accepts_only_explicit_valid_iana_updates_and_exposes_effective_boundaries() {
        when(currentUserService.getCurrentUserLogin()).thenReturn("owner");
        when(budgetTimeZoneService.scheduleProfileTimeZoneChange("owner", "America/New_York")).thenReturn(
            List.of(new BudgetTimeZoneService.EffectiveChange(9L, "Food", LocalDate.of(2026, 4, 1)))
        );

        BudgetProfileTimeZoneView view = service().update(new BudgetProfileTimeZoneCommand("America/New_York"));

        assertThat(view.timeZone()).isEqualTo("America/New_York");
        assertThat(view.configurationRequired()).isFalse();
        assertThat(view.effectiveChanges())
            .singleElement()
            .satisfies(change -> {
                assertThat(change.budgetId()).isEqualTo(9L);
                assertThat(change.effectivePeriodStart()).isEqualTo(LocalDate.of(2026, 4, 1));
            });
        verify(budgetTimeZoneService).scheduleProfileTimeZoneChange("owner", "America/New_York");
    }

    @Test
    void rejects_invalid_or_empty_identifiers_before_scheduling_any_change() {
        assertThatThrownBy(() -> service().update(new BudgetProfileTimeZoneCommand("not/a-zone")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("timeZone must be a valid IANA zone identifier.");
        assertThatThrownBy(() -> service().update(new BudgetProfileTimeZoneCommand(" ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("timeZone must be a valid IANA zone identifier.");
    }

    private BudgetProfileTimeZoneService service() {
        return new BudgetProfileTimeZoneService(currentUserService, budgetTimeZoneService, budgetRepository, periodCloseCursorService);
    }
}
