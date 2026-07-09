package com.fintrack.app.web.rest;

import static com.fintrack.app.domain.UserDashboardPreferenceAsserts.*;
import static com.fintrack.app.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.UserDashboardPreference;
import com.fintrack.app.repository.UserDashboardPreferenceRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.service.UserDashboardPreferenceService;
import com.fintrack.app.service.dto.UserDTO;
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

    private static final String CURRENT_MOCK_USER_LOGIN = "user";

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
        userDashboardPreference.setUser(getCurrentMockUser(em));
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
        updatedUserDashboardPreference.setUser(getCurrentMockUser(em));
        return updatedUserDashboardPreference;
    }

    private static User getCurrentMockUser(EntityManager em) {
        return TestUtil.findAll(em, User.class)
            .stream()
            .filter(user -> CURRENT_MOCK_USER_LOGIN.equals(user.getLogin()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Current mock user not found"));
    }

    private static User createOtherUser(EntityManager em) {
        User otherUser = UserResourceIT.createEntity();
        em.persist(otherUser);
        em.flush();
        return otherUser;
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
        String patchJson = "{\"id\":" + userDashboardPreference.getId() + ",\"configuration\":\"" + UPDATED_CONFIGURATION + "\"}";

        restUserDashboardPreferenceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, userDashboardPreference.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isOk());

        // Validate the UserDashboardPreference in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertThat(getPersistedUserDashboardPreference(userDashboardPreference).getConfiguration()).isEqualTo(UPDATED_CONFIGURATION);
    }

    @Test
    @Transactional
    void fullUpdateUserDashboardPreferenceWithPatch() throws Exception {
        // Initialize the database
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        String patchJson =
            "{\"id\":" +
            userDashboardPreference.getId() +
            ",\"configuration\":\"" +
            UPDATED_CONFIGURATION +
            "\",\"createdAt\":\"" +
            UPDATED_CREATED_AT +
            "\",\"updatedAt\":\"" +
            UPDATED_UPDATED_AT +
            "\"}";

        restUserDashboardPreferenceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, userDashboardPreference.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isOk());

        // Validate the UserDashboardPreference in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        UserDashboardPreference persisted = getPersistedUserDashboardPreference(userDashboardPreference);
        assertThat(persisted.getConfiguration()).isEqualTo(UPDATED_CONFIGURATION);
        assertThat(persisted.getCreatedAt()).isEqualTo(UPDATED_CREATED_AT);
        assertThat(persisted.getUpdatedAt()).isEqualTo(UPDATED_UPDATED_AT);
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

    @Test
    @Transactional
    void getUserDashboardPreferenceOwnedByAnotherUserIsNotFound() throws Exception {
        userDashboardPreference.setUser(createOtherUser(em));
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        restUserDashboardPreferenceMockMvc
            .perform(get(ENTITY_API_URL_ID, userDashboardPreference.getId()))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getAllUserDashboardPreferencesDoesNotIncludeAnotherUsersPreferences() throws Exception {
        userDashboardPreference.setUser(createOtherUser(em));
        userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        restUserDashboardPreferenceMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(not(hasItem(userDashboardPreference.getId().intValue()))));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanGetUserDashboardPreferenceOwnedByAnotherUser() throws Exception {
        userDashboardPreference.setUser(createOtherUser(em));
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        restUserDashboardPreferenceMockMvc
            .perform(get(ENTITY_API_URL_ID, userDashboardPreference.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(userDashboardPreference.getId().intValue()));
    }

    @Test
    @Transactional
    void createUserDashboardPreferenceWithoutUserInPayloadSucceeds() throws Exception {
        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);
        userDashboardPreferenceDTO.setId(null);
        userDashboardPreferenceDTO.setUser(null);

        UserDashboardPreferenceDTO returnedUserDashboardPreferenceDTO = om.readValue(
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

        assertThat(returnedUserDashboardPreferenceDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedUserDashboardPreference = userDashboardPreferenceMapper.toEntity(returnedUserDashboardPreferenceDTO);
    }

    @Test
    @Transactional
    void createUserDashboardPreferenceWithForeignUserAssignsCurrentUser() throws Exception {
        User otherUser = createOtherUser(em);
        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);
        userDashboardPreferenceDTO.setId(null);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        userDashboardPreferenceDTO.setUser(otherUserDTO);

        UserDashboardPreferenceDTO returnedUserDashboardPreferenceDTO = om.readValue(
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

        assertThat(returnedUserDashboardPreferenceDTO.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        insertedUserDashboardPreference = userDashboardPreferenceMapper.toEntity(returnedUserDashboardPreferenceDTO);
    }

    @Test
    @Transactional
    void createUserDashboardPreferenceWhenUserAlreadyHasPreferenceFails() throws Exception {
        userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(createEntity(em));
        userDashboardPreferenceDTO.setId(null);
        userDashboardPreferenceDTO.setUser(null);

        restUserDashboardPreferenceMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userDashboardPreferenceDTO)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void putUserDashboardPreferenceOwnedByAnotherUserIsNotFound() throws Exception {
        userDashboardPreference.setUser(createOtherUser(em));
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);
        userDashboardPreferenceDTO.setConfiguration(UPDATED_CONFIGURATION);

        restUserDashboardPreferenceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, userDashboardPreferenceDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(userDashboardPreferenceDTO))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void patchUserDashboardPreferenceOwnedByAnotherUserIsNotFound() throws Exception {
        userDashboardPreference.setUser(createOtherUser(em));
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        String patchJson = "{\"id\":" + userDashboardPreference.getId() + ",\"configuration\":\"" + UPDATED_CONFIGURATION + "\"}";

        restUserDashboardPreferenceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, userDashboardPreference.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteUserDashboardPreferenceOwnedByAnotherUserIsNotFound() throws Exception {
        userDashboardPreference.setUser(createOtherUser(em));
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        restUserDashboardPreferenceMockMvc
            .perform(delete(ENTITY_API_URL_ID, userDashboardPreference.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        assertThat(userDashboardPreferenceRepository.existsById(userDashboardPreference.getId())).isTrue();
    }

    @Test
    @Transactional
    void updateUserDashboardPreferenceCannotChangeOwner() throws Exception {
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);
        User otherUser = createOtherUser(em);

        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);
        UserDTO otherUserDTO = new UserDTO();
        otherUserDTO.setId(otherUser.getId());
        otherUserDTO.setLogin(otherUser.getLogin());
        userDashboardPreferenceDTO.setUser(otherUserDTO);
        userDashboardPreferenceDTO.setConfiguration(UPDATED_CONFIGURATION);

        restUserDashboardPreferenceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, userDashboardPreferenceDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(userDashboardPreferenceDTO))
            )
            .andExpect(status().isOk());

        UserDashboardPreference persisted = userDashboardPreferenceRepository.findById(userDashboardPreference.getId()).orElseThrow();
        assertThat(persisted.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persisted.getConfiguration()).isEqualTo(UPDATED_CONFIGURATION);
    }

    @Test
    @Transactional
    void patchUserDashboardPreferenceCannotChangeOwner() throws Exception {
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);
        User otherUser = createOtherUser(em);

        String patchJson =
            "{\"id\":" +
            userDashboardPreference.getId() +
            ",\"configuration\":\"" +
            UPDATED_CONFIGURATION +
            "\",\"user\":{\"id\":" +
            otherUser.getId() +
            ",\"login\":\"" +
            otherUser.getLogin() +
            "\"}}";

        restUserDashboardPreferenceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, userDashboardPreference.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isOk());

        UserDashboardPreference persisted = userDashboardPreferenceRepository.findById(userDashboardPreference.getId()).orElseThrow();
        assertThat(persisted.getUser().getLogin()).isEqualTo(CURRENT_MOCK_USER_LOGIN);
        assertThat(persisted.getConfiguration()).isEqualTo(UPDATED_CONFIGURATION);
    }

    @Test
    @Transactional
    void patchUserDashboardPreferenceWithoutUserFieldPreservesOwner() throws Exception {
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        String patchJson = "{\"id\":" + userDashboardPreference.getId() + ",\"configuration\":\"" + UPDATED_CONFIGURATION + "\"}";

        restUserDashboardPreferenceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, userDashboardPreference.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.login").value(CURRENT_MOCK_USER_LOGIN));
    }

    @Test
    @Transactional
    void patchUserDashboardPreferenceWithNullUserFails() throws Exception {
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        String patchJson = "{\"id\":" + userDashboardPreference.getId() + ",\"user\":null}";

        restUserDashboardPreferenceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, userDashboardPreference.getId()).contentType("application/merge-patch+json").content(patchJson)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanListAllUserDashboardPreferencesIncludingOtherUsers() throws Exception {
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);
        UserDashboardPreference otherPreference = createEntity(em);
        otherPreference.setUser(createOtherUser(em));
        otherPreference = userDashboardPreferenceRepository.saveAndFlush(otherPreference);

        restUserDashboardPreferenceMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.[*].id").value(hasItem(userDashboardPreference.getId().intValue())))
            .andExpect(jsonPath("$.[*].id").value(hasItem(otherPreference.getId().intValue())));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanUpdateUserDashboardPreferenceOwnedByAnotherUser() throws Exception {
        userDashboardPreference.setUser(createOtherUser(em));
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);

        UserDashboardPreferenceDTO userDashboardPreferenceDTO = userDashboardPreferenceMapper.toDto(userDashboardPreference);
        userDashboardPreferenceDTO.setConfiguration(UPDATED_CONFIGURATION);

        restUserDashboardPreferenceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, userDashboardPreferenceDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(userDashboardPreferenceDTO))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configuration").value(UPDATED_CONFIGURATION));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void adminCanDeleteUserDashboardPreferenceOwnedByAnotherUser() throws Exception {
        userDashboardPreference.setUser(createOtherUser(em));
        insertedUserDashboardPreference = userDashboardPreferenceRepository.saveAndFlush(userDashboardPreference);
        long databaseSizeBeforeDelete = getRepositoryCount();

        restUserDashboardPreferenceMockMvc
            .perform(delete(ENTITY_API_URL_ID, userDashboardPreference.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
        insertedUserDashboardPreference = null;
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
