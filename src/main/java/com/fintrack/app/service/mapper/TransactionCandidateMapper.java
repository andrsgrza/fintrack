package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.User;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.IngestionRecordDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionCandidateDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.dto.UserDTO;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link TransactionCandidate} and its DTO {@link TransactionCandidateDTO}.
 */
@Mapper(componentModel = "spring")
public interface TransactionCandidateMapper extends EntityMapper<TransactionCandidateDTO, TransactionCandidate> {
    @Mapping(target = "user", source = "user", qualifiedByName = "userLogin")
    @Mapping(target = "account", source = "account", qualifiedByName = "financialAccountName")
    @Mapping(target = "category", source = "category", qualifiedByName = "categoryName")
    @Mapping(target = "transactionIngestion", source = "transactionIngestion", qualifiedByName = "transactionIngestionId")
    @Mapping(target = "ingestionRecord", source = "ingestionRecord", qualifiedByName = "ingestionRecordId")
    @Mapping(target = "financialTransaction", source = "financialTransaction", qualifiedByName = "financialTransactionId")
    @Mapping(target = "tags", source = "tags", qualifiedByName = "tagNameSet")
    TransactionCandidateDTO toDto(TransactionCandidate s);

    @Mapping(target = "user", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "transactionIngestion", ignore = true)
    @Mapping(target = "ingestionRecord", ignore = true)
    @Mapping(target = "financialTransaction", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "removeTags", ignore = true)
    TransactionCandidate toEntity(TransactionCandidateDTO transactionCandidateDTO);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "transactionIngestion", ignore = true)
    @Mapping(target = "ingestionRecord", ignore = true)
    @Mapping(target = "financialTransaction", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "removeTags", ignore = true)
    void partialUpdate(@MappingTarget TransactionCandidate entity, TransactionCandidateDTO dto);

    @Named("userLogin")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    UserDTO toDtoUserLogin(User user);

    @Named("financialAccountName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    FinancialAccountDTO toDtoFinancialAccountName(FinancialAccount financialAccount);

    @Named("categoryName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "categoryType", source = "categoryType")
    CategoryDTO toDtoCategoryName(Category category);

    @Named("transactionIngestionId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    TransactionIngestionDTO toDtoTransactionIngestionId(TransactionIngestion transactionIngestion);

    @Named("ingestionRecordId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    IngestionRecordDTO toDtoIngestionRecordId(IngestionRecord ingestionRecord);

    @Named("financialTransactionId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    FinancialTransactionDTO toDtoFinancialTransactionId(FinancialTransaction financialTransaction);

    @Named("tagName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    TagDTO toDtoTagName(Tag tag);

    @Named("tagNameSet")
    default Set<TagDTO> toDtoTagNameSet(Set<Tag> tags) {
        return tags.stream().map(this::toDtoTagName).collect(Collectors.toSet());
    }
}
