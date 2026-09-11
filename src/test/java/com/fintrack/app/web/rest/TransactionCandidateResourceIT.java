package com.fintrack.app.web.rest;

import static com.fintrack.app.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateValidationStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.IngestionRecordDTO;
import com.fintrack.app.service.dto.ManualTransactionDraftSummaryDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionCandidateDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link TransactionCandidateResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class TransactionCandidateResourceIT {

    private static final String ENTITY_API_URL = "/api/transaction-candidates";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";
    private static final String ENTITY_MANUAL_API_URL = ENTITY_API_URL + "/manual";
    private static final String ENTITY_MANUAL_DRAFTS_API_URL = ENTITY_API_URL + "/manual-drafts";
    private static final String CURRENT_MOCK_USER_LOGIN = "user";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private MockMvc restTransactionCandidateMockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private FinancialAccountRepository financialAccountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private TransactionCandidateRepository transactionCandidateRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Test
    @Transactional
    void createDraftCandidateSucceeds() throws Exception {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setSource(TransactionCandidateSource.MANUAL);

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.source").value("MANUAL"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.validationStatus").value("UNKNOWN"))
            .andExpect(jsonPath("$.descriptionReviewStatus").value("NOT_EVALUATED"))
            .andExpect(jsonPath("$.classificationReviewStatus").value("NOT_EVALUATED"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @Transactional
    void signedAmountDerivesAmountAndFlow() throws Exception {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setSource(TransactionCandidateSource.MANUAL);
        dto.setSignedAmount(new BigDecimal("-100.00"));

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount").value(sameNumber(new BigDecimal("100.00"))))
            .andExpect(jsonPath("$.flow").value("OUT"));
    }

    @Test
    @Transactional
    void createManualDraftEndpointCreatesManualDraftForCurrentUser() throws Exception {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setSignedAmount(new BigDecimal("-15.00"));

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_MANUAL_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.source").value("MANUAL"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.amount").value(sameNumber(new BigDecimal("15.00"))))
            .andExpect(jsonPath("$.flow").value("OUT"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @Transactional
    void createManualDraftEndpointRejectsClientStatus() throws Exception {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setStatus(TransactionCandidateStatus.READY_TO_POST);

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_MANUAL_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void genericCreateRejectsFileImportCandidate() throws Exception {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setSource(TransactionCandidateSource.FILE_IMPORT);

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void genericCreateRejectsApiImportCandidate() throws Exception {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setSource(TransactionCandidateSource.API_IMPORT);

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void genericCreateRejectsTransactionIngestionLink() throws Exception {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setSource(TransactionCandidateSource.MANUAL);
        dto.setTransactionIngestion(refTransactionIngestion(1L));

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void genericCreateRejectsIngestionRecordLink() throws Exception {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setSource(TransactionCandidateSource.MANUAL);
        dto.setIngestionRecord(refIngestionRecord(1L));

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void patchManualDraftUpdatesEditableFieldsAndRecalculatesReadyStatus() throws Exception {
        FinancialAccount account = createAccount(currentUser());
        TransactionCandidate candidate = createDraftCandidate(currentUser());

        restTransactionCandidateMockMvc
            .perform(
                patch(ENTITY_API_URL_ID + "/manual-draft", candidate.getId())
                    .contentType("application/merge-patch+json")
                    .content(
                        """
                        {
                          "account": { "id": %d },
                          "transactionDate": "2026-01-15",
                          "description": "  Manual draft  ",
                          "signedAmount": -25.00
                        }
                        """.formatted(account.getId())
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY_TO_POST"))
            .andExpect(jsonPath("$.validationStatus").value("VALID"))
            .andExpect(jsonPath("$.description").value("Manual draft"))
            .andExpect(jsonPath("$.amount").value(sameNumber(new BigDecimal("25.00"))))
            .andExpect(jsonPath("$.flow").value("OUT"))
            .andExpect(jsonPath("$.currencySnapshot").value("MXN"));
    }

    @Test
    @Transactional
    void patchManualDraftRejectsStatusMutation() throws Exception {
        TransactionCandidate candidate = createDraftCandidate(currentUser());

        restTransactionCandidateMockMvc
            .perform(
                patch(ENTITY_API_URL_ID + "/manual-draft", candidate.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"status\":\"READY_TO_POST\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void genericPatchRejectsStatusMutation() throws Exception {
        TransactionCandidate candidate = createDraftCandidate(currentUser());

        restTransactionCandidateMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, candidate.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"status\":\"CANCELLED\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void genericPutRejectsWorkflowLinkMutation() throws Exception {
        TransactionCandidate candidate = createDraftCandidate(currentUser());
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setId(candidate.getId());
        dto.setSource(TransactionCandidateSource.MANUAL);
        dto.setTransactionIngestion(refTransactionIngestion(1L));

        restTransactionCandidateMockMvc
            .perform(put(ENTITY_API_URL_ID, candidate.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void genericPatchRejectsWorkflowLinkMutation() throws Exception {
        TransactionCandidate candidate = createDraftCandidate(currentUser());

        restTransactionCandidateMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, candidate.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"ingestionRecord\":{\"id\":1}}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void genericPatchRejectsFileImportCandidateMutation() throws Exception {
        TransactionCandidate candidate = createReadyCandidate(currentUser());
        candidate.setSource(TransactionCandidateSource.FILE_IMPORT);
        transactionCandidateRepository.saveAndFlush(candidate);

        restTransactionCandidateMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, candidate.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"description\":\"Changed\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void genericPatchRejectsApiImportCandidateMutation() throws Exception {
        TransactionCandidate candidate = createDraftCandidate(currentUser());
        candidate.setSource(TransactionCandidateSource.API_IMPORT);
        transactionCandidateRepository.saveAndFlush(candidate);

        restTransactionCandidateMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, candidate.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"description\":\"Changed\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void cancelManualDraftSetsCancelledStatusAndTimestamp() throws Exception {
        TransactionCandidate candidate = createDraftCandidate(currentUser());

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/cancel", candidate.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledAt").exists());
    }

    @Test
    @Transactional
    void cancelledManualDraftCannotBePosted() throws Exception {
        TransactionCandidate candidate = createDraftCandidate(currentUser());
        candidate.setStatus(TransactionCandidateStatus.CANCELLED);
        candidate.setCancelledAt(Instant.now());
        transactionCandidateRepository.saveAndFlush(candidate);

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/post", candidate.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void fileImportCandidateCannotBePostedThroughManualEndpoint() throws Exception {
        TransactionCandidate candidate = createReadyCandidate(currentUser());
        candidate.setSource(TransactionCandidateSource.FILE_IMPORT);
        transactionCandidateRepository.saveAndFlush(candidate);

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/post", candidate.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void validManualDraftPostsFinancialTransactionAndLinksCandidate() throws Exception {
        TransactionCandidate candidate = createReadyCandidate(currentUser());

        int transactionCountBefore = financialTransactionRepository.findAll().size();

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/post", candidate.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("POSTED"))
            .andExpect(jsonPath("$.postedAt").exists())
            .andExpect(jsonPath("$.financialTransaction.id").exists());

        em.flush();
        em.clear();

        assertThat(financialTransactionRepository.findAll()).hasSize(transactionCountBefore + 1);
        TransactionCandidate postedCandidate = transactionCandidateRepository.findOneWithRelationships(candidate.getId()).orElseThrow();
        FinancialTransaction postedTransaction = postedCandidate.getFinancialTransaction();
        assertThat(postedTransaction).isNotNull();
        assertThat(postedTransaction.getOrigin()).isEqualTo(TransactionOrigin.MANUAL);
        assertThat(postedTransaction.getAccount().getId()).isEqualTo(candidate.getAccount().getId());
        assertThat(postedTransaction.getTransactionDate()).isEqualTo(candidate.getTransactionDate());
        assertThat(postedTransaction.getPostingDate()).isEqualTo(candidate.getPostingDate());
        assertThat(postedTransaction.getDescription()).isEqualTo(candidate.getDescription());
        assertThat(postedTransaction.getAmount()).isEqualByComparingTo(candidate.getAmount());
        assertThat(postedTransaction.getFlow()).isEqualTo(candidate.getFlow());
        assertThat(postedTransaction.getCategory().getId()).isEqualTo(candidate.getCategory().getId());
        assertThat(postedTransaction.getTags())
            .extracting(Tag::getId)
            .containsExactlyInAnyOrderElementsOf(candidate.getTags().stream().map(Tag::getId).toList());
    }

    @Test
    @Transactional
    void postRejectsNotEvaluatedClassificationReview() throws Exception {
        TransactionCandidate candidate = createReadyCandidate(currentUser());
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED);
        transactionCandidateRepository.saveAndFlush(candidate);

        int transactionCountBefore = financialTransactionRepository.findAll().size();

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/post", candidate.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));

        assertThat(financialTransactionRepository.findAll()).hasSize(transactionCountBefore);
    }

    @Test
    @Transactional
    void postRejectsStaleClassificationReview() throws Exception {
        TransactionCandidate candidate = createReadyCandidate(currentUser());
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.STALE);
        transactionCandidateRepository.saveAndFlush(candidate);

        int transactionCountBefore = financialTransactionRepository.findAll().size();

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/post", candidate.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));

        assertThat(financialTransactionRepository.findAll()).hasSize(transactionCountBefore);
    }

    @Test
    @Transactional
    void postingManualDraftTwiceIsIdempotent() throws Exception {
        TransactionCandidate candidate = createReadyCandidate(currentUser());

        restTransactionCandidateMockMvc.perform(post(ENTITY_API_URL_ID + "/post", candidate.getId())).andExpect(status().isOk());
        int transactionCountAfterFirstPost = financialTransactionRepository.findAll().size();

        restTransactionCandidateMockMvc.perform(post(ENTITY_API_URL_ID + "/post", candidate.getId())).andExpect(status().isOk());

        assertThat(financialTransactionRepository.findAll()).hasSize(transactionCountAfterFirstPost);
    }

    @Test
    @Transactional
    void manualPostDoesNotApplyTransactionRules() throws Exception {
        User owner = currentUser();
        FinancialAccount account = createAccount(owner);
        Category suggestedCategory = createCategory(CategoryType.EXPENSE, owner);
        Tag suggestedTag = createTag(owner);
        createMatchingRule(owner, suggestedCategory, suggestedTag, "Coffee");
        TransactionCandidate candidate = createReadyCandidateWithoutOutputs(owner, account, "Coffee shop", new BigDecimal("-20.00"));
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE);
        transactionCandidateRepository.saveAndFlush(candidate);

        restTransactionCandidateMockMvc.perform(post(ENTITY_API_URL_ID + "/post", candidate.getId())).andExpect(status().isOk());

        em.flush();
        em.clear();

        TransactionCandidate postedCandidate = transactionCandidateRepository.findOneWithRelationships(candidate.getId()).orElseThrow();
        FinancialTransaction postedTransaction = postedCandidate.getFinancialTransaction();
        assertThat(postedTransaction).isNotNull();
        assertThat(postedTransaction.getCategory()).isNull();
        assertThat(postedTransaction.getTags()).isEmpty();
    }

    @Test
    @Transactional
    void getManualDraftsReturnsOnlyCurrentUserRecoverableManualDrafts() throws Exception {
        User owner = currentUser();
        User otherUser = createOtherUser();
        Instant baseTime = Instant.parse("2026-01-15T10:00:00Z");

        TransactionCandidate olderDraft = createSummaryCandidate(
            owner,
            TransactionCandidateSource.MANUAL,
            TransactionCandidateStatus.DRAFT,
            baseTime
        );
        TransactionCandidate newerReadyDraft = createSummaryCandidate(
            owner,
            TransactionCandidateSource.MANUAL,
            TransactionCandidateStatus.READY_TO_POST,
            baseTime.plusSeconds(60)
        );
        createSummaryCandidate(owner, TransactionCandidateSource.FILE_IMPORT, TransactionCandidateStatus.DRAFT, baseTime.plusSeconds(120));
        createSummaryCandidate(owner, TransactionCandidateSource.API_IMPORT, TransactionCandidateStatus.DRAFT, baseTime.plusSeconds(180));
        createSummaryCandidate(owner, TransactionCandidateSource.MANUAL, TransactionCandidateStatus.POSTED, baseTime.plusSeconds(240));
        createSummaryCandidate(owner, TransactionCandidateSource.MANUAL, TransactionCandidateStatus.CANCELLED, baseTime.plusSeconds(300));
        createSummaryCandidate(owner, TransactionCandidateSource.MANUAL, TransactionCandidateStatus.FAILED, baseTime.plusSeconds(360));
        createSummaryCandidate(owner, TransactionCandidateSource.MANUAL, TransactionCandidateStatus.FAILED, baseTime.plusSeconds(420));
        createSummaryCandidate(
            otherUser,
            TransactionCandidateSource.MANUAL,
            TransactionCandidateStatus.READY_TO_POST,
            baseTime.plusSeconds(480)
        );

        MvcResult result = restTransactionCandidateMockMvc
            .perform(get(ENTITY_MANUAL_DRAFTS_API_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(newerReadyDraft.getId()))
            .andExpect(jsonPath("$[0].status").value("READY_TO_POST"))
            .andExpect(jsonPath("$[0].classificationReviewStatus").value("USER_SELECTED"))
            .andExpect(jsonPath("$[0].accountId").value(newerReadyDraft.getAccount().getId()))
            .andExpect(jsonPath("$[0].accountName").value(newerReadyDraft.getAccount().getName()))
            .andExpect(jsonPath("$[0].transactionDate").value("2026-01-15"))
            .andExpect(jsonPath("$[0].description").value("Summary candidate"))
            .andExpect(jsonPath("$[0].amount").value(sameNumber(new BigDecimal("42.00"))))
            .andExpect(jsonPath("$[0].flow").value("OUT"))
            .andExpect(jsonPath("$[0].currencySnapshot").value("MXN"))
            .andExpect(jsonPath("$[0].createdAt").exists())
            .andExpect(jsonPath("$[0].updatedAt").exists())
            .andExpect(jsonPath("$[0].categoryName").value(newerReadyDraft.getCategory().getName()))
            .andExpect(jsonPath("$[0].tagNames[0]").value(newerReadyDraft.getTags().iterator().next().getName()))
            .andExpect(jsonPath("$[0].source").doesNotExist())
            .andExpect(jsonPath("$[0].validationStatus").doesNotExist())
            .andExpect(jsonPath("$[0].financialTransaction").doesNotExist())
            .andExpect(jsonPath("$[1].id").value(olderDraft.getId()))
            .andExpect(jsonPath("$[1].status").value("DRAFT"))
            .andReturn();

        ManualTransactionDraftSummaryDTO[] summaries = om.readValue(
            result.getResponse().getContentAsByteArray(),
            ManualTransactionDraftSummaryDTO[].class
        );

        assertThat(summaries).hasSize(2);
        assertThat(summaries)
            .extracting(ManualTransactionDraftSummaryDTO::getId)
            .containsExactly(newerReadyDraft.getId(), olderDraft.getId());
    }

    @Test
    @Transactional
    void getManualDraftsMapsIncompleteDraftFieldsNullSafely() throws Exception {
        TransactionCandidate incompleteDraft = createDraftCandidate(currentUser());

        restTransactionCandidateMockMvc
            .perform(get(ENTITY_MANUAL_DRAFTS_API_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(incompleteDraft.getId()))
            .andExpect(jsonPath("$[0].status").value("DRAFT"))
            .andExpect(jsonPath("$[0].accountId").value(nullValue()))
            .andExpect(jsonPath("$[0].accountName").value(nullValue()))
            .andExpect(jsonPath("$[0].transactionDate").value(nullValue()))
            .andExpect(jsonPath("$[0].description").value(nullValue()))
            .andExpect(jsonPath("$[0].amount").value(nullValue()))
            .andExpect(jsonPath("$[0].flow").value(nullValue()))
            .andExpect(jsonPath("$[0].currencySnapshot").value(nullValue()))
            .andExpect(jsonPath("$[0].categoryName").value(nullValue()))
            .andExpect(jsonPath("$[0].tagNames").isArray())
            .andExpect(jsonPath("$[0].tagNames").isEmpty());
    }

    @Test
    @Transactional
    void previewRulesReturnsSuggestionsAndDoesNotMutateCandidate() throws Exception {
        User owner = currentUser();
        FinancialAccount account = createAccount(owner);
        Category suggestedCategory = createCategory(CategoryType.EXPENSE, owner);
        Tag suggestedTag = createTag(owner);
        createMatchingRule(owner, suggestedCategory, suggestedTag, "Coffee");
        TransactionCandidate candidate = createReadyCandidateWithoutOutputs(owner, account, "Coffee shop", new BigDecimal("-20.00"));

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/rule-preview", candidate.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidateId").value(candidate.getId()))
            .andExpect(jsonPath("$.classificationReviewStatus").value("NOT_EVALUATED"))
            .andExpect(jsonPath("$.suggestedCategory.categoryId").value(suggestedCategory.getId()))
            .andExpect(jsonPath("$.suggestedTags[0].tagId").value(suggestedTag.getId()))
            .andExpect(jsonPath("$.matchedRules[0].ruleName").value("Candidate rule"))
            .andExpect(jsonPath("$.hasSuggestions").value(true));

        em.flush();
        em.clear();

        TransactionCandidate persisted = transactionCandidateRepository.findOneWithRelationships(candidate.getId()).orElseThrow();
        assertThat(persisted.getCategory()).isNull();
        assertThat(persisted.getTags()).isEmpty();
        assertThat(persisted.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED);
    }

    @Test
    @Transactional
    void applyRulesFillsEmptyCategoryAndAddsTags() throws Exception {
        User owner = currentUser();
        FinancialAccount account = createAccount(owner);
        Category suggestedCategory = createCategory(CategoryType.EXPENSE, owner);
        Tag suggestedTag = createTag(owner);
        createMatchingRule(owner, suggestedCategory, suggestedTag, "Coffee");
        TransactionCandidate candidate = createReadyCandidateWithoutOutputs(owner, account, "Coffee shop", new BigDecimal("-20.00"));

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/apply-rules", candidate.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryApplied").value(true))
            .andExpect(jsonPath("$.tagIdsApplied[0]").value(suggestedTag.getId()))
            .andExpect(jsonPath("$.candidate.category.id").value(suggestedCategory.getId()))
            .andExpect(jsonPath("$.candidate.tags[0].id").value(suggestedTag.getId()))
            .andExpect(jsonPath("$.candidate.classificationReviewStatus").value("SUGGESTED"))
            .andExpect(jsonPath("$.evaluation.suggestedCategory.categoryId").value(suggestedCategory.getId()));

        em.flush();
        em.clear();

        TransactionCandidate persisted = transactionCandidateRepository.findOneWithRelationships(candidate.getId()).orElseThrow();
        assertThat(persisted.getCategory().getId()).isEqualTo(suggestedCategory.getId());
        assertThat(persisted.getTags()).extracting(Tag::getId).containsExactly(suggestedTag.getId());
        assertThat(persisted.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.SUGGESTED);
    }

    @Test
    @Transactional
    void applyRulesPreservesManualCategoryAndTags() throws Exception {
        User owner = currentUser();
        FinancialAccount account = createAccount(owner);
        Category manualCategory = createCategory(CategoryType.EXPENSE, owner);
        Category suggestedCategory = createCategory(CategoryType.EXPENSE, owner);
        Tag manualTag = createTag(owner);
        Tag suggestedTag = createTag(owner);
        createMatchingRule(owner, suggestedCategory, suggestedTag, "Coffee");
        TransactionCandidate candidate = createReadyCandidateWithoutOutputs(owner, account, "Coffee shop", new BigDecimal("-20.00"));
        candidate.setCategory(manualCategory);
        candidate.setTags(new HashSet<>(Set.of(manualTag)));
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        transactionCandidateRepository.saveAndFlush(candidate);

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/apply-rules", candidate.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryApplied").value(false))
            .andExpect(jsonPath("$.tagIdsApplied[0]").value(suggestedTag.getId()))
            .andExpect(jsonPath("$.candidate.category.id").value(manualCategory.getId()))
            .andExpect(jsonPath("$.candidate.classificationReviewStatus").value("USER_SELECTED"));

        em.flush();
        em.clear();

        TransactionCandidate persisted = transactionCandidateRepository.findOneWithRelationships(candidate.getId()).orElseThrow();
        assertThat(persisted.getCategory().getId()).isEqualTo(manualCategory.getId());
        assertThat(persisted.getTags()).extracting(Tag::getId).containsExactlyInAnyOrder(manualTag.getId(), suggestedTag.getId());
        assertThat(persisted.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
    }

    @Test
    @Transactional
    void applyRulesWithNoSuggestionsMarksNotApplicable() throws Exception {
        User owner = currentUser();
        FinancialAccount account = createAccount(owner);
        TransactionCandidate candidate = createReadyCandidateWithoutOutputs(owner, account, "No matching rules", new BigDecimal("-20.00"));

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/apply-rules", candidate.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryApplied").value(false))
            .andExpect(jsonPath("$.tagIdsApplied").isEmpty())
            .andExpect(jsonPath("$.candidate.category").value(nullValue()))
            .andExpect(jsonPath("$.candidate.classificationReviewStatus").value("NOT_APPLICABLE"));
    }

    @Test
    @Transactional
    void previewRulesRejectsNonManualCandidate() throws Exception {
        TransactionCandidate candidate = createReadyCandidate(currentUser());
        candidate.setSource(TransactionCandidateSource.FILE_IMPORT);
        transactionCandidateRepository.saveAndFlush(candidate);

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/rule-preview", candidate.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void previewRulesRejectsForeignCandidate() throws Exception {
        TransactionCandidate candidate = createReadyCandidate(createOtherUser());

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/rule-preview", candidate.getId()))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void applyRulesRejectsFinalCandidate() throws Exception {
        TransactionCandidate candidate = createReadyCandidate(currentUser());
        candidate.setStatus(TransactionCandidateStatus.CANCELLED);
        candidate.setCancelledAt(Instant.now());
        transactionCandidateRepository.saveAndFlush(candidate);

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/apply-rules", candidate.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void manualCategoryPatchSetsClassificationUserSelected() throws Exception {
        User owner = currentUser();
        Category category = createCategory(CategoryType.EXPENSE, owner);
        TransactionCandidate candidate = createReadyCandidateWithoutOutputs(
            owner,
            createAccount(owner),
            "Manual",
            new BigDecimal("-20.00")
        );

        restTransactionCandidateMockMvc
            .perform(
                patch(ENTITY_API_URL_ID + "/manual-draft", candidate.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"category\":{\"id\":%d}}".formatted(category.getId()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category.id").value(category.getId()))
            .andExpect(jsonPath("$.classificationReviewStatus").value("USER_SELECTED"));
    }

    @Test
    @Transactional
    void ruleInputPatchAfterFreshClassificationMarksStale() throws Exception {
        TransactionCandidate candidate = createReadyCandidateWithoutOutputs(
            currentUser(),
            createAccount(currentUser()),
            "Manual",
            new BigDecimal("-20.00")
        );
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.SUGGESTED);
        transactionCandidateRepository.saveAndFlush(candidate);

        restTransactionCandidateMockMvc
            .perform(
                patch(ENTITY_API_URL_ID + "/manual-draft", candidate.getId())
                    .contentType("application/merge-patch+json")
                    .content("{\"description\":\"Changed description\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Changed description"))
            .andExpect(jsonPath("$.classificationReviewStatus").value("STALE"));
    }

    @Test
    @Transactional
    void inactiveAndForeignRulesAreIgnoredForCandidatePreview() throws Exception {
        User owner = currentUser();
        User otherUser = createOtherUser();
        FinancialAccount account = createAccount(owner);
        Category inactiveCategory = createCategory(CategoryType.EXPENSE, owner);
        Tag inactiveTag = createTag(owner);
        Category foreignCategory = createCategory(CategoryType.EXPENSE, otherUser);
        Tag foreignTag = createTag(otherUser);
        createMatchingRule(owner, inactiveCategory, inactiveTag, "Coffee", false);
        createMatchingRule(otherUser, foreignCategory, foreignTag, "Coffee", true);
        TransactionCandidate candidate = createReadyCandidateWithoutOutputs(owner, account, "Coffee shop", new BigDecimal("-20.00"));

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL_ID + "/rule-preview", candidate.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestedCategory").value(nullValue()))
            .andExpect(jsonPath("$.suggestedTags").isEmpty())
            .andExpect(jsonPath("$.matchedRules").isEmpty())
            .andExpect(jsonPath("$.hasSuggestions").value(false));
    }

    @Test
    @Transactional
    void readyToPostRequiresCompleteFields() throws Exception {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setSource(TransactionCandidateSource.MANUAL);
        dto.setStatus(TransactionCandidateStatus.READY_TO_POST);

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void foreignAccountRejected() throws Exception {
        FinancialAccount foreignAccount = createAccount(createOtherUser());
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setSource(TransactionCandidateSource.MANUAL);
        dto.setAccount(refAccount(foreignAccount.getId()));

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void categoryCompatibilityWithFlowEnforced() throws Exception {
        Category expense = createCategory(CategoryType.EXPENSE, currentUser());
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setSource(TransactionCandidateSource.MANUAL);
        dto.setSignedAmount(new BigDecimal("10.00"));
        dto.setCategory(refCategory(expense.getId()));

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void directAmountAndFlowRejected() throws Exception {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setSource(TransactionCandidateSource.MANUAL);
        dto.setAmount(new BigDecimal("10.00"));
        dto.setFlow(TransactionFlow.OUT);

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void directFinancialTransactionLinkRejected() throws Exception {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setSource(TransactionCandidateSource.MANUAL);
        dto.setFinancialTransaction(refFinancialTransaction(1L));

        restTransactionCandidateMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.invalid"));
    }

    @Test
    @Transactional
    void deleteCandidateCleansTagJoinRows() throws Exception {
        User owner = currentUser();
        Tag tag = createTag(owner);
        TransactionCandidate candidate = new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .status(TransactionCandidateStatus.DRAFT)
            .validationStatus(com.fintrack.app.domain.enumeration.TransactionCandidateValidationStatus.UNKNOWN)
            .descriptionReviewStatus(com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED)
            .classificationReviewStatus(com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus.NOT_EVALUATED)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .user(owner)
            .tags(Set.of(tag));
        candidate = transactionCandidateRepository.saveAndFlush(candidate);

        restTransactionCandidateMockMvc.perform(delete(ENTITY_API_URL_ID, candidate.getId())).andExpect(status().isNoContent());

        em.flush();
        em.clear();
        assertThat(transactionCandidateRepository.findById(candidate.getId())).isEmpty();
    }

    @Test
    @Transactional
    void deletePostedCandidateRejected() throws Exception {
        TransactionCandidate candidate = createReadyCandidate(currentUser());
        restTransactionCandidateMockMvc.perform(post(ENTITY_API_URL_ID + "/post", candidate.getId())).andExpect(status().isOk());

        restTransactionCandidateMockMvc.perform(delete(ENTITY_API_URL_ID, candidate.getId())).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteCancelledCandidateRejected() throws Exception {
        TransactionCandidate candidate = createDraftCandidate(currentUser());
        restTransactionCandidateMockMvc.perform(post(ENTITY_API_URL_ID + "/cancel", candidate.getId())).andExpect(status().isOk());

        restTransactionCandidateMockMvc.perform(delete(ENTITY_API_URL_ID, candidate.getId())).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteFileImportCandidateRejected() throws Exception {
        TransactionCandidate candidate = createReadyCandidate(currentUser());
        candidate.setSource(TransactionCandidateSource.FILE_IMPORT);
        transactionCandidateRepository.saveAndFlush(candidate);

        restTransactionCandidateMockMvc.perform(delete(ENTITY_API_URL_ID, candidate.getId())).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteApiImportCandidateRejected() throws Exception {
        TransactionCandidate candidate = createDraftCandidate(currentUser());
        candidate.setSource(TransactionCandidateSource.API_IMPORT);
        transactionCandidateRepository.saveAndFlush(candidate);

        restTransactionCandidateMockMvc.perform(delete(ENTITY_API_URL_ID, candidate.getId())).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteReadyManualCandidateRejected() throws Exception {
        TransactionCandidate candidate = createReadyCandidate(currentUser());

        restTransactionCandidateMockMvc.perform(delete(ENTITY_API_URL_ID, candidate.getId())).andExpect(status().isBadRequest());
    }

    private TransactionCandidate createDraftCandidate(User owner) {
        TransactionCandidate candidate = new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .status(TransactionCandidateStatus.DRAFT)
            .validationStatus(TransactionCandidateValidationStatus.UNKNOWN)
            .descriptionReviewStatus(com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED)
            .classificationReviewStatus(com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus.NOT_EVALUATED)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(owner)
            .tags(new HashSet<>());
        return transactionCandidateRepository.saveAndFlush(candidate);
    }

    private TransactionCandidate createReadyCandidate(User owner) {
        FinancialAccount account = createAccount(owner);
        Category category = createCategory(CategoryType.EXPENSE, owner);
        Tag tag = createTag(owner);
        TransactionCandidate candidate = new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .status(TransactionCandidateStatus.READY_TO_POST)
            .validationStatus(TransactionCandidateValidationStatus.VALID)
            .descriptionReviewStatus(com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED)
            .classificationReviewStatus(com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus.USER_SELECTED)
            .transactionDate(LocalDate.of(2026, 1, 15))
            .postingDate(LocalDate.of(2026, 1, 16))
            .description("Manual ready candidate")
            .signedAmount(new BigDecimal("-42.00"))
            .amount(new BigDecimal("42.00"))
            .flow(TransactionFlow.OUT)
            .currencySnapshot(account.getCurrency())
            .externalReference("manual-ref")
            .notes("manual notes")
            .account(account)
            .category(category)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(owner)
            .tags(new HashSet<>(Set.of(tag)));
        return transactionCandidateRepository.saveAndFlush(candidate);
    }

    private TransactionCandidate createReadyCandidateWithoutOutputs(
        User owner,
        FinancialAccount account,
        String description,
        BigDecimal signedAmount
    ) {
        TransactionCandidate candidate = new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .status(TransactionCandidateStatus.READY_TO_POST)
            .validationStatus(TransactionCandidateValidationStatus.VALID)
            .descriptionReviewStatus(com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED)
            .classificationReviewStatus(com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus.NOT_EVALUATED)
            .transactionDate(LocalDate.of(2026, 1, 15))
            .description(description)
            .signedAmount(signedAmount)
            .amount(signedAmount.abs())
            .flow(signedAmount.compareTo(BigDecimal.ZERO) > 0 ? TransactionFlow.IN : TransactionFlow.OUT)
            .currencySnapshot(account.getCurrency())
            .account(account)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(owner)
            .tags(new HashSet<>());
        return transactionCandidateRepository.saveAndFlush(candidate);
    }

    private TransactionCandidate createSummaryCandidate(
        User owner,
        TransactionCandidateSource source,
        TransactionCandidateStatus status,
        Instant updatedAt
    ) {
        FinancialAccount account = createAccount(owner);
        Category category = createCategory(CategoryType.EXPENSE, owner);
        Tag tag = createTag(owner);
        TransactionCandidate candidate = new TransactionCandidate()
            .source(source)
            .status(status)
            .validationStatus(
                status == TransactionCandidateStatus.READY_TO_POST
                    ? TransactionCandidateValidationStatus.VALID
                    : TransactionCandidateValidationStatus.UNKNOWN
            )
            .descriptionReviewStatus(com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED)
            .classificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED)
            .transactionDate(LocalDate.of(2026, 1, 15))
            .description("Summary candidate")
            .signedAmount(new BigDecimal("-42.00"))
            .amount(new BigDecimal("42.00"))
            .flow(TransactionFlow.OUT)
            .currencySnapshot(account.getCurrency())
            .account(account)
            .category(category)
            .createdAt(updatedAt.minusSeconds(600))
            .updatedAt(updatedAt)
            .user(owner)
            .tags(new HashSet<>(Set.of(tag)));
        return transactionCandidateRepository.saveAndFlush(candidate);
    }

    private void createMatchingRule(User owner, Category resultingCategory, Tag resultingTag, String descriptionValue) {
        createMatchingRule(owner, resultingCategory, resultingTag, descriptionValue, true);
    }

    private void createMatchingRule(User owner, Category resultingCategory, Tag resultingTag, String descriptionValue, boolean active) {
        TransactionRule rule = new TransactionRule()
            .name("Candidate rule")
            .priority(0)
            .conditionLogic(RuleConditionLogic.ALL)
            .active(active)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .user(owner)
            .resultingCategory(resultingCategory);
        rule.addResultingTags(resultingTag);
        em.persist(rule);

        TransactionRuleCondition condition = new TransactionRuleCondition()
            .field(TransactionRuleField.DESCRIPTION)
            .operator(RuleOperator.CONTAINS)
            .value(descriptionValue)
            .caseSensitive(false)
            .position(0);
        rule.addConditions(condition);
        em.persist(condition);
        em.flush();
    }

    private User currentUser() {
        return TestUtil.findAll(em, User.class)
            .stream()
            .filter(user -> CURRENT_MOCK_USER_LOGIN.equals(user.getLogin()))
            .findFirst()
            .orElseThrow();
    }

    private User createOtherUser() {
        User user = UserResourceIT.createEntity();
        user.setLogin("other-candidate-user");
        user.setEmail("other-candidate-user@localhost");
        em.persist(user);
        em.flush();
        return user;
    }

    private FinancialAccount createAccount(User owner) {
        FinancialAccount account = FinancialAccountResourceIT.createEntity(em);
        account.setUser(owner);
        return financialAccountRepository.saveAndFlush(account);
    }

    private Category createCategory(CategoryType type, User owner) {
        Category category = CategoryResourceIT.createEntity(em);
        category.setCategoryType(type);
        category.setUser(owner);
        return categoryRepository.saveAndFlush(category);
    }

    private Tag createTag(User owner) {
        Tag tag = TagResourceIT.createEntity(em);
        tag.setUser(owner);
        return tagRepository.saveAndFlush(tag);
    }

    private FinancialAccountDTO refAccount(Long id) {
        FinancialAccountDTO dto = new FinancialAccountDTO();
        dto.setId(id);
        return dto;
    }

    private CategoryDTO refCategory(Long id) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(id);
        return dto;
    }

    private FinancialTransactionDTO refFinancialTransaction(Long id) {
        FinancialTransactionDTO dto = new FinancialTransactionDTO();
        dto.setId(id);
        return dto;
    }

    private TransactionIngestionDTO refTransactionIngestion(Long id) {
        TransactionIngestionDTO dto = new TransactionIngestionDTO();
        dto.setId(id);
        return dto;
    }

    private IngestionRecordDTO refIngestionRecord(Long id) {
        IngestionRecordDTO dto = new IngestionRecordDTO();
        dto.setId(id);
        return dto;
    }

    @SuppressWarnings("unused")
    private TagDTO refTag(Long id) {
        TagDTO dto = new TagDTO();
        dto.setId(id);
        return dto;
    }
}
