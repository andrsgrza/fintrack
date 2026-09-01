package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateDescriptionReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateValidationStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.IngestionRecordDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionCandidateDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.TransactionCandidateMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    private final CategoryRepository categoryRepository;

    private final TagRepository tagRepository;

    private final TransactionIngestionRepository transactionIngestionRepository;

    private final IngestionRecordRepository ingestionRecordRepository;

    public TransactionCandidateService(
        TransactionCandidateRepository transactionCandidateRepository,
        TransactionCandidateMapper transactionCandidateMapper,
        CurrentUserService currentUserService,
        FinancialAccountRepository financialAccountRepository,
        CategoryRepository categoryRepository,
        TagRepository tagRepository,
        TransactionIngestionRepository transactionIngestionRepository,
        IngestionRecordRepository ingestionRecordRepository
    ) {
        this.transactionCandidateRepository = transactionCandidateRepository;
        this.transactionCandidateMapper = transactionCandidateMapper;
        this.currentUserService = currentUserService;
        this.financialAccountRepository = financialAccountRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.ingestionRecordRepository = ingestionRecordRepository;
    }

    /**
     * Save a transactionCandidate.
     *
     * @param transactionCandidateDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionCandidateDTO save(TransactionCandidateDTO transactionCandidateDTO) {
        LOG.debug("Request to save TransactionCandidate : {}", transactionCandidateDTO);
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
        transactionCandidateRepository.deleteTagLinksByTransactionCandidateId(id);
        transactionCandidateRepository.deleteById(id);
        return true;
    }

    private Optional<TransactionCandidate> findAccessibleEntity(Long id) {
        return transactionCandidateRepository.findOneWithRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
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
