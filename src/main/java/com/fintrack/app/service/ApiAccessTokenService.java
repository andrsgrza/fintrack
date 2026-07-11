package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.enumeration.ApiTokenStatus;
import com.fintrack.app.repository.ApiAccessTokenPermissionRepository;
import com.fintrack.app.repository.ApiAccessTokenRepository;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.mapper.ApiAccessTokenMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.ApiAccessToken}.
 */
@Service
@Transactional
public class ApiAccessTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(ApiAccessTokenService.class);

    private static final String TOKEN_PREFIX_LABEL = "ftk_";

    private static final int TOKEN_RANDOM_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiAccessTokenRepository apiAccessTokenRepository;

    private final ApiAccessTokenPermissionRepository apiAccessTokenPermissionRepository;

    private final ApiAccessTokenMapper apiAccessTokenMapper;

    private final CurrentUserService currentUserService;

    public ApiAccessTokenService(
        ApiAccessTokenRepository apiAccessTokenRepository,
        ApiAccessTokenPermissionRepository apiAccessTokenPermissionRepository,
        ApiAccessTokenMapper apiAccessTokenMapper,
        CurrentUserService currentUserService
    ) {
        this.apiAccessTokenRepository = apiAccessTokenRepository;
        this.apiAccessTokenPermissionRepository = apiAccessTokenPermissionRepository;
        this.apiAccessTokenMapper = apiAccessTokenMapper;
        this.currentUserService = currentUserService;
    }

    /**
     * Save a apiAccessToken.
     *
     * @param apiAccessTokenDTO the entity to save.
     * @return the persisted entity.
     */
    public ApiAccessTokenDTO save(ApiAccessTokenDTO apiAccessTokenDTO) {
        LOG.debug("Request to save ApiAccessToken : {}", apiAccessTokenDTO);
        String rawToken = applyCreateDefaults(apiAccessTokenDTO);
        ApiAccessToken apiAccessToken = apiAccessTokenMapper.toEntity(apiAccessTokenDTO);
        apiAccessToken.setUser(currentUserService.getCurrentUser());
        apiAccessToken = apiAccessTokenRepository.save(apiAccessToken);
        ApiAccessTokenDTO result = apiAccessTokenMapper.toDto(apiAccessToken);
        if (rawToken != null) {
            result.setRawToken(rawToken);
        }
        return result;
    }

    /**
     * Update a apiAccessToken.
     *
     * @param apiAccessTokenDTO the entity to save.
     * @return the persisted entity.
     */
    public ApiAccessTokenDTO update(ApiAccessTokenDTO apiAccessTokenDTO) {
        LOG.debug("Request to update ApiAccessToken : {}", apiAccessTokenDTO);
        ApiAccessToken existingApiAccessToken = findAccessibleEntity(apiAccessTokenDTO.getId()).orElseThrow();
        rejectTokenSecretChange(existingApiAccessToken, apiAccessTokenDTO);
        ApiAccessToken apiAccessToken = apiAccessTokenMapper.toEntity(apiAccessTokenDTO);
        apiAccessToken.setUser(existingApiAccessToken.getUser());
        apiAccessToken.setTokenHash(existingApiAccessToken.getTokenHash());
        apiAccessToken.setTokenPrefix(existingApiAccessToken.getTokenPrefix());
        apiAccessToken = apiAccessTokenRepository.save(apiAccessToken);
        return apiAccessTokenMapper.toDto(apiAccessToken);
    }

    /**
     * Partially update a apiAccessToken.
     *
     * @param apiAccessTokenDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ApiAccessTokenDTO> partialUpdate(ApiAccessTokenDTO apiAccessTokenDTO) {
        return partialUpdate(apiAccessTokenDTO, null);
    }

    /**
     * Partially update a apiAccessToken, applying owner and token secret changes only when present in the patch body.
     *
     * @param apiAccessTokenDTO the entity to update partially.
     * @param patchNode the raw patch payload.
     * @return the persisted entity.
     */
    public Optional<ApiAccessTokenDTO> partialUpdate(ApiAccessTokenDTO apiAccessTokenDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update ApiAccessToken : {}", apiAccessTokenDTO);

        return findAccessibleEntity(apiAccessTokenDTO.getId())
            .map(existingApiAccessToken -> {
                if (patchNode != null && patchNode.has("user") && patchNode.get("user").isNull()) {
                    throw new IllegalArgumentException("User cannot be null");
                }
                rejectTokenSecretChangeForPatch(existingApiAccessToken, apiAccessTokenDTO, patchNode);
                apiAccessTokenMapper.partialUpdate(existingApiAccessToken, apiAccessTokenDTO);
                return existingApiAccessToken;
            })
            .map(apiAccessTokenRepository::save)
            .map(apiAccessTokenMapper::toDto);
    }

    /**
     * Get all the apiAccessTokens.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<ApiAccessTokenDTO> findAll() {
        LOG.debug("Request to get all ApiAccessTokens");
        if (currentUserService.isAdmin()) {
            return apiAccessTokenRepository
                .findAllWithEagerRelationships()
                .stream()
                .map(apiAccessTokenMapper::toDto)
                .collect(Collectors.toCollection(LinkedList::new));
        }
        return apiAccessTokenRepository
            .findAllWithEagerRelationshipsByUserLogin(currentUserService.getCurrentUserLogin())
            .stream()
            .map(apiAccessTokenMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get all the apiAccessTokens with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<ApiAccessTokenDTO> findAllWithEagerRelationships(Pageable pageable) {
        if (currentUserService.isAdmin()) {
            return apiAccessTokenRepository.findAllWithEagerRelationships(pageable).map(apiAccessTokenMapper::toDto);
        }
        throw new UnsupportedOperationException("Paged access is only supported for admin users");
    }

    /**
     * Get one apiAccessToken by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ApiAccessTokenDTO> findOne(Long id) {
        LOG.debug("Request to get ApiAccessToken : {}", id);
        return findAccessibleEntity(id).map(apiAccessTokenMapper::toDto);
    }

    /**
     * Returns whether the current user can access the api access token.
     *
     * @param id the id of the entity.
     * @return true when the token exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the apiAccessToken by id.
     *
     * @param id the id of the entity.
     * @return true when the token was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete ApiAccessToken : {}", id);
        Optional<ApiAccessToken> apiAccessToken = findAccessibleEntity(id);
        if (apiAccessToken.isEmpty()) {
            return false;
        }
        apiAccessTokenPermissionRepository.deleteByApiAccessTokenId(id);
        apiAccessTokenRepository.deleteById(id);
        return true;
    }

    /**
     * Returns the api access token entity when it is visible to the current user.
     *
     * @param id the id of the entity.
     * @return the entity when accessible.
     */
    @Transactional(readOnly = true)
    public Optional<ApiAccessToken> findAccessibleApiAccessTokenEntity(Long id) {
        return findAccessibleEntity(id);
    }

    private Optional<ApiAccessToken> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return apiAccessTokenRepository.findOneWithEagerRelationships(id);
        }
        return apiAccessTokenRepository.findOneWithEagerRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private String applyCreateDefaults(ApiAccessTokenDTO apiAccessTokenDTO) {
        String rawToken = null;
        if (apiAccessTokenDTO.getTokenHash() == null || apiAccessTokenDTO.getTokenHash().isBlank()) {
            rawToken = generateRawToken();
            apiAccessTokenDTO.setTokenHash(hashToken(rawToken));
            apiAccessTokenDTO.setTokenPrefix(extractTokenPrefix(rawToken));
        } else {
            validateTokenHashForCreate(apiAccessTokenDTO);
            if (apiAccessTokenDTO.getTokenPrefix() == null || apiAccessTokenDTO.getTokenPrefix().isBlank()) {
                throw new IllegalArgumentException("Token prefix is required when token hash is provided");
            }
        }
        if (apiAccessTokenDTO.getStatus() == null) {
            apiAccessTokenDTO.setStatus(ApiTokenStatus.ACTIVE);
        }
        Instant now = Instant.now();
        apiAccessTokenDTO.setCreatedAt(now);
        apiAccessTokenDTO.setUpdatedAt(now);
        return rawToken;
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return TOKEN_PREFIX_LABEL + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String extractTokenPrefix(String rawToken) {
        return rawToken.length() <= 20 ? rawToken : rawToken.substring(0, 20);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void validateTokenHashForCreate(ApiAccessTokenDTO apiAccessTokenDTO) {
        if (apiAccessTokenRepository.existsByTokenHash(apiAccessTokenDTO.getTokenHash())) {
            throw new IllegalArgumentException("Token hash already exists");
        }
    }

    private void rejectTokenSecretChange(ApiAccessToken existingApiAccessToken, ApiAccessTokenDTO apiAccessTokenDTO) {
        if (apiAccessTokenDTO.getTokenHash() != null && !apiAccessTokenDTO.getTokenHash().equals(existingApiAccessToken.getTokenHash())) {
            throw new IllegalArgumentException("Token hash cannot be changed");
        }
        if (
            apiAccessTokenDTO.getTokenPrefix() != null &&
            !apiAccessTokenDTO.getTokenPrefix().equals(existingApiAccessToken.getTokenPrefix())
        ) {
            throw new IllegalArgumentException("Token prefix cannot be changed");
        }
    }

    private void rejectTokenSecretChangeForPatch(
        ApiAccessToken existingApiAccessToken,
        ApiAccessTokenDTO apiAccessTokenDTO,
        JsonNode patchNode
    ) {
        if (patchNode == null) {
            rejectTokenSecretChange(existingApiAccessToken, apiAccessTokenDTO);
            return;
        }
        if (patchNode.has("tokenHash") && !patchNode.get("tokenHash").isNull()) {
            String tokenHash = apiAccessTokenDTO.getTokenHash();
            if (tokenHash != null && !tokenHash.equals(existingApiAccessToken.getTokenHash())) {
                throw new IllegalArgumentException("Token hash cannot be changed");
            }
        }
        if (patchNode.has("tokenPrefix") && !patchNode.get("tokenPrefix").isNull()) {
            String tokenPrefix = apiAccessTokenDTO.getTokenPrefix();
            if (tokenPrefix != null && !tokenPrefix.equals(existingApiAccessToken.getTokenPrefix())) {
                throw new IllegalArgumentException("Token prefix cannot be changed");
            }
        }
    }
}
