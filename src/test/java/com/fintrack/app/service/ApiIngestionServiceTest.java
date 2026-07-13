package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.ApiIngestion;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.ApiIngestionRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.ApiIngestionCreateRequestDTO;
import com.fintrack.app.service.dto.ApiIngestionDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.ApiIngestionMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiIngestionServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private ApiIngestionRepository apiIngestionRepository;

    @Mock
    private ApiIngestionMapper apiIngestionMapper;

    @Mock
    private TransactionIngestionRepository transactionIngestionRepository;

    @Mock
    private ApiAccessTokenService apiAccessTokenService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private ApiIngestionService apiIngestionService;

    private User currentUser;
    private User otherUser;
    private FinancialAccount account;
    private TransactionIngestion transactionIngestion;
    private TransactionIngestion fileTransactionIngestion;
    private ApiAccessToken apiAccessToken;
    private ApiIngestion apiIngestion;
    private ApiIngestionCreateRequestDTO createRequest;
    private ApiIngestionDTO apiIngestionDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        otherUser = new User();
        otherUser.setId(3L);
        otherUser.setLogin("other");

        account = new FinancialAccount();
        account.setId(10L);
        account.setUser(currentUser);

        transactionIngestion = new TransactionIngestion();
        transactionIngestion.setId(50L);
        transactionIngestion.setIngestionType(IngestionType.API);
        transactionIngestion.setAccount(account);

        fileTransactionIngestion = new TransactionIngestion();
        fileTransactionIngestion.setId(51L);
        fileTransactionIngestion.setIngestionType(IngestionType.FILE);
        fileTransactionIngestion.setAccount(account);

        apiAccessToken = new ApiAccessToken();
        apiAccessToken.setId(70L);
        apiAccessToken.setUser(currentUser);
        apiAccessToken.setTokenPrefix("ftk_prefix");
        apiAccessToken.setName("Import Token");

        apiIngestion = new ApiIngestion();
        apiIngestion.setId(100L);
        apiIngestion.setRequestId("req-1");
        apiIngestion.setApiVersion("v1");
        apiIngestion.setEndpoint("/transactions");
        apiIngestion.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        apiIngestion.setReceivedAt(Instant.parse("2026-01-01T00:00:00Z"));
        apiIngestion.setTransactionIngestion(transactionIngestion);
        apiIngestion.setApiTokenIdSnapshot(70L);
        apiIngestion.setApiTokenPrefixSnapshot("ftk_prefix");
        apiIngestion.setApiTokenNameSnapshot("Import Token");

        createRequest = new ApiIngestionCreateRequestDTO();
        createRequest.setRequestId("req-1");
        createRequest.setApiVersion("v1");
        createRequest.setEndpoint("/transactions");
        createRequest.setApiAccessTokenId(70L);

        TransactionIngestionDTO transactionIngestionDTO = new TransactionIngestionDTO();
        transactionIngestionDTO.setId(50L);
        createRequest.setTransactionIngestion(transactionIngestionDTO);

        apiIngestionDTO = new ApiIngestionDTO();
        apiIngestionDTO.setRequestId("req-1");
        apiIngestionDTO.setApiVersion("v1");
        apiIngestionDTO.setEndpoint("/transactions");
        apiIngestionDTO.setTransactionIngestion(transactionIngestionDTO);
    }

    @Test
    void saveShouldResolveParentsValidateSameOwnerApplyTimestampsAndRejectFileParent() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionIngestionRepository.findOneWithToOneRelationshipsByIdAndAccountUserLogin(50L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );
        when(apiAccessTokenService.findAccessibleApiAccessTokenEntity(70L)).thenReturn(Optional.of(apiAccessToken));
        when(apiIngestionRepository.existsByTransactionIngestionId(50L)).thenReturn(false);
        when(apiIngestionRepository.existsByRequestId("req-1")).thenReturn(false);
        when(apiIngestionRepository.save(any(ApiIngestion.class))).thenReturn(apiIngestion);
        when(apiIngestionMapper.toDto(apiIngestion)).thenReturn(apiIngestionDTO);

        apiIngestionService.save(createRequest);

        ArgumentCaptor<ApiIngestion> captor = ArgumentCaptor.forClass(ApiIngestion.class);
        verify(apiIngestionRepository).save(captor.capture());
        ApiIngestion saved = captor.getValue();
        assertThat(saved.getTransactionIngestion()).isEqualTo(transactionIngestion);
        assertThat(saved.getApiTokenIdSnapshot()).isEqualTo(70L);
        assertThat(saved.getApiTokenPrefixSnapshot()).isEqualTo("ftk_prefix");
        assertThat(saved.getApiTokenNameSnapshot()).isEqualTo("Import Token");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getReceivedAt()).isNotNull();

        TransactionIngestionDTO fileParentDTO = new TransactionIngestionDTO();
        fileParentDTO.setId(51L);
        createRequest.setTransactionIngestion(fileParentDTO);
        when(transactionIngestionRepository.findOneWithToOneRelationshipsByIdAndAccountUserLogin(51L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(fileTransactionIngestion)
        );

        assertThatThrownBy(() -> apiIngestionService.save(createRequest)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveShouldRejectCrossOwnerParentsEvenForAdmin() {
        createRequest.setRequestId("req-2");

        User userA = new User();
        userA.setId(4L);
        userA.setLogin("user-a");

        User userB = new User();
        userB.setId(5L);
        userB.setLogin("user-b");

        TransactionIngestion ingestionUserA = new TransactionIngestion();
        ingestionUserA.setId(52L);
        ingestionUserA.setIngestionType(IngestionType.API);
        FinancialAccount accountA = new FinancialAccount();
        accountA.setUser(userA);
        ingestionUserA.setAccount(accountA);

        ApiAccessToken tokenUserB = new ApiAccessToken();
        tokenUserB.setId(71L);
        tokenUserB.setUser(userB);

        TransactionIngestionDTO ingestionUserADTO = new TransactionIngestionDTO();
        ingestionUserADTO.setId(52L);
        createRequest.setTransactionIngestion(ingestionUserADTO);
        createRequest.setApiAccessTokenId(71L);

        when(currentUserService.isAdmin()).thenReturn(true);
        when(transactionIngestionRepository.findOneWithToOneRelationships(52L)).thenReturn(Optional.of(ingestionUserA));
        when(apiAccessTokenService.findAccessibleApiAccessTokenEntity(71L)).thenReturn(Optional.of(tokenUserB));

        assertThatThrownBy(() -> apiIngestionService.save(createRequest)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveShouldRejectDuplicateParentAndDuplicateRequestId() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionIngestionRepository.findOneWithToOneRelationshipsByIdAndAccountUserLogin(50L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );
        when(apiAccessTokenService.findAccessibleApiAccessTokenEntity(70L)).thenReturn(Optional.of(apiAccessToken));
        when(apiIngestionRepository.existsByTransactionIngestionId(50L)).thenReturn(true);

        assertThatThrownBy(() -> apiIngestionService.save(createRequest)).isInstanceOf(IllegalArgumentException.class);

        when(apiIngestionRepository.existsByTransactionIngestionId(50L)).thenReturn(false);
        when(apiIngestionRepository.existsByRequestId("req-1")).thenReturn(true);

        assertThatThrownBy(() -> apiIngestionService.save(createRequest)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateShouldRejectImmutableFieldChanges() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiIngestionRepository.findOneWithToOneRelationshipsByUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiIngestion)
        );

        apiIngestionDTO.setId(100L);
        apiIngestionDTO.setCreatedAt(apiIngestion.getCreatedAt());
        apiIngestionDTO.setReceivedAt(apiIngestion.getReceivedAt());
        apiIngestionDTO.setApiTokenIdSnapshot(70L);
        apiIngestionDTO.setApiTokenPrefixSnapshot("ftk_prefix");
        apiIngestionDTO.setApiTokenNameSnapshot("Import Token");

        TransactionIngestionDTO otherParentDTO = new TransactionIngestionDTO();
        otherParentDTO.setId(99L);
        apiIngestionDTO.setTransactionIngestion(otherParentDTO);
        assertThatThrownBy(() -> apiIngestionService.update(apiIngestionDTO)).isInstanceOf(IllegalArgumentException.class);

        apiIngestionDTO.setTransactionIngestion(new TransactionIngestionDTO());
        apiIngestionDTO.getTransactionIngestion().setId(50L);
        apiIngestionDTO.setApiTokenNameSnapshot("renamed");
        assertThatThrownBy(() -> apiIngestionService.update(apiIngestionDTO)).isInstanceOf(IllegalArgumentException.class);

        apiIngestionDTO.setApiTokenNameSnapshot("Import Token");
        apiIngestionDTO.setRequestId("req-2");
        assertThatThrownBy(() -> apiIngestionService.update(apiIngestionDTO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partialUpdateShouldRejectNullTransactionIngestion() throws Exception {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiIngestionRepository.findOneWithToOneRelationshipsByUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiIngestion)
        );

        apiIngestionDTO.setId(100L);
        ObjectNode patchNode = new ObjectMapper().createObjectNode();
        patchNode.putNull("transactionIngestion");
        assertThatThrownBy(() -> apiIngestionService.partialUpdate(apiIngestionDTO, patchNode)).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    void partialUpdateShouldRejectNullApiTokenNameSnapshot() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiIngestionRepository.findOneWithToOneRelationshipsByUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiIngestion)
        );

        apiIngestionDTO.setId(100L);
        ObjectNode patchNode = new ObjectMapper().createObjectNode();
        patchNode.putNull("apiTokenNameSnapshot");
        assertThatThrownBy(() -> apiIngestionService.partialUpdate(apiIngestionDTO, patchNode)).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    void partialUpdateShouldRejectRequestIdChange() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiIngestionRepository.findOneWithToOneRelationshipsByUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiIngestion)
        );

        apiIngestionDTO.setId(100L);
        ObjectNode patchNode = new ObjectMapper().createObjectNode();
        patchNode.put("requestId", "req-2");
        apiIngestionDTO.setRequestId("req-2");
        assertThatThrownBy(() -> apiIngestionService.partialUpdate(apiIngestionDTO, patchNode)).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    void deleteShouldReturnFalseWhenNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiIngestionRepository.findOneWithToOneRelationshipsByUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(apiIngestionService.delete(100L)).isFalse();
        verify(apiIngestionRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldRejectAccessibleApiIngestion() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiIngestionRepository.findOneWithToOneRelationshipsByUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiIngestion)
        );

        assertThatThrownBy(() -> apiIngestionService.delete(100L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be deleted directly");
        verify(apiIngestionRepository, never()).deleteById(any());
    }

    @Test
    void findAllShouldUseScopedRepositoryForNormalUser() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(apiIngestionRepository.findAllWithToOneRelationshipsByUserLogin(CURRENT_USER_LOGIN)).thenReturn(List.of(apiIngestion));
        when(apiIngestionMapper.toDto(apiIngestion)).thenReturn(apiIngestionDTO);

        assertThat(apiIngestionService.findAll()).hasSize(1);
    }
}
