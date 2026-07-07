package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.UserDashboardPreferenceAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.UserDashboardPreference;
import com.fintrack.app.repository.UserDashboardPreferenceRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.service.UserDashboardPreferenceService;
import com.fintrack.app.service.dto.UserDashboardPreferenceDTO;
import com.fintrack.app.service.mapper.UserDashboardPreferenceMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link UserDashboardPreferenceResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class UserDashboardPreferenceResourceIT {

    private static final String DEFAULT_CONFIGURATION = "AAAAAAAAAA";
    private static final String UPDATED_CONFIGURATION = "BBBBBBBBBB";

    private static final Instant DEFAULT_CREATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_UPDATED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPDATED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/user-dashboard-preferences";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private UserDashboardPreferenceRepository userDashboardPreferenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Mock
    private UserDashboardPreferenceRepository userDashboardPreferenceRepositoryMock;

    @Autowired
    private UserDashboardPreferenceMapper userDashboardPreferenceMapper;

    @Mock
    private UserDashboardPreferenceService userDashboardPreferenceServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restUserDashboardPreferenceMockMvc;

    private UserDashboardPreference userDashboardPreference;

    private UserDashboardPreference insertedUserDashboardPreference;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static UserDashboardPreference createEntity(EntityManager em) {
        UserDashboardPreference userDashboardPreference = new UserDashboardPreference()
            .configuration(DEFAULT_CONFIGURATION)
            .createdAt(DEFAULT_CREATED_AT)
            .updatedAt(DEFAULT_UPDATED_AT);
        // Add required entity
        User user = UserResourceIT.createEntity();
        em.persist(user);
        em.flush();
        userDashboardPreference.setUser(user);
        return userDashboardPreference;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static UserDashboardPreference createUpdatedEntity(EntityManager em) {
        UserDashboardPreference updatedUserDashboardPreference = new UserDashboardPreference()
            .configuration(UPDATED_CONFIGURATION)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);
        // Add required entity
        User user = UserResourceIT.createEntity();
        em.persist(user);
        em.flush();
        updatedUserDashboardPreference.setUser(user);
        return updatedUserDashboardPreference;
    }

    @BeforeEach
    void initTest() {
        userDashboardPreference = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedUserDashboardPreference != null) {
            userDashboardPreferenceRepository.delete(insertedUserDashboardPreference);
            insertedUserDashboardPreference = null;
        }
    }

    @Test
    @Transactional
    void createUserDashboardPreference() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the UserDashboardPreference
        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);
        var returnedUserDashboardPreferenceDTO = om.readValue(
            restUserDashboardPreferenceMockMvc
                .perform(
                    post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDashboardPreferenceDTO))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            UserDashboardPreferenceDTO.class
        );

        // Validate the UserDashboardPreference in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedUserDashboardPreference = userDashboardPreferenceMapper.toEntity(returnedUserDashboardPreferenceDTO);
        assertUserDashboardPreferenceUpdatableFieldsEquals(
            returnedUserDashboardPreference,
            getPersistedUserDashboardPreference(returnedUserDashboardPreference)
        );

        insertedUserDashboardPreference = returnedUserDashboardPreference;
    }

    @Test
    @Transactional
    void createUserDashboardPreferenceWithExistingId() throws Exception {
        // Create the UserDashboardPreference with an existing ID
        userDashboardPreference.setId(1L);
        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restUserDashboardPreferenceMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDashboardPreferenceDTO)))
            .andExpect(status().isBadRequest());

        // Validate the UserDashboardPreference in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkCreatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        userDashboardPreference.setCreatedAt(null);

        // Create the UserDashboardPreference, which fails.
        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);

        restUserDashboardPreferenceMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDashboardPreferenceDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkUpdatedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        userDashboardPreference.setUpdatedAt(null);

        // Create the UserDashboardPreference, which fails.
        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);

        restUserDashboardPreferenceMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDashboardPreferenceDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllUserDashboardPreferences() throws Exception {
        // Initialize the database
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        // Get all the userDashboardPreferenceList
        restUserDashboardPreferenceMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(userDashboardPreference.getId().intValue())))
            .andExpect(jsonPath("$.[*].configuration").value(hasItem(DEFAULT_CONFIGURATION)))
            .andExpect(jsonPath("$.[*].createdAt").value(hasItem(DEFAULT_CREATED_AT.toString())))
            .andExpect(jsonPath("$.[*].updatedAt").value(hasItem(DEFAULT_UPDATED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllUserDashboardPreferencesWithEagerRelationshipsIsEnabled() throws Exception {
        when(userDashboardPreferenceServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restUserDashboardPreferenceMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(userDashboardPreferenceServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllUserDashboardPreferencesWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(userDashboardPreferenceServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restUserDashboardPreferenceMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(userDashboardPreferenceRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getUserDashboardPreference() throws Exception {
        // Initialize the database
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        // Get the userDashboardPreference
        restUserDashboardPreferenceMockMvc
            .perform(get(ENTITY_API_URL_ID, userDashboardPreference.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(userDashboardPreference.getId().intValue()))
            .andExpect(jsonPath("$.configuration").value(DEFAULT_CONFIGURATION))
            .andExpect(jsonPath("$.createdAt").value(DEFAULT_CREATED_AT.toString()))
            .andExpect(jsonPath("$.updatedAt").value(DEFAULT_UPDATED_AT.toString()));
    }

    @Test
    @Transactional
    void getNonExistingUserDashboardPreference() throws Exception {
        // Get the userDashboardPreference
        restUserDashboardPreferenceMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingUserDashboardPreference() throws Exception {
        // Initialize the database
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the userDashboardPreference
        UserDashboardPreference updatedUserDashboardPreference = userDashboardPreferenceRepository
            .findById(userDashboardPreference.getId())
            .orElseThrow();
        // Disconnect from session so that the updates on updatedUserDashboardPreference are not directly saved in db
        em.detach(updatedUserDashboardPreference);
        updatedUserDashboardPreference.configuration(UPDATED_CONFIGURATION).createdAt(UPDATED_CREATED_AT).updatedAt(UPDATED_UPDATED_AT);
        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(updatedUserDashboardPreference);

        restUserDashboardPreferenceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, userDashboardPreferenceDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(userDashboardPreferenceDTO))
            )
            .andExpect(status().isOk());

        // Validate the UserDashboardPreference in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedUserDashboardPreferenceToMatchAllProperties(updatedUserDashboardPreference);
    }

    @Test
    @Transactional
    void putNonExistingUserDashboardPreference() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userDashboardPreference.setId(longCount.incrementAndGet());

        // Create the UserDashboardPreference
        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restUserDashboardPreferenceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, userDashboardPreferenceDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(userDashboardPreferenceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the UserDashboardPreference in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchUserDashboardPreference() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userDashboardPreference.setId(longCount.incrementAndGet());

        // Create the UserDashboardPreference
        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUserDashboardPreferenceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(userDashboardPreferenceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the UserDashboardPreference in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamUserDashboardPreference() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userDashboardPreference.setId(longCount.incrementAndGet());

        // Create the UserDashboardPreference
        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUserDashboardPreferenceMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDashboardPreferenceDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the UserDashboardPreference in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateUserDashboardPreferenceWithPatch() throws Exception {
        // Initialize the database
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the userDashboardPreference using partial update
        UserDashboardPreference partialUpdatedUserDashboardPreference = new UserDashboardPreference();
        partialUpdatedUserDashboardPreference.setId(userDashboardPreference.getId());

        partialUpdatedUserDashboardPreference.configuration(UPDATED_CONFIGURATION);

        restUserDashboardPreferenceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedUserDashboardPreference.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedUserDashboardPreference))
            )
            .andExpect(status().isOk());

        // Validate the UserDashboardPreference in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertUserDashboardPreferenceUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedUserDashboardPreference, userDashboardPreference),
            getPersistedUserDashboardPreference(userDashboardPreference)
        );
    }

    @Test
    @Transactional
    void fullUpdateUserDashboardPreferenceWithPatch() throws Exception {
        // Initialize the database
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the userDashboardPreference using partial update
        UserDashboardPreference partialUpdatedUserDashboardPreference = new UserDashboardPreference();
        partialUpdatedUserDashboardPreference.setId(userDashboardPreference.getId());

        partialUpdatedUserDashboardPreference
            .configuration(UPDATED_CONFIGURATION)
            .createdAt(UPDATED_CREATED_AT)
            .updatedAt(UPDATED_UPDATED_AT);

        restUserDashboardPreferenceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedUserDashboardPreference.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedUserDashboardPreference))
            )
            .andExpect(status().isOk());

        // Validate the UserDashboardPreference in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertUserDashboardPreferenceUpdatableFieldsEquals(
            partialUpdatedUserDashboardPreference,
            getPersistedUserDashboardPreference(partialUpdatedUserDashboardPreference)
        );
    }

    @Test
    @Transactional
    void patchNonExistingUserDashboardPreference() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userDashboardPreference.setId(longCount.incrementAndGet());

        // Create the UserDashboardPreference
        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restUserDashboardPreferenceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, userDashboardPreferenceDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(userDashboardPreferenceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the UserDashboardPreference in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchUserDashboardPreference() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userDashboardPreference.setId(longCount.incrementAndGet());

        // Create the UserDashboardPreference
        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUserDashboardPreferenceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(userDashboardPreferenceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the UserDashboardPreference in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamUserDashboardPreference() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userDashboardPreference.setId(longCount.incrementAndGet());

        // Create the UserDashboardPreference
        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUserDashboardPreferenceMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(userDashboardPreferenceDTO))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the UserDashboardPreference in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteUserDashboardPreference() throws Exception {
        // Initialize the database
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the userDashboardPreference
        restUserDashboardPreferenceMockMvc
            .perform(delete(ENTITY_API_URL_ID, userDashboardPreference.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return userDashboardPreferenceRepository.count();
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

    protected UserDashboardPreference getPersistedUserDashboardPreference(UserDashboardPreference userDashboardPreference) {
        return userDashboardPreferenceRepository.findById(userDashboardPreference.getId()).orElseThrow();
    }

    protected void assertPersistedUserDashboardPreferenceToMatchAllProperties(UserDashboardPreference expectedUserDashboardPreference) {
        assertUserDashboardPreferenceAllPropertiesEquals(
            expectedUserDashboardPreference,
            getPersistedUserDashboardPreference(expectedUserDashboardPreference)
        );
    }

    protected void assertPersistedUserDashboardPreferenceToMatchUpdatableProperties(
        UserDashboardPreference expectedUserDashboardPreference
    ) {
        assertUserDashboardPreferenceAllUpdatablePropertiesEquals(
            expectedUserDashboardPreference,
            getPersistedUserDashboardPreference(expectedUserDashboardPreference)
        );
    }
}
