package com.fintrack.app.web.rest;

import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.service.FileIngestionService;
import com.fintrack.app.service.dto.FileIngestionDTO;
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
 * REST controller for managing {@link com.fintrack.app.domain.FileIngestion}.
 */
@RestController
@RequestMapping("/api/file-ingestions")
public class FileIngestionResource {

    private static final Logger LOG = LoggerFactory.getLogger(FileIngestionResource.class);

    private static final String ENTITY_NAME = "fileIngestion";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final FileIngestionService fileIngestionService;

    private final FileIngestionRepository fileIngestionRepository;

    public FileIngestionResource(FileIngestionService fileIngestionService, FileIngestionRepository fileIngestionRepository) {
        this.fileIngestionService = fileIngestionService;
        this.fileIngestionRepository = fileIngestionRepository;
    }

    /**
     * {@code POST  /file-ingestions} : Create a new fileIngestion.
     *
     * @param fileIngestionDTO the fileIngestionDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new fileIngestionDTO, or with status {@code 400 (Bad Request)} if the fileIngestion has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<FileIngestionDTO> createFileIngestion(@Valid @RequestBody FileIngestionDTO fileIngestionDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save FileIngestion : {}", fileIngestionDTO);
        if (fileIngestionDTO.getId() != null) {
            throw new BadRequestAlertException("A new fileIngestion cannot already have an ID", ENTITY_NAME, "idexists");
        }
        fileIngestionDTO = fileIngestionService.save(fileIngestionDTO);
        return ResponseEntity.created(new URI("/api/file-ingestions/" + fileIngestionDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, fileIngestionDTO.getId().toString()))
            .body(fileIngestionDTO);
    }

    /**
     * {@code PUT  /file-ingestions/:id} : Updates an existing fileIngestion.
     *
     * @param id the id of the fileIngestionDTO to save.
     * @param fileIngestionDTO the fileIngestionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated fileIngestionDTO,
     * or with status {@code 400 (Bad Request)} if the fileIngestionDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the fileIngestionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FileIngestionDTO> updateFileIngestion(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody FileIngestionDTO fileIngestionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update FileIngestion : {}, {}", id, fileIngestionDTO);
        if (fileIngestionDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, fileIngestionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!fileIngestionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        fileIngestionDTO = fileIngestionService.update(fileIngestionDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, fileIngestionDTO.getId().toString()))
            .body(fileIngestionDTO);
    }

    /**
     * {@code PATCH  /file-ingestions/:id} : Partial updates given fields of an existing fileIngestion, field will ignore if it is null
     *
     * @param id the id of the fileIngestionDTO to save.
     * @param fileIngestionDTO the fileIngestionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated fileIngestionDTO,
     * or with status {@code 400 (Bad Request)} if the fileIngestionDTO is not valid,
     * or with status {@code 404 (Not Found)} if the fileIngestionDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the fileIngestionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<FileIngestionDTO> partialUpdateFileIngestion(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody FileIngestionDTO fileIngestionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update FileIngestion partially : {}, {}", id, fileIngestionDTO);
        if (fileIngestionDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, fileIngestionDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!fileIngestionRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<FileIngestionDTO> result = fileIngestionService.partialUpdate(fileIngestionDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, fileIngestionDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /file-ingestions} : get all the fileIngestions.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of fileIngestions in body.
     */
    @GetMapping("")
    public List<FileIngestionDTO> getAllFileIngestions() {
        LOG.debug("REST request to get all FileIngestions");
        return fileIngestionService.findAll();
    }

    /**
     * {@code GET  /file-ingestions/:id} : get the "id" fileIngestion.
     *
     * @param id the id of the fileIngestionDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the fileIngestionDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FileIngestionDTO> getFileIngestion(@PathVariable("id") Long id) {
        LOG.debug("REST request to get FileIngestion : {}", id);
        Optional<FileIngestionDTO> fileIngestionDTO = fileIngestionService.findOne(id);
        return ResponseUtil.wrapOrNotFound(fileIngestionDTO);
    }

    /**
     * {@code DELETE  /file-ingestions/:id} : delete the "id" fileIngestion.
     *
     * @param id the id of the fileIngestionDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFileIngestion(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete FileIngestion : {}", id);
        fileIngestionService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
