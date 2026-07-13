package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.ApiIngestionRepository;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.TransactionIngestionMapper;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.TransactionIngestion}.
 */
@Service
@Transactional
public class TransactionIngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionIngestionService.class);

    private final TransactionIngestionRepository transactionIngestionRepository;

    private final TransactionIngestionMapper transactionIngestionMapper;

    private final CurrentUserService currentUserService;

    private final FinancialAccountService financialAccountService;

    private final FileIngestionRepository fileIngestionRepository;

    private final ApiIngestionRepository apiIngestionRepository;

    private final FinancialTransactionRepository financialTransactionRepository;

    private final InternalTransferRepository internalTransferRepository;

    private final IngestionRecordRepository ingestionRecordRepository;

    public TransactionIngestionService(
        TransactionIngestionRepository transactionIngestionRepository,
        TransactionIngestionMapper transactionIngestionMapper,
        CurrentUserService currentUserService,
        FinancialAccountService financialAccountService,
        FileIngestionRepository fileIngestionRepository,
        ApiIngestionRepository apiIngestionRepository,
        FinancialTransactionRepository financialTransactionRepository,
        InternalTransferRepository internalTransferRepository,
        IngestionRecordRepository ingestionRecordRepository
    ) {
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.transactionIngestionMapper = transactionIngestionMapper;
        this.currentUserService = currentUserService;
        this.financialAccountService = financialAccountService;
        this.fileIngestionRepository = fileIngestionRepository;
        this.apiIngestionRepository = apiIngestionRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.internalTransferRepository = internalTransferRepository;
        this.ingestionRecordRepository = ingestionRecordRepository;
    }

    /**
     * Save a transactionIngestion.
     *
     * @param transactionIngestionDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionIngestionDTO save(TransactionIngestionDTO transactionIngestionDTO) {
        LOG.debug("Request to save TransactionIngestion : {}", transactionIngestionDTO);
        if (transactionIngestionDTO.getIngestionType() == null) {
            throw new IllegalArgumentException("Ingestion type is required");
        }
        TransactionIngestion transactionIngestion = transactionIngestionMapper.toEntity(transactionIngestionDTO);
        transactionIngestion.setIngestionType(transactionIngestionDTO.getIngestionType());
        transactionIngestion.setSourceLabel(normalizeOptionalString(transactionIngestionDTO.getSourceLabel(), "Source label", 100));
        transactionIngestion.setAccount(resolveAccountForCreate(transactionIngestionDTO.getAccount()));
        applyCreateDefaults(transactionIngestion);
        validateFinalState(transactionIngestion);
        transactionIngestion = transactionIngestionRepository.save(transactionIngestion);
        return transactionIngestionMapper.toDto(transactionIngestion);
    }

    /**
     * Update a transactionIngestion.
     *
     * @param transactionIngestionDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionIngestionDTO update(TransactionIngestionDTO transactionIngestionDTO) {
        LOG.debug("Request to update TransactionIngestion : {}", transactionIngestionDTO);
        TransactionIngestion existingTransactionIngestion = findAccessibleEntity(transactionIngestionDTO.getId()).orElseThrow();
        validatePutImmutableFields(existingTransactionIngestion, transactionIngestionDTO);
        applyMergedFields(existingTransactionIngestion, transactionIngestionDTO, null);
        validateFinalState(existingTransactionIngestion);
        existingTransactionIngestion = transactionIngestionRepository.save(existingTransactionIngestion);
        return transactionIngestionMapper.toDto(existingTransactionIngestion);
    }

    /**
     * Partially update a transactionIngestion.
     *
     * @param transactionIngestionDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<TransactionIngestionDTO> partialUpdate(TransactionIngestionDTO transactionIngestionDTO) {
        return partialUpdate(transactionIngestionDTO, null);
    }

    /**
     * Partially update a transactionIngestion, applying immutable field changes only when present in the patch body.
     *
     * @param transactionIngestionDTO the entity to update partially.
     * @param patchNode the raw patch payload.
     * @return the persisted entity.
     */
    public Optional<TransactionIngestionDTO> partialUpdate(TransactionIngestionDTO transactionIngestionDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update TransactionIngestion : {}", transactionIngestionDTO);

        return findAccessibleEntity(transactionIngestionDTO.getId())
            .map(existingTransactionIngestion -> {
                validatePatchImmutableFields(existingTransactionIngestion, transactionIngestionDTO, patchNode);
                applyMergedFields(existingTransactionIngestion, transactionIngestionDTO, patchNode);
                validateFinalState(existingTransactionIngestion);
                return existingTransactionIngestion;
            })
            .map(transactionIngestionRepository::save)
            .map(transactionIngestionMapper::toDto);
    }

    /**
     * Get all the transactionIngestions with eager load of relationships.
     *
     * @return the list of entities.
     */
    public Page<TransactionIngestionDTO> findAllWithEagerRelationships(Pageable pageable) {
        if (currentUserService.isAdmin()) {
            return transactionIngestionRepository.findAllWithEagerRelationships(pageable).map(transactionIngestionMapper::toDto);
        }
        throw new UnsupportedOperationException("Paged access is only supported for admin users");
    }

    /**
     *  Get all the transactionIngestions where FileIngestion is {@code null}.
     *  @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<TransactionIngestionDTO> findAllWhereFileIngestionIsNull() {
        LOG.debug("Request to get all transactionIngestions where FileIngestion is null");
        return findAccessibleEntities()
            .stream()
            .filter(transactionIngestion -> transactionIngestion.getIngestionType() == IngestionType.FILE)
            .filter(transactionIngestion -> transactionIngestion.getFileIngestion() == null)
            .map(transactionIngestionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     *  Get all the transactionIngestions where ApiIngestion is {@code null}.
     *  @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<TransactionIngestionDTO> findAllWhereApiIngestionIsNull() {
        LOG.debug("Request to get all transactionIngestions where ApiIngestion is null");
        return findAccessibleEntities()
            .stream()
            .filter(transactionIngestion -> transactionIngestion.getIngestionType() == IngestionType.API)
            .filter(transactionIngestion -> transactionIngestion.getApiIngestion() == null)
            .map(transactionIngestionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one transactionIngestion by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionIngestionDTO> findOne(Long id) {
        LOG.debug("Request to get TransactionIngestion : {}", id);
        return findAccessibleEntity(id).map(transactionIngestionMapper::toDto);
    }

    /**
     * Returns whether the current user can access the transaction ingestion.
     *
     * @param id the id of the entity.
     * @return true when the ingestion exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the transactionIngestion by id.
     *
     * @param id the id of the entity.
     * @return true when the ingestion was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete TransactionIngestion : {}", id);
        Optional<TransactionIngestion> transactionIngestion = findAccessibleEntity(id);
        if (transactionIngestion.isEmpty()) {
            return false;
        }
        fileIngestionRepository.deleteByTransactionIngestionId(id);
        apiIngestionRepository.deleteByTransactionIngestionId(id);
        internalTransferRepository.deleteByTransactionIngestionIdInEitherRole(id);
        ingestionRecordRepository.deleteByTransactionIngestionId(id);
        financialTransactionRepository.deleteTagLinksByTransactionIngestionId(id);
        financialTransactionRepository.deleteByTransactionIngestionId(id);
        transactionIngestionRepository.deleteById(id);
        return true;
    }

    private List<TransactionIngestion> findAccessibleEntities() {
        if (currentUserService.isAdmin()) {
            return StreamSupport.stream(transactionIngestionRepository.findAll().spliterator(), false).toList();
        }
        return transactionIngestionRepository.findAllWithEagerRelationshipsByAccountUserLogin(currentUserService.getCurrentUserLogin());
    }

    private Optional<TransactionIngestion> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return transactionIngestionRepository.findOneWithEagerRelationships(id);
        }
        return transactionIngestionRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(
            id,
            currentUserService.getCurrentUserLogin()
        );
    }

    private FinancialAccount resolveAccountForCreate(FinancialAccountDTO accountDTO) {
        if (accountDTO == null || accountDTO.getId() == null) {
            throw new IllegalArgumentException("Account is required");
        }
        return financialAccountService
            .findAccessibleAccountEntity(accountDTO.getId())
            .orElseThrow(() -> new IllegalArgumentException("Account is not accessible"));
    }

    private void applyCreateDefaults(TransactionIngestion transactionIngestion) {
        Instant now = Instant.now();
        transactionIngestion.setCreatedAt(now);
        transactionIngestion.setStartedAt(now);
        transactionIngestion.setStatus(IngestionStatus.PENDING);
        transactionIngestion.setRecordsReceived(0);
        transactionIngestion.setRecordsCreated(0);
        transactionIngestion.setRecordsSkipped(0);
        transactionIngestion.setRecordsRejected(0);
        transactionIngestion.setCompletedAt(null);
        transactionIngestion.setErrorMessage(null);
    }

    private void applyMergedFields(
        TransactionIngestion transactionIngestion,
        TransactionIngestionDTO transactionIngestionDTO,
        JsonNode patchNode
    ) {
        IngestionStatus previousStatus = transactionIngestion.getStatus();

        if (shouldApply(patchNode, "status")) {
            if (transactionIngestionDTO.getStatus() == null) {
                throw new IllegalArgumentException("Status is required");
            }
            validateStatusTransition(previousStatus, transactionIngestionDTO.getStatus());
            transactionIngestion.setStatus(transactionIngestionDTO.getStatus());
        }
        if (shouldApply(patchNode, "sourceLabel")) {
            transactionIngestion.setSourceLabel(normalizeOptionalString(transactionIngestionDTO.getSourceLabel(), "Source label", 100));
        }
        if (shouldApply(patchNode, "errorMessage")) {
            transactionIngestion.setErrorMessage(normalizeOptionalString(transactionIngestionDTO.getErrorMessage(), "Error message", 2000));
        }
        if (shouldApply(patchNode, "recordsReceived")) {
            transactionIngestion.setRecordsReceived(requireNonNegative(transactionIngestionDTO.getRecordsReceived(), "Records received"));
        }
        if (shouldApply(patchNode, "recordsCreated")) {
            transactionIngestion.setRecordsCreated(requireNonNegative(transactionIngestionDTO.getRecordsCreated(), "Records created"));
        }
        if (shouldApply(patchNode, "recordsSkipped")) {
            transactionIngestion.setRecordsSkipped(requireNonNegative(transactionIngestionDTO.getRecordsSkipped(), "Records skipped"));
        }
        if (shouldApply(patchNode, "recordsRejected")) {
            transactionIngestion.setRecordsRejected(requireNonNegative(transactionIngestionDTO.getRecordsRejected(), "Records rejected"));
        }

        if (isFinalStatus(transactionIngestion.getStatus()) && !isFinalStatus(previousStatus)) {
            transactionIngestion.setCompletedAt(Instant.now());
        } else if (!isFinalStatus(transactionIngestion.getStatus())) {
            transactionIngestion.setCompletedAt(null);
        }
    }

    private void validatePutImmutableFields(
        TransactionIngestion existingTransactionIngestion,
        TransactionIngestionDTO transactionIngestionDTO
    ) {
        rejectAccountChange(existingTransactionIngestion, transactionIngestionDTO.getAccount(), true);
        rejectIngestionTypeChange(existingTransactionIngestion, transactionIngestionDTO.getIngestionType(), true);
        rejectInstantChange(existingTransactionIngestion.getCreatedAt(), transactionIngestionDTO.getCreatedAt(), "Created at", true);
        rejectInstantChange(existingTransactionIngestion.getStartedAt(), transactionIngestionDTO.getStartedAt(), "Started at", true);
        rejectCompletedAtClientChange(existingTransactionIngestion, transactionIngestionDTO.getCompletedAt(), true);
    }

    private void validatePatchImmutableFields(
        TransactionIngestion existingTransactionIngestion,
        TransactionIngestionDTO transactionIngestionDTO,
        JsonNode patchNode
    ) {
        if (patchNode == null) {
            return;
        }
        if (patchNode.has("account")) {
            if (patchNode.get("account").isNull()) {
                throw new IllegalArgumentException("Account cannot be null");
            }
            rejectAccountChange(existingTransactionIngestion, transactionIngestionDTO.getAccount(), true);
        }
        if (patchNode.has("ingestionType")) {
            if (patchNode.get("ingestionType").isNull()) {
                throw new IllegalArgumentException("Ingestion type cannot be null");
            }
            rejectIngestionTypeChange(existingTransactionIngestion, transactionIngestionDTO.getIngestionType(), true);
        }
        if (patchNode.has("createdAt")) {
            if (patchNode.get("createdAt").isNull()) {
                throw new IllegalArgumentException("Created at cannot be changed");
            }
            rejectInstantChange(existingTransactionIngestion.getCreatedAt(), transactionIngestionDTO.getCreatedAt(), "Created at", true);
        }
        if (patchNode.has("startedAt")) {
            if (patchNode.get("startedAt").isNull()) {
                throw new IllegalArgumentException("Started at cannot be changed");
            }
            rejectInstantChange(existingTransactionIngestion.getStartedAt(), transactionIngestionDTO.getStartedAt(), "Started at", true);
        }
        if (patchNode.has("completedAt")) {
            rejectCompletedAtClientChange(existingTransactionIngestion, transactionIngestionDTO.getCompletedAt(), true);
        }
    }

    private void rejectAccountChange(TransactionIngestion existingTransactionIngestion, FinancialAccountDTO accountDTO, boolean required) {
        if (accountDTO == null || accountDTO.getId() == null) {
            if (required) {
                throw new IllegalArgumentException("Account is required");
            }
            return;
        }
        if (!accountDTO.getId().equals(existingTransactionIngestion.getAccount().getId())) {
            throw new IllegalArgumentException("Account cannot be changed");
        }
    }

    private void rejectIngestionTypeChange(
        TransactionIngestion existingTransactionIngestion,
        IngestionType ingestionType,
        boolean required
    ) {
        if (ingestionType == null) {
            if (required) {
                throw new IllegalArgumentException("Ingestion type is required");
            }
            return;
        }
        if (existingTransactionIngestion.getIngestionType() != ingestionType) {
            throw new IllegalArgumentException("Ingestion type cannot be changed");
        }
    }

    private void rejectInstantChange(Instant existingValue, Instant requestedValue, String fieldName, boolean required) {
        if (requestedValue == null) {
            if (required) {
                throw new IllegalArgumentException(fieldName + " cannot be changed");
            }
            return;
        }
        if (!requestedValue.equals(existingValue)) {
            throw new IllegalArgumentException(fieldName + " cannot be changed");
        }
    }

    private void rejectCompletedAtClientChange(
        TransactionIngestion existingTransactionIngestion,
        Instant requestedCompletedAt,
        boolean present
    ) {
        if (!present) {
            return;
        }
        if (requestedCompletedAt == null) {
            if (existingTransactionIngestion.getCompletedAt() != null) {
                throw new IllegalArgumentException("Completed at cannot be changed");
            }
            return;
        }
        if (!requestedCompletedAt.equals(existingTransactionIngestion.getCompletedAt())) {
            throw new IllegalArgumentException("Completed at cannot be changed");
        }
    }

    private boolean shouldApply(JsonNode patchNode, String fieldName) {
        return patchNode == null || patchNode.has(fieldName);
    }

    private String normalizeOptionalString(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " cannot exceed " + maxLength + " characters");
        }
        return trimmed;
    }

    private Integer requireNonNegative(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than or equal to 0");
        }
        return value;
    }

    private void validateStatusTransition(IngestionStatus currentStatus, IngestionStatus requestedStatus) {
        if (currentStatus == requestedStatus) {
            return;
        }
        if (isFinalStatus(currentStatus)) {
            throw new IllegalArgumentException("Final ingestion status cannot be changed");
        }
        if (currentStatus == IngestionStatus.PENDING) {
            if (
                EnumSet.of(
                    IngestionStatus.PROCESSING,
                    IngestionStatus.COMPLETED,
                    IngestionStatus.PARTIALLY_COMPLETED,
                    IngestionStatus.FAILED
                ).contains(requestedStatus)
            ) {
                return;
            }
        }
        if (
            currentStatus == IngestionStatus.PROCESSING &&
            EnumSet.of(IngestionStatus.COMPLETED, IngestionStatus.PARTIALLY_COMPLETED, IngestionStatus.FAILED).contains(requestedStatus)
        ) {
            return;
        }
        throw new IllegalArgumentException("Invalid ingestion status transition");
    }

    private void validateFinalState(TransactionIngestion transactionIngestion) {
        if (transactionIngestion.getStatus() == null) {
            throw new IllegalArgumentException("Status is required");
        }
        requireNonNegative(transactionIngestion.getRecordsReceived(), "Records received");
        requireNonNegative(transactionIngestion.getRecordsCreated(), "Records created");
        requireNonNegative(transactionIngestion.getRecordsSkipped(), "Records skipped");
        requireNonNegative(transactionIngestion.getRecordsRejected(), "Records rejected");

        if (transactionIngestion.getStartedAt() == null) {
            throw new IllegalArgumentException("Started at is required");
        }
        if (transactionIngestion.getCreatedAt() == null) {
            throw new IllegalArgumentException("Created at is required");
        }

        int processed =
            transactionIngestion.getRecordsCreated() + transactionIngestion.getRecordsSkipped() + transactionIngestion.getRecordsRejected();

        if (isFinalStatus(transactionIngestion.getStatus())) {
            if (transactionIngestion.getCompletedAt() == null) {
                throw new IllegalArgumentException("Completed at is required for final status");
            }
            if (transactionIngestion.getCompletedAt().isBefore(transactionIngestion.getStartedAt())) {
                throw new IllegalArgumentException("Completed at cannot be before started at");
            }
            if (processed != transactionIngestion.getRecordsReceived()) {
                throw new IllegalArgumentException("Final ingestion counts must equal records received");
            }
        } else {
            if (transactionIngestion.getCompletedAt() != null) {
                throw new IllegalArgumentException("Completed at must be null until final status");
            }
            if (processed > transactionIngestion.getRecordsReceived()) {
                throw new IllegalArgumentException("Processed counts cannot exceed records received");
            }
        }

        validateStatusSpecificState(transactionIngestion);
        validateChildMetadataConsistency(transactionIngestion);
    }

    private void validateStatusSpecificState(TransactionIngestion transactionIngestion) {
        IngestionStatus status = transactionIngestion.getStatus();
        String normalizedError = normalizeOptionalString(transactionIngestion.getErrorMessage(), "Error message", 2000);
        transactionIngestion.setErrorMessage(normalizedError);

        if (status == IngestionStatus.PENDING || status == IngestionStatus.PROCESSING || status == IngestionStatus.COMPLETED) {
            if (normalizedError != null) {
                throw new IllegalArgumentException("Error message is only allowed for failed or partially completed ingestions");
            }
        }
        if (status == IngestionStatus.COMPLETED && transactionIngestion.getRecordsRejected() > 0) {
            throw new IllegalArgumentException("Completed ingestion cannot have rejected records");
        }
        if (status == IngestionStatus.PARTIALLY_COMPLETED) {
            if (transactionIngestion.getRecordsReceived() <= 0) {
                throw new IllegalArgumentException("Partially completed ingestion requires received records");
            }
            if (transactionIngestion.getRecordsCreated() <= 0) {
                throw new IllegalArgumentException("Partially completed ingestion requires created records");
            }
            if (transactionIngestion.getRecordsRejected() <= 0 && transactionIngestion.getRecordsSkipped() <= 0) {
                throw new IllegalArgumentException("Partially completed ingestion requires rejected or skipped records");
            }
        }
        if (status == IngestionStatus.FAILED) {
            if (transactionIngestion.getRecordsCreated() != 0) {
                throw new IllegalArgumentException("Failed ingestion cannot have created records");
            }
            if (normalizedError == null) {
                throw new IllegalArgumentException("Error message is required for failed ingestion");
            }
        }
    }

    private void validateChildMetadataConsistency(TransactionIngestion transactionIngestion) {
        boolean hasFileIngestion = fileIngestionRepository.existsByTransactionIngestionId(transactionIngestion.getId());
        boolean hasApiIngestion = apiIngestionRepository.existsByTransactionIngestionId(transactionIngestion.getId());

        if (transactionIngestion.getIngestionType() == IngestionType.FILE && hasApiIngestion) {
            throw new IllegalArgumentException("FILE ingestion cannot have API metadata");
        }
        if (transactionIngestion.getIngestionType() == IngestionType.API && hasFileIngestion) {
            throw new IllegalArgumentException("API ingestion cannot have file metadata");
        }
        if (isFinalStatus(transactionIngestion.getStatus())) {
            if (transactionIngestion.getIngestionType() == IngestionType.FILE && !hasFileIngestion) {
                throw new IllegalArgumentException("Final FILE ingestion requires file metadata");
            }
            if (transactionIngestion.getIngestionType() == IngestionType.API && !hasApiIngestion) {
                throw new IllegalArgumentException("Final API ingestion requires API metadata");
            }
        }
    }

    private boolean isFinalStatus(IngestionStatus status) {
        return (
            Objects.equals(status, IngestionStatus.COMPLETED) ||
            Objects.equals(status, IngestionStatus.PARTIALLY_COMPLETED) ||
            Objects.equals(status, IngestionStatus.FAILED)
        );
    }
}
