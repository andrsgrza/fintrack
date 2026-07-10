package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.ApiIngestion;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.ApiIngestionRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.dto.ApiIngestionDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.ApiIngestionMapper;
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
 * Service Implementation for managing {@link com.fintrack.app.domain.ApiIngestion}.
 */
@Service
@Transactional
public class ApiIngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(ApiIngestionService.class);

    private final ApiIngestionRepository apiIngestionRepository;
    private final ApiIngestionMapper apiIngestionMapper;
    private final TransactionIngestionRepository transactionIngestionRepository;
    private final ApiAccessTokenService apiAccessTokenService;
    private final CurrentUserService currentUserService;

    public ApiIngestionService(
        ApiIngestionRepository apiIngestionRepository,
        ApiIngestionMapper apiIngestionMapper,
        TransactionIngestionRepository transactionIngestionRepository,
        ApiAccessTokenService apiAccessTokenService,
        CurrentUserService currentUserService
    ) {
        this.apiIngestionRepository = apiIngestionRepository;
        this.apiIngestionMapper = apiIngestionMapper;
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.apiAccessTokenService = apiAccessTokenService;
        this.currentUserService = currentUserService;
    }

    public ApiIngestionDTO save(ApiIngestionDTO apiIngestionDTO) {
        LOG.debug("Request to save ApiIngestion : {}", apiIngestionDTO);
        ApiIngestion apiIngestion = apiIngestionMapper.toEntity(apiIngestionDTO);
        Instant now = Instant.now();
        apiIngestion.setCreatedAt(now);
        apiIngestion.setReceivedAt(now);
        resolveParentsForCreate(apiIngestion, apiIngestionDTO);
        validateRequestIdForCreate(apiIngestion);
        apiIngestion = apiIngestionRepository.save(apiIngestion);
        return apiIngestionMapper.toDto(apiIngestion);
    }

    public ApiIngestionDTO update(ApiIngestionDTO apiIngestionDTO) {
        LOG.debug("Request to update ApiIngestion : {}", apiIngestionDTO);
        ApiIngestion existing = findAccessibleApiIngestionEntity(apiIngestionDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Entity not found")
        );
        rejectImmutableFieldChanges(existing, apiIngestionDTO);
        apiIngestionMapper.partialUpdate(existing, apiIngestionDTO);
        existing = apiIngestionRepository.save(existing);
        return apiIngestionMapper.toDto(existing);
    }

    public Optional<ApiIngestionDTO> partialUpdate(ApiIngestionDTO apiIngestionDTO) {
        return partialUpdate(apiIngestionDTO, null);
    }

    public Optional<ApiIngestionDTO> partialUpdate(ApiIngestionDTO apiIngestionDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update ApiIngestion : {}", apiIngestionDTO);
        return findAccessibleApiIngestionEntity(apiIngestionDTO.getId())
            .map(existing -> {
                if (patchNode != null && patchNode.has("transactionIngestion") && patchNode.get("transactionIngestion").isNull()) {
                    throw new IllegalArgumentException("Transaction ingestion cannot be changed");
                }
                if (patchNode != null && patchNode.has("apiAccessToken") && patchNode.get("apiAccessToken").isNull()) {
                    throw new IllegalArgumentException("Api access token cannot be changed");
                }
                if (patchNode != null && patchNode.has("requestId")) {
                    rejectRequestIdChange(existing, apiIngestionDTO);
                }
                if (patchNode != null && patchNode.has("transactionIngestion")) {
                    rejectTransactionIngestionChange(existing, apiIngestionDTO);
                }
                if (patchNode != null && patchNode.has("apiAccessToken")) {
                    rejectApiAccessTokenChange(existing, apiIngestionDTO);
                }
                if (patchNode != null && patchNode.has("createdAt")) {
                    rejectTimestampChange(existing.getCreatedAt(), apiIngestionDTO.getCreatedAt(), "createdAt");
                }
                if (patchNode != null && patchNode.has("receivedAt")) {
                    rejectTimestampChange(existing.getReceivedAt(), apiIngestionDTO.getReceivedAt(), "receivedAt");
                }
                apiIngestionMapper.partialUpdate(existing, apiIngestionDTO);
                return existing;
            })
            .map(apiIngestionRepository::save)
            .map(apiIngestionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<ApiIngestionDTO> findAll() {
        LOG.debug("Request to get all ApiIngestions");
        if (currentUserService.isAdmin()) {
            return apiIngestionRepository
                .findAllWithEagerRelationships()
                .stream()
                .map(apiIngestionMapper::toDto)
                .collect(Collectors.toCollection(LinkedList::new));
        }
        return apiIngestionRepository
            .findAllWithToOneRelationshipsByUserLogin(currentUserService.getCurrentUserLogin())
            .stream()
            .map(apiIngestionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    @Transactional(readOnly = true)
    public Optional<ApiIngestionDTO> findOne(Long id) {
        LOG.debug("Request to get ApiIngestion : {}", id);
        return findAccessibleApiIngestionEntity(id).map(apiIngestionMapper::toDto);
    }

    public boolean delete(Long id) {
        LOG.debug("Request to delete ApiIngestion : {}", id);
        Optional<ApiIngestion> apiIngestion = findAccessibleApiIngestionEntity(id);
        if (apiIngestion.isEmpty()) {
            return false;
        }
        apiIngestionRepository.deleteById(id);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleApiIngestionEntity(id).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<ApiIngestion> findAccessibleApiIngestionEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return apiIngestionRepository.findOneWithEagerRelationships(id);
        }
        return apiIngestionRepository.findOneWithToOneRelationshipsByUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void resolveParentsForCreate(ApiIngestion apiIngestion, ApiIngestionDTO apiIngestionDTO) {
        TransactionIngestion transactionIngestion = resolveTransactionIngestion(apiIngestionDTO.getTransactionIngestion());
        ApiAccessToken apiAccessToken = resolveApiAccessToken(apiIngestionDTO.getApiAccessToken());
        validateSameOwner(transactionIngestion, apiAccessToken);
        validateApiTransactionIngestion(transactionIngestion);
        validateTransactionIngestionNotAlreadyLinked(transactionIngestion);
        apiIngestion.setTransactionIngestion(transactionIngestion);
        apiIngestion.setApiAccessToken(apiAccessToken);
    }

    private TransactionIngestion resolveTransactionIngestion(TransactionIngestionDTO transactionIngestionDTO) {
        if (transactionIngestionDTO == null || transactionIngestionDTO.getId() == null) {
            throw new IllegalArgumentException("Transaction ingestion is required");
        }
        return findAccessibleTransactionIngestion(transactionIngestionDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Transaction ingestion not found")
        );
    }

    private ApiAccessToken resolveApiAccessToken(ApiAccessTokenDTO apiAccessTokenDTO) {
        if (apiAccessTokenDTO == null || apiAccessTokenDTO.getId() == null) {
            throw new IllegalArgumentException("Api access token is required");
        }
        return apiAccessTokenService
            .findAccessibleApiAccessTokenEntity(apiAccessTokenDTO.getId())
            .orElseThrow(() -> new IllegalArgumentException("Api access token not found"));
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

    private void validateSameOwner(TransactionIngestion transactionIngestion, ApiAccessToken apiAccessToken) {
        if (
            transactionIngestion.getAccount() == null ||
            transactionIngestion.getAccount().getUser() == null ||
            apiAccessToken.getUser() == null
        ) {
            throw new IllegalArgumentException("Transaction ingestion and api access token must belong to the same owner");
        }
        if (!transactionIngestion.getAccount().getUser().getLogin().equals(apiAccessToken.getUser().getLogin())) {
            throw new IllegalArgumentException("Transaction ingestion and api access token must belong to the same owner");
        }
    }

    private void validateApiTransactionIngestion(TransactionIngestion transactionIngestion) {
        if (transactionIngestion.getIngestionType() != IngestionType.API) {
            throw new IllegalArgumentException("Transaction ingestion must have ingestion type API");
        }
    }

    private void validateTransactionIngestionNotAlreadyLinked(TransactionIngestion transactionIngestion) {
        if (apiIngestionRepository.existsByTransactionIngestionId(transactionIngestion.getId())) {
            throw new IllegalArgumentException("Transaction ingestion already has an api ingestion");
        }
    }

    private void validateRequestIdForCreate(ApiIngestion apiIngestion) {
        if (apiIngestion.getRequestId() == null || apiIngestion.getRequestId().isBlank()) {
            throw new IllegalArgumentException("Request id is required");
        }
        if (apiIngestionRepository.existsByRequestId(apiIngestion.getRequestId())) {
            throw new IllegalArgumentException("Request id already exists");
        }
    }

    private void rejectImmutableFieldChanges(ApiIngestion existing, ApiIngestionDTO apiIngestionDTO) {
        rejectTransactionIngestionChange(existing, apiIngestionDTO);
        rejectApiAccessTokenChange(existing, apiIngestionDTO);
        rejectRequestIdChange(existing, apiIngestionDTO);
        rejectTimestampChange(existing.getCreatedAt(), apiIngestionDTO.getCreatedAt(), "createdAt");
        rejectTimestampChange(existing.getReceivedAt(), apiIngestionDTO.getReceivedAt(), "receivedAt");
    }

    private void rejectTransactionIngestionChange(ApiIngestion existing, ApiIngestionDTO apiIngestionDTO) {
        if (apiIngestionDTO.getTransactionIngestion() == null) {
            throw new IllegalArgumentException("Transaction ingestion cannot be changed");
        }
        Long incomingId = apiIngestionDTO.getTransactionIngestion().getId();
        if (incomingId == null || !incomingId.equals(existing.getTransactionIngestion().getId())) {
            throw new IllegalArgumentException("Transaction ingestion cannot be changed");
        }
    }

    private void rejectApiAccessTokenChange(ApiIngestion existing, ApiIngestionDTO apiIngestionDTO) {
        if (apiIngestionDTO.getApiAccessToken() == null) {
            throw new IllegalArgumentException("Api access token cannot be changed");
        }
        Long incomingId = apiIngestionDTO.getApiAccessToken().getId();
        if (incomingId == null || !incomingId.equals(existing.getApiAccessToken().getId())) {
            throw new IllegalArgumentException("Api access token cannot be changed");
        }
    }

    private void rejectRequestIdChange(ApiIngestion existing, ApiIngestionDTO apiIngestionDTO) {
        if (apiIngestionDTO.getRequestId() == null) {
            throw new IllegalArgumentException("Request id cannot be changed");
        }
        if (!apiIngestionDTO.getRequestId().equals(existing.getRequestId())) {
            throw new IllegalArgumentException("Request id cannot be changed");
        }
    }

    private void rejectTimestampChange(Instant existingValue, Instant incomingValue, String fieldName) {
        if (incomingValue == null) {
            throw new IllegalArgumentException(fieldName + " cannot be changed");
        }
        if (!incomingValue.equals(existingValue)) {
            throw new IllegalArgumentException(fieldName + " cannot be changed");
        }
    }
}
