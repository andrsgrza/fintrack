package com.fintrack.app.web.rest;

import com.fintrack.app.repository.ApiAccessTokenPermissionRepository;
import com.fintrack.app.service.ApiAccessTokenPermissionService;
import com.fintrack.app.service.dto.ApiAccessTokenPermissionDTO;
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
 * REST controller for managing {@link com.fintrack.app.domain.ApiAccessTokenPermission}.
 */
@RestController
@RequestMapping("/api/api-access-token-permissions")
public class ApiAccessTokenPermissionResource {

    private static final Logger LOG = LoggerFactory.getLogger(ApiAccessTokenPermissionResource.class);

    private static final String ENTITY_NAME = "apiAccessTokenPermission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ApiAccessTokenPermissionService apiAccessTokenPermissionService;

    private final ApiAccessTokenPermissionRepository apiAccessTokenPermissionRepository;

    public ApiAccessTokenPermissionResource(
        ApiAccessTokenPermissionService apiAccessTokenPermissionService,
        ApiAccessTokenPermissionRepository apiAccessTokenPermissionRepository
    ) {
        this.apiAccessTokenPermissionService = apiAccessTokenPermissionService;
        this.apiAccessTokenPermissionRepository = apiAccessTokenPermissionRepository;
    }

    /**
     * {@code POST  /api-access-token-permissions} : Create a new apiAccessTokenPermission.
     *
     * @param apiAccessTokenPermissionDTO the apiAccessTokenPermissionDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new apiAccessTokenPermissionDTO, or with status {@code 400 (Bad Request)} if the apiAccessTokenPermission has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ApiAccessTokenPermissionDTO> createApiAccessTokenPermission(
        @Valid @RequestBody ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to save ApiAccessTokenPermission : {}", apiAccessTokenPermissionDTO);
        if (apiAccessTokenPermissionDTO.getId() != null) {
            throw new BadRequestAlertException("A new apiAccessTokenPermission cannot already have an ID", ENTITY_NAME, "idexists");
        }
        apiAccessTokenPermissionDTO = apiAccessTokenPermissionService.save(apiAccessTokenPermissionDTO);
        return ResponseEntity.created(new URI("/api/api-access-token-permissions/" + apiAccessTokenPermissionDTO.getId()))
            .headers(
                HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, apiAccessTokenPermissionDTO.getId().toString())
            )
            .body(apiAccessTokenPermissionDTO);
    }

    /**
     * {@code PUT  /api-access-token-permissions/:id} : Updates an existing apiAccessTokenPermission.
     *
     * @param id the id of the apiAccessTokenPermissionDTO to save.
     * @param apiAccessTokenPermissionDTO the apiAccessTokenPermissionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated apiAccessTokenPermissionDTO,
     * or with status {@code 400 (Bad Request)} if the apiAccessTokenPermissionDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the apiAccessTokenPermissionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiAccessTokenPermissionDTO> updateApiAccessTokenPermission(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update ApiAccessTokenPermission : {}, {}", id, apiAccessTokenPermissionDTO);
        if (apiAccessTokenPermissionDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, apiAccessTokenPermissionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!apiAccessTokenPermissionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        apiAccessTokenPermissionDTO = apiAccessTokenPermissionService.update(apiAccessTokenPermissionDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, apiAccessTokenPermissionDTO.getId().toString()))
            .body(apiAccessTokenPermissionDTO);
    }

    /**
     * {@code PATCH  /api-access-token-permissions/:id} : Partial updates given fields of an existing apiAccessTokenPermission, field will ignore if it is null
     *
     * @param id the id of the apiAccessTokenPermissionDTO to save.
     * @param apiAccessTokenPermissionDTO the apiAccessTokenPermissionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated apiAccessTokenPermissionDTO,
     * or with status {@code 400 (Bad Request)} if the apiAccessTokenPermissionDTO is not valid,
     * or with status {@code 404 (Not Found)} if the apiAccessTokenPermissionDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the apiAccessTokenPermissionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ApiAccessTokenPermissionDTO> partialUpdateApiAccessTokenPermission(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ApiAccessTokenPermission partially : {}, {}", id, apiAccessTokenPermissionDTO);
        if (apiAccessTokenPermissionDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, apiAccessTokenPermissionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!apiAccessTokenPermissionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ApiAccessTokenPermissionDTO> result = apiAccessTokenPermissionService.partialUpdate(apiAccessTokenPermissionDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, apiAccessTokenPermissionDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /api-access-token-permissions} : get all the apiAccessTokenPermissions.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of apiAccessTokenPermissions in body.
     */
    @GetMapping("")
    public List<ApiAccessTokenPermissionDTO> getAllApiAccessTokenPermissions(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all ApiAccessTokenPermissions");
        return apiAccessTokenPermissionService.findAll();
    }

    /**
     * {@code GET  /api-access-token-permissions/:id} : get the "id" apiAccessTokenPermission.
     *
     * @param id the id of the apiAccessTokenPermissionDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the apiAccessTokenPermissionDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiAccessTokenPermissionDTO> getApiAccessTokenPermission(@PathVariable("id") Long id) {
        LOG.debug("REST request to get ApiAccessTokenPermission : {}", id);
        Optional<ApiAccessTokenPermissionDTO> apiAccessTokenPermissionDTO = apiAccessTokenPermissionService.findOne(id);
        return ResponseUtil.wrapOrNotFound(apiAccessTokenPermissionDTO);
    }

    /**
     * {@code DELETE  /api-access-token-permissions/:id} : delete the "id" apiAccessTokenPermission.
     *
     * @param id the id of the apiAccessTokenPermissionDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApiAccessTokenPermission(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete ApiAccessTokenPermission : {}", id);
        apiAccessTokenPermissionService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
