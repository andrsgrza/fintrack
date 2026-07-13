package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.FileIngestionAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.FileIngestion;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.ImportFileType;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.dto.FileIngestionDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.FileIngestionMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link FileIngestionResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class FileIngestionResourceIT {

    private static final String DEFAULT_ORIGINAL_FILENAME = "AAAAAAAAAA";
    private static final String UPDATED_ORIGINAL_FILENAME = "BBBBBBBBBB";

    private static final ImportFileType DEFAULT_FILE_TYPE = ImportFileType.CSV;
    private static final ImportFileType UPDATED_FILE_TYPE = ImportFileType.PDF;

    private static final String DEFAULT_CONTENT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_CONTENT_TYPE = "BBBBBBBBBB";

    private static final Long DEFAULT_FILE_SIZE_BYTES = 0L;
    private static final Long UPDATED_FILE_SIZE_BYTES = 1L;

    private static final String DEFAULT_CHECKSUM = "AAAAAAAAAA";
    private static final String UPDATED_CHECKSUM = "BBBBBBBBBB";

    private static final String DEFAULT_STORAGE_KEY = "AAAAAAAAAA";
    private static final String UPDATED_STORAGE_KEY = "BBBBBBBBBB";

    private static final String DEFAULT_PARSER_NAME = "AAAAAAAAAA";
    private static final String UPDATED_PARSER_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_PARSER_VERSION = "AAAAAAAAAA";
    private static final String UPDATED_PARSER_VERSION = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_STATEMENT_START_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_STATEMENT_START_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final LocalDate DEFAULT_STATEMENT_END_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_STATEMENT_END_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/file-ingestions";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private FileIngestionRepository fileIngestionRepository;

    @Autowired
    private TransactionIngestionRepository transactionIngestionRepository;

    @Autowired
    private FileIngestionMapper fileIngestionMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restFileIngestionMockMvc;

    private FileIngestion fileIngestion;

    private FileIngestion insertedFileIngestion;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static FileIngestion createEntity(EntityManager em) {
        FileIngestion fileIngestion = new FileIngestion()
            .originalFilename(DEFAULT_ORIGINAL_FILENAME)
            .fileType(DEFAULT_FILE_TYPE)
            .contentType(DEFAULT_CONTENT_TYPE)
            .fileSizeBytes(DEFAULT_FILE_SIZE_BYTES)
            .checksum(DEFAULT_CHECKSUM)
            .storageKey(DEFAULT_STORAGE_KEY)
            .parserName(DEFAULT_PARSER_NAME)
            .parserVersion(DEFAULT_PARSER_VERSION)
            .statementStartDate(DEFAULT_STATEMENT_START_DATE)
            .statementEndDate(DEFAULT_STATEMENT_END_DATE)
            .createdAt(DEFAULT_CREATED_AT);
        // Add required entity
        TransactionIngestion transactionIngestion;
        if (TestUtil.findAll(em, TransactionIngestion.class).isEmpty()) {
            transactionIngestion = TransactionIngestionResourceIT.createEntity(em);
            em.persist(transactionIngestion);
            em.flush();
        } else {
            transactionIngestion = TestUtil.findAll(em, TransactionIngestion.class).get(0);
        }
        fileIngestion.setTransactionIngestion(transactionIngestion);
        return fileIngestion;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static FileIngestion createUpdatedEntity(EntityManager em) {
        FileIngestion updatedFileIngestion = new FileIngestion()
            .originalFilename(UPDATED_ORIGINAL_FILENAME)
            .fileType(UPDATED_FILE_TYPE)
            .contentType(UPDATED_CONTENT_TYPE)
            .fileSizeBytes(UPDATED_FILE_SIZE_BYTES)
            .checksum(UPDATED_CHECKSUM)
            .storageKey(UPDATED_STORAGE_KEY)
            .parserName(UPDATED_PARSER_NAME)
            .parserVersion(UPDATED_PARSER_VERSION)
            .statementStartDate(UPDATED_STATEMENT_START_DATE)
            .statementEndDate(UPDATED_STATEMENT_END_DATE)
            .createdAt(UPDATED_CREATED_AT);
        // Add required entity
        TransactionIngestion transactionIngestion;
        if (TestUtil.findAll(em, TransactionIngestion.class).isEmpty()) {
            transactionIngestion = TransactionIngestionResourceIT.createUpdatedEntity(em);
            em.persist(transactionIngestion);
            em.flush();
        } else {
            transactionIngestion = TestUtil.findAll(em, TransactionIngestion.class).get(0);
        }
        updatedFileIngestion.setTransactionIngestion(transactionIngestion);
        return updatedFileIngestion;
    }

    @BeforeEach
    void initTest() {
        fileIngestion = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedFileIngestion != null) {
            fileIngestionRepository.delete(insertedFileIngestion);
            insertedFileIngestion = null;
        }
    }

    @Test
    @Transactional
    void createFileIngestion() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the FileIngestion
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(fileIngestion);
        var returnedFileIngestionDTO = om.readValue(
            restFileIngestionMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FileIngestionDTO.class
        );

        // Validate the FileIngestion in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertThat(returnedFileIngestionDTO.getCreatedAt()).isNotNull();
        FileIngestion persisted = fileIngestionRepository.findById(returnedFileIngestionDTO.getId()).orElseThrow();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getOriginalFilename()).isEqualTo(DEFAULT_ORIGINAL_FILENAME);
        assertThat(persisted.getTransactionIngestion().getId()).isEqualTo(fileIngestion.getTransactionIngestion().getId());

        insertedFileIngestion = persisted;
    }

    @Test
    @Transactional
    void createFileIngestionWithExistingId() throws Exception {
        // Create the FileIngestion with an existing ID
        fileIngestion.setId(1L);
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(fileIngestion);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restFileIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
            .andExpect(status().isBadRequest());

        // Validate the FileIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkOriginalFilenameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        fileIngestion.setOriginalFilename(null);

        // Create the FileIngestion, which fails.
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(fileIngestion);

        restFileIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkFileTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        fileIngestion.setFileType(null);

        // Create the FileIngestion, which fails.
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(fileIngestion);

        restFileIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void createFileIngestionWithoutCreatedAtSucceeds() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        FileIngestionDTO fileIngestionDTO = new FileIngestionDTO();
        fileIngestionDTO.setOriginalFilename(DEFAULT_ORIGINAL_FILENAME);
        fileIngestionDTO.setFileType(DEFAULT_FILE_TYPE);
        TransactionIngestionDTO transactionIngestionDTO = new TransactionIngestionDTO();
        transactionIngestionDTO.setId(fileIngestion.getTransactionIngestion().getId());
        fileIngestionDTO.setTransactionIngestion(transactionIngestionDTO);

        var returnedFileIngestionDTO = om.readValue(
            restFileIngestionMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FileIngestionDTO.class
        );

        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertThat(returnedFileIngestionDTO.getCreatedAt()).isNotNull();
        FileIngestion persisted = fileIngestionRepository.findById(returnedFileIngestionDTO.getId()).orElseThrow();
        assertThat(persisted.getCreatedAt()).isNotNull();
        insertedFileIngestion = persisted;
    }

    @Test
    @Transactional
    void createFileIngestionIgnoresClientProvidedCreatedAt() throws Exception {
        FileIngestionDTO fileIngestionDTO = buildCreateFileIngestionDTO(fileIngestion.getTransactionIngestion().getId());
        fileIngestionDTO.setCreatedAt(Instant.parse("2000-01-01T00:00:00Z"));

        var returnedFileIngestionDTO = om.readValue(
            restFileIngestionMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FileIngestionDTO.class
        );

        assertThat(returnedFileIngestionDTO.getCreatedAt()).isNotEqualTo(Instant.parse("2000-01-01T00:00:00Z"));
        insertedFileIngestion = fileIngestionRepository.findById(returnedFileIngestionDTO.getId()).orElseThrow();
    }

    @Test
    @Transactional
    void createFileIngestionNormalizesStrings() throws Exception {
        FileIngestionDTO fileIngestionDTO = buildCreateFileIngestionDTO(fileIngestion.getTransactionIngestion().getId());
        fileIngestionDTO.setOriginalFilename("  Import.CSV  ");
        fileIngestionDTO.setContentType("   ");
        fileIngestionDTO.setChecksum("ABCDEF1234");
        fileIngestionDTO.setStorageKey("  storage/key  ");
        fileIngestionDTO.setParserName("  parser  ");
        fileIngestionDTO.setParserVersion("  v1  ");

        var returnedFileIngestionDTO = om.readValue(
            restFileIngestionMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("Import.CSV"))
                .andExpect(jsonPath("$.contentType").value(nullValue()))
                .andExpect(jsonPath("$.checksum").value("abcdef1234"))
                .andExpect(jsonPath("$.storageKey").value("storage/key"))
                .andExpect(jsonPath("$.parserName").value("parser"))
                .andExpect(jsonPath("$.parserVersion").value("v1"))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FileIngestionDTO.class
        );

        insertedFileIngestion = fileIngestionRepository.findById(returnedFileIngestionDTO.getId()).orElseThrow();
    }

    @Test
    @Transactional
    void createFileIngestionWithBlankOriginalFilenameFails() throws Exception {
        FileIngestionDTO fileIngestionDTO = buildCreateFileIngestionDTO(fileIngestion.getTransactionIngestion().getId());
        fileIngestionDTO.setOriginalFilename("   ");

        restFileIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createFileIngestionWithStatementStartAfterEndFails() throws Exception {
        FileIngestionDTO fileIngestionDTO = buildCreateFileIngestionDTO(fileIngestion.getTransactionIngestion().getId());
        fileIngestionDTO.setStatementStartDate(LocalDate.parse("2026-02-01"));
        fileIngestionDTO.setStatementEndDate(LocalDate.parse("2026-01-01"));

        restFileIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void getAllFileIngestions() throws Exception {
        // Initialize the database
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);

        // Get all the fileIngestionList
        restFileIngestionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(fileIngestion.getId().intValue())))
            .andExpect(jsonPath("$.[*].originalFilename").value(hasItem(DEFAULT_ORIGINAL_FILENAME)))
            .andExpect(jsonPath("$.[*].fileType").value(hasItem(DEFAULT_FILE_TYPE.toString())))
            .andExpect(jsonPath("$.[*].contentType").value(hasItem(DEFAULT_CONTENT_TYPE)))
            .andExpect(jsonPath("$.[*].fileSizeBytes").value(hasItem(DEFAULT_FILE_SIZE_BYTES.intValue())))
            .andExpect(jsonPath("$.[*].checksum").value(hasItem(DEFAULT_CHECKSUM)))
            .andExpect(jsonPath("$.[*].storageKey").value(hasItem(DEFAULT_STORAGE_KEY)))
            .andExpect(jsonPath("$.[*].parserName").value(hasItem(DEFAULT_PARSER_NAME)))
            .andExpect(jsonPath("$.[*].parserVersion").value(hasItem(DEFAULT_PARSER_VERSION)))
            .andExpect(jsonPath("$.[*].statementStartDate").value(hasItem(DEFAULT_STATEMENT_START_DATE.toString())))
            .andExpect(jsonPath("$.[*].statementEndDate").value(hasItem(DEFAULT_STATEMENT_END_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())));
    }

    @Test
    @Transactional
    void getFileIngestion() throws Exception {
        // Initialize the database
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);

        // Get the fileIngestion
        restFileIngestionMockMvc
            .perform(get(ENTITY_API_URL_ID, fileIngestion.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(fileIngestion.getId().intValue()))
            .andExpect(jsonPath("$.originalFilename").value(DEFAULT_ORIGINAL_FILENAME))
            .andExpect(jsonPath("$.fileType").value(DEFAULT_FILE_TYPE.toString()))
            .andExpect(jsonPath("$.contentType").value(DEFAULT_CONTENT_TYPE))
            .andExpect(jsonPath("$.fileSizeBytes").value(DEFAULT_FILE_SIZE_BYTES.intValue()))
            .andExpect(jsonPath("$.checksum").value(DEFAULT_CHECKSUM))
            .andExpect(jsonPath("$.storageKey").value(DEFAULT_STORAGE_KEY))
            .andExpect(jsonPath("$.parserName").value(DEFAULT_PARSER_NAME))
            .andExpect(jsonPath("$.parserVersion").value(DEFAULT_PARSER_VERSION))
            .andExpect(jsonPath("$.statementStartDate").value(DEFAULT_STATEMENT_START_DATE.toString()))
            .andExpect(jsonPath("$.statementEndDate").value(DEFAULT_STATEMENT_END_DATE.toString()))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()));
    }

    @Test
    @Transactional
    void getNonExistingFileIngestion() throws Exception {
        // Get the fileIngestion
        restFileIngestionMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingFileIngestion() throws Exception {
        // Initialize the database
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the fileIngestion
        FileIngestion updatedFileIngestion = fileIngestionRepository.findById(fileIngestion.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedFileIngestion are not directly saved in db
        em.detach(updatedFileIngestion);
        updatedFileIngestion
            .statementStartDate(UPDATED_STATEMENT_START_DATE)
            .statementEndDate(UPDATED_STATEMENT_END_DATE)
            .createdAt(insertedFileIngestion.getCreatedAt());
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(updatedFileIngestion);

        restFileIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, fileIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(fileIngestionDTO))
            )
            .andExpect(status().isOk());

        // Validate the FileIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedFileIngestionToMatchAllProperties(updatedFileIngestion);
    }

    @Test
    @Transactional
    void putNonExistingFileIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        fileIngestion.setId(longCount.incrementAndGet());

        // Create the FileIngestion
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(fileIngestion);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restFileIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, fileIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(fileIngestionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FileIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchFileIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        fileIngestion.setId(longCount.incrementAndGet());

        // Create the FileIngestion
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(fileIngestion);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFileIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(fileIngestionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FileIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamFileIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        fileIngestion.setId(longCount.incrementAndGet());

        // Create the FileIngestion
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(fileIngestion);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFileIngestionMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the FileIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateFileIngestionWithPatch() throws Exception {
        // Initialize the database
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the fileIngestion using partial update
        String patchJson =
            "{\"id\":" +
            fileIngestion.getId() +
            ",\"statementStartDate\":\"" +
            UPDATED_STATEMENT_START_DATE +
            "\",\"statementEndDate\":\"" +
            UPDATED_STATEMENT_END_DATE +
            "\"}";

        restFileIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, fileIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk());

        // Validate the FileIngestion in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        FileIngestion persisted = getPersistedFileIngestion(fileIngestion);
        assertThat(persisted.getStatementStartDate()).isEqualTo(UPDATED_STATEMENT_START_DATE);
        assertThat(persisted.getStatementEndDate()).isEqualTo(UPDATED_STATEMENT_END_DATE);
        assertThat(persisted.getCreatedAt()).isEqualTo(insertedFileIngestion.getCreatedAt());
    }

    @Test
    @Transactional
    void patchFileIngestionWithNullStatementDatesClearsThem() throws Exception {
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);
        String patchJson = "{\"id\":" + fileIngestion.getId() + ",\"statementStartDate\":null,\"statementEndDate\":null}";

        restFileIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, fileIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statementStartDate").value(nullValue()))
            .andExpect(jsonPath("$.statementEndDate").value(nullValue()));

        FileIngestion persisted = getPersistedFileIngestion(fileIngestion);
        assertThat(persisted.getStatementStartDate()).isNull();
        assertThat(persisted.getStatementEndDate()).isNull();
    }

    @Test
    @Transactional
    void patchFileIngestionWithFinalInvalidStatementRangeFails() throws Exception {
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);
        String patchJson = "{\"id\":" + fileIngestion.getId() + ",\"statementStartDate\":\"2026-02-01\"}";

        restFileIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, fileIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchFileIngestionWithImmutableOriginalFilenameFails() throws Exception {
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);
        String patchJson = "{\"id\":" + fileIngestion.getId() + ",\"originalFilename\":\"" + UPDATED_ORIGINAL_FILENAME + "\"}";

        restFileIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, fileIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchFileIngestionWithImmutableFileTypeFails() throws Exception {
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);
        String patchJson = "{\"id\":" + fileIngestion.getId() + ",\"fileType\":\"" + UPDATED_FILE_TYPE + "\"}";

        restFileIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, fileIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchFileIngestionWithNullCreatedAtFails() throws Exception {
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);
        String patchJson = "{\"id\":" + fileIngestion.getId() + ",\"createdAt\":null}";

        restFileIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, fileIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchFileIngestionWithChangedCreatedAtFails() throws Exception {
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);
        String patchJson = "{\"id\":" + fileIngestion.getId() + ",\"createdAt\":\"2026-02-01T00:00:00Z\"}";

        restFileIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, fileIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void fullUpdateFileIngestionWithPatch() throws Exception {
        // Initialize the database
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        String patchJson =
            "{\"id\":" +
            fileIngestion.getId() +
            ",\"originalFilename\":\"" +
            DEFAULT_ORIGINAL_FILENAME +
            "\",\"fileType\":\"" +
            DEFAULT_FILE_TYPE +
            "\",\"contentType\":\"" +
            DEFAULT_CONTENT_TYPE +
            "\",\"fileSizeBytes\":" +
            DEFAULT_FILE_SIZE_BYTES +
            ",\"checksum\":\"" +
            DEFAULT_CHECKSUM +
            "\",\"storageKey\":\"" +
            DEFAULT_STORAGE_KEY +
            "\",\"parserName\":\"" +
            DEFAULT_PARSER_NAME +
            "\",\"parserVersion\":\"" +
            DEFAULT_PARSER_VERSION +
            "\",\"statementStartDate\":\"" +
            UPDATED_STATEMENT_START_DATE +
            "\",\"statementEndDate\":\"" +
            UPDATED_STATEMENT_END_DATE +
            "\"}";

        restFileIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, fileIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk());

        // Validate the FileIngestion in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        FileIngestion persisted = getPersistedFileIngestion(fileIngestion);
        assertThat(persisted.getOriginalFilename()).isEqualTo(DEFAULT_ORIGINAL_FILENAME);
        assertThat(persisted.getFileType()).isEqualTo(DEFAULT_FILE_TYPE);
        assertThat(persisted.getStatementStartDate()).isEqualTo(UPDATED_STATEMENT_START_DATE);
        assertThat(persisted.getStatementEndDate()).isEqualTo(UPDATED_STATEMENT_END_DATE);
        assertThat(persisted.getCreatedAt()).isEqualTo(insertedFileIngestion.getCreatedAt());
    }

    @Test
    @Transactional
    void patchNonExistingFileIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        fileIngestion.setId(longCount.incrementAndGet());

        // Create the FileIngestion
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(fileIngestion);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restFileIngestionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, fileIngestionDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(fileIngestionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FileIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchFileIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        fileIngestion.setId(longCount.incrementAndGet());

        // Create the FileIngestion
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(fileIngestion);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFileIngestionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(fileIngestionDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the FileIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamFileIngestion() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        fileIngestion.setId(longCount.incrementAndGet());

        // Create the FileIngestion
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(fileIngestion);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFileIngestionMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(fileIngestionDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the FileIngestion in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteFileIngestion() throws Exception {
        // Initialize the database
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the fileIngestion
        restFileIngestionMockMvc
            .perform(delete(ENTITY_API_URL_ID, fileIngestion.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());

        // Direct FileIngestion delete is blocked; lifecycle is owned by TransactionIngestion.
        assertSameRepositoryCount(databaseSizeBeforeDelete);
    }

    @Test
    @Transactional
    void getFileIngestionOwnedByAnotherUserIsNotFound() throws Exception {
        FileIngestion otherFileIngestion = saveFileIngestionOnOtherUsersIngestion();

        restFileIngestionMockMvc.perform(get(ENTITY_API_URL_ID, otherFileIngestion.getId())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllFileIngestionsDoesNotIncludeAnotherUsersFileIngestions() throws Exception {
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);
        FileIngestion otherFileIngestion = saveFileIngestionOnOtherUsersIngestion();

        restFileIngestionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(fileIngestion.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(otherFileIngestion.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetFileIngestionOwnedByAnotherUser() throws Exception {
        FileIngestion otherFileIngestion = saveFileIngestionOnOtherUsersIngestion();

        restFileIngestionMockMvc
            .perform(get(ENTITY_API_URL_ID, otherFileIngestion.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(otherFileIngestion.getId().intValue()));
    }

    @Test
    @Transactional
    void putFileIngestionOwnedByAnotherUserIsNotFound() throws Exception {
        FileIngestion otherFileIngestion = saveFileIngestionOnOtherUsersIngestion();
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(otherFileIngestion);
        fileIngestionDTO.setStatementEndDate(UPDATED_STATEMENT_END_DATE);

        restFileIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, fileIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(fileIngestionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchFileIngestionOwnedByAnotherUserIsNotFound() throws Exception {
        FileIngestion otherFileIngestion = saveFileIngestionOnOtherUsersIngestion();
        String patchJson = "{\"id\":" + otherFileIngestion.getId() + ",\"originalFilename\":\"" + UPDATED_ORIGINAL_FILENAME + "\"}";

        restFileIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, otherFileIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteFileIngestionOwnedByAnotherUserIsNotFound() throws Exception {
        FileIngestion otherFileIngestion = saveFileIngestionOnOtherUsersIngestion();

        restFileIngestionMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherFileIngestion.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllFileIngestionsIncludingOtherUsers() throws Exception {
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);
        FileIngestion otherFileIngestion = saveFileIngestionOnOtherUsersIngestion();

        restFileIngestionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(fileIngestion.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherFileIngestion.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateFileIngestionOwnedByAnotherUser() throws Exception {
        FileIngestion otherFileIngestion = saveFileIngestionOnOtherUsersIngestion();
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(otherFileIngestion);
        fileIngestionDTO.setStatementEndDate(UPDATED_STATEMENT_END_DATE);

        restFileIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, fileIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(fileIngestionDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statementEndDate").value(UPDATED_STATEMENT_END_DATE.toString()));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteFileIngestionOwnedByAnotherUser() throws Exception {
        FileIngestion otherFileIngestion = saveFileIngestionOnOtherUsersIngestion();

        restFileIngestionMockMvc
            .perform(delete(ENTITY_API_URL_ID, otherFileIngestion.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createFileIngestionWithTransactionIngestionOwnedByAnotherUserFails() throws Exception {
        TransactionIngestion otherIngestion = saveIngestionOnOtherUsersAccount();
        FileIngestionDTO fileIngestionDTO = buildCreateFileIngestionDTO(otherIngestion.getId());

        restFileIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanCreateFileIngestionWithForeignTransactionIngestion() throws Exception {
        TransactionIngestion otherIngestion = saveIngestionOnOtherUsersAccount();
        FileIngestionDTO fileIngestionDTO = buildCreateFileIngestionDTO(otherIngestion.getId());

        restFileIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
            .andExpect(status().isCreated());

        fileIngestionRepository
            .findAll()
            .stream()
            .filter(fi -> fi.getTransactionIngestion().getId().equals(otherIngestion.getId()))
            .findFirst()
            .ifPresent(fi -> fileIngestionRepository.delete(fi));
    }

    @Test
    @Transactional
    void createFileIngestionWithApiTransactionIngestionFails() throws Exception {
        TransactionIngestion apiIngestion = TransactionIngestionResourceIT.createEntity(em);
        apiIngestion.setIngestionType(IngestionType.API);
        em.persist(apiIngestion);
        em.flush();

        FileIngestionDTO fileIngestionDTO = buildCreateFileIngestionDTO(apiIngestion.getId());

        restFileIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void createFileIngestionWithParentThatAlreadyHasFileIngestionFails() throws Exception {
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);
        FileIngestionDTO fileIngestionDTO = buildCreateFileIngestionDTO(fileIngestion.getTransactionIngestion().getId());

        restFileIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateFileIngestionWithDifferentTransactionIngestionFails() throws Exception {
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);
        TransactionIngestion otherIngestion = TransactionIngestionResourceIT.createEntity(em);
        em.persist(otherIngestion);
        em.flush();

        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(insertedFileIngestion);
        TransactionIngestionDTO otherParentDTO = new TransactionIngestionDTO();
        otherParentDTO.setId(otherIngestion.getId());
        fileIngestionDTO.setTransactionIngestion(otherParentDTO);

        restFileIngestionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, fileIngestionDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(fileIngestionDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchFileIngestionWithNullTransactionIngestionFails() throws Exception {
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);
        String patchJson = "{\"id\":" + fileIngestion.getId() + ",\"transactionIngestion\":null}";

        restFileIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, fileIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchFileIngestionWithDifferentTransactionIngestionFails() throws Exception {
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);
        TransactionIngestion otherIngestion = TransactionIngestionResourceIT.createEntity(em);
        em.persist(otherIngestion);
        em.flush();

        String patchJson = "{\"id\":" + fileIngestion.getId() + ",\"transactionIngestion\":{\"id\":" + otherIngestion.getId() + "}}";

        restFileIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, fileIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchFileIngestionWithoutTransactionIngestionFieldPreservesParent() throws Exception {
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);
        Long originalParentId = fileIngestion.getTransactionIngestion().getId();
        String patchJson = "{\"id\":" + fileIngestion.getId() + ",\"statementEndDate\":\"" + UPDATED_STATEMENT_END_DATE + "\"}";

        restFileIngestionMockMvc
            .perform(patch(ENTITY_API_URL_ID, fileIngestion.getId()).contentType("application/merge-patch+json").content(patchJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionIngestion.id").value(originalParentId.intValue()));

        assertThat(getPersistedFileIngestion(fileIngestion).getTransactionIngestion().getId()).isEqualTo(originalParentId);
    }

    private FileIngestion saveFileIngestionOnOtherUsersIngestion() {
        TransactionIngestion otherIngestion = saveIngestionOnOtherUsersAccount();
        FileIngestion otherFileIngestion = FileIngestionResourceIT.createEntity(em);
        otherFileIngestion.setTransactionIngestion(otherIngestion);
        return fileIngestionRepository.saveAndFlush(otherFileIngestion);
    }

    private TransactionIngestion saveIngestionOnOtherUsersAccount() {
        FinancialAccount otherAccount = createAccountForUser(em, createOtherUser(em));
        TransactionIngestion otherIngestion = TransactionIngestionResourceIT.createEntity(em);
        otherIngestion.setAccount(otherAccount);
        return transactionIngestionRepository.saveAndFlush(otherIngestion);
    }

    private static User createOtherUser(EntityManager em) {
        User otherUser = UserResourceIT.createEntity();
        em.persist(otherUser);
        em.flush();
        return otherUser;
    }

    private static FinancialAccount createAccountForUser(EntityManager em, User user) {
        FinancialAccount financialAccount = FinancialAccountResourceIT.createEntity(em);
        financialAccount.setUser(user);
        em.persist(financialAccount);
        em.flush();
        return financialAccount;
    }

    private FileIngestionDTO buildCreateFileIngestionDTO(Long transactionIngestionId) {
        FileIngestionDTO fileIngestionDTO = new FileIngestionDTO();
        fileIngestionDTO.setOriginalFilename(DEFAULT_ORIGINAL_FILENAME);
        fileIngestionDTO.setFileType(DEFAULT_FILE_TYPE);
        TransactionIngestionDTO transactionIngestionDTO = new TransactionIngestionDTO();
        transactionIngestionDTO.setId(transactionIngestionId);
        fileIngestionDTO.setTransactionIngestion(transactionIngestionDTO);
        return fileIngestionDTO;
    }

    protected long getRepositoryCount() {
        return fileIngestionRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected FileIngestion getPersistedFileIngestion(FileIngestion fileIngestion) {
        return fileIngestionRepository.findById(fileIngestion.getId()).orElseThrow();
    }

    protected void assertPersistedFileIngestionToMatchAllProperties(FileIngestion expectedFileIngestion) {
        assertFileIngestionAllPropertiesEquals(expectedFileIngestion, getPersistedFileIngestion(expectedFileIngestion));
    }

    protected void assertPersistedFileIngestionToMatchUpdatableProperties(FileIngestion expectedFileIngestion) {
        assertFileIngestionAllUpdatablePropertiesEquals(expectedFileIngestion, getPersistedFileIngestion(expectedFileIngestion));
    }
}
