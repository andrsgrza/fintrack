package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.User;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.dto.BudgetDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.mapper.BudgetMapper;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
class BudgetServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private BudgetMapper budgetMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private FinancialAccountService financialAccountService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private BudgetService budgetService;

    private User currentUser;
    private Budget budget;
    private BudgetDTO budgetDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        budget = new Budget();
        budget.setId(10L);
        budget.setName("Monthly");
        budget.setUser(currentUser);

        budgetDTO = new BudgetDTO();
        budgetDTO.setId(10L);
        budgetDTO.setName("Monthly");
    }

    @Test
    void saveShouldAssignCurrentUser() {
        Budget mappedEntity = new Budget();
        Budget savedEntity = new Budget();
        savedEntity.setId(10L);
        savedEntity.setUser(currentUser);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(budgetMapper.toEntity(budgetDTO)).thenReturn(mappedEntity);
        when(budgetRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(budgetMapper.toDto(savedEntity)).thenReturn(budgetDTO);

        budgetService.save(budgetDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
        verify(budgetRepository).save(mappedEntity);
    }

    @Test
    void updateShouldPreserveExistingOwner() {
        Budget mappedEntity = new Budget();
        mappedEntity.setId(10L);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(budget));
        when(budgetMapper.toEntity(budgetDTO)).thenReturn(mappedEntity);
        when(budgetRepository.save(mappedEntity)).thenReturn(budget);
        when(budgetMapper.toDto(budget)).thenReturn(budgetDTO);

        budgetService.update(budgetDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
    }

    @Test
    void updateShouldFailWhenBudgetIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.update(budgetDTO)).isInstanceOf(java.util.NoSuchElementException.class);
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void findOneShouldReturnEmptyForAnotherUsersBudget() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(budgetService.findOne(10L)).isEmpty();
    }

    @Test
    void findOneShouldUseAdminLookupWhenCurrentUserIsAdmin() {
        when(currentUserService.isAdmin()).thenReturn(true);
        when(budgetRepository.findOneWithEagerRelationships(10L)).thenReturn(Optional.of(budget));
        when(budgetMapper.toDto(budget)).thenReturn(budgetDTO);

        Optional<BudgetDTO> result = budgetService.findOne(10L);

        assertThat(result).contains(budgetDTO);
        verify(budgetRepository).findOneWithEagerRelationships(10L);
        verify(budgetRepository, never()).findOneWithEagerRelationshipsByIdAndUserLogin(any(), any());
    }

    @Test
    void deleteShouldReturnFalseWhenBudgetIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(budgetService.delete(10L)).isFalse();
        verify(budgetRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldRemoveAccessibleBudget() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(budget));

        assertThat(budgetService.delete(10L)).isTrue();
        verify(budgetRepository).deleteById(10L);
    }

    @Test
    void findAllWithEagerRelationshipsShouldScopeToCurrentUser() {
        Page<Budget> page = new PageImpl<>(java.util.List.of(budget));
        Pageable pageable = Pageable.unpaged();

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(budgetRepository.findAllWithEagerRelationshipsByUserLogin(CURRENT_USER_LOGIN, pageable)).thenReturn(page);
        when(budgetMapper.toDto(budget)).thenReturn(budgetDTO);

        Page<BudgetDTO> result = budgetService.findAllWithEagerRelationships(pageable);

        assertThat(result.getContent()).containsExactly(budgetDTO);
        verify(budgetRepository).findAllWithEagerRelationshipsByUserLogin(eq(CURRENT_USER_LOGIN), eq(pageable));
    }

    @Test
    void partialUpdateShouldReturnEmptyWhenBudgetIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(budgetService.partialUpdate(budgetDTO)).isEmpty();
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectInaccessibleAccount() {
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(99L);
        Set<FinancialAccountDTO> accounts = new HashSet<>();
        accounts.add(accountDTO);
        budgetDTO.setAccounts(accounts);

        Budget mappedEntity = new Budget();

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(budgetMapper.toEntity(budgetDTO)).thenReturn(mappedEntity);
        when(financialAccountService.findAccessibleAccountEntity(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.save(budgetDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void updateShouldResolveAccessibleAccount() {
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(99L);
        Set<FinancialAccountDTO> accounts = new HashSet<>();
        accounts.add(accountDTO);
        budgetDTO.setAccounts(accounts);

        Budget mappedEntity = new Budget();
        mappedEntity.setId(10L);
        FinancialAccount account = new FinancialAccount();
        account.setId(99L);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(budget));
        when(budgetMapper.toEntity(budgetDTO)).thenReturn(mappedEntity);
        when(financialAccountService.findAccessibleAccountEntity(99L)).thenReturn(Optional.of(account));
        when(budgetRepository.save(mappedEntity)).thenReturn(budget);
        when(budgetMapper.toDto(budget)).thenReturn(budgetDTO);

        budgetService.update(budgetDTO);

        assertThat(mappedEntity.getAccounts()).containsExactly(account);
    }
}
