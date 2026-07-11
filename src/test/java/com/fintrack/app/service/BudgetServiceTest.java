package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.dto.BudgetDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.mapper.BudgetMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    private FinancialAccountRepository financialAccountRepository;

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
        budget.setAmount(new BigDecimal("100"));
        budget.setCurrency(CurrencyCode.MXN);
        budget.setStartDate(LocalDate.of(2026, 1, 1));

        budgetDTO = new BudgetDTO();
        budgetDTO.setId(10L);
        budgetDTO.setName("Monthly");
        budgetDTO.setAmount(new BigDecimal("100"));
        budgetDTO.setCurrency(CurrencyCode.MXN);
        budgetDTO.setStartDate(LocalDate.of(2026, 1, 1));
    }

    @Test
    void saveShouldAssignCurrentUser() {
        Budget mappedEntity = new Budget();
        mappedEntity.setAmount(new BigDecimal("100"));
        mappedEntity.setCurrency(CurrencyCode.MXN);
        mappedEntity.setStartDate(LocalDate.of(2026, 1, 1));
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
        mappedEntity.setAmount(new BigDecimal("100"));
        mappedEntity.setCurrency(CurrencyCode.MXN);
        mappedEntity.setStartDate(LocalDate.of(2026, 1, 1));

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
    void deleteShouldCleanupRelationshipsBeforeRemovingAccessibleBudget() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(budget));

        assertThat(budgetService.delete(10L)).isTrue();
        verify(budgetRepository).deleteAccountLinksByBudgetId(10L);
        verify(budgetRepository).deleteCategoryLinksByBudgetId(10L);
        verify(budgetRepository).deleteTagLinksByBudgetId(10L);
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
        mappedEntity.setAmount(new BigDecimal("100"));
        mappedEntity.setCurrency(CurrencyCode.MXN);
        mappedEntity.setStartDate(LocalDate.of(2026, 1, 1));

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(budgetMapper.toEntity(budgetDTO)).thenReturn(mappedEntity);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(99L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> budgetService.save(budgetDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void updateShouldResolveOwnerAccount() {
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(99L);
        Set<FinancialAccountDTO> accounts = new HashSet<>();
        accounts.add(accountDTO);
        budgetDTO.setAccounts(accounts);

        Budget mappedEntity = new Budget();
        mappedEntity.setId(10L);
        mappedEntity.setAmount(new BigDecimal("100"));
        mappedEntity.setCurrency(CurrencyCode.MXN);
        mappedEntity.setStartDate(LocalDate.of(2026, 1, 1));
        FinancialAccount account = new FinancialAccount();
        account.setId(99L);
        account.setCurrency(CurrencyCode.MXN);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(budget));
        when(budgetMapper.toEntity(budgetDTO)).thenReturn(mappedEntity);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(99L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(account)
        );
        when(budgetRepository.save(mappedEntity)).thenReturn(budget);
        when(budgetMapper.toDto(budget)).thenReturn(budgetDTO);

        budgetService.update(budgetDTO);

        assertThat(mappedEntity.getAccounts()).containsExactly(account);
    }

    @Test
    void partialUpdateWithPatchNodeAbsentPreservesLinks() throws Exception {
        FinancialAccount account = new FinancialAccount();
        account.setId(99L);
        account.setCurrency(CurrencyCode.MXN);
        budget.setAccounts(new HashSet<>(Set.of(account)));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode patchNode = objectMapper.readTree("{\"name\":\"Updated\"}");
        BudgetDTO patchDto = objectMapper.treeToValue(patchNode, BudgetDTO.class);
        patchDto.setId(10L);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(budget));
        when(budgetRepository.save(budget)).thenReturn(budget);
        when(budgetMapper.toDto(budget)).thenReturn(budgetDTO);

        budgetService.partialUpdate(patchDto, patchNode);

        assertThat(budget.getAccounts()).extracting(FinancialAccount::getId).containsExactly(99L);
        verify(financialAccountRepository, never()).findOneWithToOneRelationshipsByIdAndUserLogin(any(), any());
    }

    @Test
    void partialUpdateWithPatchNodeNullTagsClearsTags() throws Exception {
        Tag tag = new Tag();
        tag.setId(5L);
        budget.setTags(new HashSet<>(Set.of(tag)));

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode patchNode = objectMapper.readTree("{\"tags\":null}");
        BudgetDTO patchDto = objectMapper.treeToValue(patchNode, BudgetDTO.class);
        patchDto.setId(10L);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(budget));
        when(budgetRepository.save(budget)).thenReturn(budget);
        when(budgetMapper.toDto(budget)).thenReturn(budgetDTO);

        budgetService.partialUpdate(patchDto, patchNode);

        assertThat(budget.getTags()).isEmpty();
    }

    @Test
    void partialUpdateWithPatchNodeReplacesAccounts() throws Exception {
        FinancialAccount account = new FinancialAccount();
        account.setId(99L);
        account.setCurrency(CurrencyCode.MXN);
        budget.setCurrency(CurrencyCode.MXN);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode patchNode = objectMapper.readTree("{\"accounts\":[{\"id\":99}]}");
        BudgetDTO patchDto = objectMapper.treeToValue(patchNode, BudgetDTO.class);
        patchDto.setId(10L);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(budgetRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.of(budget));
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(99L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(account)
        );
        when(budgetRepository.save(budget)).thenReturn(budget);
        when(budgetMapper.toDto(budget)).thenReturn(budgetDTO);

        budgetService.partialUpdate(patchDto, patchNode);

        assertThat(budget.getAccounts()).containsExactly(account);
    }

    @Test
    void saveShouldRejectZeroAmount() {
        budgetDTO.setAmount(BigDecimal.ZERO);
        Budget mappedEntity = new Budget();
        mappedEntity.setAmount(BigDecimal.ZERO);
        mappedEntity.setCurrency(CurrencyCode.MXN);
        mappedEntity.setStartDate(LocalDate.of(2026, 1, 1));

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(budgetMapper.toEntity(budgetDTO)).thenReturn(mappedEntity);

        assertThatThrownBy(() -> budgetService.save(budgetDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectAccountCurrencyMismatch() {
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(99L);
        budgetDTO.setAccounts(new HashSet<>(Set.of(accountDTO)));

        Budget mappedEntity = new Budget();
        mappedEntity.setAmount(new BigDecimal("100"));
        mappedEntity.setCurrency(CurrencyCode.MXN);
        mappedEntity.setStartDate(LocalDate.of(2026, 1, 1));
        FinancialAccount account = new FinancialAccount();
        account.setId(99L);
        account.setCurrency(CurrencyCode.USD);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(budgetMapper.toEntity(budgetDTO)).thenReturn(mappedEntity);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(99L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(account)
        );

        assertThatThrownBy(() -> budgetService.save(budgetDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectEndDateBeforeStartDate() {
        Budget mappedEntity = new Budget();
        mappedEntity.setAmount(new BigDecimal("100"));
        mappedEntity.setCurrency(CurrencyCode.MXN);
        mappedEntity.setStartDate(LocalDate.of(2026, 6, 1));
        mappedEntity.setEndDate(LocalDate.of(2026, 1, 1));

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(budgetMapper.toEntity(budgetDTO)).thenReturn(mappedEntity);

        assertThatThrownBy(() -> budgetService.save(budgetDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(budgetRepository, never()).save(any());
    }
}
