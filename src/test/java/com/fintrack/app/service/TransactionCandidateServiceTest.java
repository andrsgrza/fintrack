package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateValidationStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.IngestionRecordDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionCandidateDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.TransactionCandidateMapper;
import com.fintrack.app.service.rules.TransactionRuleEvaluationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionCandidateServiceTest {

    @Mock
    private TransactionCandidateRepository transactionCandidateRepository;

    @Mock
    private TransactionCandidateMapper transactionCandidateMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TransactionIngestionRepository transactionIngestionRepository;

    @Mock
    private IngestionRecordRepository ingestionRecordRepository;

    @Mock
    private TransactionRuleEvaluationService transactionRuleEvaluationService;

    private TransactionCandidateService transactionCandidateService;

    private User user;

    @BeforeEach
    void setUp() {
        transactionCandidateService = new TransactionCandidateService(
            transactionCandidateRepository,
            transactionCandidateMapper,
            currentUserService,
            financialAccountRepository,
            financialTransactionRepository,
            categoryRepository,
            tagRepository,
            transactionIngestionRepository,
            ingestionRecordRepository,
            transactionRuleEvaluationService
        );
        user = new User();
        user.setId(1L);
        user.setLogin("user");
        lenient().when(currentUserService.getCurrentUserLogin()).thenReturn("user");
    }

    @Test
    void createDraftCandidateSucceedsAndDefaultsStatusesAndTimestamps() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        TransactionCandidate entity = new TransactionCandidate().source(TransactionCandidateSource.MANUAL);

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(transactionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionCandidateMapper.toDto(any(TransactionCandidate.class))).thenAnswer(invocation ->
            toDto((TransactionCandidate) invocation.getArgument(0))
        );

        TransactionCandidateDTO result = transactionCandidateService.save(dto);

        assertThat(result.getStatus()).isEqualTo(TransactionCandidateStatus.DRAFT);
        assertThat(result.getValidationStatus()).isEqualTo(TransactionCandidateValidationStatus.UNKNOWN);
        assertThat(result.getDescriptionReviewStatus()).isEqualTo(TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED);
        assertThat(result.getClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void signedAmountDerivesAmountAndOutFlow() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        TransactionCandidate entity = new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .signedAmount(new BigDecimal("-12.30"));

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(transactionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionCandidateMapper.toDto(any(TransactionCandidate.class))).thenAnswer(invocation ->
            toDto((TransactionCandidate) invocation.getArgument(0))
        );

        TransactionCandidateDTO result = transactionCandidateService.save(dto);

        assertThat(result.getAmount()).isEqualByComparingTo("12.30");
        assertThat(result.getFlow()).isEqualTo(TransactionFlow.OUT);
    }

    @Test
    void signedAmountDerivesAmountAndInFlow() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        TransactionCandidate entity = new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .signedAmount(new BigDecimal("12.30"));

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(transactionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionCandidateMapper.toDto(any(TransactionCandidate.class))).thenAnswer(invocation ->
            toDto((TransactionCandidate) invocation.getArgument(0))
        );

        TransactionCandidateDTO result = transactionCandidateService.save(dto);

        assertThat(result.getAmount()).isEqualByComparingTo("12.30");
        assertThat(result.getFlow()).isEqualTo(TransactionFlow.IN);
    }

    @Test
    void createManualDraftSetsSourceAndDraftStatusForCurrentUser() {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        TransactionCandidate entity = new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .status(TransactionCandidateStatus.DRAFT);

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(transactionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionCandidateMapper.toDto(any(TransactionCandidate.class))).thenAnswer(invocation ->
            toDto((TransactionCandidate) invocation.getArgument(0))
        );

        TransactionCandidateDTO result = transactionCandidateService.createManualDraft(dto);

        assertThat(result.getSource()).isEqualTo(TransactionCandidateSource.MANUAL);
        assertThat(result.getStatus()).isEqualTo(TransactionCandidateStatus.DRAFT);
    }

    @Test
    void createManualDraftRejectsExplicitStatus() {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setStatus(TransactionCandidateStatus.READY_TO_POST);

        assertThatThrownBy(() -> transactionCandidateService.createManualDraft(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Status is server-controlled for manual drafts");
    }

    @Test
    void createManualDraftRejectsIngestionLinks() {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setTransactionIngestion(refTransactionIngestion(1L));

        assertThatThrownBy(() -> transactionCandidateService.createManualDraft(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Manual drafts cannot be linked to transaction ingestion");
    }

    @Test
    void genericCreateRejectsFileImportCandidate() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.FILE_IMPORT);

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Generic candidate CRUD cannot create file import candidates");
    }

    @Test
    void genericCreateRejectsApiImportCandidate() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.API_IMPORT);

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Generic candidate CRUD cannot create api import candidates");
    }

    @Test
    void genericCreateRejectsTransactionIngestionLink() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setTransactionIngestion(refTransactionIngestion(1L));

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Transaction ingestion links are managed by ingestion workflow commands");
    }

    @Test
    void genericCreateRejectsIngestionRecordLink() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setIngestionRecord(refIngestionRecord(1L));

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ingestion record links are managed by ingestion workflow commands");
    }

    @Test
    void updateManualDraftUpdatesEditableFieldsAndRecalculatesReadyStatus() throws Exception {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT).account(account(user));
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setId(1L);
        dto.setTransactionDate(LocalDate.of(2026, 1, 2));
        dto.setDescription("  Updated draft  ");
        dto.setSignedAmount(new BigDecimal("-20.00"));

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));
        doAnswer(invocation -> {
            TransactionCandidate target = invocation.getArgument(0);
            TransactionCandidateDTO source = invocation.getArgument(1);
            target.setTransactionDate(source.getTransactionDate());
            target.setDescription(source.getDescription());
            target.setSignedAmount(source.getSignedAmount());
            return null;
        })
            .when(transactionCandidateMapper)
            .partialUpdate(any(TransactionCandidate.class), any(TransactionCandidateDTO.class));
        when(transactionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionCandidateMapper.toDto(any(TransactionCandidate.class))).thenAnswer(invocation ->
            toDto((TransactionCandidate) invocation.getArgument(0))
        );

        TransactionCandidateDTO result = transactionCandidateService
            .updateManualDraft(
                1L,
                dto,
                new ObjectMapper()
                    .readTree("{\"transactionDate\":\"2026-01-02\",\"description\":\"  Updated draft  \",\"signedAmount\":-20.00}")
            )
            .orElseThrow();

        assertThat(result.getDescription()).isEqualTo("Updated draft");
        assertThat(result.getAmount()).isEqualByComparingTo("20.00");
        assertThat(result.getFlow()).isEqualTo(TransactionFlow.OUT);
        assertThat(result.getStatus()).isEqualTo(TransactionCandidateStatus.READY_TO_POST);
        assertThat(result.getValidationStatus()).isEqualTo(TransactionCandidateValidationStatus.VALID);
    }

    @Test
    void updateManualDraftRejectsStatusMutation() throws Exception {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setId(1L);
        dto.setStatus(TransactionCandidateStatus.READY_TO_POST);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
            transactionCandidateService.updateManualDraft(1L, dto, new ObjectMapper().readTree("{\"status\":\"READY_TO_POST\"}"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Status is server-controlled for manual drafts");
    }

    @Test
    void updateManualDraftRejectsFinancialTransactionLink() throws Exception {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setId(1L);
        dto.setFinancialTransaction(refFinancialTransaction(1L));

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
            transactionCandidateService.updateManualDraft(1L, dto, new ObjectMapper().readTree("{\"financialTransaction\":{\"id\":1}}"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Financial transaction link is server-controlled");
    }

    @Test
    void updateManualDraftRejectsSignChangeThatMakesCategoryIncompatibleWithDerivedFlow() throws Exception {
        Category expenseCategory = category(CategoryType.EXPENSE, user);
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.READY_TO_POST)
            .account(account(user))
            .category(expenseCategory)
            .transactionDate(LocalDate.of(2026, 1, 2))
            .description("Expense")
            .signedAmount(new BigDecimal("-20.00"))
            .amount(new BigDecimal("20.00"))
            .flow(TransactionFlow.OUT)
            .currencySnapshot(CurrencyCode.MXN)
            .validationStatus(TransactionCandidateValidationStatus.VALID);
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setId(1L);
        dto.setSignedAmount(new BigDecimal("20.00"));

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));
        doAnswer(invocation -> {
            TransactionCandidate target = invocation.getArgument(0);
            TransactionCandidateDTO source = invocation.getArgument(1);
            target.setSignedAmount(source.getSignedAmount());
            return null;
        })
            .when(transactionCandidateMapper)
            .partialUpdate(any(TransactionCandidate.class), any(TransactionCandidateDTO.class));

        assertThatThrownBy(() ->
            transactionCandidateService.updateManualDraft(1L, dto, new ObjectMapper().readTree("{\"signedAmount\":20.00}"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Expense categories require OUT flow");

        verifyNoInteractions(financialTransactionRepository);
    }

    @Test
    void cancelManualDraftSetsCancelledStatusAndTimestamp() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));
        when(transactionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionCandidateMapper.toDto(any(TransactionCandidate.class))).thenAnswer(invocation ->
            toDto((TransactionCandidate) invocation.getArgument(0))
        );

        TransactionCandidateDTO result = transactionCandidateService.cancelManualDraft(1L).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(TransactionCandidateStatus.CANCELLED);
        assertThat(result.getCancelledAt()).isNotNull();
    }

    @Test
    void cancelledManualDraftCannotBePosted() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.CANCELLED);

        when(transactionCandidateRepository.findOneByIdAndUserLoginForPosting(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.postManualDraft(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cancelled transaction candidates cannot be posted");
    }

    @Test
    void fileImportCandidateCannotBePostedThroughManualEndpoint() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.READY_TO_POST);
        existing.setSource(TransactionCandidateSource.FILE_IMPORT);

        when(transactionCandidateRepository.findOneByIdAndUserLoginForPosting(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.postManualDraft(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Manual post command only supports manual transaction candidates");
    }

    @Test
    void incompleteManualDraftCannotBePosted() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT).classificationReviewStatus(
            TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE
        );

        when(transactionCandidateRepository.findOneByIdAndUserLoginForPosting(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.postManualDraft(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Manual transaction candidate is not ready to post");
    }

    @Test
    void validManualDraftPostsFinancialTransactionAndLinksCandidate() {
        Category category = category(CategoryType.EXPENSE, user);
        Tag tag = tag(user);
        FinancialAccount account = account(user);
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.READY_TO_POST)
            .account(account)
            .transactionDate(LocalDate.of(2026, 1, 3))
            .postingDate(LocalDate.of(2026, 1, 4))
            .description("Coffee")
            .signedAmount(new BigDecimal("-12.00"))
            .amount(new BigDecimal("12.00"))
            .flow(TransactionFlow.OUT)
            .currencySnapshot(CurrencyCode.MXN)
            .externalReference("ext-1")
            .notes("note")
            .category(category)
            .tags(Set.of(tag))
            .classificationReviewStatus(TransactionCandidateClassificationReviewStatus.SUGGESTED);

        when(transactionCandidateRepository.findOneByIdAndUserLoginForPosting(1L, "user")).thenReturn(Optional.of(existing));
        when(financialTransactionRepository.save(any())).thenAnswer(invocation -> {
            FinancialTransaction financialTransaction = invocation.getArgument(0);
            financialTransaction.setId(99L);
            return financialTransaction;
        });
        when(transactionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionCandidateMapper.toDto(any(TransactionCandidate.class))).thenAnswer(invocation ->
            toDto((TransactionCandidate) invocation.getArgument(0))
        );

        TransactionCandidateDTO result = transactionCandidateService.postManualDraft(1L).orElseThrow();

        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(captor.capture());
        FinancialTransaction posted = captor.getValue();
        assertThat(posted.getAccount()).isSameAs(account);
        assertThat(posted.getTransactionDate()).isEqualTo(LocalDate.of(2026, 1, 3));
        assertThat(posted.getPostingDate()).isEqualTo(LocalDate.of(2026, 1, 4));
        assertThat(posted.getDescription()).isEqualTo("Coffee");
        assertThat(posted.getAmount()).isEqualByComparingTo("12.00");
        assertThat(posted.getFlow()).isEqualTo(TransactionFlow.OUT);
        assertThat(posted.getOrigin()).isEqualTo(TransactionOrigin.MANUAL);
        assertThat(posted.getExternalReference()).isEqualTo("ext-1");
        assertThat(posted.getNotes()).isEqualTo("note");
        assertThat(posted.getCategory()).isSameAs(category);
        assertThat(posted.getTags()).containsExactly(tag);
        assertThat(result.getStatus()).isEqualTo(TransactionCandidateStatus.POSTED);
        assertThat(result.getPostedAt()).isNotNull();
        assertThat(result.getFinancialTransaction().getId()).isEqualTo(99L);
        verifyNoInteractions(transactionRuleEvaluationService);
    }

    @Test
    void postingManualDraftTwiceReturnsExistingLinkWithoutCreatingDuplicateTransaction() {
        FinancialTransaction financialTransaction = new FinancialTransaction().id(99L).account(account(user));
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.POSTED).financialTransaction(financialTransaction);

        when(transactionCandidateRepository.findOneByIdAndUserLoginForPosting(1L, "user")).thenReturn(Optional.of(existing));
        when(transactionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionCandidateMapper.toDto(any(TransactionCandidate.class))).thenAnswer(invocation ->
            toDto((TransactionCandidate) invocation.getArgument(0))
        );

        TransactionCandidateDTO result = transactionCandidateService.postManualDraft(1L).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(TransactionCandidateStatus.POSTED);
        assertThat(result.getFinancialTransaction().getId()).isEqualTo(99L);
        verify(transactionCandidateRepository).save(existing);
        verifyNoInteractions(financialTransactionRepository);
    }

    @Test
    void staleDescriptionReviewCannotBePosted() {
        TransactionCandidate existing = completeReadyCandidate()
            .id(1L)
            .user(user)
            .amount(new BigDecimal("10.00"))
            .flow(TransactionFlow.OUT)
            .validationStatus(TransactionCandidateValidationStatus.VALID)
            .descriptionReviewStatus(TransactionCandidateDescriptionReviewStatus.STALE)
            .classificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED);

        when(transactionCandidateRepository.findOneByIdAndUserLoginForPosting(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.postManualDraft(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Stale description review must be refreshed before posting");

        verifyNoInteractions(financialTransactionRepository);
    }

    @Test
    void staleClassificationReviewCannotBePosted() {
        TransactionCandidate existing = completeReadyCandidate()
            .id(1L)
            .user(user)
            .amount(new BigDecimal("10.00"))
            .flow(TransactionFlow.OUT)
            .validationStatus(TransactionCandidateValidationStatus.VALID)
            .descriptionReviewStatus(TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED)
            .classificationReviewStatus(TransactionCandidateClassificationReviewStatus.STALE);

        when(transactionCandidateRepository.findOneByIdAndUserLoginForPosting(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.postManualDraft(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Stale classification review must be refreshed before posting");

        verifyNoInteractions(financialTransactionRepository);
    }

    @Test
    void notEvaluatedClassificationReviewCannotBePosted() {
        TransactionCandidate existing = completeReadyCandidate()
            .id(1L)
            .user(user)
            .signedAmount(new BigDecimal("-10.00"))
            .amount(new BigDecimal("10.00"))
            .flow(TransactionFlow.OUT)
            .validationStatus(TransactionCandidateValidationStatus.VALID)
            .descriptionReviewStatus(TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED)
            .classificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED);

        when(transactionCandidateRepository.findOneByIdAndUserLoginForPosting(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.postManualDraft(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Transaction classification must be reviewed before posting");

        verifyNoInteractions(financialTransactionRepository);
        verifyNoInteractions(transactionRuleEvaluationService);
    }

    @Test
    void suggestedClassificationReviewCanBePosted() {
        assertPostSucceedsWithClassificationStatus(TransactionCandidateClassificationReviewStatus.SUGGESTED);
    }

    @Test
    void userSelectedClassificationReviewCanBePosted() {
        assertPostSucceedsWithClassificationStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
    }

    @Test
    void notApplicableClassificationReviewCanBePosted() {
        assertPostSucceedsWithClassificationStatus(TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE);
    }

    @Test
    void staleValidationStatusIsRecalculatedBeforePostingWhenCandidateIsComplete() {
        TransactionCandidate existing = completeReadyCandidate()
            .id(1L)
            .user(user)
            .signedAmount(new BigDecimal("-10.00"))
            .validationStatus(TransactionCandidateValidationStatus.STALE)
            .descriptionReviewStatus(TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED)
            .classificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE);

        when(transactionCandidateRepository.findOneByIdAndUserLoginForPosting(1L, "user")).thenReturn(Optional.of(existing));
        when(financialTransactionRepository.save(any())).thenAnswer(invocation -> {
            FinancialTransaction financialTransaction = invocation.getArgument(0);
            financialTransaction.setId(99L);
            return financialTransaction;
        });
        when(transactionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionCandidateMapper.toDto(any(TransactionCandidate.class))).thenAnswer(invocation ->
            toDto((TransactionCandidate) invocation.getArgument(0))
        );

        TransactionCandidateDTO result = transactionCandidateService.postManualDraft(1L).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(TransactionCandidateStatus.POSTED);
        assertThat(result.getValidationStatus()).isEqualTo(TransactionCandidateValidationStatus.VALID);
        verifyNoInteractions(transactionRuleEvaluationService);
    }

    @Test
    void invalidValidationStatusCannotBePostedWhenCandidateCannotRecalculateToValid() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.READY_TO_POST)
            .account(account(user))
            .transactionDate(LocalDate.of(2026, 1, 1))
            .description("Invalid")
            .signedAmount(BigDecimal.ZERO)
            .amount(BigDecimal.ZERO)
            .flow(null)
            .currencySnapshot(CurrencyCode.MXN)
            .validationStatus(TransactionCandidateValidationStatus.INVALID)
            .classificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE);

        when(transactionCandidateRepository.findOneByIdAndUserLoginForPosting(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.postManualDraft(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Manual transaction candidate is not ready to post");

        verifyNoInteractions(financialTransactionRepository);
    }

    @Test
    void zeroSignedAmountInvalidForReadyToPost() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setAccount(refAccount(1L));
        TransactionCandidate entity = completeReadyCandidate().signedAmount(BigDecimal.ZERO);

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(account(user)));

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount must be greater than zero");
    }

    @Test
    void readyToPostRequiresAccount() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        TransactionCandidate entity = new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .status(TransactionCandidateStatus.READY_TO_POST);

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Financial account is required before posting");
    }

    @Test
    void readyToPostRequiresTransactionDate() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setAccount(refAccount(1L));
        TransactionCandidate entity = completeReadyCandidate().transactionDate(null).signedAmount(new BigDecimal("10.00"));

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(account(user)));

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Transaction date is required before posting");
    }

    @Test
    void readyToPostRequiresNonblankDescription() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setAccount(refAccount(1L));
        TransactionCandidate entity = completeReadyCandidate().description("   ").signedAmount(new BigDecimal("10.00"));

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(account(user)));

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Description is required before posting");
    }

    @Test
    void readyToPostRequiresFlow() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setAccount(refAccount(1L));
        TransactionCandidate entity = completeReadyCandidate().amount(new BigDecimal("10.00"));

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(account(user)));

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Flow is required when amount is set");
    }

    @Test
    void currencyAccountMismatchRejected() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setAccount(refAccount(1L));
        TransactionCandidate entity = new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .currencySnapshot(CurrencyCode.USD);

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(account(user)));

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Currency snapshot must match the selected account currency");
    }

    @Test
    void foreignAccountRejected() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setAccount(refAccount(1L));
        TransactionCandidate entity = new TransactionCandidate().source(TransactionCandidateSource.MANUAL);

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Financial account is not accessible");
    }

    @Test
    void foreignCategoryRejected() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setCategory(refCategory(1L));
        TransactionCandidate entity = new TransactionCandidate().source(TransactionCandidateSource.MANUAL);

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Category is not accessible");
    }

    @Test
    void foreignTagRejected() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        TagDTO tagDTO = new TagDTO();
        tagDTO.setId(1L);
        dto.setTags(Set.of(tagDTO));
        TransactionCandidate entity = new TransactionCandidate().source(TransactionCandidateSource.MANUAL);

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Tag is not accessible");
    }

    @Test
    void foreignTransactionIngestionRejected() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.FILE_IMPORT);
        dto.setTransactionIngestion(refTransactionIngestion(1L));

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Generic candidate CRUD cannot create file import candidates");
    }

    @Test
    void foreignIngestionRecordRejected() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.FILE_IMPORT);
        dto.setIngestionRecord(refIngestionRecord(1L));

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Generic candidate CRUD cannot create file import candidates");
    }

    @Test
    void adminHasNoCrossUserProductBypass() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setAccount(refAccount(1L));
        TransactionCandidate entity = new TransactionCandidate().source(TransactionCandidateSource.MANUAL);
        User admin = new User();
        admin.setId(2L);
        admin.setLogin("admin");

        when(currentUserService.getCurrentUserLogin()).thenReturn("admin");
        when(currentUserService.getCurrentUser()).thenReturn(admin);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(1L, "admin")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Financial account is not accessible");
    }

    @Test
    void expenseCategoryRejectsFlowIn() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setCategory(refCategory(1L));
        TransactionCandidate entity = new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .amount(new BigDecimal("10.00"))
            .flow(TransactionFlow.IN);
        Category expenseCategory = category(CategoryType.EXPENSE, user);

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(expenseCategory));

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Expense categories require OUT flow");
    }

    @Test
    void incomeCategoryRejectsFlowOut() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setCategory(refCategory(1L));
        TransactionCandidate entity = new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .amount(new BigDecimal("10.00"))
            .flow(TransactionFlow.OUT);
        Category incomeCategory = category(CategoryType.INCOME, user);

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(incomeCategory));

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Income categories require IN flow");
    }

    @Test
    void bothCategoryAcceptsInAndOut() {
        Category bothCategory = category(CategoryType.BOTH, user);
        assertThat(saveCandidateWithCategoryAndFlow(bothCategory, TransactionFlow.IN).getFlow()).isEqualTo(TransactionFlow.IN);
        assertThat(saveCandidateWithCategoryAndFlow(bothCategory, TransactionFlow.OUT).getFlow()).isEqualTo(TransactionFlow.OUT);
    }

    @Test
    void amountAndFlowRejectedFromClientOnCreate() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setAmount(new BigDecimal("10.00"));
        dto.setFlow(TransactionFlow.OUT);

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Amount is derived from signed amount");
    }

    @Test
    void directFinancialTransactionLinkRejectedOnCreate() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setFinancialTransaction(refFinancialTransaction(1L));

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Financial transaction link is server-controlled");
    }

    @Test
    void directFinancialTransactionLinkRejectedOnUpdate() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setId(1L);
        dto.setCreatedAt(existing.getCreatedAt());
        dto.setUpdatedAt(existing.getUpdatedAt());
        dto.setFinancialTransaction(refFinancialTransaction(1L));

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.update(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Financial transaction link is server-controlled");
    }

    @Test
    void directFinancialTransactionLinkRejectedOnPatch() throws Exception {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setId(1L);
        dto.setFinancialTransaction(refFinancialTransaction(1L));

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
            transactionCandidateService.partialUpdate(dto, new ObjectMapper().readTree("{\"financialTransaction\":{\"id\":1}}"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Financial transaction link is server-controlled");
    }

    @Test
    void createRejectsServerOwnedFields() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Created at is server-controlled");
    }

    @Test
    void updateBeforeFinalStatusSucceedsWhenStatusIsUnchanged() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setId(1L);
        dto.setCreatedAt(existing.getCreatedAt());
        dto.setUpdatedAt(existing.getUpdatedAt());
        TransactionCandidate replacement = new TransactionCandidate()
            .id(1L)
            .source(TransactionCandidateSource.MANUAL)
            .status(TransactionCandidateStatus.DRAFT)
            .description("  Updated  ");

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(replacement);
        when(transactionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionCandidateMapper.toDto(any(TransactionCandidate.class))).thenAnswer(invocation ->
            toDto((TransactionCandidate) invocation.getArgument(0))
        );

        TransactionCandidateDTO result = transactionCandidateService.update(dto);

        assertThat(result.getStatus()).isEqualTo(TransactionCandidateStatus.DRAFT);
        assertThat(result.getDescription()).isEqualTo("Updated");
    }

    @Test
    void genericUpdateRejectsStatusChange() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setId(1L);
        dto.setCreatedAt(existing.getCreatedAt());
        dto.setUpdatedAt(existing.getUpdatedAt());
        dto.setStatus(TransactionCandidateStatus.CANCELLED);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.update(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Status is controlled by command endpoints");
    }

    @Test
    void patchBeforeFinalStatusSucceeds() throws Exception {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setId(1L);
        dto.setDescription("  Patched  ");

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));
        when(transactionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionCandidateMapper.toDto(any(TransactionCandidate.class))).thenAnswer(invocation ->
            toDto((TransactionCandidate) invocation.getArgument(0))
        );

        transactionCandidateService.partialUpdate(dto, new ObjectMapper().readTree("{\"description\":\"  Patched  \"}"));

        verify(transactionCandidateMapper).partialUpdate(existing, dto);
    }

    @Test
    void genericPatchRejectsStatusChange() throws Exception {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setId(1L);
        dto.setStatus(TransactionCandidateStatus.FAILED);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.partialUpdate(dto, new ObjectMapper().readTree("{\"status\":\"FAILED\"}")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Status is controlled by command endpoints");
    }

    @Test
    void genericUpdateRejectsTransactionIngestionLinkMutation() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setId(1L);
        dto.setCreatedAt(existing.getCreatedAt());
        dto.setUpdatedAt(existing.getUpdatedAt());
        dto.setTransactionIngestion(refTransactionIngestion(1L));

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.update(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Transaction ingestion links are managed by ingestion workflow commands");
    }

    @Test
    void genericPatchRejectsIngestionRecordLinkMutation() throws Exception {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setId(1L);
        dto.setIngestionRecord(refIngestionRecord(1L));

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
            transactionCandidateService.partialUpdate(dto, new ObjectMapper().readTree("{\"ingestionRecord\":{\"id\":1}}"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ingestion record links are managed by ingestion workflow commands");
    }

    @Test
    void genericUpdateRejectsFileImportCandidateMutation() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.READY_TO_POST);
        existing.setSource(TransactionCandidateSource.FILE_IMPORT);
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.FILE_IMPORT);
        dto.setId(1L);
        dto.setCreatedAt(existing.getCreatedAt());
        dto.setUpdatedAt(existing.getUpdatedAt());

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.update(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("File import candidates are managed by ingestion workflow commands");
    }

    @Test
    void genericPatchRejectsApiImportCandidateMutation() throws Exception {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        existing.setSource(TransactionCandidateSource.API_IMPORT);
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setId(1L);
        dto.setDescription("Patched");

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
            transactionCandidateService.partialUpdate(dto, new ObjectMapper().readTree("{\"description\":\"Patched\"}"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("API import candidates are not supported by generic candidate CRUD");
    }

    @Test
    void genericUpdateRejectsExistingWorkflowLinkedCandidate() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        existing.setTransactionIngestion(transactionIngestion(user));
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setId(1L);
        dto.setCreatedAt(existing.getCreatedAt());
        dto.setUpdatedAt(existing.getUpdatedAt());

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.update(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Workflow-linked candidates are managed by workflow commands");
    }

    @Test
    void sourceImmutable() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.FILE_IMPORT);
        dto.setId(1L);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.update(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Source cannot be changed");
    }

    @Test
    void updateRejectsCreatedAtChange() {
        assertTimestampUpdateRejected("Created at cannot be changed", dto -> dto.setCreatedAt(Instant.parse("2026-01-02T00:00:00Z")));
    }

    @Test
    void updateRejectsUpdatedAtChange() {
        assertTimestampUpdateRejected("Updated at cannot be changed", dto -> dto.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z")));
    }

    @Test
    void updateRejectsPostedAtChange() {
        assertTimestampUpdateRejected("Posted at cannot be changed", dto -> dto.setPostedAt(Instant.parse("2026-01-02T00:00:00Z")));
    }

    @Test
    void updateRejectsCancelledAtChange() {
        assertTimestampUpdateRejected("Cancelled at cannot be changed", dto -> dto.setCancelledAt(Instant.parse("2026-01-02T00:00:00Z")));
    }

    @Test
    void updateRejectsFailedAtChange() {
        assertTimestampUpdateRejected("Failed at cannot be changed", dto -> dto.setFailedAt(Instant.parse("2026-01-02T00:00:00Z")));
    }

    @Test
    void patchRejectsServerOwnedTimestampField() throws Exception {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setId(1L);
        dto.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
            transactionCandidateService.partialUpdate(dto, new ObjectMapper().readTree("{\"updatedAt\":\"2026-01-02T00:00:00Z\"}"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Updated at cannot be changed");
    }

    @Test
    void failedRequiresFailureReason() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        TransactionCandidate entity = new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .status(TransactionCandidateStatus.FAILED);

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Failure reason is required for failed candidates");
    }

    @Test
    void failedToPostedBlocked() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.FAILED);
        existing.setFailureReason("failed");
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setId(1L);
        dto.setCreatedAt(existing.getCreatedAt());
        dto.setUpdatedAt(existing.getUpdatedAt());
        TransactionCandidate replacement = existingCandidate(TransactionCandidateStatus.POSTED);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(replacement);

        assertThatThrownBy(() -> transactionCandidateService.update(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Failed candidates must be reviewed before posting");
    }

    @Test
    void genericCreateRejectsStatus() {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setStatus(TransactionCandidateStatus.CANCELLED);

        assertThatThrownBy(() -> transactionCandidateService.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Status is controlled by command endpoints");
    }

    @Test
    void postedCandidateCannotBeMutatedAsDraft() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.POSTED);
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setId(1L);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.update(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Final transaction candidates cannot be changed");
    }

    @Test
    void cancelledCandidateCannotBeMutatedAsDraft() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.CANCELLED);
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setId(1L);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.update(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Final transaction candidates cannot be changed");
    }

    @Test
    void deleteCleansTagJoinRows() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThat(transactionCandidateService.delete(1L)).isTrue();

        verify(transactionCandidateRepository).deleteTagLinksByTransactionCandidateId(1L);
        verify(transactionCandidateRepository).deleteById(1L);
    }

    @Test
    void deleteRejectsPostedCandidate() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.POSTED);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Posted or cancelled transaction candidates cannot be deleted");
    }

    @Test
    void deleteRejectsCancelledCandidate() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.CANCELLED);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Posted or cancelled transaction candidates cannot be deleted");
    }

    @Test
    void deleteRejectsFileImportCandidate() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.READY_TO_POST);
        existing.setSource(TransactionCandidateSource.FILE_IMPORT);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("File import candidates cannot be deleted through generic candidate CRUD");
    }

    @Test
    void deleteRejectsApiImportCandidate() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        existing.setSource(TransactionCandidateSource.API_IMPORT);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("API import candidates cannot be deleted through generic candidate CRUD");
    }

    @Test
    void deleteRejectsWorkflowLinkedCandidate() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        existing.setIngestionRecord(ingestionRecord(user));

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Workflow-linked candidates cannot be deleted through generic candidate CRUD");
    }

    @Test
    void deleteRejectsFinancialTransactionLinkedCandidate() {
        FinancialTransaction financialTransaction = new FinancialTransaction().id(99L).account(account(user));
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        existing.setFinancialTransaction(financialTransaction);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Workflow-linked candidates cannot be deleted through generic candidate CRUD");
    }

    @Test
    void deleteRejectsReadyManualCandidate() {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.READY_TO_POST);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.delete(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Generic candidate CRUD can delete only unlinked manual draft candidates");
    }

    private TransactionCandidateDTO dto(TransactionCandidateSource source) {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setSource(source);
        return dto;
    }

    private void assertPostSucceedsWithClassificationStatus(TransactionCandidateClassificationReviewStatus classificationReviewStatus) {
        TransactionCandidate existing = completeReadyCandidate()
            .id(1L)
            .user(user)
            .signedAmount(new BigDecimal("-10.00"))
            .amount(new BigDecimal("10.00"))
            .flow(TransactionFlow.OUT)
            .validationStatus(TransactionCandidateValidationStatus.VALID)
            .descriptionReviewStatus(TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED)
            .classificationReviewStatus(classificationReviewStatus);

        when(transactionCandidateRepository.findOneByIdAndUserLoginForPosting(1L, "user")).thenReturn(Optional.of(existing));
        when(financialTransactionRepository.save(any())).thenAnswer(invocation -> {
            FinancialTransaction financialTransaction = invocation.getArgument(0);
            financialTransaction.setId(99L);
            return financialTransaction;
        });
        when(transactionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionCandidateMapper.toDto(any(TransactionCandidate.class))).thenAnswer(invocation ->
            toDto((TransactionCandidate) invocation.getArgument(0))
        );

        TransactionCandidateDTO result = transactionCandidateService.postManualDraft(1L).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(TransactionCandidateStatus.POSTED);
        assertThat(result.getClassificationReviewStatus()).isEqualTo(classificationReviewStatus);
        assertThat(result.getFinancialTransaction().getId()).isEqualTo(99L);
        verifyNoInteractions(transactionRuleEvaluationService);
    }

    private TransactionCandidateDTO toDto(TransactionCandidate entity) {
        TransactionCandidateDTO dto = new TransactionCandidateDTO();
        dto.setId(entity.getId());
        dto.setSource(entity.getSource());
        dto.setStatus(entity.getStatus());
        dto.setValidationStatus(entity.getValidationStatus());
        dto.setDescriptionReviewStatus(entity.getDescriptionReviewStatus());
        dto.setClassificationReviewStatus(entity.getClassificationReviewStatus());
        dto.setDescription(entity.getDescription());
        dto.setAmount(entity.getAmount());
        dto.setFlow(entity.getFlow());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setPostedAt(entity.getPostedAt());
        dto.setCancelledAt(entity.getCancelledAt());
        dto.setFailedAt(entity.getFailedAt());
        if (entity.getFinancialTransaction() != null) {
            dto.setFinancialTransaction(refFinancialTransaction(entity.getFinancialTransaction().getId()));
        }
        return dto;
    }

    private TransactionCandidate completeReadyCandidate() {
        return new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .status(TransactionCandidateStatus.READY_TO_POST)
            .account(account(user))
            .transactionDate(LocalDate.of(2026, 1, 1))
            .description("Ready")
            .currencySnapshot(CurrencyCode.MXN);
    }

    private TransactionCandidate existingCandidate(TransactionCandidateStatus status) {
        return new TransactionCandidate()
            .id(1L)
            .source(TransactionCandidateSource.MANUAL)
            .status(status)
            .validationStatus(TransactionCandidateValidationStatus.UNKNOWN)
            .descriptionReviewStatus(TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED)
            .classificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED)
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .user(user)
            .tags(new HashSet<>());
    }

    private FinancialAccount account(User owner) {
        FinancialAccount account = new FinancialAccount();
        account.setId(1L);
        account.setName("Account");
        account.setAccountType(AccountType.DEBIT);
        account.setCurrency(CurrencyCode.MXN);
        account.setUser(owner);
        return account;
    }

    private Category category(CategoryType categoryType, User owner) {
        Category category = new Category();
        category.setId(1L);
        category.setName("Category");
        category.setCategoryType(categoryType);
        category.setUser(owner);
        return category;
    }

    private Tag tag(User owner) {
        Tag tag = new Tag();
        tag.setId(1L);
        tag.setName("Tag");
        tag.setUser(owner);
        return tag;
    }

    private TransactionIngestion transactionIngestion(User owner) {
        TransactionIngestion transactionIngestion = new TransactionIngestion();
        transactionIngestion.setId(1L);
        transactionIngestion.setAccount(account(owner));
        return transactionIngestion;
    }

    private IngestionRecord ingestionRecord(User owner) {
        IngestionRecord ingestionRecord = new IngestionRecord();
        ingestionRecord.setId(1L);
        ingestionRecord.setTransactionIngestion(transactionIngestion(owner));
        return ingestionRecord;
    }

    private FinancialAccountDTO refAccount(Long id) {
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(id);
        return accountDTO;
    }

    private CategoryDTO refCategory(Long id) {
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(id);
        return categoryDTO;
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

    private FinancialTransactionDTO refFinancialTransaction(Long id) {
        FinancialTransactionDTO dto = new FinancialTransactionDTO();
        dto.setId(id);
        return dto;
    }

    private TransactionCandidateDTO saveCandidateWithCategoryAndFlow(Category category, TransactionFlow flow) {
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setCategory(refCategory(category.getId()));
        TransactionCandidate entity = new TransactionCandidate()
            .source(TransactionCandidateSource.MANUAL)
            .amount(new BigDecimal("10.00"))
            .flow(flow);

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(transactionCandidateMapper.toEntity(dto)).thenReturn(entity);
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(category.getId(), "user")).thenReturn(Optional.of(category));
        when(transactionCandidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionCandidateMapper.toDto(any(TransactionCandidate.class))).thenAnswer(invocation ->
            toDto((TransactionCandidate) invocation.getArgument(0))
        );

        return transactionCandidateService.save(dto);
    }

    private void assertTimestampUpdateRejected(String expectedMessage, java.util.function.Consumer<TransactionCandidateDTO> mutate) {
        TransactionCandidate existing = existingCandidate(TransactionCandidateStatus.DRAFT);
        TransactionCandidateDTO dto = dto(TransactionCandidateSource.MANUAL);
        dto.setId(1L);
        dto.setCreatedAt(existing.getCreatedAt());
        dto.setUpdatedAt(existing.getUpdatedAt());
        mutate.accept(dto);

        when(transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(1L, "user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionCandidateService.update(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(expectedMessage);
    }
}
