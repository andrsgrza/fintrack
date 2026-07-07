package com.fintrack.app.web.rest;

import com.fintrack.app.repository.UserDashboardPreferenceRepository;
import com.fintrack.app.service.UserDashboardPreferenceService;
import com.fintrack.app.service.dto.UserDashboardPreferenceDTO;
import com.fintrack.app.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.fintrack.app.domain.UserDashboardPreference}.
 */
@RestController
@RequestMapping("/api/user-dashboard-preferences")
public class UserDashboardPreferenceResource {

    private static final Logger LOG = LoggerFactory.getLogger(UserDashboardPreferenceResource.class);

    private static final String ENTITY_NAME = "userDashboardPreference";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserDashboardPreferenceService userDashboardPreferenceService;

    private final UserDashboardPreferenceRepository userDashboardPreferenceRepository;

    public UserDashboardPreferenceResource(
        UserDashboardPreferenceService userDashboardPreferenceService,
        UserDashboardPreferenceRepository userDashboardPreferenceRepository
    ) {
        this.userDashboardPreferenceService = userDashboardPreferenceService;
        this.userDashboardPreferenceRepository = userDashboardPreferenceRepository;
    }

    /**
     * {@code POST  /user-dashboard-preferences} : Create a new userDashboardPreference.
     *
     * @param userDashboardPreferenceDTO the userDashboardPreferenceDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new userDashboardPreferenceDTO, or with status {@code 400 (Bad Request)} if the userDashboardPreference has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<UserDashboardPreferenceDTO> createUserDashboardPreference(
        @Valid @RequestBody UserDashboardPreferenceDTO userDashboardPreferenceDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to save UserDashboardPreference : {}", userDashboardPreferenceDTO);
        if (userDashboardPreferenceDTO.getId() != null) {
            throw new BadRequestAlertException("A new userDashboardPreference cannot already have an ID", ENTITY_NAME, "idexists");
        }
        userDashboardPreferenceDTO = userDashboardPreferenceService.save(userDashboardPreferenceDTO);
        return ResponseEntity.created(new URI("/api/user-dashboard-preferences/" + userDashboardPreferenceDTO.getId()))
            .headers(
                HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, userDashboardPreferenceDTO.getId().toString())
            )
            .body(userDashboardPreferenceDTO);
    }

    /**
     * {@code PUT  /user-dashboard-preferences/:id} : Updates an existing userDashboardPreference.
     *
     * @param id the id of the userDashboardPreferenceDTO to save.
     * @param userDashboardPreferenceDTO the userDashboardPreferenceDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated userDashboardPreferenceDTO,
     * or with status {@code 400 (Bad Request)} if the userDashboardPreferenceDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the userDashboardPreferenceDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserDashboardPreferenceDTO> updateUserDashboardPreference(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody UserDashboardPreferenceDTO userDashboardPreferenceDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update UserDashboardPreference : {}, {}", id, userDashboardPreferenceDTO);
        if (userDashboardPreferenceDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, userDashboardPreferenceDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!userDashboardPreferenceRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        userDashboardPreferenceDTO = userDashboardPreferenceService.update(userDashboardPreferenceDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, userDashboardPreferenceDTO.getId().toString()))
            .body(userDashboardPreferenceDTO);
    }

    /**
     * {@code PATCH  /user-dashboard-preferences/:id} : Partial updates given fields of an existing userDashboardPreference, field will ignore if it is null
     *
     * @param id the id of the userDashboardPreferenceDTO to save.
     * @param userDashboardPreferenceDTO the userDashboardPreferenceDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated userDashboardPreferenceDTO,
     * or with status {@code 400 (Bad Request)} if the userDashboardPreferenceDTO is not valid,
     * or with status {@code 404 (Not Found)} if the userDashboardPreferenceDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the userDashboardPreferenceDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<UserDashboardPreferenceDTO> partialUpdateUserDashboardPreference(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody UserDashboardPreferenceDTO userDashboardPreferenceDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update UserDashboardPreference partially : {}, {}", id, userDashboardPreferenceDTO);
        if (userDashboardPreferenceDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, userDashboardPreferenceDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!userDashboardPreferenceRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<UserDashboardPreferenceDTO> result = userDashboardPreferenceService.partialUpdate(userDashboardPreferenceDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, userDashboardPreferenceDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /user-dashboard-preferences} : get all the userDashboardPreferences.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of userDashboardPreferences in body.
     */
    @GetMapping("")
    public List<UserDashboardPreferenceDTO> getAllUserDashboardPreferences(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all UserDashboardPreferences");
        return userDashboardPreferenceService.findAll();
    }

    /**
     * {@code GET  /user-dashboard-preferences/:id} : get the "id" userDashboardPreference.
     *
     * @param id the id of the userDashboardPreferenceDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the userDashboardPreferenceDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDashboardPreferenceDTO> getUserDashboardPreference(@PathVariable("id") Long id) {
        LOG.debug("REST request to get UserDashboardPreference : {}", id);
        Optional<UserDashboardPreferenceDTO> userDashboardPreferenceDTO = userDashboardPreferenceService.findOne(id);
        return ResponseUtil.wrapOrNotFound(userDashboardPreferenceDTO);
    }

    /**
     * {@code DELETE  /user-dashboard-preferences/:id} : delete the "id" userDashboardPreference.
     *
     * @param id the id of the userDashboardPreferenceDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUserDashboardPreference(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete UserDashboardPreference : {}", id);
        userDashboardPreferenceService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
