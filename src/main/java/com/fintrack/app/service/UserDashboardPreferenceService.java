package com.fintrack.app.service;

import com.fintrack.app.domain.UserDashboardPreference;
import com.fintrack.app.repository.UserDashboardPreferenceRepository;
import com.fintrack.app.service.dto.UserDashboardPreferenceDTO;
import com.fintrack.app.service.mapper.UserDashboardPreferenceMapper;
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
 * Service Implementation for managing {@link com.fintrack.app.domain.UserDashboardPreference}.
 */
@Service
@Transactional
public class UserDashboardPreferenceService {

    private static final Logger LOG = LoggerFactory.getLogger(UserDashboardPreferenceService.class);

    private final UserDashboardPreferenceRepository userDashboardPreferenceRepository;

    private final UserDashboardPreferenceMapper userDashboardPreferenceMapper;

    public UserDashboardPreferenceService(
        UserDashboardPreferenceRepository userDashboardPreferenceRepository,
        UserDashboardPreferenceMapper userDashboardPreferenceMapper
    ) {
        this.userDashboardPreferenceRepository = userDashboardPreferenceRepository;
        this.userDashboardPreferenceMapper = userDashboardPreferenceMapper;
    }

    /**
     * Save a userDashboardPreference.
     *
     * @param userDashboardPreferenceDTO the entity to save.
     * @return the persisted entity.
     */
    public UserDashboardPreferenceDTO save(UserDashboardPreferenceDTO userDashboardPreferenceDTO) {
        LOG.debug("Request to save UserDashboardPreference : {}", userDashboardPreferenceDTO);
        UserDashboardPreference userDashboardPreference = userDashboardPreferenceMapper.toEntity(userDashboardPreferenceDTO);
        userDashboardPreference = userDashboardPreferenceRepository.save(userDashboardPreference);
        return userDashboardPreferenceMapper.toDto(userDashboardPreference);
    }

    /**
     * Update a userDashboardPreference.
     *
     * @param userDashboardPreferenceDTO the entity to save.
     * @return the persisted entity.
     */
    public UserDashboardPreferenceDTO update(UserDashboardPreferenceDTO userDashboardPreferenceDTO) {
        LOG.debug("Request to update UserDashboardPreference : {}", userDashboardPreferenceDTO);
        UserDashboardPreference userDashboardPreference = userDashboardPreferenceMapper.toEntity(userDashboardPreferenceDTO);
        userDashboardPreference = userDashboardPreferenceRepository.save(userDashboardPreference);
        return userDashboardPreferenceMapper.toDto(userDashboardPreference);
    }

    /**
     * Partially update a userDashboardPreference.
     *
     * @param userDashboardPreferenceDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<UserDashboardPreferenceDTO> partialUpdate(UserDashboardPreferenceDTO userDashboardPreferenceDTO) {
        LOG.debug("Request to partially update UserDashboardPreference : {}", userDashboardPreferenceDTO);

        return userDashboardPreferenceRepository
            .findById(userDashboardPreferenceDTO.getId())
            .map(existingUserDashboardPreference -> {
                userDashboardPreferenceMapper.partialUpdate(existingUserDashboardPreference, userDashboardPreferenceDTO);

                return existingUserDashboardPreference;
            })
            .map(userDashboardPreferenceRepository::save)
            .map(userDashboardPreferenceMapper::toDto);
    }

    /**
     * Get all the userDashboardPreferences.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<UserDashboardPreferenceDTO> findAll() {
        LOG.debug("Request to get all UserDashboardPreferences");
        return userDashboardPreferenceRepository
            .findAll()
            .stream()
            .map(userDashboardPreferenceMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get all the userDashboardPreferences with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<UserDashboardPreferenceDTO> findAllWithEagerRelationships(Pageable pageable) {
        return userDashboardPreferenceRepository.findAllWithEagerRelationships(pageable).map(userDashboardPreferenceMapper::toDto);
    }

    /**
     * Get one userDashboardPreference by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<UserDashboardPreferenceDTO> findOne(Long id) {
        LOG.debug("Request to get UserDashboardPreference : {}", id);
        return userDashboardPreferenceRepository.findOneWithEagerRelationships(id).map(userDashboardPreferenceMapper::toDto);
    }

    /**
     * Delete the userDashboardPreference by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete UserDashboardPreference : {}", id);
        userDashboardPreferenceRepository.deleteById(id);
    }
}
