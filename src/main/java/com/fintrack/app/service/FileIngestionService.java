package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.FileIngestion;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.FileIngestionDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.FileIngestionMapper;
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
 * Service Implementation for managing {@link com.fintrack.app.domain.FileIngestion}.
 */
@Service
@Transactional
public class FileIngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(FileIngestionService.class);

    private final FileIngestionRepository fileIngestionRepository;

    private final FileIngestionMapper fileIngestionMapper;

    private final CurrentUserService currentUserService;

    private final TransactionIngestionRepository transactionIngestionRepository;

    public FileIngestionService(
        FileIngestionRepository fileIngestionRepository,
        FileIngestionMapper fileIngestionMapper,
        CurrentUserService currentUserService,
        TransactionIngestionRepository transactionIngestionRepository
    ) {
        this.fileIngestionRepository = fileIngestionRepository;
        this.fileIngestionMapper = fileIngestionMapper;
        this.currentUserService = currentUserService;
        this.transactionIngestionRepository = transactionIngestionRepository;
    }

    /**
     * Save a fileIngestion.
     *
     * @param fileIngestionDTO the entity to save.
     * @return the persisted entity.
     */
    public FileIngestionDTO save(FileIngestionDTO fileIngestionDTO) {
        LOG.debug("Request to save FileIngestion : {}", fileIngestionDTO);
        if (fileIngestionDTO.getOriginalFilename() == null) {
            throw new IllegalArgumentException("Original filename is required");
        }
        if (fileIngestionDTO.getFileType() == null) {
            throw new IllegalArgumentException("File type is required");
        }
        FileIngestion fileIngestion = fileIngestionMapper.toEntity(fileIngestionDTO);
        fileIngestion.setTransactionIngestion(resolveTransactionIngestionForCreate(fileIngestionDTO.getTransactionIngestion()));
        fileIngestion.setCreatedAt(Instant.now());
        fileIngestion = fileIngestionRepository.save(fileIngestion);
        return fileIngestionMapper.toDto(fileIngestion);
    }

    /**
     * Update a fileIngestion.
     *
     * @param fileIngestionDTO the entity to save.
     * @return the persisted entity.
     */
    public FileIngestionDTO update(FileIngestionDTO fileIngestionDTO) {
        LOG.debug("Request to update FileIngestion : {}", fileIngestionDTO);
        FileIngestion existingFileIngestion = findAccessibleEntity(fileIngestionDTO.getId()).orElseThrow();
        rejectTransactionIngestionChange(existingFileIngestion, fileIngestionDTO.getTransactionIngestion());
        applyMutableFields(existingFileIngestion, fileIngestionDTO);
        existingFileIngestion = fileIngestionRepository.save(existingFileIngestion);
        return fileIngestionMapper.toDto(existingFileIngestion);
    }

    /**
     * Partially update a fileIngestion.
     *
     * @param fileIngestionDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<FileIngestionDTO> partialUpdate(FileIngestionDTO fileIngestionDTO) {
        return partialUpdate(fileIngestionDTO, null);
    }

    /**
     * Partially update a fileIngestion, applying parent changes only when present in the patch body.
     *
     * @param fileIngestionDTO the entity to update partially.
     * @param patchNode the raw patch payload.
     * @return the persisted entity.
     */
    public Optional<FileIngestionDTO> partialUpdate(FileIngestionDTO fileIngestionDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update FileIngestion : {}", fileIngestionDTO);

        return findAccessibleEntity(fileIngestionDTO.getId())
            .map(existingFileIngestion -> {
                if (patchNode != null && patchNode.has("transactionIngestion") && patchNode.get("transactionIngestion").isNull()) {
                    throw new IllegalArgumentException("Transaction ingestion cannot be null");
                }
                if (patchNode != null && patchNode.has("transactionIngestion")) {
                    rejectTransactionIngestionChange(existingFileIngestion, fileIngestionDTO.getTransactionIngestion());
                }
                if (patchNode != null && patchNode.has("createdAt")) {
                    rejectCreatedAtChange(existingFileIngestion, fileIngestionDTO.getCreatedAt());
                }
                fileIngestionMapper.partialUpdate(existingFileIngestion, fileIngestionDTO);
                return existingFileIngestion;
            })
            .map(fileIngestionRepository::save)
            .map(fileIngestionMapper::toDto);
    }

    /**
     * Get all the fileIngestions.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<FileIngestionDTO> findAll() {
        LOG.debug("Request to get all FileIngestions");
        if (currentUserService.isAdmin()) {
            return fileIngestionRepository
                .findAllWithRelationships()
                .stream()
                .map(fileIngestionMapper::toDto)
                .collect(Collectors.toCollection(LinkedList::new));
        }
        return fileIngestionRepository
            .findAllWithRelationshipsByTransactionIngestionAccountUserLogin(currentUserService.getCurrentUserLogin())
            .stream()
            .map(fileIngestionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one fileIngestion by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<FileIngestionDTO> findOne(Long id) {
        LOG.debug("Request to get FileIngestion : {}", id);
        return findAccessibleEntity(id).map(fileIngestionMapper::toDto);
    }

    /**
     * Returns whether the current user can access the file ingestion.
     *
     * @param id the id of the entity.
     * @return true when the file ingestion exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the fileIngestion by id.
     *
     * @param id the id of the entity.
     * @return true when the file ingestion was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete FileIngestion : {}", id);
        Optional<FileIngestion> fileIngestion = findAccessibleEntity(id);
        if (fileIngestion.isEmpty()) {
            return false;
        }
        fileIngestionRepository.deleteById(id);
        return true;
    }

    private Optional<FileIngestion> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return fileIngestionRepository.findOneWithRelationships(id);
        }
        return fileIngestionRepository.findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(
            id,
            currentUserService.getCurrentUserLogin()
        );
    }

    private TransactionIngestion resolveTransactionIngestionForCreate(TransactionIngestionDTO transactionIngestionDTO) {
        if (transactionIngestionDTO == null || transactionIngestionDTO.getId() == null) {
            throw new IllegalArgumentException("Transaction ingestion is required");
        }
        TransactionIngestion transactionIngestion = findAccessibleTransactionIngestion(transactionIngestionDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Transaction ingestion is not accessible")
        );
        validateFileIngestionType(transactionIngestion);
        if (fileIngestionRepository.existsByTransactionIngestionId(transactionIngestion.getId())) {
            throw new IllegalArgumentException("Transaction ingestion already has file ingestion");
        }
        return transactionIngestion;
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

    private void validateFileIngestionType(TransactionIngestion transactionIngestion) {
        if (transactionIngestion.getIngestionType() != IngestionType.FILE) {
            throw new IllegalArgumentException("Transaction ingestion must be a file ingestion");
        }
    }

    private void rejectTransactionIngestionChange(FileIngestion existingFileIngestion, TransactionIngestionDTO transactionIngestionDTO) {
        if (transactionIngestionDTO == null || transactionIngestionDTO.getId() == null) {
            return;
        }
        if (!transactionIngestionDTO.getId().equals(existingFileIngestion.getTransactionIngestion().getId())) {
            throw new IllegalArgumentException("Transaction ingestion cannot be changed");
        }
    }

    private void rejectCreatedAtChange(FileIngestion existingFileIngestion, Instant createdAt) {
        if (createdAt == null) {
            return;
        }
        if (!createdAt.equals(existingFileIngestion.getCreatedAt())) {
            throw new IllegalArgumentException("Created at cannot be changed");
        }
    }

    private void applyMutableFields(FileIngestion fileIngestion, FileIngestionDTO fileIngestionDTO) {
        fileIngestion.setOriginalFilename(fileIngestionDTO.getOriginalFilename());
        fileIngestion.setFileType(fileIngestionDTO.getFileType());
        fileIngestion.setContentType(fileIngestionDTO.getContentType());
        fileIngestion.setFileSizeBytes(fileIngestionDTO.getFileSizeBytes());
        fileIngestion.setChecksum(fileIngestionDTO.getChecksum());
        fileIngestion.setStorageKey(fileIngestionDTO.getStorageKey());
        fileIngestion.setParserName(fileIngestionDTO.getParserName());
        fileIngestion.setParserVersion(fileIngestionDTO.getParserVersion());
        fileIngestion.setStatementStartDate(fileIngestionDTO.getStatementStartDate());
        fileIngestion.setStatementEndDate(fileIngestionDTO.getStatementEndDate());
    }
}
