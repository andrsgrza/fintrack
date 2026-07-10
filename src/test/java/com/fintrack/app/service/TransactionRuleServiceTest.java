package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.User;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionRuleRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.mapper.TransactionRuleMapper;
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
class TransactionRuleServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";
    private static final String OTHER_USER_LOGIN = "other-user";

    @Mock
    private TransactionRuleRepository transactionRuleRepository;

    @Mock
    private TransactionRuleMapper transactionRuleMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private FinancialSubscriptionRepository financialSubscriptionRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TransactionRuleService transactionRuleService;

    private User currentUser;
    private TransactionRule transactionRule;
    private TransactionRuleDTO transactionRuleDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        transactionRule = new TransactionRule();
        transactionRule.setId(10L);
        transactionRule.setName("Amazon rule");
        transactionRule.setUser(currentUser);

        transactionRuleDTO = new TransactionRuleDTO();
        transactionRuleDTO.setId(10L);
        transactionRuleDTO.setName("Amazon rule");
    }

    @Test
    void saveShouldAssignCurrentUser() {
        TransactionRule mappedEntity = new TransactionRule();
        TransactionRule savedEntity = new TransactionRule();
        savedEntity.setId(10L);
        savedEntity.setUser(currentUser);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(transactionRuleMapper.toEntity(transactionRuleDTO)).thenReturn(mappedEntity);
        when(transactionRuleRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(transactionRuleMapper.toDto(savedEntity)).thenReturn(transactionRuleDTO);

        transactionRuleService.save(transactionRuleDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
        verify(transactionRuleRepository).save(mappedEntity);
    }

    @Test
    void updateShouldPreserveExistingOwner() {
        TransactionRule mappedEntity = new TransactionRule();
        mappedEntity.setId(10L);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRule)
        );
        when(transactionRuleMapper.toEntity(transactionRuleDTO)).thenReturn(mappedEntity);
        when(transactionRuleRepository.save(mappedEntity)).thenReturn(transactionRule);
        when(transactionRuleMapper.toDto(transactionRule)).thenReturn(transactionRuleDTO);

        transactionRuleService.update(transactionRuleDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
    }

    @Test
    void updateShouldFailWhenTransactionRuleIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionRuleService.update(transactionRuleDTO)).isInstanceOf(java.util.NoSuchElementException.class);
        verify(transactionRuleRepository, never()).save(any());
    }

    @Test
    void findOneShouldReturnEmptyForAnotherUsersTransactionRule() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(transactionRuleService.findOne(10L)).isEmpty();
    }

    @Test
    void findOneShouldUseAdminLookupWhenCurrentUserIsAdmin() {
        when(currentUserService.isAdmin()).thenReturn(true);
        when(transactionRuleRepository.findOneWithEagerRelationships(10L)).thenReturn(Optional.of(transactionRule));
        when(transactionRuleMapper.toDto(transactionRule)).thenReturn(transactionRuleDTO);

        Optional<TransactionRuleDTO> result = transactionRuleService.findOne(10L);

        assertThat(result).contains(transactionRuleDTO);
        verify(transactionRuleRepository).findOneWithEagerRelationships(10L);
        verify(transactionRuleRepository, never()).findOneWithEagerRelationshipsByIdAndUserLogin(any(), any());
    }

    @Test
    void deleteShouldReturnFalseWhenTransactionRuleIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(transactionRuleService.delete(10L)).isFalse();
        verify(transactionRuleRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldRemoveAccessibleTransactionRule() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRule)
        );

        assertThat(transactionRuleService.delete(10L)).isTrue();
        verify(transactionRuleRepository).deleteById(10L);
    }

    @Test
    void findAllWithEagerRelationshipsShouldScopeToCurrentUser() {
        Page<TransactionRule> page = new PageImpl<>(java.util.List.of(transactionRule));
        Pageable pageable = Pageable.unpaged();

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleRepository.findAllWithEagerRelationshipsByUserLogin(CURRENT_USER_LOGIN, pageable)).thenReturn(page);
        when(transactionRuleMapper.toDto(transactionRule)).thenReturn(transactionRuleDTO);

        Page<TransactionRuleDTO> result = transactionRuleService.findAllWithEagerRelationships(pageable);

        assertThat(result.getContent()).containsExactly(transactionRuleDTO);
        verify(transactionRuleRepository).findAllWithEagerRelationshipsByUserLogin(eq(CURRENT_USER_LOGIN), eq(pageable));
    }

    @Test
    void partialUpdateShouldReturnEmptyWhenTransactionRuleIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThat(transactionRuleService.partialUpdate(transactionRuleDTO)).isEmpty();
        verify(transactionRuleRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectInaccessibleCategory() {
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(88L);
        transactionRuleDTO.setResultingCategory(categoryDTO);

        TransactionRule mappedEntity = new TransactionRule();
        mappedEntity.setUser(currentUser);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(transactionRuleMapper.toEntity(transactionRuleDTO)).thenReturn(mappedEntity);
        when(categoryRepository.findOneByIdAndUserLogin(88L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionRuleService.save(transactionRuleDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(transactionRuleRepository, never()).save(any());
    }

    @Test
    void updateShouldResolveCategoryOwnedByRuleOwner() {
        User otherOwner = new User();
        otherOwner.setId(99L);
        otherOwner.setLogin(OTHER_USER_LOGIN);
        transactionRule.setUser(otherOwner);

        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(88L);
        transactionRuleDTO.setResultingCategory(categoryDTO);

        TransactionRule mappedEntity = new TransactionRule();
        mappedEntity.setId(10L);
        Category category = new Category();
        category.setId(88L);

        when(currentUserService.isAdmin()).thenReturn(true);
        when(transactionRuleRepository.findOneWithEagerRelationships(10L)).thenReturn(Optional.of(transactionRule));
        when(transactionRuleMapper.toEntity(transactionRuleDTO)).thenReturn(mappedEntity);
        when(categoryRepository.findOneByIdAndUserLogin(88L, OTHER_USER_LOGIN)).thenReturn(Optional.of(category));
        when(transactionRuleRepository.save(mappedEntity)).thenReturn(transactionRule);
        when(transactionRuleMapper.toDto(transactionRule)).thenReturn(transactionRuleDTO);

        transactionRuleService.update(transactionRuleDTO);

        assertThat(mappedEntity.getResultingCategory()).isEqualTo(category);
        verify(categoryRepository).findOneByIdAndUserLogin(88L, OTHER_USER_LOGIN);
    }

    @Test
    void updateShouldRejectSubscriptionNotOwnedByRuleOwner() {
        User otherOwner = new User();
        otherOwner.setId(99L);
        otherOwner.setLogin(OTHER_USER_LOGIN);
        transactionRule.setUser(otherOwner);

        FinancialSubscriptionDTO subscriptionDTO = new FinancialSubscriptionDTO();
        subscriptionDTO.setId(77L);
        transactionRuleDTO.setResultingFinancialSubscription(subscriptionDTO);

        TransactionRule mappedEntity = new TransactionRule();
        mappedEntity.setId(10L);

        when(currentUserService.isAdmin()).thenReturn(true);
        when(transactionRuleRepository.findOneWithEagerRelationships(10L)).thenReturn(Optional.of(transactionRule));
        when(transactionRuleMapper.toEntity(transactionRuleDTO)).thenReturn(mappedEntity);
        when(financialSubscriptionRepository.findOneByIdAndUserLogin(77L, OTHER_USER_LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionRuleService.update(transactionRuleDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(transactionRuleRepository, never()).save(any());
    }
}
