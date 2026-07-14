package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.math.BigDecimal;
import java.time.Instant;
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
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant CHANGED_TIMESTAMP = Instant.parse("2026-02-01T00:00:00Z");
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("100.00");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        financialAccount.setInitialBalance(INITIAL_BALANCE);
        financialAccount.setCreatedAt(CREATED_AT);
        financialAccount.setUpdatedAt(UPDATED_AT);

        financialAccountDTO = new FinancialAccountDTO();
        financialAccountDTO.setId(10L);
        financialAccountDTO.setName("Main account");
        financialAccountDTO.setCurrency(CurrencyCode.MXN);
        financialAccountDTO.setAccountType(AccountType.DEBIT);
        financialAccountDTO.setInitialBalance(INITIAL_BALANCE);
        financialAccountDTO.setCreatedAt(CREATED_AT);
        financialAccountDTO.setUpdatedAt(UPDATED_AT);
    }

    @Test
    void saveShouldAssignCurrentUser() {
        FinancialAccount mappedEntity = new FinancialAccount();
        mappedEntity.setInitialBalance(INITIAL_BALANCE);
        FinancialAccount savedEntity = new FinancialAccount();
        savedEntity.setId(10L);
        savedEntity.setUser(currentUser);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(financialAccountMapper.toEntity(financialAccountDTO)).thenReturn(mappedEntity);
        when(financialAccountRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(financialAccountMapper.toDto(savedEntity)).thenReturn(financialAccountDTO);

        financialAccountService.save(financialAccountDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
        assertThat(mappedEntity.getCreatedAt()).isNotNull();
        assertThat(mappedEntity.getUpdatedAt()).isEqualTo(mappedEntity.getCreatedAt());
        verify(financialAccountRepository).save(mappedEntity);
    }

    @Test
    void saveShouldIgnoreClientProvidedTimestamps() {
        FinancialAccount mappedEntity = new FinancialAccount();
        mappedEntity.setInitialBalance(INITIAL_BALANCE);
        FinancialAccount savedEntity = new FinancialAccount();
        savedEntity.setId(10L);
        savedEntity.setUser(currentUser);

        financialAccountDTO.setCreatedAt(Instant.parse("2000-01-01T00:00:00Z"));
        financialAccountDTO.setUpdatedAt(Instant.parse("2000-01-02T00:00:00Z"));

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(financialAccountMapper.toEntity(financialAccountDTO)).thenReturn(mappedEntity);
        when(financialAccountRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(financialAccountMapper.toDto(savedEntity)).thenReturn(financialAccountDTO);

        financialAccountService.save(financialAccountDTO);

        assertThat(mappedEntity.getCreatedAt()).isNotEqualTo(Instant.parse("2000-01-01T00:00:00Z"));
        assertThat(mappedEntity.getUpdatedAt()).isEqualTo(mappedEntity.getCreatedAt());
        verify(financialAccountRepository).save(mappedEntity);
    }

    @Test
    void updateShouldPreserveExistingOwner() {
        FinancialAccount mappedEntity = new FinancialAccount();
        mappedEntity.setId(10L);
        mappedEntity.setInitialBalance(INITIAL_BALANCE);

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
        assertThat(mappedEntity.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(mappedEntity.getUpdatedAt()).isAfter(UPDATED_AT);
    }

    @Test
    void updateShouldRejectChangedCreatedAt() {
        financialAccountDTO.setCreatedAt(CHANGED_TIMESTAMP);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialAccount)
        );

        assertThatThrownBy(() -> financialAccountService.update(financialAccountDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Created at cannot be changed");
        verify(financialAccountRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectNullUpdatedAt() {
        financialAccountDTO.setUpdatedAt(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialAccount)
        );

        assertThatThrownBy(() -> financialAccountService.update(financialAccountDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Updated at cannot be changed");
        verify(financialAccountRepository, never()).save(any());
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
    void partialUpdateShouldRejectExplicitNullCreatedAt() {
        financialAccountDTO.setCreatedAt(null);
        ObjectNode patchNode = OBJECT_MAPPER.createObjectNode();
        patchNode.putNull("createdAt");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialAccount)
        );

        assertThatThrownBy(() -> financialAccountService.partialUpdate(financialAccountDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Created at cannot be changed");
        verify(financialAccountRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectChangedUpdatedAt() {
        financialAccountDTO.setUpdatedAt(CHANGED_TIMESTAMP);
        ObjectNode patchNode = OBJECT_MAPPER.createObjectNode();
        patchNode.put("updatedAt", CHANGED_TIMESTAMP.toString());

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialAccount)
        );

        assertThatThrownBy(() -> financialAccountService.partialUpdate(financialAccountDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Updated at cannot be changed");
        verify(financialAccountRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldAllowSameTimestampsAndSetUpdatedAt() {
        ObjectNode patchNode = OBJECT_MAPPER.createObjectNode();
        patchNode.put("createdAt", CREATED_AT.toString());
        patchNode.put("updatedAt", UPDATED_AT.toString());

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialAccount)
        );
        when(financialAccountRepository.save(financialAccount)).thenReturn(financialAccount);
        when(financialAccountMapper.toDto(financialAccount)).thenReturn(financialAccountDTO);

        financialAccountService.partialUpdate(financialAccountDTO, patchNode);

        assertThat(financialAccount.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(financialAccount.getUpdatedAt()).isAfter(UPDATED_AT);
        verify(financialAccountRepository).save(financialAccount);
    }

    @Test
    void saveShouldAcceptInitialBalanceWithScaleZeroOneOrTwo() {
        FinancialAccount mappedEntity = new FinancialAccount();
        FinancialAccount savedEntity = new FinancialAccount();

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(financialAccountMapper.toEntity(financialAccountDTO)).thenReturn(mappedEntity);
        when(financialAccountRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(financialAccountMapper.toDto(savedEntity)).thenReturn(financialAccountDTO);

        for (BigDecimal validBalance : java.util.List.of(new BigDecimal("100"), new BigDecimal("100.0"), new BigDecimal("100.00"))) {
            mappedEntity.setInitialBalance(validBalance);
            financialAccountService.save(financialAccountDTO);
        }

        verify(financialAccountRepository, org.mockito.Mockito.times(3)).save(mappedEntity);
    }

    @Test
    void saveShouldAcceptNegativeInitialBalanceWithScaleZeroOneOrTwo() {
        FinancialAccount mappedEntity = new FinancialAccount();
        FinancialAccount savedEntity = new FinancialAccount();

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(financialAccountMapper.toEntity(financialAccountDTO)).thenReturn(mappedEntity);
        when(financialAccountRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(financialAccountMapper.toDto(savedEntity)).thenReturn(financialAccountDTO);

        for (BigDecimal validBalance : java.util.List.of(new BigDecimal("-5000"), new BigDecimal("-5000.2"), new BigDecimal("-5000.25"))) {
            mappedEntity.setInitialBalance(validBalance);
            financialAccountService.save(financialAccountDTO);
        }

        verify(financialAccountRepository, org.mockito.Mockito.times(3)).save(mappedEntity);
    }

    @Test
    void saveShouldRejectInitialBalanceScaleGreaterThanTwoWithoutRounding() {
        FinancialAccount mappedEntity = new FinancialAccount();
        mappedEntity.setInitialBalance(new BigDecimal("100.001"));

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(financialAccountMapper.toEntity(financialAccountDTO)).thenReturn(mappedEntity);

        assertThatThrownBy(() -> financialAccountService.save(financialAccountDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Initial balance must have at most 2 decimal places");
        assertThat(mappedEntity.getInitialBalance()).isEqualByComparingTo(new BigDecimal("100.001"));
        verify(financialAccountRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectNullInitialBalance() {
        FinancialAccount mappedEntity = new FinancialAccount();
        mappedEntity.setInitialBalance(null);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(financialAccountMapper.toEntity(financialAccountDTO)).thenReturn(mappedEntity);

        assertThatThrownBy(() -> financialAccountService.save(financialAccountDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Initial balance is required");
        verify(financialAccountRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectInitialBalanceScaleGreaterThanTwo() {
        financialAccountDTO.setInitialBalance(new BigDecimal("-5000.123"));
        FinancialAccount mappedEntity = new FinancialAccount();
        mappedEntity.setId(10L);
        mappedEntity.setInitialBalance(new BigDecimal("-5000.123"));

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialAccount)
        );
        when(financialAccountMapper.toEntity(financialAccountDTO)).thenReturn(mappedEntity);

        assertThatThrownBy(() -> financialAccountService.update(financialAccountDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Initial balance must have at most 2 decimal places");
        verify(financialAccountRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectExplicitInitialBalanceScaleGreaterThanTwoWithoutMutating() {
        financialAccountDTO.setInitialBalance(new BigDecimal("0.999"));
        ObjectNode patchNode = OBJECT_MAPPER.createObjectNode();
        patchNode.put("initialBalance", new BigDecimal("0.999"));

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialAccount)
        );
        assertThatThrownBy(() -> financialAccountService.partialUpdate(financialAccountDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Initial balance must have at most 2 decimal places");
        assertThat(financialAccount.getInitialBalance()).isEqualByComparingTo(INITIAL_BALANCE);
        verify(financialAccountMapper, never()).partialUpdate(any(), any());
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
        mappedEntity.setInitialBalance(INITIAL_BALANCE);
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
