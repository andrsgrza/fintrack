package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.FileIngestionAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.FileIngestion;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.ImportFileType;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.service.dto.FileIngestionDTO;
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
        var returnedFileIngestion = fileIngestionMapper.toEntity(returnedFileIngestionDTO);
        assertFileIngestionUpdatableFieldsEquals(returnedFileIngestion, getPersistedFileIngestion(returnedFileIngestion));

        insertedFileIngestion = returnedFileIngestion;
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
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        fileIngestion.setCreatedAt(null);

        // Create the FileIngestion, which fails.
        FileIngestionDTO fileIngestionDTO = fileIngestionMapper.toDto(fileIngestion);

        restFileIngestionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(fileIngestionDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
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
        FileIngestion partialUpdatedFileIngestion = new FileIngestion();
        partialUpdatedFileIngestion.setId(fileIngestion.getId());

        partialUpdatedFileIngestion
            .fileType(UPDATED_FILE_TYPE)
            .contentType(UPDATED_CONTENT_TYPE)
            .checksum(UPDATED_CHECKSUM)
            .createdAt(UPDATED_CREATED_AT);

        restFileIngestionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedFileIngestion.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedFileIngestion))
            )
            .andExpect(status().isOk());

        // Validate the FileIngestion in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertFileIngestionUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedFileIngestion, fileIngestion),
            getPersistedFileIngestion(fileIngestion)
        );
    }

    @Test
    @Transactional
    void fullUpdateFileIngestionWithPatch() throws Exception {
        // Initialize the database
        insertedFileIngestion = fileIngestionRepository.saveAndFlush(fileIngestion);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the fileIngestion using partial update
        FileIngestion partialUpdatedFileIngestion = new FileIngestion();
        partialUpdatedFileIngestion.setId(fileIngestion.getId());

        partialUpdatedFileIngestion
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

        restFileIngestionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedFileIngestion.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedFileIngestion))
            )
            .andExpect(status().isOk());

        // Validate the FileIngestion in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertFileIngestionUpdatableFieldsEquals(partialUpdatedFileIngestion, getPersistedFileIngestion(partialUpdatedFileIngestion));
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
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
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
