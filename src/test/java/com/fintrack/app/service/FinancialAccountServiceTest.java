package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.CreditAccountDetailsRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.mapper.FinancialAccountMapper;
import java.time.LocalDate;
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
class FinancialAccountServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private FinancialAccountMapper financialAccountMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private TransactionIngestionService transactionIngestionService;

    @Mock
    private FinancialTransactionService financialTransactionService;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private FinancialSubscriptionRepository financialSubscriptionRepository;

    @Mock
    private CreditAccountDetailsRepository creditAccountDetailsRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @InjectMocks
    private FinancialAccountService financialAccountService;

    private User currentUser;
    private User otherUser;
    private FinancialAccount financialAccount;
    private FinancialAccountDTO financialAccountDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        otherUser = new User();
        otherUser.setId(3L);
        otherUser.setLogin("other-user");

        financialAccount = new FinancialAccount();
        financialAccount.setId(10L);
        financialAccount.setName("Main account");
        financialAccount.setUser(currentUser);
        financialAccount.setCurrency(CurrencyCode.MXN);
        financialAccount.setAccountType(AccountType.DEBIT);

        financialAccountDTO = new FinancialAccountDTO();
        financialAccountDTO.setId(10L);
        financialAccountDTO.setName("Main account");
        financialAccountDTO.setCurrency(CurrencyCode.MXN);
        financialAccountDTO.setAccountType(AccountType.DEBIT);
    }

    @Test
    void saveShouldAssignCurrentUser() {
        FinancialAccount mappedEntity = new FinancialAccount();
        FinancialAccount savedEntity = new FinancialAccount();
        savedEntity.setId(10L);
        savedEntity.setUser(currentUser);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(financialAccountMapper.toEntity(financialAccountDTO)).thenReturn(mappedEntity);
        when(financialAccountRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(financialAccountMapper.toDto(savedEntity)).thenReturn(financialAccountDTO);

        financialAccountService.save(financialAccountDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
        verify(financialAccountRepository).save(mappedEntity);
    }

    @Test
    void updateShouldPreserveExistingOwner() {
        FinancialAccount mappedEntity = new FinancialAccount();
        mappedEntity.setId(10L);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialAccount)
        );
        when(financialAccountMapper.toEntity(financialAccountDTO)).thenReturn(mappedEntity);
        when(financialAccountRepository.save(mappedEntity)).thenReturn(financialAccount);
        when(financialAccountMapper.toDto(financialAccount)).thenReturn(financialAccountDTO);

        financialAccountService.update(financialAccountDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
    }

    @Test
    void updateShouldFailWhenAccountIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> financialAccountService.update(financialAccountDTO)).isInstanceOf(java.util.NoSuchElementException.class);
        verify(financialAccountRepository, never()).save(any());
    }

    @Test
    void findOneShouldReturnEmptyForAnotherUsersAccount() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThat(financialAccountService.findOne(10L)).isEmpty();
    }

    @Test
    void findOneShouldUseAdminLookupWhenCurrentUserIsAdmin() {
        when(currentUserService.isAdmin()).thenReturn(true);
        when(financialAccountRepository.findOneWithEagerRelationships(10L)).thenReturn(Optional.of(financialAccount));
        when(financialAccountMapper.toDto(financialAccount)).thenReturn(financialAccountDTO);

        Optional<FinancialAccountDTO> result = financialAccountService.findOne(10L);

        assertThat(result).contains(financialAccountDTO);
        verify(financialAccountRepository).findOneWithEagerRelationships(10L);
        verify(financialAccountRepository, never()).findOneWithToOneRelationshipsByIdAndUserLogin(any(), any());
    }

    @Test
    void deleteShouldReturnFalseWhenAccountIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThat(financialAccountService.delete(10L)).isFalse();
        verify(financialAccountRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldRemoveAccessibleAccount() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialAccount)
        );

        assertThat(financialAccountService.delete(10L)).isTrue();
        verify(transactionIngestionService).deleteAllForAccount(financialAccount);
        verify(financialTransactionService).deleteAllForAccount(financialAccount);
        verify(budgetRepository).deleteAccountLinksByAccountId(10L);
        verify(financialSubscriptionRepository).clearAccountByAccountId(10L);
        verify(creditAccountDetailsRepository).deleteByAccountId(10L);
        verify(financialAccountRepository).deleteById(10L);
    }

    @Test
    void findAllWithEagerRelationshipsShouldScopeToCurrentUser() {
        Page<FinancialAccount> page = new PageImpl<>(java.util.List.of(financialAccount));
        Pageable pageable = Pageable.unpaged();

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findAllWithToOneRelationshipsByUserLogin(CURRENT_USER_LOGIN, pageable)).thenReturn(page);
        when(financialAccountMapper.toDto(financialAccount)).thenReturn(financialAccountDTO);

        Page<FinancialAccountDTO> result = financialAccountService.findAllWithEagerRelationships(pageable);

        assertThat(result.getContent()).containsExactly(financialAccountDTO);
        verify(financialAccountRepository).findAllWithToOneRelationshipsByUserLogin(eq(CURRENT_USER_LOGIN), eq(pageable));
    }

    @Test
    void partialUpdateShouldReturnEmptyWhenAccountIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThat(financialAccountService.partialUpdate(financialAccountDTO)).isEmpty();
        verify(financialAccountRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectCurrencyChange() {
        financialAccountDTO.setCurrency(CurrencyCode.USD);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialAccount)
        );

        assertThatThrownBy(() -> financialAccountService.update(financialAccountDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Currency cannot be changed");
        verify(financialAccountRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectAccountTypeChange() {
        financialAccountDTO.setAccountType(AccountType.CASH);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialAccount)
        );

        assertThatThrownBy(() -> financialAccountService.update(financialAccountDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account type cannot be changed");
        verify(financialAccountRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectInitialBalanceDateAfterEarliestTransactionDate() {
        financialAccount.setInitialBalanceDate(LocalDate.parse("2026-01-01"));
        financialAccountDTO.setInitialBalanceDate(LocalDate.parse("2026-02-01"));
        FinancialAccount mappedEntity = new FinancialAccount();
        mappedEntity.setId(10L);
        mappedEntity.setCurrency(CurrencyCode.MXN);
        mappedEntity.setAccountType(AccountType.DEBIT);
        mappedEntity.setInitialBalanceDate(LocalDate.parse("2026-02-01"));

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialAccount)
        );
        when(financialAccountMapper.toEntity(financialAccountDTO)).thenReturn(mappedEntity);
        when(financialTransactionRepository.findEarliestTransactionDateByAccountId(10L)).thenReturn(
            Optional.of(LocalDate.parse("2026-01-15"))
        );

        assertThatThrownBy(() -> financialAccountService.update(financialAccountDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Initial balance date cannot be after the earliest transaction date");
        verify(financialAccountRepository, never()).save(any());
    }
}
