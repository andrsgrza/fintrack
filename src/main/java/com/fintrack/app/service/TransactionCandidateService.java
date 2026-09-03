package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionIngestion;
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
import com.fintrack.app.service.dto.CategorySuggestionDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.IngestionRecordDTO;
import com.fintrack.app.service.dto.RuleMatchResultDTO;
import com.fintrack.app.service.dto.RuleOutputConflictDTO;
import com.fintrack.app.service.dto.SkippedRuleOutputDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TagSuggestionDTO;
import com.fintrack.app.service.dto.TransactionCandidateDTO;
import com.fintrack.app.service.dto.TransactionCandidateRuleApplyResponseDTO;
import com.fintrack.app.service.dto.TransactionCandidateRulePreviewResponseDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.TransactionCandidateMapper;
import com.fintrack.app.service.rules.CategorySuggestion;
import com.fintrack.app.service.rules.RuleMatchResult;
import com.fintrack.app.service.rules.RuleOutputConflict;
import com.fintrack.app.service.rules.SkippedRuleOutput;
import com.fintrack.app.service.rules.TagSuggestion;
import com.fintrack.app.service.rules.TransactionRuleEvaluationInput;
import com.fintrack.app.service.rules.TransactionRuleEvaluationResult;
import com.fintrack.app.service.rules.TransactionRuleEvaluationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.TransactionCandidate}.
 */
@Service
@Transactional
public class TransactionCandidateService {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionCandidateService.class);

    private final TransactionCandidateRepository transactionCandidateRepository;

    private final TransactionCandidateMapper transactionCandidateMapper;

    private final CurrentUserService currentUserService;

    private final FinancialAccountRepository financialAccountRepository;

    private final FinancialTransactionRepository financialTransactionRepository;

    private final CategoryRepository categoryRepository;

    private final TagRepository tagRepository;

    private final TransactionIngestionRepository transactionIngestionRepository;

    private final IngestionRecordRepository ingestionRecordRepository;

    private final TransactionRuleEvaluationService transactionRuleEvaluationService;

    public TransactionCandidateService(
        TransactionCandidateRepository transactionCandidateRepository,
        TransactionCandidateMapper transactionCandidateMapper,
        CurrentUserService currentUserService,
        FinancialAccountRepository financialAccountRepository,
        FinancialTransactionRepository financialTransactionRepository,
        CategoryRepository categoryRepository,
        TagRepository tagRepository,
        TransactionIngestionRepository transactionIngestionRepository,
        IngestionRecordRepository ingestionRecordRepository,
        TransactionRuleEvaluationService transactionRuleEvaluationService
    ) {
        this.transactionCandidateRepository = transactionCandidateRepository;
        this.transactionCandidateMapper = transactionCandidateMapper;
        this.currentUserService = currentUserService;
        this.financialAccountRepository = financialAccountRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.transactionRuleEvaluationService = transactionRuleEvaluationService;
    }

    /**
     * Create a manual transaction draft through the product command surface.
     *
     * @param transactionCandidateDTO the editable draft fields.
     * @return the persisted manual draft.
     */
    public TransactionCandidateDTO createManualDraft(TransactionCandidateDTO transactionCandidateDTO) {
        LOG.debug("Request to create manual TransactionCandidate draft");
        rejectManualCommandControlledFieldsOnCreate(transactionCandidateDTO);
        transactionCandidateDTO.setSource(TransactionCandidateSource.MANUAL);
        return save(transactionCandidateDTO);
    }

    /**
     * Autosave editable fields on a manual transaction draft.
     *
     * @param id the candidate id.
     * @param transactionCandidateDTO the partial draft payload.
     * @param patchNode the raw patch payload.
     * @return the updated manual draft.
     */
    public Optional<TransactionCandidateDTO> updateManualDraft(
        Long id,
        TransactionCandidateDTO transactionCandidateDTO,
        JsonNode patchNode
    ) {
        LOG.debug("Request to autosave manual TransactionCandidate draft : {}", id);
        return findAccessibleEntity(id)
            .map(existing -> {
                rejectManualCommandMutation(existing);
                rejectManualCommandControlledFieldsOnUpdate(transactionCandidateDTO, patchNode);

                TransactionCandidateClassificationReviewStatus previousClassificationReviewStatus =
                    existing.getClassificationReviewStatus();
                transactionCandidateMapper.partialUpdate(existing, transactionCandidateDTO);
                applyRelationshipPatches(existing, transactionCandidateDTO, patchNode);
                normalizeFields(existing);
                deriveAmountAndFlow(existing);
                recalculateManualDraftStatus(existing);
                updateClassificationReviewStatusAfterManualPatch(existing, patchNode, previousClassificationReviewStatus);
                validateCandidate(existing, existing);
                existing.setUpdatedAt(Instant.now());
                return existing;
            })
            .map(transactionCandidateRepository::save)
            .map(transactionCandidateMapper::toDto);
    }

    /**
     * Cancel a manual transaction draft without deleting it.
     *
     * @param id the candidate id.
     * @return the cancelled manual draft.
     */
    public Optional<TransactionCandidateDTO> cancelManualDraft(Long id) {
        LOG.debug("Request to cancel manual TransactionCandidate draft : {}", id);
        return findAccessibleEntity(id)
            .map(existing -> {
                rejectManualCommandMutation(existing);
                existing.setStatus(TransactionCandidateStatus.CANCELLED);
                existing.setUpdatedAt(Instant.now());
                existing.setCancelledAt(existing.getUpdatedAt());
                return existing;
            })
            .map(transactionCandidateRepository::save)
            .map(transactionCandidateMapper::toDto);
    }

    /**
     * Post a complete manual transaction draft into the ledger.
     *
     * @param id the candidate id.
     * @return the posted manual candidate.
     */
    public Optional<TransactionCandidateDTO> postManualDraft(Long id) {
        LOG.debug("Request to post manual TransactionCandidate draft : {}", id);
        return findAccessibleEntityForPosting(id)
            .map(existing -> {
                if (existing.getStatus() == TransactionCandidateStatus.POSTED && existing.getFinancialTransaction() != null) {
                    return existing;
                }
                rejectManualPostMutation(existing);
                normalizeFields(existing);
                deriveAmountAndFlow(existing);
                recalculateManualDraftStatus(existing);
                rejectStaleReviewStateForPosting(existing);
                if (existing.getStatus() != TransactionCandidateStatus.READY_TO_POST) {
                    throw new IllegalArgumentException("Manual transaction candidate is not ready to post");
                }
                validateCandidate(existing, existing);

                Instant now = Instant.now();
                FinancialTransaction financialTransaction = new FinancialTransaction()
                    .account(existing.getAccount())
                    .transactionDate(existing.getTransactionDate())
                    .postingDate(existing.getPostingDate())
                    .description(existing.getDescription())
                    .amount(existing.getAmount())
                    .flow(existing.getFlow())
                    .origin(TransactionOrigin.MANUAL)
                    .externalReference(existing.getExternalReference())
                    .notes(existing.getNotes())
                    .category(existing.getCategory())
                    .createdAt(now)
                    .updatedAt(now);
                existing.getTags().forEach(financialTransaction::addTags);

                financialTransaction = financialTransactionRepository.save(financialTransaction);
                existing.setFinancialTransaction(financialTransaction);
                existing.setStatus(TransactionCandidateStatus.POSTED);
                existing.setValidationStatus(TransactionCandidateValidationStatus.VALID);
                existing.setPostedAt(now);
                existing.setUpdatedAt(now);
                return existing;
            })
            .map(transactionCandidateRepository::save)
            .map(transactionCandidateMapper::toDto);
    }

    /**
     * Preview TransactionRule evaluation for an editable manual transaction candidate.
     *
     * @param id the candidate id.
     * @return the transient rule evaluation preview.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionCandidateRulePreviewResponseDTO> previewRules(Long id) {
        LOG.debug("Request to preview TransactionRule evaluation for TransactionCandidate : {}", id);
        return findAccessibleEntity(id).map(candidate -> {
            rejectManualRuleReviewCommand(candidate);
            TransactionRuleEvaluationResult evaluation = evaluateRules(candidate);
            return toRulePreviewResponse(candidate, evaluation);
        });
    }

    /**
     * Apply TransactionRule suggestions to an editable manual transaction candidate using FILL_EMPTY_ONLY semantics.
     *
     * @param id the candidate id.
     * @return the updated candidate plus transient rule evaluation metadata.
     */
    public Optional<TransactionCandidateRuleApplyResponseDTO> applyRules(Long id) {
        LOG.debug("Request to apply TransactionRule suggestions to TransactionCandidate : {}", id);
        return findAccessibleEntity(id).map(candidate -> {
            rejectManualRuleReviewCommand(candidate);
            TransactionRuleEvaluationResult evaluation = evaluateRules(candidate);
            boolean hadManualSelections = candidate.getCategory() != null || !candidate.getTags().isEmpty();
            boolean categoryApplied = applySuggestedCategory(candidate, evaluation);
            List<Long> tagIdsApplied = applySuggestedTags(candidate, evaluation);

            if (hadManualSelections) {
                candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
            } else if (categoryApplied || !tagIdsApplied.isEmpty() || evaluation.hasSuggestions()) {
                candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.SUGGESTED);
            } else {
                candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE);
            }

            candidate.setUpdatedAt(Instant.now());
            validateCandidate(candidate, candidate);
            TransactionCandidate saved = transactionCandidateRepository.save(candidate);

            TransactionCandidateRuleApplyResponseDTO response = new TransactionCandidateRuleApplyResponseDTO();
            response.setCandidate(transactionCandidateMapper.toDto(saved));
            response.setEvaluation(toRulePreviewResponse(saved, evaluation));
            response.setCategoryApplied(categoryApplied);
            response.setTagIdsApplied(tagIdsApplied);
            return response;
        });
    }

    /**
     * Save a transactionCandidate.
     *
     * @param transactionCandidateDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionCandidateDTO save(TransactionCandidateDTO transactionCandidateDTO) {
        LOG.debug("Request to save TransactionCandidate : {}", transactionCandidateDTO);
        rejectGenericCreateControlledFields(transactionCandidateDTO);
        rejectClientControlledFieldsOnCreate(transactionCandidateDTO);
        TransactionCandidate transactionCandidate = transactionCandidateMapper.toEntity(transactionCandidateDTO);
        transactionCandidate.setUser(currentUserService.getCurrentUser());
        applyDefaults(transactionCandidate);
        applyRelationships(transactionCandidate, transactionCandidateDTO);
        normalizeFields(transactionCandidate);
        deriveAmountAndFlow(transactionCandidate);
        validateCandidate(transactionCandidate, null);
        Instant now = Instant.now();
        transactionCandidate.setCreatedAt(now);
        transactionCandidate.setUpdatedAt(now);
        applyLifecycleTimestamps(transactionCandidate, now);
        transactionCandidate = transactionCandidateRepository.save(transactionCandidate);
        return transactionCandidateMapper.toDto(transactionCandidate);
    }

    /**
     * Update a transactionCandidate.
     *
     * @param transactionCandidateDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionCandidateDTO update(TransactionCandidateDTO transactionCandidateDTO) {
        return update(transactionCandidateDTO, null);
    }

    public TransactionCandidateDTO update(TransactionCandidateDTO transactionCandidateDTO, JsonNode updateNode) {
        LOG.debug("Request to update TransactionCandidate : {}", transactionCandidateDTO.getId());
        TransactionCandidate existing = findAccessibleEntity(transactionCandidateDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Entity not found")
        );
        rejectFinalMutation(existing);
        rejectSourceChange(existing, transactionCandidateDTO);
        rejectGenericStatusChange(existing, transactionCandidateDTO, updateNode);
        rejectServerTimestampChanges(existing, transactionCandidateDTO, updateNode);
        rejectClientControlledFieldsOnUpdate(transactionCandidateDTO, updateNode);

        TransactionCandidate candidate = transactionCandidateMapper.toEntity(transactionCandidateDTO);
        candidate.setUser(existing.getUser());
        candidate.setCreatedAt(existing.getCreatedAt());
        candidate.setUpdatedAt(Instant.now());
        candidate.setPostedAt(existing.getPostedAt());
        candidate.setCancelledAt(existing.getCancelledAt());
        candidate.setFailedAt(existing.getFailedAt());
        applyDefaults(candidate);
        applyRelationships(candidate, transactionCandidateDTO);
        normalizeFields(candidate);
        deriveAmountAndFlow(candidate);
        validateTransition(existing.getStatus(), candidate.getStatus());
        validateCandidate(candidate, existing);
        applyLifecycleTimestamps(candidate, candidate.getUpdatedAt());
        candidate = transactionCandidateRepository.save(candidate);
        return transactionCandidateMapper.toDto(candidate);
    }

    /**
     * Partially update a transactionCandidate.
     *
     * @param transactionCandidateDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<TransactionCandidateDTO> partialUpdate(TransactionCandidateDTO transactionCandidateDTO) {
        return partialUpdate(transactionCandidateDTO, null);
    }

    public Optional<TransactionCandidateDTO> partialUpdate(TransactionCandidateDTO transactionCandidateDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update TransactionCandidate : {}", transactionCandidateDTO.getId());
        return findAccessibleEntity(transactionCandidateDTO.getId())
            .map(existing -> {
                rejectFinalMutation(existing);
                rejectSourceChange(existing, transactionCandidateDTO);
                rejectGenericStatusChange(existing, transactionCandidateDTO, patchNode);
                rejectServerTimestampChanges(existing, transactionCandidateDTO, patchNode);
                rejectClientControlledFieldsOnUpdate(transactionCandidateDTO, patchNode);

                TransactionCandidateStatus previousStatus = existing.getStatus();
                transactionCandidateMapper.partialUpdate(existing, transactionCandidateDTO);
                applyRelationshipPatches(existing, transactionCandidateDTO, patchNode);
                normalizeFields(existing);
                deriveAmountAndFlow(existing);
                validateTransition(previousStatus, existing.getStatus());
                validateCandidate(existing, existing);
                existing.setUpdatedAt(Instant.now());
                applyLifecycleTimestamps(existing, existing.getUpdatedAt());
                return existing;
            })
            .map(transactionCandidateRepository::save)
            .map(transactionCandidateMapper::toDto);
    }

    /**
     * Get all the transactionCandidates with eager load of relationships for the current owner.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<TransactionCandidateDTO> findAll(Pageable pageable) {
        return transactionCandidateRepository
            .findAllWithRelationshipsByUserLogin(currentUserService.getCurrentUserLogin(), pageable)
            .map(transactionCandidateMapper::toDto);
    }

    /**
     * Get one transactionCandidate by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionCandidateDTO> findOne(Long id) {
        LOG.debug("Request to get TransactionCandidate : {}", id);
        return findAccessibleEntity(id).map(transactionCandidateMapper::toDto);
    }

    /**
     * Count transaction candidates visible to the current owner.
     *
     * @return visible candidate count.
     */
    @Transactional(readOnly = true)
    public long count() {
        return transactionCandidateRepository.countByUserLogin(currentUserService.getCurrentUserLogin());
    }

    /**
     * Returns whether the current user can access the transaction candidate.
     *
     * @param id the id of the entity.
     * @return true when accessible.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the transactionCandidate by id.
     *
     * @param id the id of the entity.
     * @return true when deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete TransactionCandidate : {}", id);
        Optional<TransactionCandidate> candidate = findAccessibleEntity(id);
        if (candidate.isEmpty()) {
            return false;
        }
        rejectPostedOrCancelledDelete(candidate.get());
        transactionCandidateRepository.deleteTagLinksByTransactionCandidateId(id);
        transactionCandidateRepository.deleteById(id);
        return true;
    }

    private Optional<TransactionCandidate> findAccessibleEntity(Long id) {
        return transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private Optional<TransactionCandidate> findAccessibleEntityForPosting(Long id) {
        return transactionCandidateRepository.findOneByIdAndUserLoginForPosting(id, currentUserService.getCurrentUserLogin());
    }

    private void applyDefaults(TransactionCandidate candidate) {
        if (candidate.getStatus() == null) {
            candidate.setStatus(TransactionCandidateStatus.DRAFT);
        }
        if (candidate.getValidationStatus() == null) {
            candidate.setValidationStatus(TransactionCandidateValidationStatus.UNKNOWN);
        }
        if (candidate.getDescriptionReviewStatus() == null) {
            candidate.setDescriptionReviewStatus(TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED);
        }
        if (candidate.getClassificationReviewStatus() == null) {
            candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED);
        }
    }

    private void applyRelationships(TransactionCandidate candidate, TransactionCandidateDTO dto) {
        String ownerLogin = currentUserService.getCurrentUserLogin();
        candidate.setAccount(resolveOptionalAccount(dto.getAccount(), ownerLogin));
        candidate.setCategory(resolveOptionalCategory(dto.getCategory(), ownerLogin));
        candidate.setTags(resolveTags(dto.getTags(), ownerLogin));
        candidate.setTransactionIngestion(resolveOptionalTransactionIngestion(dto.getTransactionIngestion(), ownerLogin));
        candidate.setIngestionRecord(resolveOptionalIngestionRecord(dto.getIngestionRecord(), ownerLogin));
    }

    private void applyRelationshipPatches(TransactionCandidate candidate, TransactionCandidateDTO dto, JsonNode patchNode) {
        String ownerLogin = currentUserService.getCurrentUserLogin();
        if (fieldPresent(patchNode, "account")) {
            candidate.setAccount(resolveOptionalAccount(dto.getAccount(), ownerLogin));
        }
        if (fieldPresent(patchNode, "category")) {
            candidate.setCategory(resolveOptionalCategory(dto.getCategory(), ownerLogin));
        }
        if (fieldPresent(patchNode, "tags")) {
            candidate.setTags(resolveTags(dto.getTags(), ownerLogin));
        }
        if (fieldPresent(patchNode, "transactionIngestion")) {
            candidate.setTransactionIngestion(resolveOptionalTransactionIngestion(dto.getTransactionIngestion(), ownerLogin));
        }
        if (fieldPresent(patchNode, "ingestionRecord")) {
            candidate.setIngestionRecord(resolveOptionalIngestionRecord(dto.getIngestionRecord(), ownerLogin));
        }
    }

    private FinancialAccount resolveOptionalAccount(FinancialAccountDTO accountDTO, String ownerLogin) {
        if (accountDTO == null) {
            return null;
        }
        if (accountDTO.getId() == null) {
            throw new IllegalArgumentException("Financial account id is required");
        }
        return financialAccountRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(accountDTO.getId(), ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Financial account is not accessible"));
    }

    private Category resolveOptionalCategory(CategoryDTO categoryDTO, String ownerLogin) {
        if (categoryDTO == null) {
            return null;
        }
        if (categoryDTO.getId() == null) {
            throw new IllegalArgumentException("Category id is required");
        }
        return categoryRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(categoryDTO.getId(), ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Category is not accessible"));
    }

    private Set<Tag> resolveTags(Set<TagDTO> tagDTOs, String ownerLogin) {
        Set<Tag> tags = new HashSet<>();
        if (tagDTOs == null || tagDTOs.isEmpty()) {
            return tags;
        }
        for (TagDTO tagDTO : tagDTOs) {
            if (tagDTO == null || tagDTO.getId() == null) {
                throw new IllegalArgumentException("Tag id is required");
            }
            Tag tag = tagRepository
                .findOneWithToOneRelationshipsByIdAndUserLogin(tagDTO.getId(), ownerLogin)
                .orElseThrow(() -> new IllegalArgumentException("Tag is not accessible"));
            tags.add(tag);
        }
        return tags;
    }

    private TransactionIngestion resolveOptionalTransactionIngestion(TransactionIngestionDTO dto, String ownerLogin) {
        if (dto == null) {
            return null;
        }
        if (dto.getId() == null) {
            throw new IllegalArgumentException("Transaction ingestion id is required");
        }
        return transactionIngestionRepository
            .findOneWithEagerRelationshipsByIdAndAccountUserLogin(dto.getId(), ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Transaction ingestion is not accessible"));
    }

    private IngestionRecord resolveOptionalIngestionRecord(IngestionRecordDTO dto, String ownerLogin) {
        if (dto == null) {
            return null;
        }
        if (dto.getId() == null) {
            throw new IllegalArgumentException("Ingestion record id is required");
        }
        return ingestionRecordRepository
            .findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(dto.getId(), ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Ingestion record is not accessible"));
    }

    private void normalizeFields(TransactionCandidate candidate) {
        candidate.setDescription(normalizeOptionalText(candidate.getDescription(), 500, "Description"));
        candidate.setExternalReference(normalizeOptionalText(candidate.getExternalReference(), 150, "External reference"));
        candidate.setNotes(normalizeOptionalText(candidate.getNotes(), 1000, "Notes"));
        candidate.setFailureReason(normalizeOptionalText(candidate.getFailureReason(), 1000, "Failure reason"));
        if (candidate.getSignedAmount() != null) {
            candidate.setSignedAmount(normalizeScale(candidate.getSignedAmount(), "Signed amount"));
        }
    }

    private String normalizeOptionalText(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " cannot exceed " + maxLength + " characters");
        }
        return trimmed;
    }

    private BigDecimal normalizeScale(BigDecimal amount, String fieldName) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(fieldName + " must have at most 2 decimal places");
        }
    }

    private void deriveAmountAndFlow(TransactionCandidate candidate) {
        if (candidate.getSignedAmount() == null) {
            return;
        }
        if (candidate.getSignedAmount().compareTo(BigDecimal.ZERO) == 0) {
            candidate.setAmount(BigDecimal.ZERO.setScale(2));
            candidate.setFlow(null);
            return;
        }
        candidate.setAmount(candidate.getSignedAmount().abs());
        candidate.setFlow(candidate.getSignedAmount().compareTo(BigDecimal.ZERO) > 0 ? TransactionFlow.IN : TransactionFlow.OUT);
    }

    private TransactionRuleEvaluationResult evaluateRules(TransactionCandidate candidate) {
        validateCandidateCanBeEvaluated(candidate);
        return transactionRuleEvaluationService.evaluate(
            new TransactionRuleEvaluationInput(
                candidate.getUser().getLogin(),
                candidate.getDescription(),
                candidate.getAmount(),
                candidate.getFlow(),
                candidate.getExternalReference(),
                TransactionOrigin.MANUAL,
                candidate.getTransactionDate(),
                candidate.getPostingDate(),
                candidate.getAccount().getId(),
                candidate.getCategory() == null ? null : candidate.getCategory().getId(),
                candidate.getCategory() == null ? null : candidate.getCategory().getName(),
                currentTagIds(candidate),
                currentTagNames(candidate)
            )
        );
    }

    private void validateCandidateCanBeEvaluated(TransactionCandidate candidate) {
        if (candidate.getAccount() == null) {
            throw new IllegalArgumentException("Financial account is required for rule preview");
        }
        if (candidate.getDescription() == null || candidate.getDescription().isBlank()) {
            throw new IllegalArgumentException("Description is required for rule preview");
        }
        if (candidate.getAmount() == null || candidate.getAmount().compareTo(BigDecimal.ZERO) <= 0 || candidate.getFlow() == null) {
            throw new IllegalArgumentException("Signed amount is required for rule preview");
        }
    }

    private boolean applySuggestedCategory(TransactionCandidate candidate, TransactionRuleEvaluationResult evaluation) {
        if (
            candidate.getCategory() == null &&
            evaluation.suggestedCategory() != null &&
            !evaluation.suggestedCategory().conflictsWithCurrentValue()
        ) {
            Category suggestedCategory = categoryRepository
                .findOneWithToOneRelationshipsByIdAndUserLogin(evaluation.suggestedCategory().categoryId(), candidate.getUser().getLogin())
                .orElseThrow(() -> new IllegalArgumentException("Suggested category is not accessible"));
            candidate.setCategory(suggestedCategory);
            return true;
        }
        return false;
    }

    private List<Long> applySuggestedTags(TransactionCandidate candidate, TransactionRuleEvaluationResult evaluation) {
        List<Long> tagIdsApplied = new ArrayList<>();
        Set<Long> tagIds = currentTagIds(candidate);
        for (TagSuggestion suggestedTag : evaluation.suggestedTags()) {
            if (suggestedTag.alreadyPresent() || suggestedTag.duplicateOfEarlierSuggestion() || tagIds.contains(suggestedTag.tagId())) {
                continue;
            }
            Tag tag = tagRepository
                .findOneWithToOneRelationshipsByIdAndUserLogin(suggestedTag.tagId(), candidate.getUser().getLogin())
                .orElseThrow(() -> new IllegalArgumentException("Suggested tag is not accessible"));
            candidate.addTags(tag);
            tagIds.add(tag.getId());
            tagIdsApplied.add(tag.getId());
        }
        return tagIdsApplied;
    }

    private Set<Long> currentTagIds(TransactionCandidate candidate) {
        if (candidate.getTags() == null || candidate.getTags().isEmpty()) {
            return new HashSet<>();
        }
        return candidate.getTags().stream().map(Tag::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private Map<Long, String> currentTagNames(TransactionCandidate candidate) {
        if (candidate.getTags() == null || candidate.getTags().isEmpty()) {
            return Map.of();
        }
        Map<Long, String> tagNames = new HashMap<>();
        for (Tag tag : candidate.getTags()) {
            if (tag.getId() != null) {
                tagNames.put(tag.getId(), tag.getName());
            }
        }
        return tagNames;
    }

    private TransactionCandidateRulePreviewResponseDTO toRulePreviewResponse(
        TransactionCandidate candidate,
        TransactionRuleEvaluationResult evaluation
    ) {
        TransactionCandidateRulePreviewResponseDTO response = new TransactionCandidateRulePreviewResponseDTO();
        response.setCandidateId(candidate.getId());
        response.setCandidateUpdatedAt(candidate.getUpdatedAt());
        response.setClassificationReviewStatus(candidate.getClassificationReviewStatus());
        response.setSuggestedCategory(toCategorySuggestionDTO(evaluation.suggestedCategory()));
        response.setSuggestedTags(evaluation.suggestedTags().stream().map(this::toTagSuggestionDTO).toList());
        response.setConflicts(evaluation.conflicts().stream().map(this::toRuleOutputConflictDTO).toList());
        response.setSkippedOutputs(evaluation.skippedOutputs().stream().map(this::toSkippedRuleOutputDTO).toList());
        response.setMatchedRules(evaluation.matchedRules().stream().map(this::toRuleMatchResultDTO).toList());
        response.setHasSuggestions(evaluation.hasSuggestions());
        response.setHasConflicts(evaluation.hasConflicts());
        return response;
    }

    private CategorySuggestionDTO toCategorySuggestionDTO(CategorySuggestion suggestion) {
        if (suggestion == null) {
            return null;
        }
        CategorySuggestionDTO dto = new CategorySuggestionDTO();
        dto.setCategoryId(suggestion.categoryId());
        dto.setCategoryName(suggestion.categoryName());
        dto.setSourceRuleId(suggestion.sourceRuleId());
        dto.setSourceRuleName(suggestion.sourceRuleName());
        dto.setConflictsWithCurrentValue(suggestion.conflictsWithCurrentValue());
        dto.setCurrentCategoryId(suggestion.currentCategoryId());
        dto.setCurrentCategoryName(suggestion.currentCategoryName());
        return dto;
    }

    private TagSuggestionDTO toTagSuggestionDTO(TagSuggestion suggestion) {
        TagSuggestionDTO dto = new TagSuggestionDTO();
        dto.setTagId(suggestion.tagId());
        dto.setTagName(suggestion.tagName());
        dto.setSourceRuleId(suggestion.sourceRuleId());
        dto.setSourceRuleName(suggestion.sourceRuleName());
        dto.setAlreadyPresent(suggestion.alreadyPresent());
        dto.setDuplicateOfEarlierSuggestion(suggestion.duplicateOfEarlierSuggestion());
        return dto;
    }

    private RuleOutputConflictDTO toRuleOutputConflictDTO(RuleOutputConflict conflict) {
        RuleOutputConflictDTO dto = new RuleOutputConflictDTO();
        dto.setField(conflict.field());
        dto.setCurrentValueId(conflict.currentValueId());
        dto.setCurrentValueLabel(conflict.currentValueLabel());
        dto.setSuggestedValueId(conflict.suggestedValueId());
        dto.setSuggestedValueLabel(conflict.suggestedValueLabel());
        dto.setSourceRuleId(conflict.sourceRuleId());
        dto.setSourceRuleName(conflict.sourceRuleName());
        dto.setReason(conflict.reason());
        return dto;
    }

    private SkippedRuleOutputDTO toSkippedRuleOutputDTO(SkippedRuleOutput skippedOutput) {
        SkippedRuleOutputDTO dto = new SkippedRuleOutputDTO();
        dto.setField(skippedOutput.field());
        dto.setSourceRuleId(skippedOutput.sourceRuleId());
        dto.setSourceRuleName(skippedOutput.sourceRuleName());
        dto.setReason(skippedOutput.reason());
        dto.setValueId(skippedOutput.valueId());
        dto.setValueLabel(skippedOutput.valueLabel());
        return dto;
    }

    private RuleMatchResultDTO toRuleMatchResultDTO(RuleMatchResult match) {
        RuleMatchResultDTO dto = new RuleMatchResultDTO();
        dto.setRuleId(match.ruleId());
        dto.setRuleName(match.ruleName());
        dto.setPriority(match.priority());
        dto.setConditionLogic(match.conditionLogic());
        dto.setProposedOutputs(match.proposedOutputs());
        return dto;
    }

    private void validateCandidate(TransactionCandidate candidate, TransactionCandidate existing) {
        if (candidate.getSource() == null) {
            throw new IllegalArgumentException("Source is required");
        }
        if (candidate.getStatus() == null) {
            throw new IllegalArgumentException("Status is required");
        }
        validateSameOwner(candidate);
        validateCurrency(candidate);
        validateAmountAndFlow(candidate);
        validateCategoryCompatibility(candidate.getCategory(), candidate.getFlow());
        validateFinalState(candidate);
        if (candidate.getStatus() == TransactionCandidateStatus.READY_TO_POST) {
            validateReadyToPost(candidate);
        }
        if (candidate.getStatus() == TransactionCandidateStatus.POSTED) {
            validatePosted(candidate);
        }
    }

    private void validateSameOwner(TransactionCandidate candidate) {
        Long ownerId = candidate.getUser() == null ? null : candidate.getUser().getId();
        if (ownerId == null) {
            throw new IllegalArgumentException("Candidate owner is required");
        }
        if (candidate.getAccount() != null && !Objects.equals(ownerId, candidate.getAccount().getUser().getId())) {
            throw new IllegalArgumentException("Financial account must belong to candidate owner");
        }
        if (candidate.getCategory() != null && !Objects.equals(ownerId, candidate.getCategory().getUser().getId())) {
            throw new IllegalArgumentException("Category must belong to candidate owner");
        }
        for (Tag tag : candidate.getTags()) {
            if (!Objects.equals(ownerId, tag.getUser().getId())) {
                throw new IllegalArgumentException("Tags must belong to candidate owner");
            }
        }
        if (
            candidate.getTransactionIngestion() != null &&
            !Objects.equals(ownerId, candidate.getTransactionIngestion().getAccount().getUser().getId())
        ) {
            throw new IllegalArgumentException("Transaction ingestion must belong to candidate owner");
        }
        if (
            candidate.getIngestionRecord() != null &&
            !Objects.equals(ownerId, candidate.getIngestionRecord().getTransactionIngestion().getAccount().getUser().getId())
        ) {
            throw new IllegalArgumentException("Ingestion record must belong to candidate owner");
        }
        if (
            candidate.getFinancialTransaction() != null &&
            !Objects.equals(ownerId, candidate.getFinancialTransaction().getAccount().getUser().getId())
        ) {
            throw new IllegalArgumentException("Financial transaction must belong to candidate owner");
        }
    }

    private void validateCurrency(TransactionCandidate candidate) {
        if (candidate.getAccount() == null) {
            return;
        }
        CurrencyCode accountCurrency = candidate.getAccount().getCurrency();
        if (candidate.getCurrencySnapshot() == null) {
            candidate.setCurrencySnapshot(accountCurrency);
            return;
        }
        if (candidate.getCurrencySnapshot() != accountCurrency) {
            throw new IllegalArgumentException("Currency snapshot must match the selected account currency");
        }
    }

    private void validateAmountAndFlow(TransactionCandidate candidate) {
        if (candidate.getAmount() == null && candidate.getFlow() != null) {
            throw new IllegalArgumentException("Amount is required when flow is set");
        }
        if (candidate.getAmount() != null) {
            if (candidate.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                if (candidate.getStatus() == TransactionCandidateStatus.READY_TO_POST) {
                    throw new IllegalArgumentException("Transaction candidate amount must be greater than zero");
                }
                return;
            }
            if (candidate.getFlow() == null) {
                throw new IllegalArgumentException("Flow is required when amount is set");
            }
        }
    }

    private void validateCategoryCompatibility(Category category, TransactionFlow flow) {
        if (category == null || flow == null) {
            return;
        }
        if (category.getCategoryType() == CategoryType.EXPENSE && flow != TransactionFlow.OUT) {
            throw new IllegalArgumentException("Expense categories require OUT flow");
        }
        if (category.getCategoryType() == CategoryType.INCOME && flow != TransactionFlow.IN) {
            throw new IllegalArgumentException("Income categories require IN flow");
        }
    }

    private void validateReadyToPost(TransactionCandidate candidate) {
        if (candidate.getAccount() == null) {
            throw new IllegalArgumentException("Financial account is required before posting");
        }
        if (candidate.getTransactionDate() == null) {
            throw new IllegalArgumentException("Transaction date is required before posting");
        }
        if (candidate.getDescription() == null) {
            throw new IllegalArgumentException("Description is required before posting");
        }
        if (candidate.getAmount() == null || candidate.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction candidate amount must be greater than zero");
        }
        if (candidate.getFlow() == null) {
            throw new IllegalArgumentException("Flow is required before posting");
        }
        if (candidate.getCurrencySnapshot() == null) {
            throw new IllegalArgumentException("Currency snapshot is required before posting");
        }
        candidate.setValidationStatus(TransactionCandidateValidationStatus.VALID);
    }

    private void validatePosted(TransactionCandidate candidate) {
        if (candidate.getFinancialTransaction() == null || candidate.getFinancialTransaction().getId() == null) {
            throw new IllegalArgumentException("Posted candidates require a financial transaction link");
        }
    }

    private void validateFinalState(TransactionCandidate candidate) {
        if (candidate.getStatus() == TransactionCandidateStatus.FAILED && candidate.getFailureReason() == null) {
            throw new IllegalArgumentException("Failure reason is required for failed candidates");
        }
    }

    private void validateTransition(TransactionCandidateStatus previousStatus, TransactionCandidateStatus nextStatus) {
        if (previousStatus == null || nextStatus == null || previousStatus == nextStatus) {
            return;
        }
        if (previousStatus == TransactionCandidateStatus.POSTED || previousStatus == TransactionCandidateStatus.CANCELLED) {
            throw new IllegalArgumentException("Final transaction candidates cannot be changed");
        }
        if (previousStatus == TransactionCandidateStatus.FAILED && nextStatus == TransactionCandidateStatus.POSTED) {
            throw new IllegalArgumentException("Failed candidates must be reviewed before posting");
        }
    }

    private void rejectFinalMutation(TransactionCandidate existing) {
        if (existing.getStatus() == TransactionCandidateStatus.POSTED || existing.getStatus() == TransactionCandidateStatus.CANCELLED) {
            throw new IllegalArgumentException("Final transaction candidates cannot be changed");
        }
    }

    private void rejectManualCommandMutation(TransactionCandidate existing) {
        if (existing.getSource() != TransactionCandidateSource.MANUAL) {
            throw new IllegalArgumentException("Manual draft command only supports manual transaction candidates");
        }
        rejectFinalMutation(existing);
    }

    private void rejectManualRuleReviewCommand(TransactionCandidate existing) {
        if (existing.getSource() != TransactionCandidateSource.MANUAL) {
            throw new IllegalArgumentException("Rule preview only supports manual transaction candidates");
        }
        if (
            existing.getStatus() == TransactionCandidateStatus.POSTED ||
            existing.getStatus() == TransactionCandidateStatus.CANCELLED ||
            existing.getStatus() == TransactionCandidateStatus.FAILED
        ) {
            throw new IllegalArgumentException("Final transaction candidates cannot use rule preview");
        }
    }

    private void rejectManualPostMutation(TransactionCandidate existing) {
        if (existing.getSource() != TransactionCandidateSource.MANUAL) {
            throw new IllegalArgumentException("Manual post command only supports manual transaction candidates");
        }
        if (existing.getTransactionIngestion() != null || existing.getIngestionRecord() != null) {
            throw new IllegalArgumentException("Manual candidates cannot be linked to ingestion records");
        }
        if (existing.getStatus() == TransactionCandidateStatus.CANCELLED) {
            throw new IllegalArgumentException("Cancelled transaction candidates cannot be posted");
        }
        if (existing.getStatus() == TransactionCandidateStatus.FAILED) {
            throw new IllegalArgumentException("Failed candidates must be reviewed before posting");
        }
    }

    private void rejectStaleReviewStateForPosting(TransactionCandidate existing) {
        if (existing.getValidationStatus() == TransactionCandidateValidationStatus.INVALID) {
            throw new IllegalArgumentException("Invalid transaction candidates cannot be posted");
        }
        if (existing.getValidationStatus() == TransactionCandidateValidationStatus.STALE) {
            throw new IllegalArgumentException("Stale transaction candidates must be reviewed before posting");
        }
        if (existing.getDescriptionReviewStatus() == TransactionCandidateDescriptionReviewStatus.STALE) {
            throw new IllegalArgumentException("Stale description review must be refreshed before posting");
        }
        if (existing.getClassificationReviewStatus() == TransactionCandidateClassificationReviewStatus.STALE) {
            throw new IllegalArgumentException("Stale classification review must be refreshed before posting");
        }
    }

    private void rejectSourceChange(TransactionCandidate existing, TransactionCandidateDTO dto) {
        if (dto.getSource() != null && existing.getSource() != dto.getSource()) {
            throw new IllegalArgumentException("Source cannot be changed");
        }
    }

    private void rejectServerTimestampChanges(TransactionCandidate existing, TransactionCandidateDTO dto, JsonNode node) {
        if (fieldPresent(node, "createdAt") && !Objects.equals(existing.getCreatedAt(), dto.getCreatedAt())) {
            throw new IllegalArgumentException("Created at cannot be changed");
        }
        if (fieldPresent(node, "updatedAt") && !Objects.equals(existing.getUpdatedAt(), dto.getUpdatedAt())) {
            throw new IllegalArgumentException("Updated at cannot be changed");
        }
        if (fieldPresent(node, "postedAt") && !Objects.equals(existing.getPostedAt(), dto.getPostedAt())) {
            throw new IllegalArgumentException("Posted at cannot be changed");
        }
        if (fieldPresent(node, "cancelledAt") && !Objects.equals(existing.getCancelledAt(), dto.getCancelledAt())) {
            throw new IllegalArgumentException("Cancelled at cannot be changed");
        }
        if (fieldPresent(node, "failedAt") && !Objects.equals(existing.getFailedAt(), dto.getFailedAt())) {
            throw new IllegalArgumentException("Failed at cannot be changed");
        }
    }

    private void rejectClientControlledFieldsOnCreate(TransactionCandidateDTO dto) {
        rejectCreateField(dto.getValidationStatus(), "Validation status is server-controlled");
        rejectCreateField(dto.getDescriptionReviewStatus(), "Description review status is server-controlled");
        rejectCreateField(dto.getClassificationReviewStatus(), "Classification review status is server-controlled");
        rejectCreateField(dto.getAmount(), "Amount is derived from signed amount");
        rejectCreateField(dto.getFlow(), "Flow is derived from signed amount");
        rejectCreateField(dto.getCreatedAt(), "Created at is server-controlled");
        rejectCreateField(dto.getUpdatedAt(), "Updated at is server-controlled");
        rejectCreateField(dto.getPostedAt(), "Posted at is server-controlled");
        rejectCreateField(dto.getCancelledAt(), "Cancelled at is server-controlled");
        rejectCreateField(dto.getFailedAt(), "Failed at is server-controlled");
        rejectCreateField(dto.getFinancialTransaction(), "Financial transaction link is server-controlled");
    }

    private void rejectGenericCreateControlledFields(TransactionCandidateDTO dto) {
        if (dto.getSource() != null && dto.getSource() != TransactionCandidateSource.MANUAL) {
            throw new IllegalArgumentException("Generic TransactionCandidate create only supports manual draft candidates");
        }
        rejectCreateField(dto.getStatus(), "Status is controlled by command endpoints");
    }

    private void rejectManualCommandControlledFieldsOnCreate(TransactionCandidateDTO dto) {
        if (dto.getSource() != null && dto.getSource() != TransactionCandidateSource.MANUAL) {
            throw new IllegalArgumentException("Manual draft source must be MANUAL");
        }
        rejectCreateField(dto.getStatus(), "Status is server-controlled for manual drafts");
        rejectCreateField(dto.getTransactionIngestion(), "Manual drafts cannot be linked to transaction ingestion");
        rejectCreateField(dto.getIngestionRecord(), "Manual drafts cannot be linked to ingestion records");
        rejectClientControlledFieldsOnCreate(dto);
    }

    private void rejectManualCommandControlledFieldsOnUpdate(TransactionCandidateDTO dto, JsonNode node) {
        rejectUpdateField(dto.getSource(), node, "source", "Source cannot be changed");
        rejectUpdateField(dto.getStatus(), node, "status", "Status is server-controlled for manual drafts");
        rejectClientControlledFieldsOnUpdate(dto, node);
        rejectServerTimestampFieldsPresent(node);
        if (fieldPresent(node, "transactionIngestion")) {
            throw new IllegalArgumentException("Manual drafts cannot be linked to transaction ingestion");
        }
        if (fieldPresent(node, "ingestionRecord")) {
            throw new IllegalArgumentException("Manual drafts cannot be linked to ingestion records");
        }
    }

    private void rejectClientControlledFieldsOnUpdate(TransactionCandidateDTO dto, JsonNode node) {
        rejectUpdateField(dto.getValidationStatus(), node, "validationStatus", "Validation status is server-controlled");
        rejectUpdateField(
            dto.getDescriptionReviewStatus(),
            node,
            "descriptionReviewStatus",
            "Description review status is server-controlled"
        );
        rejectUpdateField(
            dto.getClassificationReviewStatus(),
            node,
            "classificationReviewStatus",
            "Classification review status is server-controlled"
        );
        rejectUpdateField(dto.getAmount(), node, "amount", "Amount is derived from signed amount");
        rejectUpdateField(dto.getFlow(), node, "flow", "Flow is derived from signed amount");
        if ((node == null && dto.getFinancialTransaction() != null) || (node != null && node.has("financialTransaction"))) {
            throw new IllegalArgumentException("Financial transaction link is server-controlled");
        }
    }

    private void rejectGenericStatusChange(TransactionCandidate existing, TransactionCandidateDTO dto, JsonNode node) {
        if (node != null && node.has("status")) {
            throw new IllegalArgumentException("Status is controlled by command endpoints");
        }
        if (node == null && dto.getStatus() != null && dto.getStatus() != existing.getStatus()) {
            throw new IllegalArgumentException("Status is controlled by command endpoints");
        }
    }

    private void rejectPostedOrCancelledDelete(TransactionCandidate candidate) {
        if (candidate.getStatus() == TransactionCandidateStatus.POSTED || candidate.getStatus() == TransactionCandidateStatus.CANCELLED) {
            throw new IllegalArgumentException("Posted or cancelled transaction candidates cannot be deleted");
        }
    }

    private void rejectCreateField(Object value, String message) {
        if (value != null) {
            throw new IllegalArgumentException(message);
        }
    }

    private void rejectUpdateField(Object value, JsonNode node, String fieldName, String message) {
        if ((node == null && value != null) || (node != null && node.has(fieldName))) {
            throw new IllegalArgumentException(message);
        }
    }

    private void rejectServerTimestampFieldsPresent(JsonNode node) {
        if (fieldPresent(node, "createdAt")) {
            throw new IllegalArgumentException("Created at is server-controlled");
        }
        if (fieldPresent(node, "updatedAt")) {
            throw new IllegalArgumentException("Updated at is server-controlled");
        }
        if (fieldPresent(node, "postedAt")) {
            throw new IllegalArgumentException("Posted at is server-controlled");
        }
        if (fieldPresent(node, "cancelledAt")) {
            throw new IllegalArgumentException("Cancelled at is server-controlled");
        }
        if (fieldPresent(node, "failedAt")) {
            throw new IllegalArgumentException("Failed at is server-controlled");
        }
    }

    private void updateClassificationReviewStatusAfterManualPatch(
        TransactionCandidate candidate,
        JsonNode patchNode,
        TransactionCandidateClassificationReviewStatus previousStatus
    ) {
        if (fieldPresent(patchNode, "category") || fieldPresent(patchNode, "tags")) {
            candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
            return;
        }
        if (isFreshClassificationStatus(previousStatus) && containsRuleInputField(patchNode)) {
            candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.STALE);
        }
    }

    private boolean isFreshClassificationStatus(TransactionCandidateClassificationReviewStatus status) {
        return (
            status == TransactionCandidateClassificationReviewStatus.SUGGESTED ||
            status == TransactionCandidateClassificationReviewStatus.USER_SELECTED ||
            status == TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE
        );
    }

    private boolean containsRuleInputField(JsonNode patchNode) {
        return (
            fieldPresent(patchNode, "account") ||
            fieldPresent(patchNode, "description") ||
            fieldPresent(patchNode, "signedAmount") ||
            fieldPresent(patchNode, "amount") ||
            fieldPresent(patchNode, "flow") ||
            fieldPresent(patchNode, "transactionDate") ||
            fieldPresent(patchNode, "postingDate") ||
            fieldPresent(patchNode, "externalReference")
        );
    }

    private void recalculateManualDraftStatus(TransactionCandidate candidate) {
        if (candidate.getAccount() != null && candidate.getCurrencySnapshot() == null) {
            candidate.setCurrencySnapshot(candidate.getAccount().getCurrency());
        }
        if (isReadyToPostComplete(candidate)) {
            candidate.setStatus(TransactionCandidateStatus.READY_TO_POST);
            candidate.setValidationStatus(TransactionCandidateValidationStatus.VALID);
        } else {
            candidate.setStatus(TransactionCandidateStatus.DRAFT);
            candidate.setValidationStatus(TransactionCandidateValidationStatus.UNKNOWN);
        }
    }

    private boolean isReadyToPostComplete(TransactionCandidate candidate) {
        return (
            candidate.getAccount() != null &&
            candidate.getTransactionDate() != null &&
            candidate.getDescription() != null &&
            candidate.getAmount() != null &&
            candidate.getAmount().compareTo(BigDecimal.ZERO) > 0 &&
            candidate.getFlow() != null &&
            candidate.getCurrencySnapshot() != null
        );
    }

    private void applyLifecycleTimestamps(TransactionCandidate candidate, Instant now) {
        if (candidate.getStatus() == TransactionCandidateStatus.POSTED && candidate.getPostedAt() == null) {
            candidate.setPostedAt(now);
        }
        if (candidate.getStatus() == TransactionCandidateStatus.CANCELLED && candidate.getCancelledAt() == null) {
            candidate.setCancelledAt(now);
        }
        if (candidate.getStatus() == TransactionCandidateStatus.FAILED && candidate.getFailedAt() == null) {
            candidate.setFailedAt(now);
        }
    }

    private boolean fieldPresent(JsonNode node, String fieldName) {
        return node == null || node.has(fieldName);
    }
}
