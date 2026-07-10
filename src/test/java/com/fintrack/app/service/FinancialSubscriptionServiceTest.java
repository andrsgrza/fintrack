package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.User;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialSubscriptionRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.mapper.FinancialSubscriptionMapper;
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
class FinancialSubscriptionServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private FinancialSubscriptionRepository financialSubscriptionRepository;

    @Mock
    private FinancialSubscriptionMapper financialSubscriptionMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private FinancialAccountService financialAccountService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private FinancialSubscriptionService financialSubscriptionService;

    private User currentUser;
    private FinancialSubscription financialSubscription;
    private FinancialSubscriptionDTO financialSubscriptionDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        financialSubscription = new FinancialSubscription();
        financialSubscription.setId(10L);
        financialSubscription.setName("Netflix");
        financialSubscription.setUser(currentUser);

        financialSubscriptionDTO = new FinancialSubscriptionDTO();
        financialSubscriptionDTO.setId(10L);
        financialSubscriptionDTO.setName("Netflix");
    }

    @Test
    void saveShouldAssignCurrentUser() {
        FinancialSubscription mappedEntity = new FinancialSubscription();
        FinancialSubscription savedEntity = new FinancialSubscription();
        savedEntity.setId(10L);
        savedEntity.setUser(currentUser);

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(financialSubscriptionMapper.toEntity(financialSubscriptionDTO)).thenReturn(mappedEntity);
        when(financialSubscriptionRepository.save(mappedEntity)).thenReturn(savedEntity);
        when(financialSubscriptionMapper.toDto(savedEntity)).thenReturn(financialSubscriptionDTO);

        financialSubscriptionService.save(financialSubscriptionDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
        verify(financialSubscriptionRepository).save(mappedEntity);
    }

    @Test
    void updateShouldPreserveExistingOwner() {
        FinancialSubscription mappedEntity = new FinancialSubscription();
        mappedEntity.setId(10L);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialSubscriptionRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialSubscription)
        );
        when(financialSubscriptionMapper.toEntity(financialSubscriptionDTO)).thenReturn(mappedEntity);
        when(financialSubscriptionRepository.save(mappedEntity)).thenReturn(financialSubscription);
        when(financialSubscriptionMapper.toDto(financialSubscription)).thenReturn(financialSubscriptionDTO);

        financialSubscriptionService.update(financialSubscriptionDTO);

        assertThat(mappedEntity.getUser()).isEqualTo(currentUser);
    }

    @Test
    void updateShouldFailWhenFinancialSubscriptionIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialSubscriptionRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> financialSubscriptionService.update(financialSubscriptionDTO)).isInstanceOf(
            java.util.NoSuchElementException.class
        );
        verify(financialSubscriptionRepository, never()).save(any());
    }

    @Test
    void findOneShouldReturnEmptyForAnotherUsersFinancialSubscription() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialSubscriptionRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThat(financialSubscriptionService.findOne(10L)).isEmpty();
    }

    @Test
    void findOneShouldUseAdminLookupWhenCurrentUserIsAdmin() {
        when(currentUserService.isAdmin()).thenReturn(true);
        when(financialSubscriptionRepository.findOneWithEagerRelationships(10L)).thenReturn(Optional.of(financialSubscription));
        when(financialSubscriptionMapper.toDto(financialSubscription)).thenReturn(financialSubscriptionDTO);

        Optional<FinancialSubscriptionDTO> result = financialSubscriptionService.findOne(10L);

        assertThat(result).contains(financialSubscriptionDTO);
        verify(financialSubscriptionRepository).findOneWithEagerRelationships(10L);
        verify(financialSubscriptionRepository, never()).findOneWithEagerRelationshipsByIdAndUserLogin(any(), any());
    }

    @Test
    void deleteShouldReturnFalseWhenFinancialSubscriptionIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialSubscriptionRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThat(financialSubscriptionService.delete(10L)).isFalse();
        verify(financialSubscriptionRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldRemoveAccessibleFinancialSubscription() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialSubscriptionRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialSubscription)
        );

        assertThat(financialSubscriptionService.delete(10L)).isTrue();
        verify(financialSubscriptionRepository).deleteById(10L);
    }

    @Test
    void findAllWithEagerRelationshipsShouldScopeToCurrentUser() {
        Page<FinancialSubscription> page = new PageImpl<>(java.util.List.of(financialSubscription));
        Pageable pageable = Pageable.unpaged();

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialSubscriptionRepository.findAllWithEagerRelationshipsByUserLogin(CURRENT_USER_LOGIN, pageable)).thenReturn(page);
        when(financialSubscriptionMapper.toDto(financialSubscription)).thenReturn(financialSubscriptionDTO);

        Page<FinancialSubscriptionDTO> result = financialSubscriptionService.findAllWithEagerRelationships(pageable);

        assertThat(result.getContent()).containsExactly(financialSubscriptionDTO);
        verify(financialSubscriptionRepository).findAllWithEagerRelationshipsByUserLogin(eq(CURRENT_USER_LOGIN), eq(pageable));
    }

    @Test
    void partialUpdateShouldReturnEmptyWhenFinancialSubscriptionIsNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialSubscriptionRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThat(financialSubscriptionService.partialUpdate(financialSubscriptionDTO)).isEmpty();
        verify(financialSubscriptionRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectInaccessibleAccount() {
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(99L);
        financialSubscriptionDTO.setAccount(accountDTO);

        FinancialSubscription mappedEntity = new FinancialSubscription();

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(financialSubscriptionMapper.toEntity(financialSubscriptionDTO)).thenReturn(mappedEntity);
        when(financialAccountService.findAccessibleAccountEntity(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> financialSubscriptionService.save(financialSubscriptionDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(financialSubscriptionRepository, never()).save(any());
    }

    @Test
    void updateShouldResolveAccessibleAccount() {
        FinancialAccountDTO accountDTO = new FinancialAccountDTO();
        accountDTO.setId(99L);
        financialSubscriptionDTO.setAccount(accountDTO);

        FinancialSubscription mappedEntity = new FinancialSubscription();
        mappedEntity.setId(10L);
        FinancialAccount account = new FinancialAccount();
        account.setId(99L);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(financialSubscriptionRepository.findOneWithEagerRelationshipsByIdAndUserLogin(10L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(financialSubscription)
        );
        when(financialSubscriptionMapper.toEntity(financialSubscriptionDTO)).thenReturn(mappedEntity);
        when(financialAccountService.findAccessibleAccountEntity(99L)).thenReturn(Optional.of(account));
        when(financialSubscriptionRepository.save(mappedEntity)).thenReturn(financialSubscription);
        when(financialSubscriptionMapper.toDto(financialSubscription)).thenReturn(financialSubscriptionDTO);

        financialSubscriptionService.update(financialSubscriptionDTO);

        assertThat(mappedEntity.getAccount()).isEqualTo(account);
    }

    @Test
    void saveShouldRejectInaccessibleCategory() {
        CategoryDTO categoryDTO = new CategoryDTO();
        categoryDTO.setId(88L);
        financialSubscriptionDTO.setCategory(categoryDTO);

        FinancialSubscription mappedEntity = new FinancialSubscription();

        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(financialSubscriptionMapper.toEntity(financialSubscriptionDTO)).thenReturn(mappedEntity);
        when(categoryRepository.findOneByIdAndUserLogin(88L, CURRENT_USER_LOGIN)).thenReturn(Optional.empty());
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(currentUserService.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> financialSubscriptionService.save(financialSubscriptionDTO)).isInstanceOf(IllegalArgumentException.class);
        verify(financialSubscriptionRepository, never()).save(any());
    }
}
