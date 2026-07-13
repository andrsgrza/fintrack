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
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
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

    private static final Pattern HEX_CHECKSUM = Pattern.compile("^[0-9a-fA-F]+$");

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
        if (fileIngestionDTO.getFileType() == null) {
            throw new IllegalArgumentException("File type is required");
        }
        FileIngestion fileIngestion = fileIngestionMapper.toEntity(fileIngestionDTO);
        fileIngestion.setTransactionIngestion(resolveTransactionIngestionForCreate(fileIngestionDTO.getTransactionIngestion()));
        normalizeCreateFields(fileIngestion);
        validateStatementDateRange(fileIngestion.getStatementStartDate(), fileIngestion.getStatementEndDate());
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
        return update(fileIngestionDTO, null);
    }

    /**
     * Update a fileIngestion.
     *
     * @param fileIngestionDTO the entity to save.
     * @param updateNode the raw update payload.
     * @return the persisted entity.
     */
    public FileIngestionDTO update(FileIngestionDTO fileIngestionDTO, JsonNode updateNode) {
        LOG.debug("Request to update FileIngestion : {}", fileIngestionDTO);
        FileIngestion existingFileIngestion = findAccessibleEntity(fileIngestionDTO.getId()).orElseThrow();
        if (updateNode == null || updateNode.has("transactionIngestion")) {
            rejectTransactionIngestionChange(existingFileIngestion, fileIngestionDTO.getTransactionIngestion());
        }
        if (updateNode == null || updateNode.has("createdAt")) {
            rejectCreatedAtChange(existingFileIngestion, fileIngestionDTO.getCreatedAt(), true);
        }
        rejectImmutableFieldChanges(existingFileIngestion, fileIngestionDTO, updateNode);
        applyMutableFields(existingFileIngestion, fileIngestionDTO);
        validateStatementDateRange(existingFileIngestion.getStatementStartDate(), existingFileIngestion.getStatementEndDate());
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
                if (patchNode != null && patchNode.has("createdAt") && patchNode.get("createdAt").isNull()) {
                    throw new IllegalArgumentException("Created at cannot be null");
                }
                if (patchNode != null && patchNode.has("transactionIngestion")) {
                    rejectTransactionIngestionChange(existingFileIngestion, fileIngestionDTO.getTransactionIngestion());
                }
                if (patchNode != null && patchNode.has("createdAt")) {
                    rejectCreatedAtChange(existingFileIngestion, fileIngestionDTO.getCreatedAt(), true);
                }
                rejectImmutableFieldChanges(existingFileIngestion, fileIngestionDTO, patchNode);
                fileIngestionMapper.partialUpdate(existingFileIngestion, fileIngestionDTO);
                if (patchNode != null && patchNode.has("statementStartDate")) {
                    existingFileIngestion.setStatementStartDate(fileIngestionDTO.getStatementStartDate());
                }
                if (patchNode != null && patchNode.has("statementEndDate")) {
                    existingFileIngestion.setStatementEndDate(fileIngestionDTO.getStatementEndDate());
                }
                validateStatementDateRange(existingFileIngestion.getStatementStartDate(), existingFileIngestion.getStatementEndDate());
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
        throw new IllegalArgumentException("File ingestion cannot be deleted directly");
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
            throw new IllegalArgumentException("Transaction ingestion is required");
        }
        if (!transactionIngestionDTO.getId().equals(existingFileIngestion.getTransactionIngestion().getId())) {
            throw new IllegalArgumentException("Transaction ingestion cannot be changed");
        }
    }

    private void rejectCreatedAtChange(FileIngestion existingFileIngestion, Instant createdAt, boolean requiredWhenPresent) {
        if (createdAt == null && requiredWhenPresent) {
            throw new IllegalArgumentException("Created at cannot be null");
        }
        if (createdAt != null && !createdAt.equals(existingFileIngestion.getCreatedAt())) {
            throw new IllegalArgumentException("Created at cannot be changed");
        }
    }

    private void applyMutableFields(FileIngestion fileIngestion, FileIngestionDTO fileIngestionDTO) {
        fileIngestion.setStatementStartDate(fileIngestionDTO.getStatementStartDate());
        fileIngestion.setStatementEndDate(fileIngestionDTO.getStatementEndDate());
    }

    private void normalizeCreateFields(FileIngestion fileIngestion) {
        fileIngestion.setOriginalFilename(normalizeRequiredString(fileIngestion.getOriginalFilename(), "Original filename", 255));
        fileIngestion.setContentType(normalizeOptionalString(fileIngestion.getContentType(), "Content type", 100));
        fileIngestion.setChecksum(normalizeChecksum(fileIngestion.getChecksum()));
        fileIngestion.setStorageKey(normalizeOptionalString(fileIngestion.getStorageKey(), "Storage key", 500));
        fileIngestion.setParserName(normalizeOptionalString(fileIngestion.getParserName(), "Parser name", 100));
        fileIngestion.setParserVersion(normalizeOptionalString(fileIngestion.getParserVersion(), "Parser version", 50));
        if (fileIngestion.getFileSizeBytes() != null && fileIngestion.getFileSizeBytes() < 0) {
            throw new IllegalArgumentException("File size bytes cannot be negative");
        }
    }

    private void rejectImmutableFieldChanges(FileIngestion existingFileIngestion, FileIngestionDTO fileIngestionDTO, JsonNode payloadNode) {
        if (payloadNode == null || payloadNode.has("originalFilename")) {
            String normalizedOriginalFilename = normalizeRequiredString(fileIngestionDTO.getOriginalFilename(), "Original filename", 255);
            rejectStringChange(
                "Original filename",
                normalizeRequiredString(existingFileIngestion.getOriginalFilename(), "Original filename", 255),
                normalizedOriginalFilename
            );
        }
        if (payloadNode == null || payloadNode.has("fileType")) {
            if (fileIngestionDTO.getFileType() == null) {
                throw new IllegalArgumentException("File type cannot be null");
            }
            if (existingFileIngestion.getFileType() != fileIngestionDTO.getFileType()) {
                throw new IllegalArgumentException("File type cannot be changed");
            }
        }
        if (payloadNode == null || payloadNode.has("contentType")) {
            rejectStringChange(
                "Content type",
                normalizeOptionalString(existingFileIngestion.getContentType(), "Content type", 100),
                normalizeOptionalString(fileIngestionDTO.getContentType(), "Content type", 100)
            );
        }
        if (payloadNode == null || payloadNode.has("fileSizeBytes")) {
            if (fileIngestionDTO.getFileSizeBytes() != null && fileIngestionDTO.getFileSizeBytes() < 0) {
                throw new IllegalArgumentException("File size bytes cannot be negative");
            }
            if (!java.util.Objects.equals(existingFileIngestion.getFileSizeBytes(), fileIngestionDTO.getFileSizeBytes())) {
                throw new IllegalArgumentException("File size bytes cannot be changed");
            }
        }
        if (payloadNode == null || payloadNode.has("checksum")) {
            rejectStringChange(
                "Checksum",
                normalizeChecksum(existingFileIngestion.getChecksum()),
                normalizeChecksum(fileIngestionDTO.getChecksum())
            );
        }
        if (payloadNode == null || payloadNode.has("storageKey")) {
            rejectStringChange(
                "Storage key",
                normalizeOptionalString(existingFileIngestion.getStorageKey(), "Storage key", 500),
                normalizeOptionalString(fileIngestionDTO.getStorageKey(), "Storage key", 500)
            );
        }
        if (payloadNode == null || payloadNode.has("parserName")) {
            rejectStringChange(
                "Parser name",
                normalizeOptionalString(existingFileIngestion.getParserName(), "Parser name", 100),
                normalizeOptionalString(fileIngestionDTO.getParserName(), "Parser name", 100)
            );
        }
        if (payloadNode == null || payloadNode.has("parserVersion")) {
            rejectStringChange(
                "Parser version",
                normalizeOptionalString(existingFileIngestion.getParserVersion(), "Parser version", 50),
                normalizeOptionalString(fileIngestionDTO.getParserVersion(), "Parser version", 50)
            );
        }
    }

    private String normalizeRequiredString(String value, String fieldLabel, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(fieldLabel + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldLabel + " is required");
        }
        validateMaxLength(fieldLabel, trimmed, maxLength);
        return trimmed;
    }

    private String normalizeOptionalString(String value, String fieldLabel, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        validateMaxLength(fieldLabel, trimmed, maxLength);
        return trimmed;
    }

    private String normalizeChecksum(String checksum) {
        String normalized = normalizeOptionalString(checksum, "Checksum", 128);
        if (normalized != null && HEX_CHECKSUM.matcher(normalized).matches()) {
            return normalized.toLowerCase();
        }
        return normalized;
    }

    private void validateMaxLength(String fieldLabel, String value, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldLabel + " cannot exceed " + maxLength + " characters");
        }
    }

    private void rejectStringChange(String fieldLabel, String existingValue, String requestedValue) {
        if (!java.util.Objects.equals(existingValue, requestedValue)) {
            throw new IllegalArgumentException(fieldLabel + " cannot be changed");
        }
    }

    private void validateStatementDateRange(LocalDate statementStartDate, LocalDate statementEndDate) {
        if (statementStartDate != null && statementEndDate != null && statementStartDate.isAfter(statementEndDate)) {
            throw new IllegalArgumentException("Statement start date cannot be after statement end date");
        }
    }
}
