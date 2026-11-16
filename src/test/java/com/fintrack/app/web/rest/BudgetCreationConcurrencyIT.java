package com.fintrack.app.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetRevision;
import com.fintrack.app.domain.BudgetRevisionSchedule;
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
import com.fintrack.app.repository.BudgetRevisionRepository;
import com.fintrack.app.repository.BudgetRevisionScheduleRepository;
import com.fintrack.app.service.budget.BudgetCreateCommand;
import com.fintrack.app.service.budget.BudgetInitialRevisionCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL-only regression coverage for owner-scoped create idempotency. Each request runs through the actual REST
 * boundary in an independent transaction; the start barrier prevents this from becoming a sequential replay test.
 */
@IntegrationTest
@AutoConfigureMockMvc
class BudgetCreationConcurrencyIT {

    private static final String OWNER = "user";
    private static final String OTHER_OWNER = "admin";

    @Autowired
    private MockMvc restBudgetMockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private BudgetRevisionRepository revisionRepository;

    @Autowired
    private BudgetRevisionScheduleRepository revisionScheduleRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<BudgetReference> fixtures = new ArrayList<>();

    @AfterEach
    void removeCommittedFixtures() {
        transactionTemplate.executeWithoutResult(status -> {
            for (BudgetReference fixture : fixtures) {
                budgetRepository
                    .findOneWithRevisionsByUserLoginAndCreationRequestId(fixture.ownerLogin(), fixture.requestId())
                    .ifPresent(budget -> {
                        revisionScheduleRepository.deleteAll(
                            revisionScheduleRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId())
                        );
                        budgetRepository.delete(budget);
                    });
            }
            budgetRepository.flush();
        });
        fixtures.clear();
    }

    @Test
    void replaysTwoOverlappingIdenticalCreatesWithoutDuplicateBudgetOrRevision() throws Exception {
        String requestId = requestId("same");
        track(OWNER, requestId);
        BudgetCreateCommand command = command(requestId, "Concurrent identical budget", "1800.00");

        List<Invocation> outcomes = invokeConcurrently(() -> createAs(OWNER, command), () -> createAs(OWNER, command));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        assertThat(outcomes).extracting(outcome -> outcome.response().status()).containsOnly(201);
        long firstId = responseId(outcomes.get(0).response());
        assertThat(outcomes).extracting(outcome -> responseIdUnchecked(outcome.response())).containsOnly(firstId);
        assertExactlyOneCompleteBudget(OWNER, requestId, firstId);
    }

    @Test
    void rejectsConflictingConcurrentReuseWithoutPersistingASecondBudget() throws Exception {
        String requestId = requestId("conflict");
        track(OWNER, requestId);
        BudgetCreateCommand first = command(requestId, "Concurrent first budget", "1800.00");
        BudgetCreateCommand conflicting = command(requestId, "Concurrent conflicting budget", "2200.00");

        List<Invocation> outcomes = invokeConcurrently(() -> createAs(OWNER, first), () -> createAs(OWNER, conflicting));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        assertThat(outcomes).extracting(outcome -> outcome.response().status()).containsExactlyInAnyOrder(201, 409);
        Invocation conflict = outcomes.stream().filter(outcome -> outcome.response().status() == 409).findFirst().orElseThrow();
        assertThat(objectMapper.readTree(conflict.response().body()).path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        Invocation created = outcomes.stream().filter(outcome -> outcome.response().status() == 201).findFirst().orElseThrow();
        assertExactlyOneCompleteBudget(OWNER, requestId, responseId(created.response()));
    }

    @Test
    void permitsDifferentOwnersToUseTheSameClientRequestIdIndependently() throws Exception {
        String requestId = requestId("different-owners");
        track(OWNER, requestId);
        track(OTHER_OWNER, requestId);

        List<Invocation> outcomes = invokeConcurrently(
            () -> createAs(OWNER, command(requestId, "Owner budget", "1800.00")),
            () -> createAs(OTHER_OWNER, command(requestId, "Other owner budget", "2200.00"))
        );

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        assertThat(outcomes).extracting(outcome -> outcome.response().status()).containsOnly(201);
        assertExactlyOneCompleteBudget(OWNER, requestId, responseId(outcomes.get(0).response()));
        assertExactlyOneCompleteBudget(OTHER_OWNER, requestId, responseId(outcomes.get(1).response()));
        assertThat(responseId(outcomes.get(0).response())).isNotEqualTo(responseId(outcomes.get(1).response()));
    }

    private void assertExactlyOneCompleteBudget(String owner, String requestId, long expectedId) {
        Budget budget = budgetRepository.findOneWithRevisionsByUserLoginAndCreationRequestId(owner, requestId).orElseThrow();
        assertThat(budget.getId()).isEqualTo(expectedId);
        List<BudgetRevision> revisions = revisionRepository.findAllByBudgetIdOrderByRevisionNumber(budget.getId());
        List<BudgetRevisionSchedule> schedules = revisionScheduleRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId());
        assertThat(revisions).singleElement().extracting(BudgetRevision::getRevisionNumber).isEqualTo(1);
        assertThat(schedules)
            .singleElement()
            .satisfies(schedule -> {
                assertThat(schedule.getRevision().getId()).isEqualTo(revisions.get(0).getId());
                assertThat(schedule.getSequenceNumber()).isEqualTo(1L);
            });
    }

    private HttpResponse createAs(String owner, BudgetCreateCommand command) throws Exception {
        MvcResult result = restBudgetMockMvc
            .perform(
                post("/api/budgets")
                    .with(user(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(command))
            )
            .andReturn();
        return new HttpResponse(result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private List<Invocation> invokeConcurrently(Callable<HttpResponse> first, Callable<HttpResponse> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Invocation> firstFuture = executor.submit(() -> invoke(first, ready, start));
            Future<Invocation> secondFuture = executor.submit(() -> invoke(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstFuture.get(20, TimeUnit.SECONDS), secondFuture.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Invocation invoke(Callable<HttpResponse> action, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent creation did not start");
            return new Invocation(action.call(), null);
        } catch (Throwable failure) {
            return new Invocation(null, failure);
        }
    }

    private BudgetCreateCommand command(String requestId, String name, String amount) {
        return new BudgetCreateCommand(
            name,
            "PostgreSQL concurrency fixture",
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            requestId,
            "America/Mexico_City",
            new BudgetInitialRevisionCommand(
                new BigDecimal(amount),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.ANCHORED,
                LocalDate.of(2026, 3, 15),
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null,
                BudgetAccountScopeMode.ALL,
                java.util.Set.of(),
                BudgetCategoryScopeMode.ALL,
                java.util.Set.of(),
                BudgetTagScopeMode.NONE,
                java.util.Set.of(),
                BudgetTagMatchMode.ANY
            )
        );
    }

    private long responseId(HttpResponse response) throws Exception {
        return objectMapper.readTree(response.body()).path("id").asLong();
    }

    private long responseIdUnchecked(HttpResponse response) {
        try {
            return responseId(response);
        } catch (Exception exception) {
            throw new AssertionError("Could not read budget creation response", exception);
        }
    }

    private String requestId(String scenario) {
        return "budget-concurrent-" + scenario + "-" + UUID.randomUUID();
    }

    private void track(String ownerLogin, String requestId) {
        fixtures.add(new BudgetReference(ownerLogin, requestId));
    }

    private record BudgetReference(String ownerLogin, String requestId) {}

    private record HttpResponse(int status, String body) {}

    private record Invocation(HttpResponse response, Throwable failure) {}
}
