package com.fintrack.app.web.rest;

import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.service.IngestionRecordQueryService;
import com.fintrack.app.service.IngestionRecordService;
import com.fintrack.app.service.criteria.IngestionRecordCriteria;
import com.fintrack.app.service.dto.IngestionRecordDTO;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link com.fintrack.app.domain.IngestionRecord}.
 */
@RestController
@RequestMapping("/api/ingestion-records")
public class IngestionRecordResource {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionRecordResource.class);

    private static final String ENTITY_NAME = "ingestionRecord";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final IngestionRecordService ingestionRecordService;

    private final IngestionRecordRepository ingestionRecordRepository;

    private final IngestionRecordQueryService ingestionRecordQueryService;

    public IngestionRecordResource(
        IngestionRecordService ingestionRecordService,
        IngestionRecordRepository ingestionRecordRepository,
        IngestionRecordQueryService ingestionRecordQueryService
    ) {
        this.ingestionRecordService = ingestionRecordService;
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.ingestionRecordQueryService = ingestionRecordQueryService;
    }

    /**
     * {@code POST  /ingestion-records} : Create a new ingestionRecord.
     *
     * @param ingestionRecordDTO the ingestionRecordDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new ingestionRecordDTO, or with status {@code 400 (Bad Request)} if the ingestionRecord has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<IngestionRecordDTO> createIngestionRecord(@Valid @RequestBody IngestionRecordDTO ingestionRecordDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save IngestionRecord : {}", ingestionRecordDTO);
        if (ingestionRecordDTO.getId() != null) {
            throw new BadRequestAlertException("A new ingestionRecord cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ingestionRecordDTO = ingestionRecordService.save(ingestionRecordDTO);
        return ResponseEntity.created(new URI("/api/ingestion-records/" + ingestionRecordDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, ingestionRecordDTO.getId().toString()))
            .body(ingestionRecordDTO);
    }

    /**
     * {@code PUT  /ingestion-records/:id} : Updates an existing ingestionRecord.
     *
     * @param id the id of the ingestionRecordDTO to save.
     * @param ingestionRecordDTO the ingestionRecordDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated ingestionRecordDTO,
     * or with status {@code 400 (Bad Request)} if the ingestionRecordDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the ingestionRecordDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<IngestionRecordDTO> updateIngestionRecord(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody IngestionRecordDTO ingestionRecordDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update IngestionRecord : {}, {}", id, ingestionRecordDTO);
        if (ingestionRecordDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, ingestionRecordDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!ingestionRecordRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        ingestionRecordDTO = ingestionRecordService.update(ingestionRecordDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, ingestionRecordDTO.getId().toString()))
            .body(ingestionRecordDTO);
    }

    /**
     * {@code PATCH  /ingestion-records/:id} : Partial updates given fields of an existing ingestionRecord, field will ignore if it is null
     *
     * @param id the id of the ingestionRecordDTO to save.
     * @param ingestionRecordDTO the ingestionRecordDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated ingestionRecordDTO,
     * or with status {@code 400 (Bad Request)} if the ingestionRecordDTO is not valid,
     * or with status {@code 404 (Not Found)} if the ingestionRecordDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the ingestionRecordDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<IngestionRecordDTO> partialUpdateIngestionRecord(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody IngestionRecordDTO ingestionRecordDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update IngestionRecord partially : {}, {}", id, ingestionRecordDTO);
        if (ingestionRecordDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, ingestionRecordDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!ingestionRecordRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<IngestionRecordDTO> result = ingestionRecordService.partialUpdate(ingestionRecordDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, ingestionRecordDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /ingestion-records} : get all the ingestionRecords.
     *
     * @param pageable the pagination information.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of ingestionRecords in body.
     */
    @GetMapping("")
    public ResponseEntity<List<IngestionRecordDTO>> getAllIngestionRecords(
        IngestionRecordCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get IngestionRecords by criteria: {}", criteria);

        Page<IngestionRecordDTO> page = ingestionRecordQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /ingestion-records/count} : count all the ingestionRecords.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countIngestionRecords(IngestionRecordCriteria criteria) {
        LOG.debug("REST request to count IngestionRecords by criteria: {}", criteria);
        return ResponseEntity.ok().body(ingestionRecordQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /ingestion-records/:id} : get the "id" ingestionRecord.
     *
     * @param id the id of the ingestionRecordDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the ingestionRecordDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<IngestionRecordDTO> getIngestionRecord(@PathVariable("id") Long id) {
        LOG.debug("REST request to get IngestionRecord : {}", id);
        Optional<IngestionRecordDTO> ingestionRecordDTO = ingestionRecordService.findOne(id);
        return ResponseUtil.wrapOrNotFound(ingestionRecordDTO);
    }

    /**
     * {@code DELETE  /ingestion-records/:id} : delete the "id" ingestionRecord.
     *
     * @param id the id of the ingestionRecordDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIngestionRecord(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete IngestionRecord : {}", id);
        ingestionRecordService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
