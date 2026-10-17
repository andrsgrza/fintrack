package com.fintrack.app.web.rest;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TransactionBatchRepository;
import com.fintrack.app.service.TransactionBatchCommandService;
import com.fintrack.app.service.dto.TransactionBatchDraftCommand;
import com.fintrack.app.service.dto.TransactionBatchRowMutationDTO;
import com.fintrack.app.service.dto.TransactionBatchWorkspaceDTO;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP boundary coverage: product callers can only create and read their owned OPEN workspaces. */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class TransactionBatchResourceIT {

    private static final String ENTITY_API_URL = "/api/transaction-batches";

    @Autowired
    private MockMvc restTransactionBatchMockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private FinancialAccountRepository financialAccountRepository;

    @Autowired
    private TransactionBatchRepository transactionBatchRepository;

    @Autowired
    private TransactionBatchCommandService transactionBatchCommandService;

    @Autowired
    private EntityManager em;

    private FinancialAccount financialAccount;

    @BeforeEach
    void initTest() {
        financialAccount = FinancialAccountResourceIT.createEntity(em);
        financialAccount.setActive(true);
        financialAccount = financialAccountRepository.saveAndFlush(financialAccount);
    }

    @AfterEach
    void cleanup() {
        transactionBatchRepository.deleteAll();
        financialAccountRepository.delete(financialAccount);
    }

    @Test
    void createAndListOwnedOpenWorkspace() throws Exception {
        restTransactionBatchMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(Map.of("accountId", financialAccount.getId())))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.accountId").value(financialAccount.getId()))
            .andExpect(jsonPath("$.candidates").isArray())
            .andExpect(jsonPath("$.candidates").isEmpty());

        restTransactionBatchMockMvc
            .perform(get(ENTITY_API_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].accountId", hasItem(financialAccount.getId().intValue())))
            .andExpect(jsonPath("$[*].status", hasItem("OPEN")));
    }

    @Test
    void refusesAnUnknownAccountAtTheProductBoundary() throws Exception {
        restTransactionBatchMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":999999999}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void reevaluatesOnlyTheRequestedOpenBatchRow() throws Exception {
        TransactionBatchWorkspaceDTO batch = transactionBatchCommandService.createOpenBatch(financialAccount.getId());
        TransactionBatchDraftCommand firstDraft = new TransactionBatchDraftCommand();
        firstDraft.setExpectedBatchVersion(batch.getVersion());
        firstDraft.setDescription("First current row");
        TransactionBatchRowMutationDTO first = transactionBatchCommandService.addManualDraft(batch.getId(), firstDraft);

        TransactionBatchDraftCommand secondDraft = new TransactionBatchDraftCommand();
        secondDraft.setExpectedBatchVersion(first.getBatchVersion());
        secondDraft.setDescription("Other current row");
        transactionBatchCommandService.addManualDraft(batch.getId(), secondDraft);

        restTransactionBatchMockMvc
            .perform(post(ENTITY_API_URL + "/{batchId}/candidates/{candidateId}/reevaluate", batch.getId(), first.getCandidate().getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.batchId").value(batch.getId()))
            .andExpect(jsonPath("$.previews.length()").value(1))
            .andExpect(jsonPath("$.previews[0].candidateId").value(first.getCandidate().getId()));
    }
}
