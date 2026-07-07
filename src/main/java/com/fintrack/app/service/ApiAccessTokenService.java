package com.fintrack.app.service;

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

    public ApiAccessTokenService(ApiAccessTokenRepository apiAccessTokenRepository, ApiAccessTokenMapper apiAccessTokenMapper) {
        this.apiAccessTokenRepository = apiAccessTokenRepository;
        this.apiAccessTokenMapper = apiAccessTokenMapper;
    }

    /**
     * Save a apiAccessToken.
     *
     * @param apiAccessTokenDTO the entity to save.
     * @return the persisted entity.
     */
    public ApiAccessTokenDTO save(ApiAccessTokenDTO apiAccessTokenDTO) {
        LOG.debug("Request to save ApiAccessToken : {}", apiAccessTokenDTO);
        ApiAccessToken apiAccessToken = apiAccessTokenMapper.toEntity(apiAccessTokenDTO);
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
        ApiAccessToken apiAccessToken = apiAccessTokenMapper.toEntity(apiAccessTokenDTO);
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
        LOG.debug("Request to partially update ApiAccessToken : {}", apiAccessTokenDTO);

        return apiAccessTokenRepository
            .findById(apiAccessTokenDTO.getId())
            .map(existingApiAccessToken -> {
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
        return apiAccessTokenRepository
            .findAll()
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
        return apiAccessTokenRepository.findAllWithEagerRelationships(pageable).map(apiAccessTokenMapper::toDto);
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
        return apiAccessTokenRepository.findOneWithEagerRelationships(id).map(apiAccessTokenMapper::toDto);
    }

    /**
     * Delete the apiAccessToken by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete ApiAccessToken : {}", id);
        apiAccessTokenRepository.deleteById(id);
    }
}
