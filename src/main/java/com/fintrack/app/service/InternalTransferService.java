package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.InternalTransferDTO;
import com.fintrack.app.service.mapper.InternalTransferMapper;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.InternalTransfer}.
 */
@Service
@Transactional
public class InternalTransferService {

    private static final Logger LOG = LoggerFactory.getLogger(InternalTransferService.class);

    private final InternalTransferRepository internalTransferRepository;

    private final InternalTransferMapper internalTransferMapper;

    private final CurrentUserService currentUserService;

    private final FinancialTransactionService financialTransactionService;

    public InternalTransferService(
        InternalTransferRepository internalTransferRepository,
        InternalTransferMapper internalTransferMapper,
        CurrentUserService currentUserService,
        FinancialTransactionService financialTransactionService
    ) {
        this.internalTransferRepository = internalTransferRepository;
        this.internalTransferMapper = internalTransferMapper;
        this.currentUserService = currentUserService;
        this.financialTransactionService = financialTransactionService;
    }

    /**
     * Save a internalTransfer.
     *
     * @param internalTransferDTO the entity to save.
     * @return the persisted entity.
     */
    public InternalTransferDTO save(InternalTransferDTO internalTransferDTO) {
        LOG.debug("Request to save InternalTransfer : {}", internalTransferDTO);
        InternalTransfer internalTransfer = internalTransferMapper.toEntity(internalTransferDTO);
        FinancialTransaction outgoingTransaction = resolveOutgoingTransactionForCreate(internalTransferDTO.getOutgoingTransaction());
        FinancialTransaction incomingTransaction = resolveIncomingTransactionForCreate(internalTransferDTO.getIncomingTransaction());
        validateTransferPair(outgoingTransaction, incomingTransaction);
        internalTransfer.setOutgoingTransaction(outgoingTransaction);
        internalTransfer.setIncomingTransaction(incomingTransaction);
        internalTransfer.setNotes(normalizeNotes(internalTransfer.getNotes()));
        internalTransfer.setCreatedAt(Instant.now());
        internalTransfer = internalTransferRepository.save(internalTransfer);
        return internalTransferMapper.toDto(internalTransfer);
    }

    /**
     * Update a internalTransfer.
     *
     * @param internalTransferDTO the entity to save.
     * @return the persisted entity.
     */
    public InternalTransferDTO update(InternalTransferDTO internalTransferDTO) {
        return update(internalTransferDTO, null);
    }

    /**
     * Update a internalTransfer.
     *
     * @param internalTransferDTO the entity to save.
     * @param updateNode the raw update payload.
     * @return the persisted entity.
     */
    public InternalTransferDTO update(InternalTransferDTO internalTransferDTO, JsonNode updateNode) {
        LOG.debug("Request to update InternalTransfer : {}", internalTransferDTO);
        InternalTransfer existingInternalTransfer = findAccessibleEntity(internalTransferDTO.getId()).orElseThrow();
        if (updateNode == null || updateNode.has("outgoingTransaction")) {
            rejectTransactionChange(existingInternalTransfer, internalTransferDTO.getOutgoingTransaction(), "Outgoing transaction");
        }
        if (updateNode == null || updateNode.has("incomingTransaction")) {
            rejectTransactionChange(existingInternalTransfer, internalTransferDTO.getIncomingTransaction(), "Incoming transaction");
        }
        rejectCreatedAtChange(existingInternalTransfer, internalTransferDTO.getCreatedAt());
        InternalTransfer internalTransfer = internalTransferMapper.toEntity(internalTransferDTO);
        internalTransfer.setOutgoingTransaction(existingInternalTransfer.getOutgoingTransaction());
        internalTransfer.setIncomingTransaction(existingInternalTransfer.getIncomingTransaction());
        internalTransfer.setNotes(normalizeNotes(internalTransfer.getNotes()));
        internalTransfer.setCreatedAt(existingInternalTransfer.getCreatedAt());
        internalTransfer = internalTransferRepository.save(internalTransfer);
        return internalTransferMapper.toDto(internalTransfer);
    }

    /**
     * Partially update a internalTransfer.
     *
     * @param internalTransferDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<InternalTransferDTO> partialUpdate(InternalTransferDTO internalTransferDTO) {
        return partialUpdate(internalTransferDTO, null);
    }

    /**
     * Partially update a internalTransfer, applying transaction changes only when present in the patch body.
     *
     * @param internalTransferDTO the entity to update partially.
     * @param patchNode the raw patch payload.
     * @return the persisted entity.
     */
    public Optional<InternalTransferDTO> partialUpdate(InternalTransferDTO internalTransferDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update InternalTransfer : {}", internalTransferDTO);

        return findAccessibleEntity(internalTransferDTO.getId())
            .map(existingInternalTransfer -> {
                if (patchNode != null && patchNode.has("outgoingTransaction") && patchNode.get("outgoingTransaction").isNull()) {
                    throw new IllegalArgumentException("Outgoing transaction cannot be null");
                }
                if (patchNode != null && patchNode.has("incomingTransaction") && patchNode.get("incomingTransaction").isNull()) {
                    throw new IllegalArgumentException("Incoming transaction cannot be null");
                }
                if (patchNode != null && patchNode.has("createdAt") && patchNode.get("createdAt").isNull()) {
                    throw new IllegalArgumentException("Created at cannot be null");
                }
                if (patchNode != null && patchNode.has("outgoingTransaction")) {
                    rejectTransactionChange(existingInternalTransfer, internalTransferDTO.getOutgoingTransaction(), "Outgoing transaction");
                }
                if (patchNode != null && patchNode.has("incomingTransaction")) {
                    rejectTransactionChange(existingInternalTransfer, internalTransferDTO.getIncomingTransaction(), "Incoming transaction");
                }
                if (patchNode != null && patchNode.has("createdAt")) {
                    rejectCreatedAtChange(existingInternalTransfer, internalTransferDTO.getCreatedAt());
                }
                internalTransferMapper.partialUpdate(existingInternalTransfer, internalTransferDTO);
                if (patchNode != null && patchNode.has("notes")) {
                    existingInternalTransfer.setNotes(normalizeNotes(internalTransferDTO.getNotes()));
                } else {
                    existingInternalTransfer.setNotes(normalizeNotes(existingInternalTransfer.getNotes()));
                }
                return existingInternalTransfer;
            })
            .map(internalTransferRepository::save)
            .map(internalTransferMapper::toDto);
    }

    /**
     * Get all the internalTransfers.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<InternalTransferDTO> findAll() {
        LOG.debug("Request to get all InternalTransfers");
        if (currentUserService.isAdmin()) {
            return internalTransferRepository
                .findAllWithEagerRelationships()
                .stream()
                .map(internalTransferMapper::toDto)
                .collect(Collectors.toCollection(LinkedList::new));
        }
        return internalTransferRepository
            .findAllWithEagerRelationshipsByAccountUserLogin(currentUserService.getCurrentUserLogin())
            .stream()
            .map(internalTransferMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one internalTransfer by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<InternalTransferDTO> findOne(Long id) {
        LOG.debug("Request to get InternalTransfer : {}", id);
        return findAccessibleEntity(id).map(internalTransferMapper::toDto);
    }

    /**
     * Returns whether the current user can access the internal transfer.
     *
     * @param id the id of the entity.
     * @return true when the transfer exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the internalTransfer by id.
     *
     * @param id the id of the entity.
     * @return true when the transfer was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete InternalTransfer : {}", id);
        Optional<InternalTransfer> internalTransfer = findAccessibleEntity(id);
        if (internalTransfer.isEmpty()) {
            return false;
        }
        internalTransferRepository.deleteById(id);
        return true;
    }

    private Optional<InternalTransfer> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return internalTransferRepository.findOneWithEagerRelationships(id);
        }
        return internalTransferRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(
            id,
            currentUserService.getCurrentUserLogin()
        );
    }

    private FinancialTransaction resolveOutgoingTransactionForCreate(FinancialTransactionDTO outgoingTransactionDTO) {
        if (outgoingTransactionDTO == null || outgoingTransactionDTO.getId() == null) {
            throw new IllegalArgumentException("Outgoing transaction is required");
        }
        return financialTransactionService
            .findAccessibleTransactionEntity(outgoingTransactionDTO.getId())
            .orElseThrow(() -> new IllegalArgumentException("Outgoing transaction is not accessible"));
    }

    private FinancialTransaction resolveIncomingTransactionForCreate(FinancialTransactionDTO incomingTransactionDTO) {
        if (incomingTransactionDTO == null || incomingTransactionDTO.getId() == null) {
            throw new IllegalArgumentException("Incoming transaction is required");
        }
        return financialTransactionService
            .findAccessibleTransactionEntity(incomingTransactionDTO.getId())
            .orElseThrow(() -> new IllegalArgumentException("Incoming transaction is not accessible"));
    }

    private void validateTransferPair(FinancialTransaction outgoingTransaction, FinancialTransaction incomingTransaction) {
        if (outgoingTransaction.getId().equals(incomingTransaction.getId())) {
            throw new IllegalArgumentException("Outgoing and incoming transactions must be different");
        }
        if (outgoingTransaction.getFlow() != TransactionFlow.OUT) {
            throw new IllegalArgumentException("Outgoing transaction must have OUT flow");
        }
        if (incomingTransaction.getFlow() != TransactionFlow.IN) {
            throw new IllegalArgumentException("Incoming transaction must have IN flow");
        }
        if (outgoingTransaction.getAccount().getId().equals(incomingTransaction.getAccount().getId())) {
            throw new IllegalArgumentException("Transactions must belong to different accounts");
        }
        if (outgoingTransaction.getAccount().getCurrency() != incomingTransaction.getAccount().getCurrency()) {
            throw new IllegalArgumentException("Transactions must use the same currency");
        }
        if (outgoingTransaction.getAmount().compareTo(incomingTransaction.getAmount()) != 0) {
            throw new IllegalArgumentException("Transaction amounts must match");
        }
        if (
            outgoingTransaction.getAccount().getUser() == null ||
            incomingTransaction.getAccount().getUser() == null ||
            !outgoingTransaction.getAccount().getUser().getId().equals(incomingTransaction.getAccount().getUser().getId())
        ) {
            throw new IllegalArgumentException("Transactions must belong to the same user");
        }
        if (internalTransferRepository.existsByTransactionIdInEitherRole(outgoingTransaction.getId())) {
            throw new IllegalArgumentException("Outgoing transaction is already linked to an internal transfer");
        }
        if (internalTransferRepository.existsByTransactionIdInEitherRole(incomingTransaction.getId())) {
            throw new IllegalArgumentException("Incoming transaction is already linked to an internal transfer");
        }
    }

    private void rejectTransactionChange(
        InternalTransfer existingInternalTransfer,
        FinancialTransactionDTO transactionDTO,
        String fieldLabel
    ) {
        if (transactionDTO == null || transactionDTO.getId() == null) {
            throw new IllegalArgumentException(fieldLabel + " is required");
        }
        FinancialTransaction existingTransaction = fieldLabel.startsWith("Outgoing")
            ? existingInternalTransfer.getOutgoingTransaction()
            : existingInternalTransfer.getIncomingTransaction();
        if (!transactionDTO.getId().equals(existingTransaction.getId())) {
            throw new IllegalArgumentException(fieldLabel + " cannot be changed");
        }
    }

    private void rejectCreatedAtChange(InternalTransfer existingInternalTransfer, Instant requestedCreatedAt) {
        if (requestedCreatedAt != null && !requestedCreatedAt.equals(existingInternalTransfer.getCreatedAt())) {
            throw new IllegalArgumentException("Created at cannot be changed");
        }
    }

    private String normalizeNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Notes cannot exceed 500 characters");
        }
        return trimmed;
    }
}
