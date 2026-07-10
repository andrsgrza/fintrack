package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.IngestionRecordDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.IngestionRecordMapper;
import java.time.Instant;
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
        LOG.debug("Request to save IngestionRecord : {}", ingestionRecordDTO);
        if (ingestionRecordDTO.getRecordIndex() == null) {
            throw new IllegalArgumentException("Record index is required");
        }
        IngestionRecord ingestionRecord = ingestionRecordMapper.toEntity(ingestionRecordDTO);
        TransactionIngestion transactionIngestion = resolveTransactionIngestionForCreate(ingestionRecordDTO.getTransactionIngestion());
        ingestionRecord.setTransactionIngestion(transactionIngestion);
        validateRecordIndexForCreate(transactionIngestion, ingestionRecord.getRecordIndex());
        if (ingestionRecordDTO.getFinancialTransaction() != null && ingestionRecordDTO.getFinancialTransaction().getId() != null) {
            FinancialTransaction financialTransaction = resolveFinancialTransactionForCreate(
                ingestionRecordDTO.getFinancialTransaction(),
                transactionIngestion
            );
            ingestionRecord.setFinancialTransaction(financialTransaction);
        }
        ingestionRecord.setCreatedAt(Instant.now());
        ingestionRecord = ingestionRecordRepository.save(ingestionRecord);
        return ingestionRecordMapper.toDto(ingestionRecord);
    }

    public IngestionRecordDTO update(IngestionRecordDTO ingestionRecordDTO) {
        LOG.debug("Request to update IngestionRecord : {}", ingestionRecordDTO);
        IngestionRecord existing = findAccessibleEntity(ingestionRecordDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Entity not found")
        );
        rejectImmutableFieldChanges(existing, ingestionRecordDTO, null);
        ingestionRecordMapper.partialUpdate(existing, ingestionRecordDTO);
        applyMutableFields(existing, ingestionRecordDTO);
        existing = ingestionRecordRepository.save(existing);
        return ingestionRecordMapper.toDto(existing);
    }

    public Optional<IngestionRecordDTO> partialUpdate(IngestionRecordDTO ingestionRecordDTO) {
        return partialUpdate(ingestionRecordDTO, null);
    }

    public Optional<IngestionRecordDTO> partialUpdate(IngestionRecordDTO ingestionRecordDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update IngestionRecord : {}", ingestionRecordDTO);
        return findAccessibleEntity(ingestionRecordDTO.getId())
            .map(existing -> {
                if (patchNode != null && patchNode.has("transactionIngestion") && patchNode.get("transactionIngestion").isNull()) {
                    throw new IllegalArgumentException("Transaction ingestion cannot be changed");
                }
                if (patchNode != null && patchNode.has("financialTransaction") && patchNode.get("financialTransaction").isNull()) {
                    throw new IllegalArgumentException("Financial transaction cannot be changed");
                }
                rejectImmutableFieldChanges(existing, ingestionRecordDTO, patchNode);
                ingestionRecordMapper.partialUpdate(existing, ingestionRecordDTO);
                applyMutableFields(existing, ingestionRecordDTO);
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
        ingestionRecordRepository.deleteById(id);
        return true;
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
        if (financialTransactionDTO == null || financialTransactionDTO.getId() == null) {
            throw new IllegalArgumentException("Financial transaction is required");
        }
        FinancialTransaction financialTransaction = financialTransactionService
            .findAccessibleTransactionEntity(financialTransactionDTO.getId())
            .orElseThrow(() -> new IllegalArgumentException("Financial transaction not found"));
        validateSameOwner(transactionIngestion, financialTransaction);
        if (ingestionRecordRepository.existsByFinancialTransactionId(financialTransaction.getId())) {
            throw new IllegalArgumentException("Financial transaction already has an ingestion record");
        }
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

    private void validateRecordIndexForCreate(TransactionIngestion transactionIngestion, Integer recordIndex) {
        if (ingestionRecordRepository.existsByTransactionIngestionIdAndRecordIndex(transactionIngestion.getId(), recordIndex)) {
            throw new IllegalArgumentException("Record index already exists for transaction ingestion");
        }
    }

    private void rejectImmutableFieldChanges(IngestionRecord existing, IngestionRecordDTO ingestionRecordDTO, JsonNode patchNode) {
        if (patchNode == null || patchNode.has("transactionIngestion")) {
            rejectTransactionIngestionChange(existing, ingestionRecordDTO.getTransactionIngestion());
        }
        if (patchNode == null || patchNode.has("financialTransaction")) {
            rejectFinancialTransactionChange(existing, ingestionRecordDTO.getFinancialTransaction());
        }
        if (patchNode == null || patchNode.has("recordIndex")) {
            rejectRecordIndexChange(existing, ingestionRecordDTO.getRecordIndex());
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

    private void rejectFinancialTransactionChange(IngestionRecord existing, FinancialTransactionDTO financialTransactionDTO) {
        Long existingId = existing.getFinancialTransaction() != null ? existing.getFinancialTransaction().getId() : null;
        Long incomingId = financialTransactionDTO != null ? financialTransactionDTO.getId() : null;
        if (existingId == null && incomingId == null) {
            return;
        }
        if (existingId == null) {
            throw new IllegalArgumentException("Financial transaction cannot be changed");
        }
        if (incomingId == null || !incomingId.equals(existingId)) {
            throw new IllegalArgumentException("Financial transaction cannot be changed");
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

    private void rejectCreatedAtChange(IngestionRecord existing, Instant createdAt) {
        if (createdAt == null) {
            throw new IllegalArgumentException("Created at cannot be changed");
        }
        if (!createdAt.equals(existing.getCreatedAt())) {
            throw new IllegalArgumentException("Created at cannot be changed");
        }
    }

    private void applyMutableFields(IngestionRecord existing, IngestionRecordDTO ingestionRecordDTO) {
        if (ingestionRecordDTO.getStatus() != null) {
            existing.setStatus(ingestionRecordDTO.getStatus());
        }
        if (ingestionRecordDTO.getRawData() != null) {
            existing.setRawData(ingestionRecordDTO.getRawData());
        }
        if (ingestionRecordDTO.getErrorCode() != null) {
            existing.setErrorCode(ingestionRecordDTO.getErrorCode());
        }
        if (ingestionRecordDTO.getErrorMessage() != null) {
            existing.setErrorMessage(ingestionRecordDTO.getErrorMessage());
        }
        if (ingestionRecordDTO.getExternalRecordId() != null) {
            existing.setExternalRecordId(ingestionRecordDTO.getExternalRecordId());
        }
    }
}
