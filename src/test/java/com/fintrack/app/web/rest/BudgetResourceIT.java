package com.fintrack.app.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.Tag;
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
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.service.budget.BudgetCalendarPreviewCommand;
import com.fintrack.app.service.budget.BudgetCreateCommand;
import com.fintrack.app.service.budget.BudgetDeleteCommand;
import com.fintrack.app.service.budget.BudgetInitialRevisionCommand;
import com.fintrack.app.service.budget.BudgetLifecycleCommand;
import com.fintrack.app.service.budget.BudgetMetadataUpdateCommand;
import com.fintrack.app.service.budget.BudgetNotificationPreferenceUpdateCommand;
import com.fintrack.app.service.budget.BudgetRevisionScheduleCommand;
import com.fintrack.app.service.budget.BudgetVersionedCommand;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed command-boundary coverage for the Budget-1 foundation. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = BudgetResourceIT.CURRENT_USER_LOGIN)
@Import(BudgetResourceIT.FixedClockConfiguration.class)
class BudgetResourceIT {

    static final String CURRENT_USER_LOGIN = "user";
    private static final String OTHER_USER_LOGIN = "budget-other-user";
    private static final LocalDate TEST_TODAY = LocalDate.of(2026, 3, 15);

    @Autowired
    private MockMvc restBudgetMockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void createsAnAtomicFirstRevisionAndReplaysAnIdenticalRequest() throws Exception {
        User owner = currentUser();
        FinancialAccount account = FinancialAccountResourceIT.createEntity(entityManager);
        account.setUser(owner);
        account.setCurrency(CurrencyCode.MXN);
        Category category = CategoryResourceIT.createEntity(entityManager);
        category.setUser(owner);
        Tag tag = TagResourceIT.createEntity(entityManager);
        tag.setUser(owner);
        entityManager.persist(account);
        entityManager.persist(category);
        entityManager.persist(tag);
        entityManager.flush();

        BudgetCreateCommand command = command(
            "budget-create-atomic",
            Set.of(account.getId()),
            Set.of(category.getId()),
            Set.of(tag.getId())
        );

        String first = restBudgetMockMvc
            .perform(post("/api/budgets").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(command)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("EXPENSE"))
            .andExpect(jsonPath("$.currency").value("MXN"))
            .andExpect(jsonPath("$.initialRevision.revisionNumber").value(1))
            .andExpect(jsonPath("$.initialRevision.accountIds[0]").value(account.getId()))
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long firstId = objectMapper.readTree(first).path("id").asLong();
        assertThat(budgetRepository.findAll())
            .singleElement()
            .satisfies(budget -> {
                assertThat(budget.getUser().getLogin()).isEqualTo(CURRENT_USER_LOGIN);
                assertThat(budget.getRevisions())
                    .singleElement()
                    .satisfies(revision -> {
                        assertThat(revision.getRevisionNumber()).isEqualTo(1);
                        assertThat(revision.getAccounts()).extracting(FinancialAccount::getId).containsExactly(account.getId());
                        assertThat(revision.getCategories()).extracting(Category::getId).containsExactly(category.getId());
                        assertThat(revision.getTags()).extracting(Tag::getId).containsExactly(tag.getId());
                    });
            });

        restBudgetMockMvc
            .perform(post("/api/budgets").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(command)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(firstId));
        assertThat(budgetRepository.findAll()).hasSize(1);
    }

    @Test
    @Transactional
    void replacesNotificationPreferencesForTheOwnerAndRejectsForeignOrStaleUpdates() throws Exception {
        Budget budget = budgetRepository.saveAndFlush(
            Budget.create(
                currentUser(),
                "Notification budget",
                null,
                BudgetType.EXPENSE,
                CurrencyCode.MXN,
                "notification-preference-api",
                "notification-preference-api",
                Instant.now()
            )
        );

        restBudgetMockMvc
            .perform(get("/api/budgets/{id}/notification-preferences", budget.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(false));

        BudgetNotificationPreferenceUpdateCommand create = new BudgetNotificationPreferenceUpdateCommand(
            null,
            true,
            Set.of(new BigDecimal("50.0000"), new BigDecimal("100.0000")),
            false
        );
        restBudgetMockMvc
            .perform(
                put("/api/budgets/{id}/notification-preferences", budget.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.percentageAlertsEnabled").value(true))
            .andExpect(jsonPath("$.thresholdPercentages.length()").value(2));

        BudgetNotificationPreferenceUpdateCommand stale = new BudgetNotificationPreferenceUpdateCommand(
            99L,
            true,
            Set.of(new BigDecimal("50.0000")),
            false
        );
        restBudgetMockMvc
            .perform(
                put("/api/budgets/{id}/notification-preferences", budget.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(stale))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        restBudgetMockMvc
            .perform(get("/api/budgets/{id}/notification-preferences", budget.getId()).with(user(OTHER_USER_LOGIN)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
    }

    @Test
    @Transactional
    void rejectsASelectedAccountFromAnotherOwnerWithoutDisclosingIt() throws Exception {
        User otherOwner = otherUser();
        FinancialAccount foreignAccount = FinancialAccountResourceIT.createEntity(entityManager);
        foreignAccount.setUser(otherOwner);
        foreignAccount.setCurrency(CurrencyCode.MXN);
        entityManager.persist(foreignAccount);
        entityManager.flush();

        restBudgetMockMvc
            .perform(
                post("/api/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(command("foreign-account", Set.of(foreignAccount.getId()), Set.of(), Set.of())))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
        assertThat(budgetRepository.findAll()).isEmpty();
    }

    @Test
    @Transactional
    void exposesNoLegacyBudgetCollectionOrGenericCrudContract() throws Exception {
        restBudgetMockMvc.perform(get("/api/budgets").with(user(CURRENT_USER_LOGIN))).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @Transactional
    void previews_calendar_through_the_authoritative_calculator_without_creating_a_budget() throws Exception {
        long before = budgetRepository.count();
        BudgetCalendarPreviewCommand preview = new BudgetCalendarPreviewCommand(
            "America/Mexico_City",
            14,
            BudgetIntervalUnit.DAY,
            BudgetAlignment.ANCHORED,
            TEST_TODAY,
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.AFTER_N_PERIODS,
            2,
            null
        );

        restBudgetMockMvc
            .perform(
                post("/api/budgets/calendar-preview")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(preview))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.localToday").value(TEST_TODAY.toString()))
            .andExpect(jsonPath("$.periods.length()").value(2))
            .andExpect(jsonPath("$.periods[0].startInclusive").value(TEST_TODAY.toString()))
            .andExpect(jsonPath("$.periods[1].startInclusive").value(TEST_TODAY.plusDays(14).toString()));
        assertThat(budgetRepository.count()).isEqualTo(before);

        BudgetCalendarPreviewCommand invalidNaturalMonth = new BudgetCalendarPreviewCommand(
            "America/Mexico_City",
            1,
            BudgetIntervalUnit.MONTH,
            BudgetAlignment.NATURAL,
            TEST_TODAY,
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE,
            null,
            null
        );
        restBudgetMockMvc
            .perform(
                post("/api/budgets/calendar-preview")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(invalidNaturalMonth))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
        assertThat(budgetRepository.count()).isEqualTo(before);
    }

    @Test
    @Transactional
    void persistsTheCustomCalendarRuleAndAllowsAHistoricalStart() throws Exception {
        BudgetCreateCommand custom = new BudgetCreateCommand(
            "Historical custom day 31",
            null,
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            "budget-custom-day-31",
            "America/Mexico_City",
            new BudgetInitialRevisionCommand(
                new BigDecimal("800.00"),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.CUSTOM,
                LocalDate.of(2026, 2, 28),
                31,
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null,
                BudgetAccountScopeMode.ALL,
                Set.of(),
                BudgetCategoryScopeMode.ALL,
                Set.of(),
                BudgetTagScopeMode.NONE,
                Set.of(),
                BudgetTagMatchMode.ANY
            )
        );

        String response = restBudgetMockMvc
            .perform(post("/api/budgets").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(custom)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.initialRevision.alignment").value("CUSTOM"))
            .andExpect(jsonPath("$.initialRevision.customDayOfMonth").value(31))
            .andExpect(jsonPath("$.initialRevision.anchorDate").value("2026-02-28"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        Long budgetId = objectMapper.readTree(response).path("id").asLong();

        assertThat(budgetRepository.findById(budgetId))
            .get()
            .extracting(Budget::getCreatedAt)
            .isEqualTo(Instant.parse("2026-03-15T18:00:00Z"));
        assertThat(budgetRepository.findById(budgetId).orElseThrow().getRevisions())
            .singleElement()
            .satisfies(revision -> assertThat(revision.getCustomDayOfMonth()).isEqualTo(31));

        BudgetCalendarPreviewCommand preview = new BudgetCalendarPreviewCommand(
            "America/Mexico_City",
            1,
            BudgetIntervalUnit.MONTH,
            BudgetAlignment.CUSTOM,
            LocalDate.of(2026, 2, 28),
            31,
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE,
            null,
            null
        );
        restBudgetMockMvc
            .perform(
                post("/api/budgets/calendar-preview")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(preview))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.periods[0].startInclusive").value("2026-02-28"))
            .andExpect(jsonPath("$.periods[1].startInclusive").value("2026-03-31"));
    }

    @Test
    @Transactional
    void schedulesCancelsAndReadsImmutableBudgetHistoryThroughTheOwnerScopedCommands() throws Exception {
        LocalDate anchor = TEST_TODAY;
        BudgetCreateCommand create = command("budget-two-history", Set.of(), Set.of(), Set.of(), anchor);
        String created = restBudgetMockMvc
            .perform(post("/api/budgets").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        Long budgetId = objectMapper.readTree(created).path("id").asLong();
        long initialVersion = objectMapper.readTree(created).path("version").asLong();
        BudgetInitialRevisionCommand revised = new BudgetInitialRevisionCommand(
            new BigDecimal("2600.00"),
            1,
            BudgetIntervalUnit.MONTH,
            BudgetAlignment.ANCHORED,
            anchor,
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE,
            null,
            null,
            BudgetAccountScopeMode.ALL,
            Set.of(),
            BudgetCategoryScopeMode.ALL,
            Set.of(),
            BudgetTagScopeMode.NONE,
            Set.of(),
            BudgetTagMatchMode.ANY
        );
        BudgetRevisionScheduleCommand schedule = new BudgetRevisionScheduleCommand(
            initialVersion,
            "budget-two-schedule",
            "America/Mexico_City",
            false,
            anchor.plusMonths(1),
            revised
        );
        String scheduled = restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/revisions", budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(schedule))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.effectivePeriodStart").value(anchor.plusMonths(1).toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
        long scheduleId = objectMapper.readTree(scheduled).path("revisionScheduleId").asLong();
        long scheduledVersion = objectMapper.readTree(scheduled).path("version").asLong();
        assertThat(scheduledVersion).isGreaterThan(initialVersion);

        restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/revisions", budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(schedule))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revisionScheduleId").value(scheduleId))
            .andExpect(jsonPath("$.version").value(scheduledVersion));

        restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/revisions/{scheduleId}/cancel", budgetId, scheduleId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new BudgetVersionedCommand(scheduledVersion, "budget-two-cancel", "America/Mexico_City")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(scheduledVersion + 1));

        restBudgetMockMvc
            .perform(get("/api/budgets/{id}/effective-revision", budgetId).param("periodStart", anchor.plusMonths(1).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetAmount").value(2500.00));

        restBudgetMockMvc
            .perform(get("/api/budgets/{id}/history", budgetId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revisions.length()").value(2))
            .andExpect(jsonPath("$.schedules.length()").value(3));
    }

    @Test
    @Transactional
    void keepsHistoryOwnerScopedRejectsStaleVersionsAndRollsBackInvalidScheduling() throws Exception {
        String created = restBudgetMockMvc
            .perform(
                post("/api/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(command("budget-two-owner", Set.of(), Set.of(), Set.of(), TEST_TODAY)))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        Long budgetId = objectMapper.readTree(created).path("id").asLong();
        long version = objectMapper.readTree(created).path("version").asLong();
        otherUser();
        BudgetRevisionScheduleCommand future = revisionSchedule(version, "owner-schedule", TEST_TODAY.plusMonths(1));

        restBudgetMockMvc
            .perform(get("/api/budgets/{id}/history", budgetId).with(user(OTHER_USER_LOGIN)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
        restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/revisions", budgetId)
                    .with(user(OTHER_USER_LOGIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(future))
            )
            .andExpect(status().isBadRequest());
        restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/revisions", budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(revisionSchedule(version + 1, "stale-schedule", TEST_TODAY.plusMonths(1))))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/revisions", budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(revisionSchedule(version, "invalid-boundary", TEST_TODAY.plusDays(3))))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));

        restBudgetMockMvc
            .perform(get("/api/budgets/{id}/history", budgetId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revisions.length()").value(1))
            .andExpect(jsonPath("$.schedules.length()").value(1));
        restBudgetMockMvc
            .perform(
                get("/api/budgets/{id}/periods", budgetId)
                    .param("firstIndex", "0")
                    .param("lastIndex", "2")
                    .param("timeZone", "America/Mexico_City")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].startInclusive").value(TEST_TODAY.toString()))
            .andExpect(jsonPath("$[1].startInclusive").value(TEST_TODAY.plusMonths(1).toString()));
    }

    @Test
    @Transactional
    void deniesEveryBudgetReadAndMutationCommandToAnotherOwner() throws Exception {
        String created = restBudgetMockMvc
            .perform(
                post("/api/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(command("owner-isolation-all-commands", Set.of(), Set.of(), Set.of(), TEST_TODAY))
                    )
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long budgetId = objectMapper.readTree(created).path("id").asLong();
        long creationVersion = objectMapper.readTree(created).path("version").asLong();
        String scheduled = restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/revisions", budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            revisionSchedule(creationVersion, "owner-isolation-revision", TEST_TODAY.plusMonths(1))
                        )
                    )
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long scheduleId = objectMapper.readTree(scheduled).path("revisionScheduleId").asLong();
        long scheduledVersion = objectMapper.readTree(scheduled).path("version").asLong();
        String paused = restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/pause", budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new BudgetLifecycleCommand(scheduledVersion, "owner-isolation-pause", "America/Mexico_City")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long pauseEventId = objectMapper.readTree(paused).path("lifecycleEventId").asLong();
        long currentVersion = objectMapper.readTree(paused).path("version").asLong();
        otherUser();

        // Evaluation endpoints intentionally return 404; command/history endpoints preserve their existing 400
        // owner-scoped contract. Every route must deny before exposing revision, event, or financial information.
        restBudgetMockMvc
            .perform(
                get("/api/budgets/{id}/evaluation/current", budgetId).with(user(OTHER_USER_LOGIN)).param("timeZone", "America/Mexico_City")
            )
            .andExpect(status().isNotFound());
        restBudgetMockMvc
            .perform(
                get("/api/budgets/{id}/evaluations", budgetId)
                    .with(user(OTHER_USER_LOGIN))
                    .param("firstPeriodIndex", "0")
                    .param("size", "1")
                    .param("timeZone", "America/Mexico_City")
            )
            .andExpect(status().isNotFound());
        restBudgetMockMvc
            .perform(get("/api/budgets/{id}/history", budgetId).with(user(OTHER_USER_LOGIN)))
            .andExpect(status().isBadRequest());
        restBudgetMockMvc
            .perform(
                get("/api/budgets/{id}/effective-revision", budgetId)
                    .with(user(OTHER_USER_LOGIN))
                    .param("periodStart", TEST_TODAY.toString())
            )
            .andExpect(status().isBadRequest());
        restBudgetMockMvc
            .perform(
                get("/api/budgets/{id}/periods", budgetId)
                    .with(user(OTHER_USER_LOGIN))
                    .param("firstIndex", "0")
                    .param("lastIndex", "1")
                    .param("timeZone", "America/Mexico_City")
            )
            .andExpect(status().isBadRequest());

        restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/revisions", budgetId)
                    .with(user(OTHER_USER_LOGIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(revisionSchedule(currentVersion, "foreign-revision", TEST_TODAY.plusMonths(2))))
            )
            .andExpect(status().isBadRequest());
        restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/revisions/{scheduleId}/replace", budgetId, scheduleId)
                    .with(user(OTHER_USER_LOGIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(revisionSchedule(currentVersion, "foreign-replace", TEST_TODAY.plusMonths(1))))
            )
            .andExpect(status().isBadRequest());
        restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/revisions/{scheduleId}/cancel", budgetId, scheduleId)
                    .with(user(OTHER_USER_LOGIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new BudgetVersionedCommand(currentVersion, "foreign-cancel-revision", "America/Mexico_City")
                        )
                    )
            )
            .andExpect(status().isBadRequest());
        restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/pause", budgetId)
                    .with(user(OTHER_USER_LOGIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(new BudgetLifecycleCommand(currentVersion, "foreign-pause", "America/Mexico_City"))
                    )
            )
            .andExpect(status().isBadRequest());
        restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/pause/{eventId}/cancel", budgetId, pauseEventId)
                    .with(user(OTHER_USER_LOGIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new BudgetLifecycleCommand(currentVersion, "foreign-cancel-pause", "America/Mexico_City")
                        )
                    )
            )
            .andExpect(status().isBadRequest());
        restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/resume", budgetId)
                    .with(user(OTHER_USER_LOGIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(new BudgetLifecycleCommand(currentVersion, "foreign-resume", "America/Mexico_City"))
                    )
            )
            .andExpect(status().isBadRequest());
        restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/finish", budgetId)
                    .with(user(OTHER_USER_LOGIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(new BudgetLifecycleCommand(currentVersion, "foreign-finish", "America/Mexico_City"))
                    )
            )
            .andExpect(status().isBadRequest());
        restBudgetMockMvc
            .perform(
                delete("/api/budgets/{id}", budgetId)
                    .with(user(OTHER_USER_LOGIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new BudgetDeleteCommand(currentVersion)))
            )
            .andExpect(status().isBadRequest());

        assertThat(budgetRepository.findById(budgetId)).isPresent();
    }

    @Test
    @Transactional
    void updatesMetadataAndReplacesAFutureRevisionWithoutMutatingPriorHistory() throws Exception {
        String created = restBudgetMockMvc
            .perform(
                post("/api/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(command("budget-edit", Set.of(), Set.of(), Set.of(), TEST_TODAY)))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        Long budgetId = objectMapper.readTree(created).path("id").asLong();
        long createdVersion = objectMapper.readTree(created).path("version").asLong();

        restBudgetMockMvc
            .perform(
                put("/api/budgets/{id}/metadata", budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new BudgetMetadataUpdateCommand(createdVersion, "Updated food", "Updated description")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(createdVersion + 1));
        assertThat(budgetRepository.findById(budgetId).orElseThrow()).satisfies(budget -> {
            assertThat(budget.getName()).isEqualTo("Updated food");
            assertThat(budget.getDescription()).isEqualTo("Updated description");
        });

        String scheduled = restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/revisions", budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(revisionSchedule(createdVersion + 1, "edit-future", TEST_TODAY.plusMonths(1))))
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long scheduleId = objectMapper.readTree(scheduled).path("revisionScheduleId").asLong();
        long scheduledVersion = objectMapper.readTree(scheduled).path("version").asLong();

        restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/revisions/{scheduleId}/replace", budgetId, scheduleId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(revisionSchedule(scheduledVersion, "edit-replace", TEST_TODAY.plusMonths(1))))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.effectivePeriodStart").value(TEST_TODAY.plusMonths(1).toString()))
            .andExpect(jsonPath("$.revisionScheduleId").isNumber());

        restBudgetMockMvc
            .perform(get("/api/budgets/{id}/effective-revision", budgetId).param("periodStart", TEST_TODAY.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetAmount").value(2500.00));
        restBudgetMockMvc
            .perform(get("/api/budgets/{id}/effective-revision", budgetId).param("periodStart", TEST_TODAY.plusMonths(1).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetAmount").value(2600.00));
        restBudgetMockMvc
            .perform(get("/api/budgets/{id}/history", budgetId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revisions.length()").value(3))
            .andExpect(jsonPath("$.schedules.length()").value(4));
    }

    @Test
    @Transactional
    void permanentlyDeletesOnlyTheOwnedBudgetAggregateAndPreservesFinancialScopeRecords() throws Exception {
        User owner = currentUser();
        FinancialAccount account = FinancialAccountResourceIT.createEntity(entityManager);
        account.setUser(owner);
        account.setCurrency(CurrencyCode.MXN);
        Category category = CategoryResourceIT.createEntity(entityManager);
        category.setUser(owner);
        Tag tag = TagResourceIT.createEntity(entityManager);
        tag.setUser(owner);
        entityManager.persist(account);
        entityManager.persist(category);
        entityManager.persist(tag);
        entityManager.flush();

        FinancialTransaction transaction = FinancialTransactionResourceIT.createEntity(entityManager);
        transaction.setAccount(account);
        entityManager.persist(transaction);
        entityManager.flush();

        String created = restBudgetMockMvc
            .perform(
                post("/api/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            command("budget-delete", Set.of(account.getId()), Set.of(category.getId()), Set.of(tag.getId()), TEST_TODAY)
                        )
                    )
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long budgetId = objectMapper.readTree(created).path("id").asLong();
        long createdVersion = objectMapper.readTree(created).path("version").asLong();

        String scheduled = restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/revisions", budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(revisionSchedule(createdVersion, "delete-future-revision", TEST_TODAY.plusMonths(1)))
                    )
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long scheduledVersion = objectMapper.readTree(scheduled).path("version").asLong();
        String paused = restBudgetMockMvc
            .perform(
                post("/api/budgets/{id}/pause", budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new BudgetLifecycleCommand(scheduledVersion, "delete-pending-pause", "America/Mexico_City")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long currentVersion = objectMapper.readTree(paused).path("version").asLong();

        otherUser();
        restBudgetMockMvc
            .perform(
                delete("/api/budgets/{id}", budgetId)
                    .with(user(OTHER_USER_LOGIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new BudgetDeleteCommand(currentVersion)))
            )
            .andExpect(status().isBadRequest());
        restBudgetMockMvc
            .perform(
                delete("/api/budgets/{id}", budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new BudgetDeleteCommand(currentVersion - 1)))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        restBudgetMockMvc
            .perform(
                delete("/api/budgets/{id}", budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new BudgetDeleteCommand(currentVersion)))
            )
            .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        assertThat(budgetRepository.existsById(budgetId)).isFalse();
        assertThat(rows("budget_revision", budgetId)).isZero();
        assertThat(rows("budget_revision_schedule", budgetId)).isZero();
        assertThat(rows("budget_lifecycle_event", budgetId)).isZero();
        assertThat(rows("budget_command_receipt", budgetId)).isZero();
        assertThat(entityManager.find(FinancialAccount.class, account.getId())).isNotNull();
        assertThat(entityManager.find(FinancialTransaction.class, transaction.getId())).isNotNull();
        assertThat(entityManager.find(Category.class, category.getId())).isNotNull();
        assertThat(entityManager.find(Tag.class, tag.getId())).isNotNull();

        restBudgetMockMvc.perform(get("/api/budgets/{id}/history", budgetId)).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void refusesTheFinalDatabaseDeleteWhenAnotherCommandAlreadyAdvancedTheBudgetVersion() throws Exception {
        String created = restBudgetMockMvc
            .perform(
                post("/api/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(command("delete-version-guard", Set.of(), Set.of(), Set.of(), TEST_TODAY)))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long budgetId = objectMapper.readTree(created).path("id").asLong();
        long creationVersion = objectMapper.readTree(created).path("version").asLong();

        restBudgetMockMvc
            .perform(
                put("/api/budgets/{id}/metadata", budgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(new BudgetMetadataUpdateCommand(creationVersion, "Changed", null)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(creationVersion + 1));

        // This runs against the real PostgreSQL row, not a mocked repository. The final delete must use the
        // expected version as a predicate, otherwise a stale deleter could erase the just-updated aggregate.
        assertThat(budgetRepository.deleteAggregateById(budgetId, creationVersion)).isZero();
        entityManager.flush();
        entityManager.clear();
        assertThat(budgetRepository.findById(budgetId)).isPresent();
    }

    private long rows(String table, long budgetId) {
        return (
            (Number) entityManager
                .createNativeQuery("select count(*) from " + table + " where budget_id = :budgetId")
                .setParameter("budgetId", budgetId)
                .getSingleResult()
        ).longValue();
    }

    private BudgetCreateCommand command(String requestId, Set<Long> accountIds, Set<Long> categoryIds, Set<Long> tagIds) {
        return command(requestId, accountIds, categoryIds, tagIds, TEST_TODAY.plusDays(1));
    }

    private BudgetCreateCommand command(
        String requestId,
        Set<Long> accountIds,
        Set<Long> categoryIds,
        Set<Long> tagIds,
        LocalDate anchorDate
    ) {
        return new BudgetCreateCommand(
            "Monthly food",
            "Food spending plan",
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            requestId,
            "America/Mexico_City",
            new BudgetInitialRevisionCommand(
                new BigDecimal("2500.00"),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.ANCHORED,
                anchorDate,
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null,
                accountIds.isEmpty() ? BudgetAccountScopeMode.ALL : BudgetAccountScopeMode.SELECTED,
                accountIds,
                categoryIds.isEmpty() ? BudgetCategoryScopeMode.ALL : BudgetCategoryScopeMode.SELECTED,
                categoryIds,
                tagIds.isEmpty() ? BudgetTagScopeMode.NONE : BudgetTagScopeMode.SELECTED,
                tagIds,
                BudgetTagMatchMode.ANY
            )
        );
    }

    private BudgetRevisionScheduleCommand revisionSchedule(long expectedVersion, String requestId, LocalDate effectivePeriodStart) {
        return new BudgetRevisionScheduleCommand(
            expectedVersion,
            requestId,
            "America/Mexico_City",
            false,
            effectivePeriodStart,
            new BudgetInitialRevisionCommand(
                new BigDecimal("2600.00"),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.ANCHORED,
                TEST_TODAY,
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null,
                BudgetAccountScopeMode.ALL,
                Set.of(),
                BudgetCategoryScopeMode.ALL,
                Set.of(),
                BudgetTagScopeMode.NONE,
                Set.of(),
                BudgetTagMatchMode.ANY
            )
        );
    }

    private User currentUser() {
        return userRepository.findOneByLogin(CURRENT_USER_LOGIN).orElseThrow();
    }

    private User otherUser() {
        User user = UserResourceIT.createEntity();
        user.setLogin(OTHER_USER_LOGIN);
        user.setEmail("budget-other-user@example.test");
        return userRepository.saveAndFlush(user);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock budgetTestClock() {
            return Clock.fixed(Instant.parse("2026-03-15T18:00:00Z"), ZoneOffset.UTC);
        }
    }
}
