package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.domain.FileIngestion;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.ImportFileType;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.FileIngestionDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.FileIngestionMapper;
import java.time.Instant;
import java.time.LocalDate;
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
class FileIngestionServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private FileIngestionRepository fileIngestionRepository;

    @Mock
    private FileIngestionMapper fileIngestionMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private TransactionIngestionRepository transactionIngestionRepository;

    @InjectMocks
    private FileIngestionService fileIngestionService;

    private User currentUser;
    private FinancialAccount account;
    private TransactionIngestion transactionIngestion;
    private TransactionIngestion apiTransactionIngestion;
    private FileIngestion fileIngestion;
    private FileIngestionDTO fileIngestionDTO;
    private TransactionIngestionDTO transactionIngestionDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        account = new FinancialAccount();
        account.setId(10L);
        account.setUser(currentUser);

        transactionIngestion = new TransactionIngestion();
        transactionIngestion.setId(50L);
        transactionIngestion.setIngestionType(IngestionType.FILE);
        transactionIngestion.setAccount(account);

        apiTransactionIngestion = new TransactionIngestion();
        apiTransactionIngestion.setId(51L);
        apiTransactionIngestion.setIngestionType(IngestionType.API);
        apiTransactionIngestion.setAccount(account);

        fileIngestion = new FileIngestion();
        fileIngestion.setId(100L);
        fileIngestion.setOriginalFilename("import.csv");
        fileIngestion.setFileType(ImportFileType.CSV);
        fileIngestion.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        fileIngestion.setTransactionIngestion(transactionIngestion);

        fileIngestionDTO = new FileIngestionDTO();
        fileIngestionDTO.setId(100L);
        fileIngestionDTO.setOriginalFilename("import.csv");
        fileIngestionDTO.setFileType(ImportFileType.CSV);

        transactionIngestionDTO = new TransactionIngestionDTO();
        transactionIngestionDTO.setId(50L);
        fileIngestionDTO.setTransactionIngestion(transactionIngestionDTO);
    }

    @Test
    void saveShouldResolveAccessibleParentApplyCreatedAtAndRejectApiParent() {
        FileIngestion mappedEntity = new FileIngestion();
        mappedEntity.setOriginalFilename("import.csv");
        mappedEntity.setFileType(ImportFileType.CSV);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(fileIngestionMapper.toEntity(fileIngestionDTO)).thenReturn(mappedEntity);
        when(transactionIngestionRepository.findOneWithToOneRelationshipsByIdAndAccountUserLogin(50L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );
        when(fileIngestionRepository.existsByTransactionIngestionId(50L)).thenReturn(false);
        when(fileIngestionRepository.save(mappedEntity)).thenReturn(fileIngestion);
        when(fileIngestionMapper.toDto(fileIngestion)).thenReturn(fileIngestionDTO);

        fileIngestionService.save(fileIngestionDTO);

        ArgumentCaptor<FileIngestion> captor = ArgumentCaptor.forClass(FileIngestion.class);
        verify(fileIngestionRepository).save(captor.capture());
        FileIngestion saved = captor.getValue();
        assertThat(saved.getTransactionIngestion()).isEqualTo(transactionIngestion);
        assertThat(saved.getCreatedAt()).isNotNull();

        TransactionIngestionDTO apiParentDTO = new TransactionIngestionDTO();
        apiParentDTO.setId(51L);
        fileIngestionDTO.setTransactionIngestion(apiParentDTO);
        when(transactionIngestionRepository.findOneWithToOneRelationshipsByIdAndAccountUserLogin(51L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(apiTransactionIngestion)
        );

        assertThatThrownBy(() -> fileIngestionService.save(fileIngestionDTO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveShouldRejectDuplicateParent() {
        FileIngestion mappedEntity = new FileIngestion();

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(fileIngestionMapper.toEntity(fileIngestionDTO)).thenReturn(mappedEntity);
        when(transactionIngestionRepository.findOneWithToOneRelationshipsByIdAndAccountUserLogin(50L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );
        when(fileIngestionRepository.existsByTransactionIngestionId(50L)).thenReturn(true);

        assertThatThrownBy(() -> fileIngestionService.save(fileIngestionDTO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateShouldRejectTransactionIngestionChange() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(
            fileIngestionRepository.findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(100L, CURRENT_USER_LOGIN)
        ).thenReturn(Optional.of(fileIngestion));

        TransactionIngestionDTO otherParentDTO = new TransactionIngestionDTO();
        otherParentDTO.setId(99L);
        fileIngestionDTO.setTransactionIngestion(otherParentDTO);

        assertThatThrownBy(() -> fileIngestionService.update(fileIngestionDTO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partialUpdateShouldRejectNullTransactionIngestion() throws Exception {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(
            fileIngestionRepository.findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(100L, CURRENT_USER_LOGIN)
        ).thenReturn(Optional.of(fileIngestion));

        ObjectNode patchNode = new ObjectMapper().createObjectNode();
        patchNode.putNull("transactionIngestion");

        assertThatThrownBy(() -> fileIngestionService.partialUpdate(fileIngestionDTO, patchNode)).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    void saveShouldNormalizeStringsAndValidateStatementRange() {
        FileIngestion mappedEntity = new FileIngestion();
        mappedEntity.setOriginalFilename("  Import.CSV  ");
        mappedEntity.setFileType(ImportFileType.CSV);
        mappedEntity.setContentType("   ");
        mappedEntity.setChecksum("ABCDEF1234");
        mappedEntity.setStorageKey("  storage/key  ");
        mappedEntity.setParserName("  parser  ");
        mappedEntity.setParserVersion("  v1  ");
        mappedEntity.setStatementStartDate(LocalDate.parse("2026-02-01"));
        mappedEntity.setStatementEndDate(LocalDate.parse("2026-01-01"));

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(fileIngestionMapper.toEntity(fileIngestionDTO)).thenReturn(mappedEntity);
        when(transactionIngestionRepository.findOneWithToOneRelationshipsByIdAndAccountUserLogin(50L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionIngestion)
        );
        when(fileIngestionRepository.existsByTransactionIngestionId(50L)).thenReturn(false);

        assertThatThrownBy(() -> fileIngestionService.save(fileIngestionDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Statement start date cannot be after statement end date");

        mappedEntity.setStatementEndDate(LocalDate.parse("2026-02-28"));
        when(fileIngestionRepository.save(mappedEntity)).thenReturn(fileIngestion);
        when(fileIngestionMapper.toDto(fileIngestion)).thenReturn(fileIngestionDTO);

        fileIngestionService.save(fileIngestionDTO);

        assertThat(mappedEntity.getOriginalFilename()).isEqualTo("Import.CSV");
        assertThat(mappedEntity.getContentType()).isNull();
        assertThat(mappedEntity.getChecksum()).isEqualTo("abcdef1234");
        assertThat(mappedEntity.getStorageKey()).isEqualTo("storage/key");
        assertThat(mappedEntity.getParserName()).isEqualTo("parser");
        assertThat(mappedEntity.getParserVersion()).isEqualTo("v1");
    }

    @Test
    void updateShouldRejectImmutableOriginalFilenameChange() {
        fileIngestionDTO.setOriginalFilename("other.csv");
        fileIngestionDTO.setCreatedAt(fileIngestion.getCreatedAt());

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(
            fileIngestionRepository.findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(100L, CURRENT_USER_LOGIN)
        ).thenReturn(Optional.of(fileIngestion));

        assertThatThrownBy(() -> fileIngestionService.update(fileIngestionDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Original filename cannot be changed");
    }

    @Test
    void deleteShouldRejectAccessibleFileIngestion() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(
            fileIngestionRepository.findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(100L, CURRENT_USER_LOGIN)
        ).thenReturn(Optional.of(fileIngestion));

        assertThatThrownBy(() -> fileIngestionService.delete(100L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("File ingestion cannot be deleted directly");

        verify(fileIngestionRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldReturnFalseWhenNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(
            fileIngestionRepository.findOneWithRelationshipsByIdAndTransactionIngestionAccountUserLogin(100L, CURRENT_USER_LOGIN)
        ).thenReturn(Optional.empty());

        assertThat(fileIngestionService.delete(100L)).isFalse();
        verify(fileIngestionRepository, never()).deleteById(any());
    }

    @Test
    void findAllShouldUseScopedRepositoryForNormalUser() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(fileIngestionRepository.findAllWithRelationshipsByTransactionIngestionAccountUserLogin(CURRENT_USER_LOGIN)).thenReturn(
            List.of(fileIngestion)
        );
        when(fileIngestionMapper.toDto(fileIngestion)).thenReturn(fileIngestionDTO);

        assertThat(fileIngestionService.findAll()).hasSize(1);
    }
}
