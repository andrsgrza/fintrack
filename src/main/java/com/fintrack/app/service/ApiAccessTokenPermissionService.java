package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.ApiAccessTokenPermission;
import com.fintrack.app.domain.enumeration.ApiPermission;
import com.fintrack.app.repository.ApiAccessTokenPermissionRepository;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.dto.ApiAccessTokenPermissionDTO;
import com.fintrack.app.service.mapper.ApiAccessTokenPermissionMapper;
import java.time.Instant;
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
 * Service Implementation for managing {@link com.fintrack.app.domain.ApiAccessTokenPermission}.
 */
@Service
@Transactional
public class ApiAccessTokenPermissionService {

    private static final Logger LOG = LoggerFactory.getLogger(ApiAccessTokenPermissionService.class);

    private final ApiAccessTokenPermissionRepository apiAccessTokenPermissionRepository;

    private final ApiAccessTokenPermissionMapper apiAccessTokenPermissionMapper;

    private final CurrentUserService currentUserService;

    private final ApiAccessTokenService apiAccessTokenService;

    public ApiAccessTokenPermissionService(
        ApiAccessTokenPermissionRepository apiAccessTokenPermissionRepository,
        ApiAccessTokenPermissionMapper apiAccessTokenPermissionMapper,
        CurrentUserService currentUserService,
        ApiAccessTokenService apiAccessTokenService
    ) {
        this.apiAccessTokenPermissionRepository = apiAccessTokenPermissionRepository;
        this.apiAccessTokenPermissionMapper = apiAccessTokenPermissionMapper;
        this.currentUserService = currentUserService;
        this.apiAccessTokenService = apiAccessTokenService;
    }

    /**
     * Save a apiAccessTokenPermission.
     *
     * @param apiAccessTokenPermissionDTO the entity to save.
     * @return the persisted entity.
     */
    public ApiAccessTokenPermissionDTO save(ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO) {
        LOG.debug("Request to save ApiAccessTokenPermission : {}", apiAccessTokenPermissionDTO);
        if (apiAccessTokenPermissionDTO.getPermission() == null) {
            throw new IllegalArgumentException("Permission is required");
        }
        ApiAccessTokenPermission apiAccessTokenPermission = apiAccessTokenPermissionMapper.toEntity(apiAccessTokenPermissionDTO);
        ApiAccessToken apiAccessToken = resolveApiAccessTokenForCreate(
            apiAccessTokenPermissionDTO.getApiAccessToken(),
            apiAccessTokenPermissionDTO.getPermission()
        );
        apiAccessTokenPermission.setApiAccessToken(apiAccessToken);
        apiAccessTokenPermission.setCreatedAt(Instant.now());
        apiAccessTokenPermission = apiAccessTokenPermissionRepository.save(apiAccessTokenPermission);
        return apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);
    }

    /**
     * Update a apiAccessTokenPermission.
     *
     * @param apiAccessTokenPermissionDTO the entity to save.
     * @return the persisted entity.
     */
    public ApiAccessTokenPermissionDTO update(ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO) {
        LOG.debug("Request to update ApiAccessTokenPermission : {}", apiAccessTokenPermissionDTO);
        ApiAccessTokenPermission existingApiAccessTokenPermission = findAccessibleEntity(apiAccessTokenPermissionDTO.getId()).orElseThrow();
        rejectApiAccessTokenChange(existingApiAccessTokenPermission, apiAccessTokenPermissionDTO.getApiAccessToken());
        rejectPermissionChange(existingApiAccessTokenPermission, apiAccessTokenPermissionDTO.getPermission());
        rejectCreatedAtChange(existingApiAccessTokenPermission, apiAccessTokenPermissionDTO.getCreatedAt());
        ApiAccessTokenPermission apiAccessTokenPermission = apiAccessTokenPermissionMapper.toEntity(apiAccessTokenPermissionDTO);
        apiAccessTokenPermission.setApiAccessToken(existingApiAccessTokenPermission.getApiAccessToken());
        apiAccessTokenPermission.setPermission(existingApiAccessTokenPermission.getPermission());
        apiAccessTokenPermission.setCreatedAt(existingApiAccessTokenPermission.getCreatedAt());
        apiAccessTokenPermission = apiAccessTokenPermissionRepository.save(apiAccessTokenPermission);
        return apiAccessTokenPermissionMapper.toDto(apiAccessTokenPermission);
    }

    /**
     * Partially update a apiAccessTokenPermission.
     *
     * @param apiAccessTokenPermissionDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ApiAccessTokenPermissionDTO> partialUpdate(ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO) {
        return partialUpdate(apiAccessTokenPermissionDTO, null);
    }

    /**
     * Partially update a apiAccessTokenPermission, applying parent changes only when present in the patch body.
     *
     * @param apiAccessTokenPermissionDTO the entity to update partially.
     * @param patchNode the raw patch payload.
     * @return the persisted entity.
     */
    public Optional<ApiAccessTokenPermissionDTO> partialUpdate(
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO,
        JsonNode patchNode
    ) {
        LOG.debug("Request to partially update ApiAccessTokenPermission : {}", apiAccessTokenPermissionDTO);

        return findAccessibleEntity(apiAccessTokenPermissionDTO.getId())
            .map(existingApiAccessTokenPermission -> {
                if (patchNode != null && patchNode.has("apiAccessToken") && patchNode.get("apiAccessToken").isNull()) {
                    throw new IllegalArgumentException("Api access token cannot be null");
                }
                if (patchNode != null && patchNode.has("apiAccessToken")) {
                    rejectApiAccessTokenChange(existingApiAccessTokenPermission, apiAccessTokenPermissionDTO.getApiAccessToken());
                }
                if (patchNode != null && patchNode.has("permission")) {
                    rejectPermissionChange(existingApiAccessTokenPermission, apiAccessTokenPermissionDTO.getPermission());
                }
                if (patchNode != null && patchNode.has("createdAt")) {
                    rejectCreatedAtChange(existingApiAccessTokenPermission, apiAccessTokenPermissionDTO.getCreatedAt());
                }
                if (patchNode == null) {
                    rejectApiAccessTokenChange(existingApiAccessTokenPermission, apiAccessTokenPermissionDTO.getApiAccessToken());
                    rejectPermissionChange(existingApiAccessTokenPermission, apiAccessTokenPermissionDTO.getPermission());
                    rejectCreatedAtChange(existingApiAccessTokenPermission, apiAccessTokenPermissionDTO.getCreatedAt());
                }
                apiAccessTokenPermissionMapper.partialUpdate(existingApiAccessTokenPermission, apiAccessTokenPermissionDTO);
                return existingApiAccessTokenPermission;
            })
            .map(apiAccessTokenPermissionRepository::save)
            .map(apiAccessTokenPermissionMapper::toDto);
    }

    /**
     * Get all the apiAccessTokenPermissions.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<ApiAccessTokenPermissionDTO> findAll() {
        LOG.debug("Request to get all ApiAccessTokenPermissions");
        if (currentUserService.isAdmin()) {
            return apiAccessTokenPermissionRepository
                .findAllWithEagerRelationships()
                .stream()
                .map(apiAccessTokenPermissionMapper::toDto)
                .collect(Collectors.toCollection(LinkedList::new));
        }
        return apiAccessTokenPermissionRepository
            .findAllWithEagerRelationshipsByTokenUserLogin(currentUserService.getCurrentUserLogin())
            .stream()
            .map(apiAccessTokenPermissionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get all the apiAccessTokenPermissions with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<ApiAccessTokenPermissionDTO> findAllWithEagerRelationships(Pageable pageable) {
        if (currentUserService.isAdmin()) {
            return apiAccessTokenPermissionRepository.findAllWithEagerRelationships(pageable).map(apiAccessTokenPermissionMapper::toDto);
        }
        throw new UnsupportedOperationException("Paged access is only supported for admin users");
    }

    /**
     * Get one apiAccessTokenPermission by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ApiAccessTokenPermissionDTO> findOne(Long id) {
        LOG.debug("Request to get ApiAccessTokenPermission : {}", id);
        return findAccessibleEntity(id).map(apiAccessTokenPermissionMapper::toDto);
    }

    /**
     * Returns whether the current user can access the api access token permission.
     *
     * @param id the id of the entity.
     * @return true when the permission exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the apiAccessTokenPermission by id.
     *
     * @param id the id of the entity.
     * @return true when the permission was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete ApiAccessTokenPermission : {}", id);
        Optional<ApiAccessTokenPermission> apiAccessTokenPermission = findAccessibleEntity(id);
        if (apiAccessTokenPermission.isEmpty()) {
            return false;
        }
        apiAccessTokenPermissionRepository.deleteById(id);
        return true;
    }

    private Optional<ApiAccessTokenPermission> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return apiAccessTokenPermissionRepository.findOneWithEagerRelationships(id);
        }
        return apiAccessTokenPermissionRepository.findOneWithEagerRelationshipsByIdAndTokenUserLogin(
            id,
            currentUserService.getCurrentUserLogin()
        );
    }

    private ApiAccessToken resolveApiAccessTokenForCreate(ApiAccessTokenDTO apiAccessTokenDTO, ApiPermission permission) {
        if (apiAccessTokenDTO == null || apiAccessTokenDTO.getId() == null) {
            throw new IllegalArgumentException("Api access token is required");
        }
        ApiAccessToken apiAccessToken = apiAccessTokenService
            .findAccessibleApiAccessTokenEntity(apiAccessTokenDTO.getId())
            .orElseThrow(() -> new IllegalArgumentException("Api access token is not accessible"));
        if (apiAccessTokenPermissionRepository.existsByApiAccessTokenIdAndPermission(apiAccessToken.getId(), permission)) {
            throw new IllegalArgumentException("Permission already exists for this api access token");
        }
        return apiAccessToken;
    }

    private void rejectApiAccessTokenChange(
        ApiAccessTokenPermission existingApiAccessTokenPermission,
        ApiAccessTokenDTO apiAccessTokenDTO
    ) {
        if (apiAccessTokenDTO == null || apiAccessTokenDTO.getId() == null) {
            return;
        }
        if (!apiAccessTokenDTO.getId().equals(existingApiAccessTokenPermission.getApiAccessToken().getId())) {
            throw new IllegalArgumentException("Api access token cannot be changed");
        }
    }

    private void rejectPermissionChange(ApiAccessTokenPermission existingApiAccessTokenPermission, ApiPermission permission) {
        if (permission == null) {
            return;
        }
        if (permission != existingApiAccessTokenPermission.getPermission()) {
            throw new IllegalArgumentException("Permission cannot be changed");
        }
    }

    private void rejectCreatedAtChange(ApiAccessTokenPermission existingApiAccessTokenPermission, Instant createdAt) {
        if (createdAt == null) {
            return;
        }
        if (!createdAt.equals(existingApiAccessTokenPermission.getCreatedAt())) {
            throw new IllegalArgumentException("Created at cannot be changed");
        }
    }
}
