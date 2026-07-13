package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.ApiIngestion;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.ApiIngestionRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.ApiIngestionCreateRequestDTO;
import com.fintrack.app.service.dto.ApiIngestionDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.ApiIngestionMapper;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
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

    public ApiIngestionDTO save(ApiIngestionCreateRequestDTO createRequest) {
        LOG.debug("Request to save ApiIngestion : {}", createRequest);
        ApiIngestion apiIngestion = toCreateEntity(createRequest);
        normalizeCreateFields(apiIngestion);
        resolveParentsForCreate(apiIngestion, createRequest);
        validateRequestIdForCreate(apiIngestion);

        Instant now = Instant.now();
        apiIngestion.setCreatedAt(now);
        apiIngestion.setReceivedAt(now);

        apiIngestion = apiIngestionRepository.save(apiIngestion);
        return apiIngestionMapper.toDto(apiIngestion);
    }

    public ApiIngestionDTO update(ApiIngestionDTO apiIngestionDTO) {
        return update(apiIngestionDTO, null);
    }

    public ApiIngestionDTO update(ApiIngestionDTO apiIngestionDTO, JsonNode updateNode) {
        LOG.debug("Request to update ApiIngestion : {}", apiIngestionDTO);
        ApiIngestion existing = findAccessibleApiIngestionEntity(apiIngestionDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Entity not found")
        );
        rejectImmutableFieldChanges(existing, apiIngestionDTO, updateNode);
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
                rejectImmutableFieldChanges(existing, apiIngestionDTO, patchNode);
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
        throw new IllegalArgumentException("Api ingestion cannot be deleted directly");
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

    private ApiIngestion toCreateEntity(ApiIngestionCreateRequestDTO createRequest) {
        ApiIngestion apiIngestion = new ApiIngestion();
        apiIngestion.setRequestId(createRequest.getRequestId());
        apiIngestion.setIdempotencyKey(createRequest.getIdempotencyKey());
        apiIngestion.setSourceSystem(createRequest.getSourceSystem());
        apiIngestion.setApiVersion(createRequest.getApiVersion());
        apiIngestion.setEndpoint(createRequest.getEndpoint());
        apiIngestion.setClientReference(createRequest.getClientReference());
        return apiIngestion;
    }

    private void resolveParentsForCreate(ApiIngestion apiIngestion, ApiIngestionCreateRequestDTO createRequest) {
        TransactionIngestion transactionIngestion = resolveTransactionIngestion(createRequest.getTransactionIngestion());
        ApiAccessToken apiAccessToken = resolveApiAccessToken(createRequest.getApiAccessTokenId());
        validateSameOwner(transactionIngestion, apiAccessToken);
        validateApiTransactionIngestion(transactionIngestion);
        validateTransactionIngestionNotAlreadyLinked(transactionIngestion);
        apiIngestion.setTransactionIngestion(transactionIngestion);
        applyTokenSnapshots(apiIngestion, apiAccessToken);
    }

    private void applyTokenSnapshots(ApiIngestion apiIngestion, ApiAccessToken apiAccessToken) {
        apiIngestion.setApiTokenIdSnapshot(apiAccessToken.getId());
        apiIngestion.setApiTokenPrefixSnapshot(normalizeOptionalString(apiAccessToken.getTokenPrefix(), "Api token prefix snapshot", 20));
        apiIngestion.setApiTokenNameSnapshot(normalizeOptionalString(apiAccessToken.getName(), "Api token name snapshot", 100));
    }

    private TransactionIngestion resolveTransactionIngestion(TransactionIngestionDTO transactionIngestionDTO) {
        if (transactionIngestionDTO == null || transactionIngestionDTO.getId() == null) {
            throw new IllegalArgumentException("Transaction ingestion is required");
        }
        return findAccessibleTransactionIngestion(transactionIngestionDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Transaction ingestion not found")
        );
    }

    private ApiAccessToken resolveApiAccessToken(Long apiAccessTokenId) {
        if (apiAccessTokenId == null) {
            throw new IllegalArgumentException("Api access token is required");
        }
        return apiAccessTokenService
            .findAccessibleApiAccessTokenEntity(apiAccessTokenId)
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
        if (apiIngestionRepository.existsByRequestId(apiIngestion.getRequestId())) {
            throw new IllegalArgumentException("Request id already exists");
        }
    }

    private void normalizeCreateFields(ApiIngestion apiIngestion) {
        apiIngestion.setRequestId(normalizeRequiredString(apiIngestion.getRequestId(), "Request id", 100));
        apiIngestion.setIdempotencyKey(normalizeOptionalString(apiIngestion.getIdempotencyKey(), "Idempotency key", 150));
        apiIngestion.setSourceSystem(normalizeOptionalString(apiIngestion.getSourceSystem(), "Source system", 100));
        apiIngestion.setApiVersion(normalizeRequiredString(apiIngestion.getApiVersion(), "Api version", 20));
        apiIngestion.setEndpoint(normalizeRequiredString(apiIngestion.getEndpoint(), "Endpoint", 150));
        apiIngestion.setClientReference(normalizeOptionalString(apiIngestion.getClientReference(), "Client reference", 150));
    }

    private void rejectImmutableFieldChanges(ApiIngestion existing, ApiIngestionDTO apiIngestionDTO, JsonNode updateNode) {
        rejectTransactionIngestionChange(existing, apiIngestionDTO, updateNode);
        rejectRequiredStringChange(existing.getRequestId(), apiIngestionDTO.getRequestId(), "requestId", "Request id", 100, updateNode);
        rejectOptionalStringChange(
            existing.getIdempotencyKey(),
            apiIngestionDTO.getIdempotencyKey(),
            "idempotencyKey",
            "Idempotency key",
            150,
            updateNode
        );
        rejectOptionalStringChange(
            existing.getSourceSystem(),
            apiIngestionDTO.getSourceSystem(),
            "sourceSystem",
            "Source system",
            100,
            updateNode
        );
        rejectRequiredStringChange(existing.getApiVersion(), apiIngestionDTO.getApiVersion(), "apiVersion", "Api version", 20, updateNode);
        rejectRequiredStringChange(existing.getEndpoint(), apiIngestionDTO.getEndpoint(), "endpoint", "Endpoint", 150, updateNode);
        rejectOptionalStringChange(
            existing.getClientReference(),
            apiIngestionDTO.getClientReference(),
            "clientReference",
            "Client reference",
            150,
            updateNode
        );
        rejectTimestampChange(existing.getReceivedAt(), apiIngestionDTO.getReceivedAt(), "receivedAt", updateNode);
        rejectTimestampChange(existing.getCreatedAt(), apiIngestionDTO.getCreatedAt(), "createdAt", updateNode);
        rejectLongChange(
            existing.getApiTokenIdSnapshot(),
            apiIngestionDTO.getApiTokenIdSnapshot(),
            "apiTokenIdSnapshot",
            "Api token id snapshot",
            updateNode
        );
        rejectOptionalStringChange(
            existing.getApiTokenPrefixSnapshot(),
            apiIngestionDTO.getApiTokenPrefixSnapshot(),
            "apiTokenPrefixSnapshot",
            "Api token prefix snapshot",
            20,
            updateNode
        );
        rejectOptionalStringChange(
            existing.getApiTokenNameSnapshot(),
            apiIngestionDTO.getApiTokenNameSnapshot(),
            "apiTokenNameSnapshot",
            "Api token name snapshot",
            100,
            updateNode
        );
    }

    private void rejectTransactionIngestionChange(ApiIngestion existing, ApiIngestionDTO apiIngestionDTO, JsonNode updateNode) {
        if (!shouldValidateField(updateNode, "transactionIngestion")) {
            return;
        }
        if (updateNode != null && updateNode.has("transactionIngestion") && updateNode.get("transactionIngestion").isNull()) {
            throw new IllegalArgumentException("Transaction ingestion cannot be changed");
        }
        if (apiIngestionDTO.getTransactionIngestion() == null || apiIngestionDTO.getTransactionIngestion().getId() == null) {
            throw new IllegalArgumentException("Transaction ingestion cannot be changed");
        }
        Long incomingId = apiIngestionDTO.getTransactionIngestion().getId();
        if (!incomingId.equals(existing.getTransactionIngestion().getId())) {
            throw new IllegalArgumentException("Transaction ingestion cannot be changed");
        }
    }

    private void rejectRequiredStringChange(
        String existingValue,
        String incomingValue,
        String jsonFieldName,
        String label,
        int maxLength,
        JsonNode updateNode
    ) {
        if (!shouldValidateField(updateNode, jsonFieldName)) {
            return;
        }
        String normalizedIncoming = normalizeRequiredString(incomingValue, label, maxLength);
        String normalizedExisting = normalizeRequiredString(existingValue, label, maxLength);
        if (!normalizedIncoming.equals(normalizedExisting)) {
            throw new IllegalArgumentException(label + " cannot be changed");
        }
    }

    private void rejectOptionalStringChange(
        String existingValue,
        String incomingValue,
        String jsonFieldName,
        String label,
        int maxLength,
        JsonNode updateNode
    ) {
        if (!shouldValidateField(updateNode, jsonFieldName)) {
            return;
        }
        String normalizedIncoming = normalizeOptionalString(incomingValue, label, maxLength);
        String normalizedExisting = normalizeOptionalString(existingValue, label, maxLength);
        if (!Objects.equals(normalizedIncoming, normalizedExisting)) {
            throw new IllegalArgumentException(label + " cannot be changed");
        }
    }

    private void rejectLongChange(Long existingValue, Long incomingValue, String jsonFieldName, String label, JsonNode updateNode) {
        if (!shouldValidateField(updateNode, jsonFieldName)) {
            return;
        }
        if (!Objects.equals(incomingValue, existingValue)) {
            throw new IllegalArgumentException(label + " cannot be changed");
        }
    }

    private void rejectTimestampChange(Instant existingValue, Instant incomingValue, String jsonFieldName, JsonNode updateNode) {
        if (!shouldValidateField(updateNode, jsonFieldName)) {
            return;
        }
        if (incomingValue == null || !incomingValue.equals(existingValue)) {
            throw new IllegalArgumentException(jsonFieldName + " cannot be changed");
        }
    }

    private boolean shouldValidateField(JsonNode updateNode, String fieldName) {
        return updateNode == null || updateNode.has(fieldName);
    }

    private String normalizeRequiredString(String value, String label, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        validateMaxLength(normalized, label, maxLength);
        return normalized;
    }

    private String normalizeOptionalString(String value, String label, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        validateMaxLength(normalized, label, maxLength);
        return normalized;
    }

    private void validateMaxLength(String value, String label, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be at most " + maxLength + " characters");
        }
    }
}
