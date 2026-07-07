package com.fintrack.app.web.rest;

import com.fintrack.app.repository.ApiIngestionRepository;
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

    private final ApiIngestionRepository apiIngestionRepository;

    public ApiIngestionResource(ApiIngestionService apiIngestionService, ApiIngestionRepository apiIngestionRepository) {
        this.apiIngestionService = apiIngestionService;
        this.apiIngestionRepository = apiIngestionRepository;
    }

    /**
     * {@code POST  /api-ingestions} : Create a new apiIngestion.
     *
     * @param apiIngestionDTO the apiIngestionDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new apiIngestionDTO, or with status {@code 400 (Bad Request)} if the apiIngestion has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ApiIngestionDTO> createApiIngestion(@Valid @RequestBody ApiIngestionDTO apiIngestionDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save ApiIngestion : {}", apiIngestionDTO);
        if (apiIngestionDTO.getId() != null) {
            throw new BadRequestAlertException("A new apiIngestion cannot already have an ID", ENTITY_NAME, "idexists");
        }
        apiIngestionDTO = apiIngestionService.save(apiIngestionDTO);
        return ResponseEntity.created(new URI("/api/api-ingestions/" + apiIngestionDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, apiIngestionDTO.getId().toString()))
            .body(apiIngestionDTO);
    }

    /**
     * {@code PUT  /api-ingestions/:id} : Updates an existing apiIngestion.
     *
     * @param id the id of the apiIngestionDTO to save.
     * @param apiIngestionDTO the apiIngestionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated apiIngestionDTO,
     * or with status {@code 400 (Bad Request)} if the apiIngestionDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the apiIngestionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
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

        if (!apiIngestionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        apiIngestionDTO = apiIngestionService.update(apiIngestionDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, apiIngestionDTO.getId().toString()))
            .body(apiIngestionDTO);
    }

    /**
     * {@code PATCH  /api-ingestions/:id} : Partial updates given fields of an existing apiIngestion, field will ignore if it is null
     *
     * @param id the id of the apiIngestionDTO to save.
     * @param apiIngestionDTO the apiIngestionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated apiIngestionDTO,
     * or with status {@code 400 (Bad Request)} if the apiIngestionDTO is not valid,
     * or with status {@code 404 (Not Found)} if the apiIngestionDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the apiIngestionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ApiIngestionDTO> partialUpdateApiIngestion(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody ApiIngestionDTO apiIngestionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ApiIngestion partially : {}, {}", id, apiIngestionDTO);
        if (apiIngestionDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, apiIngestionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!apiIngestionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ApiIngestionDTO> result = apiIngestionService.partialUpdate(apiIngestionDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, apiIngestionDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /api-ingestions} : get all the apiIngestions.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of apiIngestions in body.
     */
    @GetMapping("")
    public List<ApiIngestionDTO> getAllApiIngestions(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all ApiIngestions");
        return apiIngestionService.findAll();
    }

    /**
     * {@code GET  /api-ingestions/:id} : get the "id" apiIngestion.
     *
     * @param id the id of the apiIngestionDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the apiIngestionDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiIngestionDTO> getApiIngestion(@PathVariable("id") Long id) {
        LOG.debug("REST request to get ApiIngestion : {}", id);
        Optional<ApiIngestionDTO> apiIngestionDTO = apiIngestionService.findOne(id);
        return ResponseUtil.wrapOrNotFound(apiIngestionDTO);
    }

    /**
     * {@code DELETE  /api-ingestions/:id} : delete the "id" apiIngestion.
     *
     * @param id the id of the apiIngestionDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApiIngestion(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete ApiIngestion : {}", id);
        apiIngestionService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
