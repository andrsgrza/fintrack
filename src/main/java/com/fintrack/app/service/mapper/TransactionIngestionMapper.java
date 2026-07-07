package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link TransactionIngestion} and its DTO {@link TransactionIngestionDTO}.
 */
@Mapper(componentModel = "spring")
public interface TransactionIngestionMapper extends EntityMapper<TransactionIngestionDTO, TransactionIngestion> {
    @Mapping(target = "accounts", source = "accounts", qualifiedByName = "financialAccountNameSet")
    TransactionIngestionDTO toDto(TransactionIngestion s);

    @Mapping(target = "removeAccounts", ignore = true)
    TransactionIngestion toEntity(TransactionIngestionDTO transactionIngestionDTO);

    @Named("financialAccountName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    FinancialAccountDTO toDtoFinancialAccountName(FinancialAccount financialAccount);

    @Named("financialAccountNameSet")
    default Set<FinancialAccountDTO> toDtoFinancialAccountNameSet(Set<FinancialAccount> financialAccount) {
        return financialAccount.stream().map(this::toDtoFinancialAccountName).collect(Collectors.toSet());
    }
}
