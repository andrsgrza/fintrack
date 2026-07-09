package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.repository.ApiAccessTokenRepository;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.mapper.ApiAccessTokenMapper;
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

    private final ApiAccessTokenRepository apiAccessTokenRepository;

    private final ApiAccessTokenMapper apiAccessTokenMapper;

    private final CurrentUserService currentUserService;

    public ApiAccessTokenService(
        ApiAccessTokenRepository apiAccessTokenRepository,
        ApiAccessTokenMapper apiAccessTokenMapper,
        CurrentUserService currentUserService
    ) {
        this.apiAccessTokenRepository = apiAccessTokenRepository;
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
        validateTokenHashForCreate(apiAccessTokenDTO);
        ApiAccessToken apiAccessToken = apiAccessTokenMapper.toEntity(apiAccessTokenDTO);
        apiAccessToken.setUser(currentUserService.getCurrentUser());
        apiAccessToken = apiAccessTokenRepository.save(apiAccessToken);
        return apiAccessTokenMapper.toDto(apiAccessToken);
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
        apiAccessTokenRepository.deleteById(id);
        return true;
    }

    private Optional<ApiAccessToken> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return apiAccessTokenRepository.findOneWithEagerRelationships(id);
        }
        return apiAccessTokenRepository.findOneWithEagerRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void validateTokenHashForCreate(ApiAccessTokenDTO apiAccessTokenDTO) {
        if (apiAccessTokenDTO.getTokenHash() == null || apiAccessTokenDTO.getTokenHash().isBlank()) {
            throw new IllegalArgumentException("Token hash is required");
        }
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
