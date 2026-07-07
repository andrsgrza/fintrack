package com.fintrack.app.service;

import com.fintrack.app.domain.ApiIngestion;
import com.fintrack.app.repository.ApiIngestionRepository;
import com.fintrack.app.service.dto.ApiIngestionDTO;
import com.fintrack.app.service.mapper.ApiIngestionMapper;
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
 * Service Implementation for managing {@link com.fintrack.app.domain.ApiIngestion}.
 */
@Service
@Transactional
public class ApiIngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(ApiIngestionService.class);

    private final ApiIngestionRepository apiIngestionRepository;

    private final ApiIngestionMapper apiIngestionMapper;

    public ApiIngestionService(ApiIngestionRepository apiIngestionRepository, ApiIngestionMapper apiIngestionMapper) {
        this.apiIngestionRepository = apiIngestionRepository;
        this.apiIngestionMapper = apiIngestionMapper;
    }

    /**
     * Save a apiIngestion.
     *
     * @param apiIngestionDTO the entity to save.
     * @return the persisted entity.
     */
    public ApiIngestionDTO save(ApiIngestionDTO apiIngestionDTO) {
        LOG.debug("Request to save ApiIngestion : {}", apiIngestionDTO);
        ApiIngestion apiIngestion = apiIngestionMapper.toEntity(apiIngestionDTO);
        apiIngestion = apiIngestionRepository.save(apiIngestion);
        return apiIngestionMapper.toDto(apiIngestion);
    }

    /**
     * Update a apiIngestion.
     *
     * @param apiIngestionDTO the entity to save.
     * @return the persisted entity.
     */
    public ApiIngestionDTO update(ApiIngestionDTO apiIngestionDTO) {
        LOG.debug("Request to update ApiIngestion : {}", apiIngestionDTO);
        ApiIngestion apiIngestion = apiIngestionMapper.toEntity(apiIngestionDTO);
        apiIngestion = apiIngestionRepository.save(apiIngestion);
        return apiIngestionMapper.toDto(apiIngestion);
    }

    /**
     * Partially update a apiIngestion.
     *
     * @param apiIngestionDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ApiIngestionDTO> partialUpdate(ApiIngestionDTO apiIngestionDTO) {
        LOG.debug("Request to partially update ApiIngestion : {}", apiIngestionDTO);

        return apiIngestionRepository
            .findById(apiIngestionDTO.getId())
            .map(existingApiIngestion -> {
                apiIngestionMapper.partialUpdate(existingApiIngestion, apiIngestionDTO);

                return existingApiIngestion;
            })
            .map(apiIngestionRepository::save)
            .map(apiIngestionMapper::toDto);
    }

    /**
     * Get all the apiIngestions.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<ApiIngestionDTO> findAll() {
        LOG.debug("Request to get all ApiIngestions");
        return apiIngestionRepository.findAll().stream().map(apiIngestionMapper::toDto).collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get all the apiIngestions with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<ApiIngestionDTO> findAllWithEagerRelationships(Pageable pageable) {
        return apiIngestionRepository.findAllWithEagerRelationships(pageable).map(apiIngestionMapper::toDto);
    }

    /**
     * Get one apiIngestion by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ApiIngestionDTO> findOne(Long id) {
        LOG.debug("Request to get ApiIngestion : {}", id);
        return apiIngestionRepository.findOneWithEagerRelationships(id).map(apiIngestionMapper::toDto);
    }

    /**
     * Delete the apiIngestion by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete ApiIngestion : {}", id);
        apiIngestionRepository.deleteById(id);
    }
}
