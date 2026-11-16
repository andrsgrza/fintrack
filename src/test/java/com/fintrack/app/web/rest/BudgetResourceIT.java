package com.fintrack.app.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
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
import com.fintrack.app.service.budget.BudgetCreateCommand;
import com.fintrack.app.service.budget.BudgetInitialRevisionCommand;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed command-boundary coverage for the Budget-1 foundation. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(username = BudgetResourceIT.CURRENT_USER_LOGIN)
class BudgetResourceIT {

    static final String CURRENT_USER_LOGIN = "user";
    private static final String OTHER_USER_LOGIN = "budget-other-user";

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

    private BudgetCreateCommand command(String requestId, Set<Long> accountIds, Set<Long> categoryIds, Set<Long> tagIds) {
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
                LocalDate.now(ZoneId.of("America/Mexico_City")).plusDays(1),
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

    private User currentUser() {
        return userRepository.findOneByLogin(CURRENT_USER_LOGIN).orElseThrow();
    }

    private User otherUser() {
        User user = UserResourceIT.createEntity();
        user.setLogin(OTHER_USER_LOGIN);
        user.setEmail("budget-other-user@example.test");
        return userRepository.saveAndFlush(user);
    }
}
