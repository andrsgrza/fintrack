package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.service.ApiAccessTokenService;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
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
 * REST controller for managing {@link com.fintrack.app.domain.ApiAccessToken}.
 */
@RestController
@RequestMapping("/api/api-access-tokens")
public class ApiAccessTokenResource {

    private static final Logger LOG = LoggerFactory.getLogger(ApiAccessTokenResource.class);

    private static final String ENTITY_NAME = "apiAccessToken";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ApiAccessTokenService apiAccessTokenService;

    private final ObjectMapper objectMapper;

    public ApiAccessTokenResource(ApiAccessTokenService apiAccessTokenService, ObjectMapper objectMapper) {
        this.apiAccessTokenService = apiAccessTokenService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code POST  /api-access-tokens} : Create a new apiAccessToken.
     *
     * @param apiAccessTokenDTO the apiAccessTokenDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new apiAccessTokenDTO, or with status {@code 400 (Bad Request)} if the apiAccessToken has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ApiAccessTokenDTO> createApiAccessToken(@Valid @RequestBody ApiAccessTokenDTO apiAccessTokenDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save ApiAccessToken : {}", apiAccessTokenDTO);
        if (apiAccessTokenDTO.getId() != null) {
            throw new BadRequestAlertException("A new apiAccessToken cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            apiAccessTokenDTO = apiAccessTokenService.save(apiAccessTokenDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/api-access-tokens/" + apiAccessTokenDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, apiAccessTokenDTO.getId().toString()))
            .body(apiAccessTokenDTO);
    }

    /**
     * {@code PUT  /api-access-tokens/:id} : Updates an existing apiAccessToken.
     *
     * @param id the id of the apiAccessTokenDTO to save.
     * @param apiAccessTokenDTO the apiAccessTokenDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated apiAccessTokenDTO,
     * or with status {@code 400 (Bad Request)} if the apiAccessTokenDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the apiAccessTokenDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiAccessTokenDTO> updateApiAccessToken(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody ApiAccessTokenDTO apiAccessTokenDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update ApiAccessToken : {}, {}", id, apiAccessTokenDTO);
        if (apiAccessTokenDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, apiAccessTokenDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!apiAccessTokenService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        try {
            apiAccessTokenDTO = apiAccessTokenService.update(apiAccessTokenDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, apiAccessTokenDTO.getId().toString()))
            .body(apiAccessTokenDTO);
    }

    /**
     * {@code PATCH  /api-access-tokens/:id} : Partial updates given fields of an existing apiAccessToken, field will ignore if it is null
     *
     * @param id the id of the apiAccessTokenDTO to save.
     * @param patchNode the fields to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated apiAccessTokenDTO,
     * or with status {@code 400 (Bad Request)} if the apiAccessTokenDTO is not valid,
     * or with status {@code 404 (Not Found)} if the apiAccessTokenDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the apiAccessTokenDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ApiAccessTokenDTO> partialUpdateApiAccessToken(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode patchNode
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ApiAccessToken partially : {}, {}", id, patchNode);
        if (patchNode.has("user") && patchNode.get("user").isNull()) {
            throw new BadRequestAlertException("User cannot be null", ENTITY_NAME, "invalid");
        }
        ApiAccessTokenDTO apiAccessTokenDTO;
        try {
            apiAccessTokenDTO = objectMapper.treeToValue(patchNode, ApiAccessTokenDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid patch payload", ENTITY_NAME, "invalid");
        }
        if (apiAccessTokenDTO.getId() == null) {
            apiAccessTokenDTO.setId(id);
        }
        if (!Objects.equals(id, apiAccessTokenDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!apiAccessTokenService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ApiAccessTokenDTO> result;
        try {
            result = apiAccessTokenService.partialUpdate(apiAccessTokenDTO, patchNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, apiAccessTokenDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /api-access-tokens} : get all the apiAccessTokens.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of apiAccessTokens in body.
     */
    @GetMapping("")
    public List<ApiAccessTokenDTO> getAllApiAccessTokens(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all ApiAccessTokens");
        return apiAccessTokenService.findAll();
    }

    /**
     * {@code GET  /api-access-tokens/:id} : get the "id" apiAccessToken.
     *
     * @param id the id of the apiAccessTokenDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the apiAccessTokenDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiAccessTokenDTO> getApiAccessToken(@PathVariable("id") Long id) {
        LOG.debug("REST request to get ApiAccessToken : {}", id);
        Optional<ApiAccessTokenDTO> apiAccessTokenDTO = apiAccessTokenService.findOne(id);
        return ResponseUtil.wrapOrNotFound(apiAccessTokenDTO);
    }

    /**
     * {@code DELETE  /api-access-tokens/:id} : delete the "id" apiAccessToken.
     *
     * @param id the id of the apiAccessTokenDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApiAccessToken(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete ApiAccessToken : {}", id);
        if (!apiAccessTokenService.delete(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
