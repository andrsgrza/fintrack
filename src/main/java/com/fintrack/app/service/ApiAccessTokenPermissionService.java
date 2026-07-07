package com.fintrack.app.service;

import com.fintrack.app.domain.ApiAccessTokenPermission;
import com.fintrack.app.repository.ApiAccessTokenPermissionRepository;
import com.fintrack.app.service.dto.ApiAccessTokenPermissionDTO;
import com.fintrack.app.service.mapper.ApiAccessTokenPermissionMapper;
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

    public ApiAccessTokenPermissionService(
        ApiAccessTokenPermissionRepository apiAccessTokenPermissionRepository,
        ApiAccessTokenPermissionMapper apiAccessTokenPermissionMapper
    ) {
        this.apiAccessTokenPermissionRepository = apiAccessTokenPermissionRepository;
        this.apiAccessTokenPermissionMapper = apiAccessTokenPermissionMapper;
    }

    /**
     * Save a apiAccessTokenPermission.
     *
     * @param apiAccessTokenPermissionDTO the entity to save.
     * @return the persisted entity.
     */
    public ApiAccessTokenPermissionDTO save(ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO) {
        LOG.debug("Request to save ApiAccessTokenPermission : {}", apiAccessTokenPermissionDTO);
        ApiAccessTokenPermission apiAccessTokenPermission = apiAccessTokenPermissionMapper.toEntity(apiAccessTokenPermissionDTO);
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
        ApiAccessTokenPermission apiAccessTokenPermission = apiAccessTokenPermissionMapper.toEntity(apiAccessTokenPermissionDTO);
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
        LOG.debug("Request to partially update ApiAccessTokenPermission : {}", apiAccessTokenPermissionDTO);

        return apiAccessTokenPermissionRepository
            .findById(apiAccessTokenPermissionDTO.getId())
            .map(existingApiAccessTokenPermission -> {
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
        return apiAccessTokenPermissionRepository
            .findAll()
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
        return apiAccessTokenPermissionRepository.findAllWithEagerRelationships(pageable).map(apiAccessTokenPermissionMapper::toDto);
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
        return apiAccessTokenPermissionRepository.findOneWithEagerRelationships(id).map(apiAccessTokenPermissionMapper::toDto);
    }

    /**
     * Delete the apiAccessTokenPermission by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete ApiAccessTokenPermission : {}", id);
        apiAccessTokenPermissionRepository.deleteById(id);
    }
}
