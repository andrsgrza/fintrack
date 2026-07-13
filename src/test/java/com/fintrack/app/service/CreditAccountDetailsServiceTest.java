package com.fintrack.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.repository.CreditAccountDetailsRepository;
import com.fintrack.app.service.dto.CreditAccountDetailsDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.mapper.CreditAccountDetailsMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditAccountDetailsServiceTest {

    private static final String CURRENT_USER_LOGIN = "user";

    @Mock
    private CreditAccountDetailsRepository creditAccountDetailsRepository;

    @Mock
    private CreditAccountDetailsMapper creditAccountDetailsMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private FinancialAccountService financialAccountService;

    @InjectMocks
    private CreditAccountDetailsService creditAccountDetailsService;

    private User currentUser;
    private FinancialAccount creditCardAccount;
    private FinancialAccount anotherCreditCardAccount;
    private CreditAccountDetails creditAccountDetails;
    private CreditAccountDetailsDTO creditAccountDetailsDTO;
    private FinancialAccountDTO creditCardAccountDTO;
    private FinancialAccountDTO anotherCreditCardAccountDTO;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(2L);
        currentUser.setLogin(CURRENT_USER_LOGIN);

        creditCardAccount = new FinancialAccount();
        creditCardAccount.setId(10L);
        creditCardAccount.setAccountType(AccountType.CREDIT_CARD);
        creditCardAccount.setUser(currentUser);

        anotherCreditCardAccount = new FinancialAccount();
        anotherCreditCardAccount.setId(11L);
        anotherCreditCardAccount.setAccountType(AccountType.CREDIT_CARD);
        anotherCreditCardAccount.setUser(currentUser);

        creditAccountDetails = new CreditAccountDetails();
        creditAccountDetails.setId(100L);
        creditAccountDetails.setCreditLimit(BigDecimal.TEN);
        creditAccountDetails.setStatementDay(1);
        creditAccountDetails.setPaymentDueDay(15);
        creditAccountDetails.setCreatedAt(Instant.now());
        creditAccountDetails.setUpdatedAt(Instant.now());
        creditAccountDetails.setAccount(creditCardAccount);

        creditAccountDetailsDTO = new CreditAccountDetailsDTO();
        creditAccountDetailsDTO.setId(100L);
        creditAccountDetailsDTO.setCreditLimit(BigDecimal.TEN);
        creditAccountDetailsDTO.setStatementDay(1);
        creditAccountDetailsDTO.setPaymentDueDay(15);
        creditAccountDetailsDTO.setCreatedAt(Instant.now());
        creditAccountDetailsDTO.setUpdatedAt(Instant.now());

        creditCardAccountDTO = new FinancialAccountDTO();
        creditCardAccountDTO.setId(10L);
        anotherCreditCardAccountDTO = new FinancialAccountDTO();
        anotherCreditCardAccountDTO.setId(11L);
        creditAccountDetailsDTO.setAccount(creditCardAccountDTO);
    }

    @Test
    void saveShouldResolveAccessibleCreditCardAccount() {
        CreditAccountDetails mappedEntity = new CreditAccountDetails();

        when(creditAccountDetailsMapper.toEntity(creditAccountDetailsDTO)).thenReturn(mappedEntity);
        when(financialAccountService.findAccessibleAccountEntity(10L)).thenReturn(Optional.of(creditCardAccount));
        when(creditAccountDetailsRepository.existsByAccountId(10L)).thenReturn(false);
        when(creditAccountDetailsRepository.save(mappedEntity)).thenReturn(creditAccountDetails);
        when(creditAccountDetailsMapper.toDto(creditAccountDetails)).thenReturn(creditAccountDetailsDTO);

        creditAccountDetailsService.save(creditAccountDetailsDTO);

        assertThat(mappedEntity.getAccount()).isEqualTo(creditCardAccount);
        verify(creditAccountDetailsRepository).save(mappedEntity);
    }

    @Test
    void saveShouldRejectNonCreditCardAccount() {
        FinancialAccount debitAccount = new FinancialAccount();
        debitAccount.setId(10L);
        debitAccount.setAccountType(AccountType.DEBIT);
        CreditAccountDetails mappedEntity = new CreditAccountDetails();

        when(creditAccountDetailsMapper.toEntity(creditAccountDetailsDTO)).thenReturn(mappedEntity);
        when(financialAccountService.findAccessibleAccountEntity(10L)).thenReturn(Optional.of(debitAccount));

        assertThatThrownBy(() -> creditAccountDetailsService.save(creditAccountDetailsDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account must be a credit card account");

        verify(creditAccountDetailsRepository, never()).save(any());
    }

    @Test
    void saveShouldRejectDuplicateDetailsForAccount() {
        CreditAccountDetails mappedEntity = new CreditAccountDetails();

        when(creditAccountDetailsMapper.toEntity(creditAccountDetailsDTO)).thenReturn(mappedEntity);
        when(financialAccountService.findAccessibleAccountEntity(10L)).thenReturn(Optional.of(creditCardAccount));
        when(creditAccountDetailsRepository.existsByAccountId(10L)).thenReturn(true);

        assertThatThrownBy(() -> creditAccountDetailsService.save(creditAccountDetailsDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account already has credit account details");

        verify(creditAccountDetailsRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectAccountChange() {
        creditAccountDetailsDTO.setAccount(anotherCreditCardAccountDTO);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(creditAccountDetailsRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(creditAccountDetails)
        );

        assertThatThrownBy(() -> creditAccountDetailsService.update(creditAccountDetailsDTO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account cannot be changed");

        verify(creditAccountDetailsRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldPreserveAccountWhenFieldAbsent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.put("creditLimit", 99);

        creditAccountDetailsDTO.setCreditLimit(BigDecimal.valueOf(99));
        creditAccountDetailsDTO.setAccount(null);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(creditAccountDetailsRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(creditAccountDetails)
        );
        when(creditAccountDetailsRepository.save(creditAccountDetails)).thenReturn(creditAccountDetails);
        when(creditAccountDetailsMapper.toDto(creditAccountDetails)).thenReturn(creditAccountDetailsDTO);

        Optional<CreditAccountDetailsDTO> result = creditAccountDetailsService.partialUpdate(creditAccountDetailsDTO, patchNode);

        assertThat(result).isPresent();
        assertThat(creditAccountDetails.getAccount()).isEqualTo(creditCardAccount);
    }

    @Test
    void partialUpdateShouldRejectNullAccount() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.putNull("account");

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(creditAccountDetailsRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(creditAccountDetails)
        );

        assertThatThrownBy(() -> creditAccountDetailsService.partialUpdate(creditAccountDetailsDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account cannot be null");

        verify(creditAccountDetailsRepository, never()).save(any());
    }

    @Test
    void partialUpdateShouldRejectDifferentAccount() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode patchNode = objectMapper.createObjectNode();
        patchNode.put("id", 100L);
        patchNode.set("account", objectMapper.createObjectNode().put("id", 11L));
        creditAccountDetailsDTO.setAccount(anotherCreditCardAccountDTO);

        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(creditAccountDetailsRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(creditAccountDetails)
        );

        assertThatThrownBy(() -> creditAccountDetailsService.partialUpdate(creditAccountDetailsDTO, patchNode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Account cannot be changed");

        verify(creditAccountDetailsRepository, never()).save(any());
    }

    @Test
    void deleteShouldReturnFalseWhenDetailsAreNotAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(creditAccountDetailsRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.empty()
        );

        assertThat(creditAccountDetailsService.delete(100L)).isFalse();
        verify(creditAccountDetailsRepository, never()).deleteById(any());
    }

    @Test
    void deleteShouldRejectDirectDeleteWhenDetailsAreAccessible() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(creditAccountDetailsRepository.findOneWithEagerRelationshipsByIdAndAccountUserLogin(100L, CURRENT_USER_LOGIN)).thenReturn(
            Optional.of(creditAccountDetails)
        );

        assertThatThrownBy(() -> creditAccountDetailsService.delete(100L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Credit account details cannot be deleted directly");

        verify(creditAccountDetailsRepository, never()).deleteById(any());
    }

    @Test
    void findAllShouldUseScopedQueryForRegularUser() {
        when(currentUserService.isAdmin()).thenReturn(false);
        when(currentUserService.getCurrentUserLogin()).thenReturn(CURRENT_USER_LOGIN);
        when(creditAccountDetailsRepository.findAllWithEagerRelationshipsByAccountUserLogin(CURRENT_USER_LOGIN)).thenReturn(
            java.util.List.of(creditAccountDetails)
        );
        when(creditAccountDetailsMapper.toDto(creditAccountDetails)).thenReturn(creditAccountDetailsDTO);

        assertThat(creditAccountDetailsService.findAll()).hasSize(1);
        verify(creditAccountDetailsRepository).findAllWithEagerRelationshipsByAccountUserLogin(CURRENT_USER_LOGIN);
        verify(creditAccountDetailsRepository, never()).findAllWithEagerRelationships();
    }
}
