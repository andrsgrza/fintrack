package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final CurrentUserService currentUserService;

    private final ObjectMapper objectMapper;

    public UserDashboardPreferenceService(
        UserDashboardPreferenceRepository userDashboardPreferenceRepository,
        UserDashboardPreferenceMapper userDashboardPreferenceMapper,
        CurrentUserService currentUserService,
        ObjectMapper objectMapper
    ) {
        this.userDashboardPreferenceRepository = userDashboardPreferenceRepository;
        this.userDashboardPreferenceMapper = userDashboardPreferenceMapper;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    /**
     * Save a userDashboardPreference.
     *
     * @param userDashboardPreferenceDTO the entity to save.
     * @return the persisted entity.
     */
    public UserDashboardPreferenceDTO save(UserDashboardPreferenceDTO userDashboardPreferenceDTO) {
        LOG.debug("Request to save UserDashboardPreference : {}", userDashboardPreferenceDTO);
        if (userDashboardPreferenceRepository.existsByUserId(currentUserService.getCurrentUser().getId())) {
            throw new IllegalArgumentException("User already has dashboard preferences");
        }
        validateConfiguration(userDashboardPreferenceDTO.getConfiguration());
        UserDashboardPreference userDashboardPreference = userDashboardPreferenceMapper.toEntity(userDashboardPreferenceDTO);
        userDashboardPreference.setUser(currentUserService.getCurrentUser());
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
        UserDashboardPreference existingUserDashboardPreference = findAccessibleEntity(userDashboardPreferenceDTO.getId()).orElseThrow();
        validateConfiguration(userDashboardPreferenceDTO.getConfiguration());
        UserDashboardPreference userDashboardPreference = userDashboardPreferenceMapper.toEntity(userDashboardPreferenceDTO);
        userDashboardPreference.setUser(existingUserDashboardPreference.getUser());
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
        return partialUpdate(userDashboardPreferenceDTO, null);
    }

    /**
     * Partially update a userDashboardPreference, applying owner changes only when present in the patch body.
     *
     * @param userDashboardPreferenceDTO the entity to update partially.
     * @param patchNode the raw patch payload.
     * @return the persisted entity.
     */
    public Optional<UserDashboardPreferenceDTO> partialUpdate(UserDashboardPreferenceDTO userDashboardPreferenceDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update UserDashboardPreference : {}", userDashboardPreferenceDTO);

        return findAccessibleEntity(userDashboardPreferenceDTO.getId())
            .map(existingUserDashboardPreference -> {
                if (patchNode != null && patchNode.has("user") && patchNode.get("user").isNull()) {
                    throw new IllegalArgumentException("User cannot be null");
                }
                if (patchNode != null && patchNode.has("configuration")) {
                    if (patchNode.get("configuration").isNull()) {
                        throw new IllegalArgumentException("Configuration cannot be null");
                    }
                    validateConfiguration(userDashboardPreferenceDTO.getConfiguration());
                }
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
        if (currentUserService.isAdmin()) {
            return userDashboardPreferenceRepository
                .findAllWithEagerRelationships()
                .stream()
                .map(userDashboardPreferenceMapper::toDto)
                .collect(Collectors.toCollection(LinkedList::new));
        }
        return userDashboardPreferenceRepository
            .findAllWithEagerRelationshipsByUserLogin(currentUserService.getCurrentUserLogin())
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
        if (currentUserService.isAdmin()) {
            return userDashboardPreferenceRepository.findAllWithEagerRelationships(pageable).map(userDashboardPreferenceMapper::toDto);
        }
        throw new UnsupportedOperationException("Paged access is only supported for admin users");
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
        return findAccessibleEntity(id).map(userDashboardPreferenceMapper::toDto);
    }

    /**
     * Returns whether the current user can access the user dashboard preference.
     *
     * @param id the id of the entity.
     * @return true when the preference exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the userDashboardPreference by id.
     *
     * @param id the id of the entity.
     * @return true when the preference was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete UserDashboardPreference : {}", id);
        Optional<UserDashboardPreference> userDashboardPreference = findAccessibleEntity(id);
        if (userDashboardPreference.isEmpty()) {
            return false;
        }
        userDashboardPreferenceRepository.deleteById(id);
        return true;
    }

    private Optional<UserDashboardPreference> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return userDashboardPreferenceRepository.findOneWithEagerRelationships(id);
        }
        return userDashboardPreferenceRepository.findOneWithEagerRelationshipsByIdAndUserLogin(
            id,
            currentUserService.getCurrentUserLogin()
        );
    }

    private void validateConfiguration(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            throw new IllegalArgumentException("Configuration is required");
        }
        JsonNode configurationNode;
        try {
            configurationNode = objectMapper.readTree(configuration);
        } catch (Exception e) {
            throw new IllegalArgumentException("Configuration must be valid JSON");
        }
        if (!configurationNode.isObject() && !configurationNode.isArray()) {
            throw new IllegalArgumentException("Configuration must be a JSON object or array");
        }
    }
}
