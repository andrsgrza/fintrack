package com.fintrack.app.service;

import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.service.dto.CreditAccountDetailsConfiguredDTO;
import com.fintrack.app.service.dto.CreditAccountDetailsConfiguredResponseDTO;
import com.fintrack.app.service.dto.CreditAccountDetailsDTO;
import com.fintrack.app.service.dto.FinancialAccountConfiguredAccountDTO;
import com.fintrack.app.service.dto.FinancialAccountConfiguredAccountResponseDTO;
import com.fintrack.app.service.dto.FinancialAccountConfiguredRequestDTO;
import com.fintrack.app.service.dto.FinancialAccountConfiguredResponseDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product workflow for atomically creating or updating a FinancialAccount with its contextual CREDIT_CARD details.
 * Generic FinancialAccount and CreditAccountDetails CRUD remains available for technical compatibility.
 */
@Service
@Transactional
public class FinancialAccountConfigurationService {

    private final FinancialAccountService financialAccountService;

    private final CreditAccountDetailsService creditAccountDetailsService;

    public FinancialAccountConfigurationService(
        FinancialAccountService financialAccountService,
        CreditAccountDetailsService creditAccountDetailsService
    ) {
        this.financialAccountService = financialAccountService;
        this.creditAccountDetailsService = creditAccountDetailsService;
    }

    public FinancialAccountConfiguredResponseDTO create(FinancialAccountConfiguredRequestDTO request) {
        validateRequest(request);

        // Preserve the established create semantics: newly created accounts are active.
        FinancialAccountDTO savedAccount = financialAccountService.save(toFinancialAccountDTO(request.getFinancialAccount(), true));
        CreditAccountDetailsDTO savedDetails = saveCreditAccountDetails(request.getCreditAccountDetails(), savedAccount, null);
        return toResponse(savedAccount, savedDetails);
    }

    public FinancialAccountConfiguredResponseDTO update(Long id, FinancialAccountConfiguredRequestDTO request) {
        validateRequest(request);

        FinancialAccountDTO existingAccount = financialAccountService
            .findOne(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (existingAccount.getAccountType() != request.getFinancialAccount().getAccountType()) {
            throw new IllegalArgumentException("Account type cannot be changed");
        }
        if (existingAccount.getCurrency() != request.getFinancialAccount().getCurrency()) {
            throw new IllegalArgumentException("Currency cannot be changed");
        }

        FinancialAccountDTO accountToUpdate = toFinancialAccountDTO(
            request.getFinancialAccount(),
            request.getFinancialAccount().getActive()
        );
        accountToUpdate.setId(existingAccount.getId());
        accountToUpdate.setCreatedAt(existingAccount.getCreatedAt());
        accountToUpdate.setUpdatedAt(existingAccount.getUpdatedAt());
        FinancialAccountDTO savedAccount = financialAccountService.update(accountToUpdate);

        CreditAccountDetailsDTO savedDetails = null;
        if (savedAccount.getAccountType() == AccountType.CREDIT_CARD) {
            Optional<CreditAccountDetailsDTO> existingDetails = creditAccountDetailsService.findOneByAccountId(id);
            savedDetails = saveCreditAccountDetails(request.getCreditAccountDetails(), savedAccount, existingDetails.orElse(null));
        }
        return toResponse(savedAccount, savedDetails);
    }

    private void validateRequest(FinancialAccountConfiguredRequestDTO request) {
        if (request == null || request.getFinancialAccount() == null) {
            throw new IllegalArgumentException("Financial account payload is required");
        }
        boolean creditCard = request.getFinancialAccount().getAccountType() == AccountType.CREDIT_CARD;
        if (creditCard && request.getCreditAccountDetails() == null) {
            throw new IllegalArgumentException("Credit card details are required for credit card accounts");
        }
        if (!creditCard && request.getCreditAccountDetails() != null) {
            throw new IllegalArgumentException("Credit card details are only allowed for credit card accounts");
        }
    }

    private FinancialAccountDTO toFinancialAccountDTO(FinancialAccountConfiguredAccountDTO source, Boolean active) {
        FinancialAccountDTO account = new FinancialAccountDTO();
        account.setName(source.getName());
        account.setInstitutionName(source.getInstitutionName());
        account.setAccountType(source.getAccountType());
        account.setCurrency(source.getCurrency());
        account.setInitialBalance(source.getInitialBalance());
        account.setInitialBalanceDate(source.getInitialBalanceDate());
        account.setLastFourDigits(source.getLastFourDigits());
        account.setDescription(source.getDescription());
        account.setColor(source.getColor());
        account.setIcon(source.getIcon());
        account.setActive(active);
        return account;
    }

    private CreditAccountDetailsDTO saveCreditAccountDetails(
        CreditAccountDetailsConfiguredDTO source,
        FinancialAccountDTO account,
        CreditAccountDetailsDTO existingDetails
    ) {
        if (source == null) {
            return null;
        }
        CreditAccountDetailsDTO details = new CreditAccountDetailsDTO();
        details.setCreditLimit(source.getCreditLimit());
        details.setStatementDay(source.getStatementDay());
        details.setPaymentDueDay(source.getPaymentDueDay());
        details.setAnnualInterestRate(source.getAnnualInterestRate());
        details.setAccount(accountReference(account));
        if (existingDetails == null) {
            return creditAccountDetailsService.save(details);
        }
        details.setId(existingDetails.getId());
        details.setCreatedAt(existingDetails.getCreatedAt());
        details.setUpdatedAt(existingDetails.getUpdatedAt());
        return creditAccountDetailsService.update(details);
    }

    private FinancialAccountDTO accountReference(FinancialAccountDTO account) {
        FinancialAccountDTO reference = new FinancialAccountDTO();
        reference.setId(account.getId());
        reference.setName(account.getName());
        return reference;
    }

    private FinancialAccountConfiguredResponseDTO toResponse(
        FinancialAccountDTO financialAccount,
        CreditAccountDetailsDTO creditAccountDetails
    ) {
        FinancialAccountConfiguredResponseDTO response = new FinancialAccountConfiguredResponseDTO();
        FinancialAccountConfiguredAccountResponseDTO accountResponse = new FinancialAccountConfiguredAccountResponseDTO();
        accountResponse.setId(financialAccount.getId());
        accountResponse.setName(financialAccount.getName());
        accountResponse.setInstitutionName(financialAccount.getInstitutionName());
        accountResponse.setAccountType(financialAccount.getAccountType());
        accountResponse.setCurrency(financialAccount.getCurrency());
        accountResponse.setInitialBalance(financialAccount.getInitialBalance());
        accountResponse.setInitialBalanceDate(financialAccount.getInitialBalanceDate());
        accountResponse.setLastFourDigits(financialAccount.getLastFourDigits());
        accountResponse.setDescription(financialAccount.getDescription());
        accountResponse.setColor(financialAccount.getColor());
        accountResponse.setIcon(financialAccount.getIcon());
        accountResponse.setActive(financialAccount.getActive());
        response.setFinancialAccount(accountResponse);
        if (creditAccountDetails != null) {
            CreditAccountDetailsConfiguredResponseDTO detailsResponse = new CreditAccountDetailsConfiguredResponseDTO();
            detailsResponse.setId(creditAccountDetails.getId());
            detailsResponse.setCreditLimit(creditAccountDetails.getCreditLimit());
            detailsResponse.setStatementDay(creditAccountDetails.getStatementDay());
            detailsResponse.setPaymentDueDay(creditAccountDetails.getPaymentDueDay());
            detailsResponse.setAnnualInterestRate(creditAccountDetails.getAnnualInterestRate());
            response.setCreditAccountDetails(detailsResponse);
        }
        return response;
    }
}
