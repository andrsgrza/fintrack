package com.fintrack.app.web.rest;

import static com.fintrack.app.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionCandidateDTO;
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
            .classificationReviewStatus(com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus.NOT_EVALUATED)
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

    private void createMatchingRule(User owner, Category resultingCategory, Tag resultingTag, String descriptionValue) {
        TransactionRule rule = new TransactionRule()
            .name("Candidate rule")
            .priority(0)
            .conditionLogic(RuleConditionLogic.ALL)
            .active(true)
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
            .position(0)
            .transactionRule(rule);
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

    @SuppressWarnings("unused")
    private TagDTO refTag(Long id) {
        TagDTO dto = new TagDTO();
        dto.setId(id);
        return dto;
    }
}
