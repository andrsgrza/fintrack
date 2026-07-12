package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.ApiIngestionRepository;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.TransactionIngestionMapper;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
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

    public TransactionIngestionService(
        TransactionIngestionRepository transactionIngestionRepository,
        TransactionIngestionMapper transactionIngestionMapper,
        CurrentUserService currentUserService,
        FinancialAccountService financialAccountService,
        FileIngestionRepository fileIngestionRepository,
        ApiIngestionRepository apiIngestionRepository
    ) {
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.transactionIngestionMapper = transactionIngestionMapper;
        this.currentUserService = currentUserService;
        this.financialAccountService = financialAccountService;
        this.fileIngestionRepository = fileIngestionRepository;
        this.apiIngestionRepository = apiIngestionRepository;
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
        transactionIngestion.setAccount(resolveAccountForCreate(transactionIngestionDTO.getAccount()));
        applyCreateDefaults(transactionIngestion);
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
        rejectAccountChange(existingTransactionIngestion, transactionIngestionDTO.getAccount());
        rejectIngestionTypeChange(existingTransactionIngestion, transactionIngestionDTO.getIngestionType());
        applyMutableFields(existingTransactionIngestion, transactionIngestionDTO);
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
                if (patchNode != null && patchNode.has("account") && patchNode.get("account").isNull()) {
                    throw new IllegalArgumentException("Account cannot be null");
                }
                if (patchNode != null && patchNode.has("account")) {
                    rejectAccountChange(existingTransactionIngestion, transactionIngestionDTO.getAccount());
                }
                if (patchNode != null && patchNode.has("ingestionType") && patchNode.get("ingestionType").isNull()) {
                    throw new IllegalArgumentException("Ingestion type cannot be null");
                }
                if (patchNode != null && patchNode.has("ingestionType")) {
                    rejectIngestionTypeChange(existingTransactionIngestion, transactionIngestionDTO.getIngestionType());
                }
                transactionIngestionMapper.partialUpdate(existingTransactionIngestion, transactionIngestionDTO);
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

    private void applyMutableFields(TransactionIngestion transactionIngestion, TransactionIngestionDTO transactionIngestionDTO) {
        if (transactionIngestionDTO.getStatus() != null) {
            transactionIngestion.setStatus(transactionIngestionDTO.getStatus());
        }
        transactionIngestion.setSourceLabel(transactionIngestionDTO.getSourceLabel());
        transactionIngestion.setCompletedAt(transactionIngestionDTO.getCompletedAt());
        transactionIngestion.setErrorMessage(transactionIngestionDTO.getErrorMessage());
        if (transactionIngestionDTO.getRecordsReceived() != null) {
            transactionIngestion.setRecordsReceived(transactionIngestionDTO.getRecordsReceived());
        }
        if (transactionIngestionDTO.getRecordsCreated() != null) {
            transactionIngestion.setRecordsCreated(transactionIngestionDTO.getRecordsCreated());
        }
        if (transactionIngestionDTO.getRecordsSkipped() != null) {
            transactionIngestion.setRecordsSkipped(transactionIngestionDTO.getRecordsSkipped());
        }
        if (transactionIngestionDTO.getRecordsRejected() != null) {
            transactionIngestion.setRecordsRejected(transactionIngestionDTO.getRecordsRejected());
        }
    }

    private void rejectAccountChange(TransactionIngestion existingTransactionIngestion, FinancialAccountDTO accountDTO) {
        if (accountDTO == null || accountDTO.getId() == null) {
            return;
        }
        if (!accountDTO.getId().equals(existingTransactionIngestion.getAccount().getId())) {
            throw new IllegalArgumentException("Account cannot be changed");
        }
    }

    private void rejectIngestionTypeChange(TransactionIngestion existingTransactionIngestion, IngestionType ingestionType) {
        if (ingestionType == null) {
            return;
        }
        if (existingTransactionIngestion.getIngestionType() != ingestionType) {
            throw new IllegalArgumentException("Ingestion type cannot be changed");
        }
    }
}
