package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.mapper.FinancialTransactionMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class FinancialTransactionServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private FinancialTransactionMapper financialTransactionMapper;

    @Mock
    private FinancialAccountService financialAccountService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private FinancialSubscriptionRepository financialSubscriptionRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private FinancialTransactionService financialTransactionService;

    private User currentUser;
    private FinancialAccount financialAccount;
    private FinancialTransaction financialTransaction;
    private FinancialTransactionDTO financialTransactionDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        financialAccount = new FinancialAccount();
        financialAccount.setId(20L);
        financialAccount.setUser(currentUser);

        financialTransaction = new FinancialTransaction();
        financialTransaction.setId(30L);
        financialTransaction.setAmount(new BigDecimal("10.00"));
        financialTransaction.setOrigin(TransactionOrigin.MANUAL);
        financialTransaction.setAccount(financialAccount);

        financialTransactionDTO = new FinancialTransactionDTO();
        financialTransactionDTO.setId(30L);
        financialTransactionDTO.setAmount(new BigDecimal("10.00"));
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(20L);
        financialTransactionDTO.setAccount(accountDTO);
    }

    @Test
    void saveShouldAssignManualOriginAndResolveAccessibleAccount() {
        FinancialTransaction mappedEntity = new FinancialTransaction();
        FinancialTransaction savedEntity = new FinancialTransaction();
        savedEntity.setId(30L);
        savedEntity.setOrigin(TransactionOrigin.MANUAL);
        savedEntity.setAccount(financialAccount);

        when(financialTransactionMapper.toEntity(financialTransactionDTO)).thenReturn(mappedEntity);
        when(financialAccountService.findAccessibleAccountEntity(20L)).thenReturn(Optional.of(financialAccount));
        when(financialTransactionRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(financialTransactionMapper.toDto(savedEntity)).thenReturn(financialTransactionDTO);

        financialTransactionService.save(financialTransactionDTO);

        assertThat(mappedEntity.getAccount()).isEqualTo(financialAccount);
        assertThat(mappedEntity.getOrigin()).isEqualTo(TransactionOrigin.MANUAL);
        assertThat(mappedEntity.getTransactionIngestion()).isNull();
        verify(financialTransactionRepository).save(mappedEntity);
    }

    @Test
    void saveShouldRejectNonPositiveAmount() {
        financialTransactionDTO.setAmount(BigDecimal.ZERO);

        assertThatThrownBy(() -> financialTransactionService.save(financialTransactionDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    void saveShouldFailWhenAccountIsNotAccessible() {
        when(financialTransactionMapper.toEntity(financialTransactionDTO)).thenReturn(new FinancialTransaction());
        when(financialAccountService.findAccessibleAccountEntity(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> financialTransactionService.save(financialTransactionDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    void updateShouldPreserveOriginAndIngestion() {
        FinancialTransaction existing = new FinancialTransaction();
        existing.setId(30L);
        existing.setOrigin(TransactionOrigin.FILE_IMPORT);
        existing.setAccount(financialAccount);

        FinancialTransaction mappedEntity = new FinancialTransaction();
        mappedEntity.setId(30L);
        mappedEntity.setOrigin(TransactionOrigin.API);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialTransactionRepository.findOneAccessibleByIdAndAccountUserLogin(30L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(existing)
        );
        when(financialTransactionMapper.toEntity(financialTransactionDTO)).thenReturn(mappedEntity);
        when(financialAccountService.findAccessibleAccountEntity(20L)).thenReturn(Optional.of(financialAccount));
        when(financialTransactionRepository.save(mappedEntity)).thenReturn(mappedEntity);
        when(financialTransactionMapper.toDto(mappedEntity)).thenReturn(financialTransactionDTO);

        financialTransactionService.update(financialTransactionDTO);

        assertThat(mappedEntity.getOrigin()).isEqualTo(TransactionOrigin.FILE_IMPORT);
    }

    @Test
    void updateShouldFailWhenTransactionIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialTransactionRepository.findOneAccessibleByIdAndAccountUserLogin(30L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> financialTransactionService.update(financialTransactionDTO)).isInstanceOf(
            java.util.NoSuchElementException.class
        );
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    void findOneShouldReturnEmptyForAnotherUsersTransaction() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialTransactionRepository.findOneAccessibleByIdAndAccountUserLogin(30L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(financialTransactionService.findOne(30L)).isEmpty();
    }

    @Test
    void deleteShouldReturnFalseWhenTransactionIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialTransactionRepository.findOneAccessibleByIdAndAccountUserLogin(30L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(financialTransactionService.delete(30L)).isFalse();
        verify(financialTransactionRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldRemoveAccessibleTransaction() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialTransactionRepository.findOneAccessibleByIdAndAccountUserLogin(30L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialTransaction)
        );

        assertThat(financialTransactionService.delete(30L)).isTrue();
        verify(financialTransactionRepository).deleteById(30L);
    }

    @Test
    void findAllWithEagerRelationshipsShouldScopeToCurrentUser() {
        Page<FinancialTransaction> page = new PageImpl<>(java.util.List.of(financialTransaction));
        Pageable pageable = Pageable.unpaged();

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(
            financialTransactionRepository.findAllAccessibleWithToOneRelationshipsByAccountUserLogin(CURRENT_USER_LOGIN, pageable)
        ).thenReturn(page);
        when(financialTransactionMapper.toDto(financialTransaction)).thenReturn(financialTransactionDTO);

        Page<FinancialTransactionDTO> result = financialTransactionService.findAllWithEagerRelationships(pageable);

        assertThat(result.getContent()).containsExactly(financialTransactionDTO);
        verify(financialTransactionRepository).findAllAccessibleWithToOneRelationshipsByAccountUserLogin(
            eq(CURRENT_USER_LOGIN),
            eq(pageable)
        );
    }

    @Test
    void partialUpdateShouldReturnEmptyWhenTransactionIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialTransactionRepository.findOneAccessibleByIdAndAccountUserLogin(30L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(financialTransactionService.partialUpdate(financialTransactionDTO)).isEmpty();
        verify(financialTransactionRepository, never()).save(any());
    }
}
