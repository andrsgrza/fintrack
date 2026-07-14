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
import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionRuleConditionRepository;
import com.fintrack.app.repository.TransactionRuleRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.mapper.TransactionRuleMapper;
import java.time.Instant;
import java.util.List;
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

    @Mock
    private TransactionRuleConditionRepository transactionRuleConditionRepository;

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
        transactionRule.setPriority(0);
        transactionRule.setConditionLogic(RuleConditionLogic.ALL);
        transactionRule.setResultingDescription("Amazon");
        transactionRule.setActive(false);
        transactionRule.setCreatedAt(Instant.parse("2026-07-11T00:00:00Z"));
        transactionRule.setUpdatedAt(Instant.parse("2026-07-11T00:00:00Z"));
        transactionRule.setUser(currentUser);

        transactionRuleDTO = new TransactionRuleDTO();
        transactionRuleDTO.setId(10L);
        transactionRuleDTO.setName("Amazon rule");
        transactionRuleDTO.setPriority(0);
        transactionRuleDTO.setConditionLogic(RuleConditionLogic.ALL);
        transactionRuleDTO.setResultingDescription("Amazon");
        transactionRuleDTO.setActive(false);
        transactionRuleDTO.setCreatedAt(Instant.parse("2026-07-11T00:00:00Z"));
        transactionRuleDTO.setUpdatedAt(Instant.parse("2026-07-11T00:00:00Z"));
    }

    @Test
    void saveShouldAssignCurrentUser() {
        TransactionRule mappedEntity = validMappedRule(null);
        TransactionRule savedEntity = new TransactionRule();
        savedEntity.setId(10L);
        savedEntity.setUser(currentUser);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(transactionRuleMapper.toEntity(transactionRuleDTO)).thenReturn(mappedEntity);
        when(transactionRuleRepository.findMaxPriorityByUserId(currentUser.getId())).thenReturn(null);
        when(transactionRuleRepository.existsByUserLoginAndNormalizedName(CURRENT_USER_LOGIN, "amazon rule", null)).thenReturn(false);
        when(transactionRuleRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(transactionRuleMapper.toDto(savedEntity)).thenReturn(transactionRuleDTO);

        transactionRuleService.save(transactionRuleDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
        assertThat(mappedEntity.getPriority()).isZero();
        verify(transactionRuleRepository).save(mappedEntity);
    }

    @Test
    void saveShouldAppendPriorityForCurrentUser() {
        TransactionRule mappedEntity = validMappedRule(null);
        mappedEntity.setPriority(99);
        TransactionRule savedEntity = validMappedRule(10L);
        savedEntity.setUser(currentUser);
        savedEntity.setPriority(3);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(transactionRuleMapper.toEntity(transactionRuleDTO)).thenReturn(mappedEntity);
        when(transactionRuleRepository.findMaxPriorityByUserId(currentUser.getId())).thenReturn(2);
        when(transactionRuleRepository.existsByUserLoginAndNormalizedName(CURRENT_USER_LOGIN, "amazon rule", null)).thenReturn(false);
        when(transactionRuleRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(transactionRuleMapper.toDto(savedEntity)).thenReturn(transactionRuleDTO);

        transactionRuleService.save(transactionRuleDTO);

        assertThat(mappedEntity.getPriority()).isEqualTo(3);
    }

    @Test
    void saveShouldUseIndependentPrioritySequencePerUser() {
        User otherUser = new User();
        otherUser.setId(99L);
        otherUser.setLogin(OTHER_USER_LOGIN);
        TransactionRule mappedEntity = validMappedRule(null);
        TransactionRule savedEntity = validMappedRule(10L);
        savedEntity.setUser(otherUser);

        when(currentUserService.getCurrentUser()).thenReturn(otherUser);
        when(transactionRuleMapper.toEntity(transactionRuleDTO)).thenReturn(mappedEntity);
        when(transactionRuleRepository.findMaxPriorityByUserId(otherUser.getId())).thenReturn(null);
        when(transactionRuleRepository.existsByUserLoginAndNormalizedName(OTHER_USER_LOGIN, "amazon rule", null)).thenReturn(false);
        when(transactionRuleRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(transactionRuleMapper.toDto(savedEntity)).thenReturn(transactionRuleDTO);

        transactionRuleService.save(transactionRuleDTO);

        assertThat(mappedEntity.getPriority()).isZero();
        verify(transactionRuleRepository).findMaxPriorityByUserId(otherUser.getId());
        verify(transactionRuleRepository, never()).findMaxPriorityByUserId(currentUser.getId());
    }

    @Test
    void updateShouldPreserveExistingOwner() {
        TransactionRule mappedEntity = validMappedRule(10L);
        mappedEntity.setPriority(null);
        transactionRuleDTO.setPriority(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRule)
        );
        when(transactionRuleMapper.toEntity(transactionRuleDTO)).thenReturn(mappedEntity);
        when(transactionRuleRepository.existsByUserLoginAndNormalizedName(CURRENT_USER_LOGIN, "amazon rule", 10L)).thenReturn(false);
        when(transactionRuleRepository.save(mappedEntity)).thenReturn(transactionRule);
        when(transactionRuleMapper.toDto(transactionRule)).thenReturn(transactionRuleDTO);

        transactionRuleService.update(transactionRuleDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
        assertThat(mappedEntity.getPriority()).isEqualTo(transactionRule.getPriority());
    }

    @Test
    void updateShouldAllowSamePriorityAsNoOp() {
        TransactionRule mappedEntity = validMappedRule(10L);
        transactionRuleDTO.setPriority(transactionRule.getPriority());

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRule)
        );
        when(transactionRuleMapper.toEntity(transactionRuleDTO)).thenReturn(mappedEntity);
        when(transactionRuleRepository.existsByUserLoginAndNormalizedName(CURRENT_USER_LOGIN, "amazon rule", 10L)).thenReturn(false);
        when(transactionRuleRepository.save(mappedEntity)).thenReturn(transactionRule);
        when(transactionRuleMapper.toDto(transactionRule)).thenReturn(transactionRuleDTO);

        transactionRuleService.update(transactionRuleDTO);

        assertThat(mappedEntity.getPriority()).isEqualTo(transactionRule.getPriority());
    }

    @Test
    void updateShouldRejectChangedPriority() {
        transactionRuleDTO.setPriority(99);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRule)
        );

        assertThatThrownBy(() -> transactionRuleService.update(transactionRuleDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(transactionRuleRepository, never()).save(any());
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
    void updateShouldRejectChangedUpdatedAt() {
        transactionRuleDTO.setUpdatedAt(Instant.parse("2026-07-12T00:00:00Z"));

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRule)
        );

        assertThatThrownBy(() -> transactionRuleService.update(transactionRuleDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(transactionRuleRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectNullUpdatedAt() {
        transactionRuleDTO.setUpdatedAt(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRule)
        );

        assertThatThrownBy(() -> transactionRuleService.update(transactionRuleDTO)).isInstanceOf(IllegalArgumentException.class);
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
        when(transactionRuleRepository.findByUserIdOrderByPriorityAscIdAsc(currentUser.getId())).thenReturn(List.of());

        assertThat(transactionRuleService.delete(10L)).isTrue();
        verify(transactionRuleConditionRepository).deleteByTransactionRuleId(10L);
        verify(transactionRuleRepository).deleteResultingTagsByRuleId(10L);
        verify(transactionRuleRepository).deleteById(10L);
        verify(transactionRuleRepository).flush();
        verify(transactionRuleRepository).findByUserIdOrderByPriorityAscIdAsc(currentUser.getId());
    }

    @Test
    void deleteShouldReindexRemainingRulesForOwner() {
        TransactionRule firstRemainingRule = validMappedRule(11L);
        firstRemainingRule.setPriority(0);
        TransactionRule secondRemainingRule = validMappedRule(12L);
        secondRemainingRule.setPriority(2);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(transactionRule)
        );
        when(transactionRuleRepository.findByUserIdOrderByPriorityAscIdAsc(currentUser.getId())).thenReturn(
            List.of(firstRemainingRule, secondRemainingRule)
        );

        assertThat(transactionRuleService.delete(10L)).isTrue();

        assertThat(firstRemainingRule.getPriority()).isZero();
        assertThat(secondRemainingRule.getPriority()).isEqualTo(1);
        verify(transactionRuleRepository).saveAll(List.of(firstRemainingRule, secondRemainingRule));
    }

    @Test
    void reorderShouldUpdatePrioritiesInRequestedOrder() {
        TransactionRule firstRule = validMappedRule(11L);
        firstRule.setPriority(0);
        TransactionRule secondRule = validMappedRule(12L);
        secondRule.setPriority(1);
        TransactionRule thirdRule = validMappedRule(13L);
        thirdRule.setPriority(2);

        TransactionRuleDTO firstDTO = new TransactionRuleDTO();
        firstDTO.setId(11L);
        TransactionRuleDTO secondDTO = new TransactionRuleDTO();
        secondDTO.setId(12L);
        TransactionRuleDTO thirdDTO = new TransactionRuleDTO();
        thirdDTO.setId(13L);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(transactionRuleRepository.findByUserIdOrderByPriorityAscIdAsc(currentUser.getId())).thenReturn(
            List.of(firstRule, secondRule, thirdRule)
        );
        when(transactionRuleMapper.toDto(List.of(thirdRule, firstRule, secondRule))).thenReturn(List.of(thirdDTO, firstDTO, secondDTO));

        List<TransactionRuleDTO> result = transactionRuleService.reorder(List.of(13L, 11L, 12L));

        assertThat(thirdRule.getPriority()).isZero();
        assertThat(firstRule.getPriority()).isEqualTo(1);
        assertThat(secondRule.getPriority()).isEqualTo(2);
        assertThat(result).containsExactly(thirdDTO, firstDTO, secondDTO);
        verify(transactionRuleRepository).saveAll(List.of(thirdRule, firstRule, secondRule));
    }

    @Test
    void reorderShouldRejectNullOrderedIds() {
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(transactionRuleRepository.findByUserIdOrderByPriorityAscIdAsc(currentUser.getId())).thenReturn(List.of(transactionRule));

        assertThatThrownBy(() -> transactionRuleService.reorder(null)).isInstanceOf(IllegalArgumentException.class);
        verify(transactionRuleRepository, never()).saveAll(any());
    }

    @Test
    void reorderShouldRejectEmptyOrderedIdsWhenUserHasRules() {
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(transactionRuleRepository.findByUserIdOrderByPriorityAscIdAsc(currentUser.getId())).thenReturn(List.of(transactionRule));

        assertThatThrownBy(() -> transactionRuleService.reorder(List.of())).isInstanceOf(IllegalArgumentException.class);
        verify(transactionRuleRepository, never()).saveAll(any());
    }

    @Test
    void reorderShouldRejectDuplicateIds() {
        TransactionRule secondRule = validMappedRule(12L);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(transactionRuleRepository.findByUserIdOrderByPriorityAscIdAsc(currentUser.getId())).thenReturn(
            List.of(transactionRule, secondRule)
        );

        assertThatThrownBy(() -> transactionRuleService.reorder(List.of(10L, 10L))).isInstanceOf(IllegalArgumentException.class);
        verify(transactionRuleRepository, never()).saveAll(any());
    }

    @Test
    void reorderShouldRejectMissingExistingRule() {
        TransactionRule secondRule = validMappedRule(12L);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(transactionRuleRepository.findByUserIdOrderByPriorityAscIdAsc(currentUser.getId())).thenReturn(
            List.of(transactionRule, secondRule)
        );

        assertThatThrownBy(() -> transactionRuleService.reorder(List.of(10L))).isInstanceOf(IllegalArgumentException.class);
        verify(transactionRuleRepository, never()).saveAll(any());
    }

    @Test
    void reorderShouldRejectUnknownOrForeignRuleId() {
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(transactionRuleRepository.findByUserIdOrderByPriorityAscIdAsc(currentUser.getId())).thenReturn(List.of(transactionRule));

        assertThatThrownBy(() -> transactionRuleService.reorder(List.of(10L, 999L))).isInstanceOf(IllegalArgumentException.class);
        verify(transactionRuleRepository, never()).saveAll(any());
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

        TransactionRule mappedEntity = validMappedRule(10L);
        Category category = new Category();
        category.setId(88L);

        when(currentUserService.isAdmin()).thenReturn(true);
        when(transactionRuleRepository.findOneWithEagerRelationships(10L)).thenReturn(Optional.of(transactionRule));
        when(transactionRuleMapper.toEntity(transactionRuleDTO)).thenReturn(mappedEntity);
        when(categoryRepository.findOneByIdAndUserLogin(88L, OTHER_USER_LOGIN)).thenReturn(Optional.of(category));
        when(transactionRuleRepository.existsByUserLoginAndNormalizedName(OTHER_USER_LOGIN, "amazon rule", 10L)).thenReturn(false);
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

    private TransactionRule validMappedRule(Long id) {
        TransactionRule mappedRule = new TransactionRule();
        mappedRule.setId(id);
        mappedRule.setName("Amazon rule");
        mappedRule.setPriority(0);
        mappedRule.setConditionLogic(RuleConditionLogic.ALL);
        mappedRule.setResultingDescription("Amazon");
        mappedRule.setActive(false);
        mappedRule.setCreatedAt(Instant.parse("2026-07-11T00:00:00Z"));
        mappedRule.setUpdatedAt(Instant.parse("2026-07-11T00:00:00Z"));
        return mappedRule;
    }
}
