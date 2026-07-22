package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.IngestionRecordDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.IngestionRecordMapper;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.IngestionRecord}.
 */
@Service
@Transactional
public class IngestionRecordService {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionRecordService.class);

    private final IngestionRecordRepository ingestionRecordRepository;

    private final IngestionRecordMapper ingestionRecordMapper;

    private final CurrentUserService currentUserService;

    private final TransactionIngestionRepository transactionIngestionRepository;

    private final FinancialTransactionService financialTransactionService;

    public IngestionRecordService(
        IngestionRecordRepository ingestionRecordRepository,
        IngestionRecordMapper ingestionRecordMapper,
        CurrentUserService currentUserService,
        TransactionIngestionRepository transactionIngestionRepository,
        FinancialTransactionService financialTransactionService
    ) {
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.ingestionRecordMapper = ingestionRecordMapper;
        this.currentUserService = currentUserService;
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.financialTransactionService = financialTransactionService;
    }

    public IngestionRecordDTO save(IngestionRecordDTO ingestionRecordDTO) {
        LOG.debug("Request to save IngestionRecord");
        if (ingestionRecordDTO.getRecordIndex() == null) {
            throw new IllegalArgumentException("Record index is required");
        }
        IngestionRecord ingestionRecord = ingestionRecordMapper.toEntity(ingestionRecordDTO);
        TransactionIngestion transactionIngestion = resolveTransactionIngestionForCreate(ingestionRecordDTO.getTransactionIngestion());
        ingestionRecord.setTransactionIngestion(transactionIngestion);
        normalizeCreateFields(ingestionRecord);
        validateCreateUniqueness(transactionIngestion, ingestionRecord);
        if (ingestionRecordDTO.getFinancialTransaction() != null) {
            FinancialTransaction financialTransaction = resolveFinancialTransactionForCreate(
                ingestionRecordDTO.getFinancialTransaction(),
                transactionIngestion
            );
            ingestionRecord.setFinancialTransaction(financialTransaction);
        }
        ingestionRecord.setCreatedAt(Instant.now());
        validateMergedState(ingestionRecord);
        ingestionRecord = ingestionRecordRepository.save(ingestionRecord);
        return ingestionRecordMapper.toDto(ingestionRecord);
    }

    public IngestionRecordDTO update(IngestionRecordDTO ingestionRecordDTO) {
        return update(ingestionRecordDTO, null);
    }

    public IngestionRecordDTO update(IngestionRecordDTO ingestionRecordDTO, JsonNode updateNode) {
        LOG.debug("Request to update IngestionRecord : {}", ingestionRecordDTO.getId());
        IngestionRecord existing = findAccessibleEntity(ingestionRecordDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Entity not found")
        );
        rejectImmutableFieldChanges(existing, ingestionRecordDTO, updateNode);
        rejectChangesWhenParentFinal(existing, ingestionRecordDTO, updateNode);
        ingestionRecordMapper.partialUpdate(existing, ingestionRecordDTO);
        applyMutableFields(existing, ingestionRecordDTO, updateNode);
        normalizeMutableFields(existing);
        validateMergedState(existing);
        existing = ingestionRecordRepository.save(existing);
        return ingestionRecordMapper.toDto(existing);
    }

    public Optional<IngestionRecordDTO> partialUpdate(IngestionRecordDTO ingestionRecordDTO) {
        return partialUpdate(ingestionRecordDTO, null);
    }

    public Optional<IngestionRecordDTO> partialUpdate(IngestionRecordDTO ingestionRecordDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update IngestionRecord : {}", ingestionRecordDTO.getId());
        return findAccessibleEntity(ingestionRecordDTO.getId())
            .map(existing -> {
                rejectImmutableFieldChanges(existing, ingestionRecordDTO, patchNode);
                rejectChangesWhenParentFinal(existing, ingestionRecordDTO, patchNode);
                ingestionRecordMapper.partialUpdate(existing, ingestionRecordDTO);
                applyMutableFields(existing, ingestionRecordDTO, patchNode);
                normalizeMutableFields(existing);
                validateMergedState(existing);
                return existing;
            })
            .map(ingestionRecordRepository::save)
            .map(ingestionRecordMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<IngestionRecordDTO> findOne(Long id) {
        LOG.debug("Request to get IngestionRecord : {}", id);
        return findAccessibleEntity(id).map(ingestionRecordMapper::toDto);
    }

    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    public boolean delete(Long id) {
        LOG.debug("Request to delete IngestionRecord : {}", id);
        Optional<IngestionRecord> ingestionRecord = findAccessibleEntity(id);
        if (ingestionRecord.isEmpty()) {
            return false;
        }
        throw new IllegalArgumentException("Ingestion records are deleted through TransactionIngestion cleanup");
    }

    private Optional<IngestionRecord> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return ingestionRecordRepository.findOneWithRelationships(id);
        }
        return ingestionRecordRepository.findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(
            id,
            currentUserService.getCurrentUserLogin()
        );
    }

    private TransactionIngestion resolveTransactionIngestionForCreate(TransactionIngestionDTO transactionIngestionDTO) {
        if (transactionIngestionDTO == null || transactionIngestionDTO.getId() == null) {
            throw new IllegalArgumentException("Transaction ingestion is required");
        }
        return findAccessibleTransactionIngestion(transactionIngestionDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Transaction ingestion not found")
        );
    }

    private FinancialTransaction resolveFinancialTransactionForCreate(
        FinancialTransactionDTO financialTransactionDTO,
        TransactionIngestion transactionIngestion
    ) {
        FinancialTransaction financialTransaction = resolveAccessibleFinancialTransaction(financialTransactionDTO);
        validateFinancialTransactionMatchesParent(transactionIngestion, financialTransaction);
        validateFinancialTransactionNotLinked(financialTransaction);
        return financialTransaction;
    }

    private Optional<TransactionIngestion> findAccessibleTransactionIngestion(Long id) {
        if (currentUserService.isAdmin()) {
            return transactionIngestionRepository.findOneWithToOneRelationships(id);
        }
        return transactionIngestionRepository.findOneWithToOneRelationshipsByIdAndAccountUserLogin(
            id,
            currentUserService.getCurrentUserLogin()
        );
    }

    private void validateSameOwner(TransactionIngestion transactionIngestion, FinancialTransaction financialTransaction) {
        if (
            transactionIngestion.getAccount() == null ||
            transactionIngestion.getAccount().getUser() == null ||
            financialTransaction.getAccount() == null ||
            financialTransaction.getAccount().getUser() == null
        ) {
            throw new IllegalArgumentException("Transaction ingestion and financial transaction must belong to the same owner");
        }
        if (!transactionIngestion.getAccount().getUser().getLogin().equals(financialTransaction.getAccount().getUser().getLogin())) {
            throw new IllegalArgumentException("Transaction ingestion and financial transaction must belong to the same owner");
        }
    }

    private void validateCreateUniqueness(TransactionIngestion transactionIngestion, IngestionRecord ingestionRecord) {
        if (
            ingestionRecordRepository.existsByTransactionIngestionIdAndRecordIndex(
                transactionIngestion.getId(),
                ingestionRecord.getRecordIndex()
            )
        ) {
            throw new IllegalArgumentException("Record index already exists for transaction ingestion");
        }
        if (
            ingestionRecord.getExternalRecordId() != null &&
            ingestionRecordRepository.existsByTransactionIngestionIdAndExternalRecordId(
                transactionIngestion.getId(),
                ingestionRecord.getExternalRecordId()
            )
        ) {
            throw new IllegalArgumentException("External record id already exists for transaction ingestion");
        }
    }

    private void rejectImmutableFieldChanges(IngestionRecord existing, IngestionRecordDTO ingestionRecordDTO, JsonNode patchNode) {
        if (patchNode == null || patchNode.has("transactionIngestion")) {
            rejectTransactionIngestionChange(existing, ingestionRecordDTO.getTransactionIngestion());
        }
        if (patchNode == null || patchNode.has("recordIndex")) {
            rejectRecordIndexChange(existing, ingestionRecordDTO.getRecordIndex());
        }
        if (patchNode == null || patchNode.has("externalRecordId")) {
            rejectExternalRecordIdChange(existing, ingestionRecordDTO.getExternalRecordId());
        }
        if (patchNode == null || patchNode.has("rawData")) {
            rejectRawDataChange(existing, ingestionRecordDTO.getRawData());
        }
        if (patchNode == null || patchNode.has("createdAt")) {
            rejectCreatedAtChange(existing, ingestionRecordDTO.getCreatedAt());
        }
    }

    private void rejectTransactionIngestionChange(IngestionRecord existing, TransactionIngestionDTO transactionIngestionDTO) {
        if (transactionIngestionDTO == null || transactionIngestionDTO.getId() == null) {
            throw new IllegalArgumentException("Transaction ingestion cannot be changed");
        }
        if (!transactionIngestionDTO.getId().equals(existing.getTransactionIngestion().getId())) {
            throw new IllegalArgumentException("Transaction ingestion cannot be changed");
        }
    }

    private void rejectRecordIndexChange(IngestionRecord existing, Integer recordIndex) {
        if (recordIndex == null) {
            throw new IllegalArgumentException("Record index cannot be changed");
        }
        if (!recordIndex.equals(existing.getRecordIndex())) {
            throw new IllegalArgumentException("Record index cannot be changed");
        }
    }

    private void rejectExternalRecordIdChange(IngestionRecord existing, String externalRecordId) {
        if (!Objects.equals(existing.getExternalRecordId(), normalizeOptionalString(externalRecordId))) {
            throw new IllegalArgumentException("External record id cannot be changed");
        }
    }

    private void rejectRawDataChange(IngestionRecord existing, String rawData) {
        if (!Objects.equals(existing.getRawData(), rawData)) {
            throw new IllegalArgumentException("Raw data cannot be changed");
        }
    }

    private void rejectCreatedAtChange(IngestionRecord existing, Instant createdAt) {
        if (createdAt == null) {
            throw new IllegalArgumentException("Created at cannot be changed");
        }
        if (!createdAt.equals(existing.getCreatedAt())) {
            throw new IllegalArgumentException("Created at cannot be changed");
        }
    }

    private void rejectChangesWhenParentFinal(IngestionRecord existing, IngestionRecordDTO ingestionRecordDTO, JsonNode patchNode) {
        if (!isParentFinal(existing)) {
            return;
        }
        IngestionRecord proposed = copyExisting(existing);
        applyMutableFields(proposed, ingestionRecordDTO, patchNode);
        normalizeMutableFields(proposed);
        if (!sameMutableState(existing, proposed)) {
            throw new IllegalArgumentException("Ingestion record cannot be changed after parent ingestion is final");
        }
    }

    private void applyMutableFields(IngestionRecord existing, IngestionRecordDTO ingestionRecordDTO, JsonNode patchNode) {
        if ((patchNode == null || patchNode.has("status")) && ingestionRecordDTO.getStatus() != null) {
            existing.setStatus(ingestionRecordDTO.getStatus());
        }
        if (patchNode == null || patchNode.has("financialTransaction")) {
            existing.setFinancialTransaction(resolveFinancialTransactionForUpdate(existing, ingestionRecordDTO.getFinancialTransaction()));
        }
        if (patchNode == null || patchNode.has("errorCode")) {
            existing.setErrorCode(ingestionRecordDTO.getErrorCode());
        }
        if (patchNode == null || patchNode.has("errorMessage")) {
            existing.setErrorMessage(ingestionRecordDTO.getErrorMessage());
        }
    }

    private FinancialTransaction resolveFinancialTransactionForUpdate(
        IngestionRecord existing,
        FinancialTransactionDTO financialTransactionDTO
    ) {
        Long existingId = existing.getFinancialTransaction() != null ? existing.getFinancialTransaction().getId() : null;
        if (financialTransactionDTO == null) {
            if (existingId != null) {
                throw new IllegalArgumentException("Financial transaction cannot be changed");
            }
            return null;
        }
        if (financialTransactionDTO.getId() == null) {
            throw new IllegalArgumentException("Financial transaction id is required");
        }
        if (existingId != null) {
            if (!financialTransactionDTO.getId().equals(existingId)) {
                throw new IllegalArgumentException("Financial transaction cannot be changed");
            }
            return existing.getFinancialTransaction();
        }
        FinancialTransaction financialTransaction = resolveAccessibleFinancialTransaction(financialTransactionDTO);
        validateFinancialTransactionMatchesParent(existing.getTransactionIngestion(), financialTransaction);
        validateFinancialTransactionNotLinked(financialTransaction);
        return financialTransaction;
    }

    private FinancialTransaction resolveAccessibleFinancialTransaction(FinancialTransactionDTO financialTransactionDTO) {
        if (financialTransactionDTO == null || financialTransactionDTO.getId() == null) {
            throw new IllegalArgumentException("Financial transaction is required");
        }
        return financialTransactionService
            .findAccessibleTransactionEntity(financialTransactionDTO.getId())
            .orElseThrow(() -> new IllegalArgumentException("Financial transaction not found"));
    }

    private void validateFinancialTransactionMatchesParent(
        TransactionIngestion transactionIngestion,
        FinancialTransaction financialTransaction
    ) {
        validateSameOwner(transactionIngestion, financialTransaction);
        if (
            financialTransaction.getTransactionIngestion() == null ||
            financialTransaction.getTransactionIngestion().getId() == null ||
            !financialTransaction.getTransactionIngestion().getId().equals(transactionIngestion.getId())
        ) {
            throw new IllegalArgumentException("Financial transaction must belong to the same transaction ingestion");
        }
    }

    private void validateFinancialTransactionNotLinked(FinancialTransaction financialTransaction) {
        if (ingestionRecordRepository.existsByFinancialTransactionId(financialTransaction.getId())) {
            throw new IllegalArgumentException("Financial transaction already has an ingestion record");
        }
    }

    private void normalizeCreateFields(IngestionRecord ingestionRecord) {
        ingestionRecord.setExternalRecordId(normalizeOptionalString(ingestionRecord.getExternalRecordId()));
        normalizeMutableFields(ingestionRecord);
    }

    private void normalizeMutableFields(IngestionRecord ingestionRecord) {
        ingestionRecord.setErrorCode(normalizeOptionalString(ingestionRecord.getErrorCode()));
        ingestionRecord.setErrorMessage(normalizeOptionalString(ingestionRecord.getErrorMessage()));
    }

    private String normalizeOptionalString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateMergedState(IngestionRecord ingestionRecord) {
        if (ingestionRecord.getRecordIndex() == null) {
            throw new IllegalArgumentException("Record index is required");
        }
        if (ingestionRecord.getRecordIndex() < 0) {
            throw new IllegalArgumentException("Record index must be greater than or equal to 0");
        }
        if (ingestionRecord.getStatus() == null) {
            throw new IllegalArgumentException("Status is required");
        }
        if (ingestionRecord.getTransactionIngestion() == null || ingestionRecord.getTransactionIngestion().getId() == null) {
            throw new IllegalArgumentException("Transaction ingestion is required");
        }
        if (ingestionRecord.getExternalRecordId() != null && ingestionRecord.getExternalRecordId().length() > 150) {
            throw new IllegalArgumentException("External record id must be at most 150 characters");
        }
        if (ingestionRecord.getErrorCode() != null && ingestionRecord.getErrorCode().length() > 100) {
            throw new IllegalArgumentException("Error code must be at most 100 characters");
        }
        if (ingestionRecord.getErrorMessage() != null && ingestionRecord.getErrorMessage().length() > 1000) {
            throw new IllegalArgumentException("Error message must be at most 1000 characters");
        }
        validateStatusConsistency(ingestionRecord);
    }

    private void validateStatusConsistency(IngestionRecord ingestionRecord) {
        if (ingestionRecord.getStatus() == IngestionRecordStatus.VALID) {
            if (ingestionRecord.getFinancialTransaction() != null) {
                throw new IllegalArgumentException("Valid records cannot have a financial transaction");
            }
            if (ingestionRecord.getErrorCode() != null || ingestionRecord.getErrorMessage() != null) {
                throw new IllegalArgumentException("Valid records cannot have error details");
            }
        } else if (ingestionRecord.getStatus() == IngestionRecordStatus.IMPORTED) {
            if (ingestionRecord.getFinancialTransaction() == null) {
                throw new IllegalArgumentException("Financial transaction is required for imported records");
            }
            if (ingestionRecord.getErrorCode() != null || ingestionRecord.getErrorMessage() != null) {
                throw new IllegalArgumentException("Imported records cannot have error details");
            }
        } else if (ingestionRecord.getStatus() == IngestionRecordStatus.DISABLED) {
            if (ingestionRecord.getFinancialTransaction() != null) {
                throw new IllegalArgumentException("Disabled records cannot have a financial transaction");
            }
        } else if (ingestionRecord.getStatus() == IngestionRecordStatus.SKIPPED_DUPLICATE) {
            if (ingestionRecord.getFinancialTransaction() != null) {
                throw new IllegalArgumentException("Skipped duplicate records cannot have a financial transaction");
            }
        } else if (ingestionRecord.getStatus() == IngestionRecordStatus.REJECTED) {
            if (ingestionRecord.getFinancialTransaction() != null) {
                throw new IllegalArgumentException("Rejected records cannot have a financial transaction");
            }
            if (ingestionRecord.getErrorMessage() == null) {
                throw new IllegalArgumentException("Rejected records require an error message");
            }
        } else if (ingestionRecord.getStatus() == IngestionRecordStatus.FAILED) {
            if (ingestionRecord.getFinancialTransaction() != null) {
                throw new IllegalArgumentException("Failed records cannot have a financial transaction");
            }
            if (ingestionRecord.getErrorMessage() == null) {
                throw new IllegalArgumentException("Failed records require an error message");
            }
        }
    }

    private boolean isParentFinal(IngestionRecord ingestionRecord) {
        IngestionStatus status = ingestionRecord.getTransactionIngestion() != null
            ? ingestionRecord.getTransactionIngestion().getStatus()
            : null;
        return status == IngestionStatus.COMPLETED || status == IngestionStatus.PARTIALLY_COMPLETED || status == IngestionStatus.FAILED;
    }

    private IngestionRecord copyExisting(IngestionRecord existing) {
        IngestionRecord copy = new IngestionRecord();
        copy.setId(existing.getId());
        copy.setRecordIndex(existing.getRecordIndex());
        copy.setExternalRecordId(existing.getExternalRecordId());
        copy.setStatus(existing.getStatus());
        copy.setRawData(existing.getRawData());
        copy.setErrorCode(existing.getErrorCode());
        copy.setErrorMessage(existing.getErrorMessage());
        copy.setCreatedAt(existing.getCreatedAt());
        copy.setFinancialTransaction(existing.getFinancialTransaction());
        copy.setTransactionIngestion(existing.getTransactionIngestion());
        return copy;
    }

    private boolean sameMutableState(IngestionRecord existing, IngestionRecord proposed) {
        return (
            existing.getStatus() == proposed.getStatus() &&
            sameFinancialTransaction(existing, proposed) &&
            Objects.equals(existing.getErrorCode(), proposed.getErrorCode()) &&
            Objects.equals(existing.getErrorMessage(), proposed.getErrorMessage())
        );
    }

    private boolean sameFinancialTransaction(IngestionRecord existing, IngestionRecord proposed) {
        Long existingId = existing.getFinancialTransaction() != null ? existing.getFinancialTransaction().getId() : null;
        Long proposedId = proposed.getFinancialTransaction() != null ? proposed.getFinancialTransaction().getId() : null;
        return Objects.equals(existingId, proposedId);
    }
}
