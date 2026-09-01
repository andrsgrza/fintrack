package com.fintrack.app.web.rest;

import static com.fintrack.app.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionCandidateDTO;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
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
