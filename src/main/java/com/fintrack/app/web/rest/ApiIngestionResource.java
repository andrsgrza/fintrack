package com.fintrack.app.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.service.ApiIngestionService;
import com.fintrack.app.service.dto.ApiIngestionDTO;
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
 * REST controller for managing {@link com.fintrack.app.domain.ApiIngestion}.
 */
@RestController
@RequestMapping("/api/api-ingestions")
public class ApiIngestionResource {

    private static final Logger LOG = LoggerFactory.getLogger(ApiIngestionResource.class);

    private static final String ENTITY_NAME = "apiIngestion";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ApiIngestionService apiIngestionService;

    private final ObjectMapper objectMapper;

    public ApiIngestionResource(ApiIngestionService apiIngestionService, ObjectMapper objectMapper) {
        this.apiIngestionService = apiIngestionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("")
    public ResponseEntity<ApiIngestionDTO> createApiIngestion(@Valid @RequestBody ApiIngestionDTO apiIngestionDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save ApiIngestion : {}", apiIngestionDTO);
        if (apiIngestionDTO.getId() != null) {
            throw new BadRequestAlertException("A new apiIngestion cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            apiIngestionDTO = apiIngestionService.save(apiIngestionDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.created(new URI("/api/api-ingestions/" + apiIngestionDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, apiIngestionDTO.getId().toString()))
            .body(apiIngestionDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiIngestionDTO> updateApiIngestion(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody ApiIngestionDTO apiIngestionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update ApiIngestion : {}, {}", id, apiIngestionDTO);
        if (apiIngestionDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, apiIngestionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!apiIngestionService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        try {
            apiIngestionDTO = apiIngestionService.update(apiIngestionDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, apiIngestionDTO.getId().toString()))
            .body(apiIngestionDTO);
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ApiIngestionDTO> partialUpdateApiIngestion(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody JsonNode patchNode
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ApiIngestion partially : {}, {}", id, patchNode);
        if (patchNode.has("transactionIngestion") && patchNode.get("transactionIngestion").isNull()) {
            throw new BadRequestAlertException("Transaction ingestion cannot be changed", ENTITY_NAME, "invalid");
        }
        if (patchNode.has("apiAccessToken") && patchNode.get("apiAccessToken").isNull()) {
            throw new BadRequestAlertException("Api access token cannot be changed", ENTITY_NAME, "invalid");
        }
        ApiIngestionDTO apiIngestionDTO;
        try {
            apiIngestionDTO = objectMapper.treeToValue(patchNode, ApiIngestionDTO.class);
        } catch (Exception e) {
            throw new BadRequestAlertException("Invalid patch payload", ENTITY_NAME, "invalid");
        }
        if (apiIngestionDTO.getId() == null) {
            apiIngestionDTO.setId(id);
        }
        if (!Objects.equals(id, apiIngestionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!apiIngestionService.isAccessible(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ApiIngestionDTO> result;
        try {
            result = apiIngestionService.partialUpdate(apiIngestionDTO, patchNode);
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "invalid");
        }

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, apiIngestionDTO.getId().toString())
        );
    }

    @GetMapping("")
    public List<ApiIngestionDTO> getAllApiIngestions() {
        LOG.debug("REST request to get all ApiIngestions");
        return apiIngestionService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiIngestionDTO> getApiIngestion(@PathVariable("id") Long id) {
        LOG.debug("REST request to get ApiIngestion : {}", id);
        Optional<ApiIngestionDTO> apiIngestionDTO = apiIngestionService.findOne(id);
        return ResponseUtil.wrapOrNotFound(apiIngestionDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApiIngestion(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete ApiIngestion : {}", id);
        if (!apiIngestionService.delete(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
