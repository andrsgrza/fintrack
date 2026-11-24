package com.fintrack.app.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetRevision;
import com.fintrack.app.domain.BudgetRevisionConfiguration;
import com.fintrack.app.domain.BudgetRevisionSchedule;
import com.fintrack.app.domain.BudgetTimeZoneAssignment;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.BudgetAccountScopeMode;
import com.fintrack.app.domain.enumeration.BudgetAlignment;
import com.fintrack.app.domain.enumeration.BudgetCategoryScopeMode;
import com.fintrack.app.domain.enumeration.BudgetIntervalUnit;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceMode;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceTermination;
import com.fintrack.app.domain.enumeration.BudgetTagMatchMode;
import com.fintrack.app.domain.enumeration.BudgetTagScopeMode;
import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.BudgetRevisionScheduleRepository;
import com.fintrack.app.repository.BudgetTimeZoneAssignmentRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.service.budget.BudgetTimeZoneService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed account boundary coverage: profile writes must go through the Budget-aware service. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = "user")
@Import(BudgetProfileTimeZoneResourceIT.FixedClockConfiguration.class)
@Transactional
class BudgetProfileTimeZoneResourceIT {

    private static final Instant NOW = Instant.parse("2026-03-15T18:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private BudgetRevisionScheduleRepository scheduleRepository;

    @Autowired
    private BudgetTimeZoneAssignmentRepository assignmentRepository;

    @Autowired
    private BudgetTimeZoneService budgetTimeZoneService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void requires_explicit_confirmation_and_persists_only_a_valid_iana_profile_zone() throws Exception {
        User user = userRepository.findOneByLogin("user").orElseThrow();
        user.setTimeZone(null);
        entityManager.flush();

        mockMvc
            .perform(get("/api/account/budget-time-zone"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeZone").isEmpty())
            .andExpect(jsonPath("$.configurationRequired").value(true));

        mockMvc
            .perform(
                put("/api/account/budget-time-zone")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(Map.of("timeZone", "America/New_York")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeZone").value("America/New_York"))
            .andExpect(jsonPath("$.configurationRequired").value(false))
            .andExpect(jsonPath("$.effectiveChanges").isEmpty());

        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findOneByLogin("user").orElseThrow().getTimeZone()).isEqualTo("America/New_York");

        mockMvc
            .perform(
                put("/api/account/budget-time-zone")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(Map.of("timeZone", "America/New_York")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.effectiveChanges").isEmpty());
    }

    @Test
    void rejects_invalid_zone_without_overwriting_the_persisted_profile() throws Exception {
        User user = userRepository.findOneByLogin("user").orElseThrow();
        user.setTimeZone("UTC");
        entityManager.flush();

        mockMvc
            .perform(
                put("/api/account/budget-time-zone")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(Map.of("timeZone", "not/a-zone")))
            )
            .andExpect(status().isBadRequest());

        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findOneByLogin("user").orElseThrow().getTimeZone()).isEqualTo("UTC");
    }

    @Test
    void generic_account_updates_cannot_bypass_the_budget_aware_timezone_command() throws Exception {
        User user = userRepository.findOneByLogin("user").orElseThrow();
        user.setTimeZone("America/Mexico_City");
        entityManager.flush();

        mockMvc
            .perform(
                post("/api/account")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of(
                                "login",
                                "user",
                                "firstName",
                                "User",
                                "lastName",
                                "Example",
                                "email",
                                user.getEmail(),
                                "langKey",
                                "en",
                                "imageUrl",
                                "",
                                "timeZone",
                                "America/New_York"
                            )
                        )
                    )
            )
            .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findOneByLogin("user").orElseThrow().getTimeZone()).isEqualTo("America/Mexico_City");
    }

    @Test
    void atomically_replaces_only_the_next_period_assignment_and_keeps_prior_periods_authoritative() throws Exception {
        User user = userRepository.findOneByLogin("user").orElseThrow();
        user.setTimeZone("America/Mexico_City");
        Budget budget = recurringBudget(user, "Zone history", LocalDate.of(2026, 3, 1));
        assignmentRepository.saveAndFlush(BudgetTimeZoneAssignment.record(budget, "America/Mexico_City", LocalDate.of(2026, 3, 1), NOW));

        mockMvc
            .perform(
                put("/api/account/budget-time-zone")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(Map.of("timeZone", "America/New_York")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeZone").value("America/New_York"))
            .andExpect(jsonPath("$.effectiveChanges[0].budgetId").value(budget.getId()))
            .andExpect(jsonPath("$.effectiveChanges[0].effectivePeriodStart").value("2026-04-01"));

        mockMvc
            .perform(
                put("/api/account/budget-time-zone")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(Map.of("timeZone", "Asia/Tokyo")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeZone").value("Asia/Tokyo"))
            .andExpect(jsonPath("$.effectiveChanges[0].effectivePeriodStart").value("2026-04-01"));

        entityManager.flush();
        entityManager.clear();

        List<BudgetTimeZoneAssignment> assignments = assignmentRepository.findAllByBudgetIdOrderByEffectivePeriodStartAsc(budget.getId());
        assertThat(assignments)
            .extracting(BudgetTimeZoneAssignment::getEffectivePeriodStart, BudgetTimeZoneAssignment::getTimeZone)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 3, 1), "America/Mexico_City"),
                org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 4, 1), "Asia/Tokyo")
            );
        Budget persisted = budgetRepository.findOneForEvaluationByIdAndUserLogin(budget.getId(), "user").orElseThrow();
        assertThat(budgetTimeZoneService.resolveZoneForPeriod(persisted, LocalDate.of(2026, 3, 1))).hasValueSatisfying(zone ->
            assertThat(zone.getId()).isEqualTo("America/Mexico_City")
        );
        assertThat(budgetTimeZoneService.resolveZoneForPeriod(persisted, LocalDate.of(2026, 4, 1))).hasValueSatisfying(zone ->
            assertThat(zone.getId()).isEqualTo("Asia/Tokyo")
        );
        assertThat(userRepository.findOneByLogin("user").orElseThrow().getTimeZone()).isEqualTo("Asia/Tokyo");
    }

    @Test
    @WithAnonymousUser
    void rejects_unauthenticated_profile_zone_access() throws Exception {
        mockMvc.perform(get("/api/account/budget-time-zone")).andExpect(status().isUnauthorized());
    }

    private Budget recurringBudget(User owner, String name, LocalDate anchor) {
        Budget budget = Budget.create(owner, name, null, BudgetType.EXPENSE, CurrencyCode.MXN, "zone-" + name, "hash", NOW);
        BudgetRevision revision = BudgetRevision.initial(
            new BudgetRevisionConfiguration(
                new BigDecimal("100.00"),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.NATURAL,
                anchor,
                BudgetRecurrenceMode.RECURRING,
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
            NOW
        );
        budget.addInitialRevision(revision);
        Budget saved = budgetRepository.saveAndFlush(budget);
        scheduleRepository.saveAndFlush(BudgetRevisionSchedule.scheduled(saved, saved.getRevisions().get(0), anchor, 1, "INITIAL", NOW));
        return saved;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock budgetProfileTimeZoneClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
