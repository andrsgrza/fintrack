package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.DescriptionNormalizationRule;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionCandidateTag;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.User;
import com.fintrack.app.service.dto.AppliedRuleAttributionDTO;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionCandidateDTO;
import com.fintrack.app.service.dto.TransactionCandidateTagSelectionDTO;
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
    @Mapping(
        target = "internalTransferCounterpartAccount",
        source = "internalTransferCounterpartAccount",
        qualifiedByName = "financialAccountName"
    )
    @Mapping(
        target = "internalTransferCounterpartTransaction",
        source = "internalTransferCounterpartTransaction",
        qualifiedByName = "financialTransactionSummary"
    )
    @Mapping(target = "tags", source = "tags", qualifiedByName = "tagNameSet")
    @Mapping(
        target = "descriptionAppliedByRule",
        expression = "java(toAppliedRule(s.getDescriptionAppliedRule(), s.getDescriptionAppliedRuleName()))"
    )
    @Mapping(
        target = "categoryAppliedByRule",
        expression = "java(toAppliedRule(s.getCategoryAppliedRule(), s.getCategoryAppliedRuleName()))"
    )
    @Mapping(target = "selectedTags", source = "tagAssociations", qualifiedByName = "tagSelectionSet")
    TransactionCandidateDTO toDto(TransactionCandidate s);

    @Mapping(target = "user", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "internalTransferEnabled", ignore = true)
    @Mapping(target = "internalTransferMode", ignore = true)
    @Mapping(target = "internalTransferCounterpartPostingDate", ignore = true)
    @Mapping(target = "internalTransferCounterpartDescription", ignore = true)
    @Mapping(target = "internalTransferCounterpartReference", ignore = true)
    @Mapping(target = "internalTransferCounterpartNotes", ignore = true)
    @Mapping(target = "internalTransferCounterpartAccount", ignore = true)
    @Mapping(target = "internalTransferCounterpartTransaction", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "categorySource", ignore = true)
    @Mapping(target = "categoryManualClear", ignore = true)
    @Mapping(target = "descriptionSource", ignore = true)
    @Mapping(target = "descriptionAppliedRule", ignore = true)
    @Mapping(target = "descriptionAppliedRuleName", ignore = true)
    @Mapping(target = "categoryAppliedRule", ignore = true)
    @Mapping(target = "categoryAppliedRuleName", ignore = true)
    @Mapping(target = "sourceDescription", ignore = true)
    @Mapping(target = "transactionBatch", ignore = true)
    @Mapping(target = "rowPosition", ignore = true)
    @Mapping(target = "tagAssociations", ignore = true)
    @Mapping(target = "tagSuppressions", ignore = true)
    @Mapping(target = "inputIssues", ignore = true)
    @Mapping(target = "removeTags", ignore = true)
    TransactionCandidate toEntity(TransactionCandidateDTO transactionCandidateDTO);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "internalTransferEnabled", ignore = true)
    @Mapping(target = "internalTransferMode", ignore = true)
    @Mapping(target = "internalTransferCounterpartPostingDate", ignore = true)
    @Mapping(target = "internalTransferCounterpartDescription", ignore = true)
    @Mapping(target = "internalTransferCounterpartReference", ignore = true)
    @Mapping(target = "internalTransferCounterpartNotes", ignore = true)
    @Mapping(target = "internalTransferCounterpartAccount", ignore = true)
    @Mapping(target = "internalTransferCounterpartTransaction", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "categorySource", ignore = true)
    @Mapping(target = "categoryManualClear", ignore = true)
    @Mapping(target = "descriptionSource", ignore = true)
    @Mapping(target = "descriptionAppliedRule", ignore = true)
    @Mapping(target = "descriptionAppliedRuleName", ignore = true)
    @Mapping(target = "categoryAppliedRule", ignore = true)
    @Mapping(target = "categoryAppliedRuleName", ignore = true)
    @Mapping(target = "sourceDescription", ignore = true)
    @Mapping(target = "transactionBatch", ignore = true)
    @Mapping(target = "rowPosition", ignore = true)
    @Mapping(target = "tagAssociations", ignore = true)
    @Mapping(target = "tagSuppressions", ignore = true)
    @Mapping(target = "inputIssues", ignore = true)
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

    @Named("financialTransactionSummary")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "transactionDate", source = "transactionDate")
    @Mapping(target = "postingDate", source = "postingDate")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "flow", source = "flow")
    @Mapping(target = "account", source = "account", qualifiedByName = "financialAccountName")
    FinancialTransactionDTO toDtoFinancialTransactionSummary(FinancialTransaction financialTransaction);

    @Named("categoryName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "categoryType", source = "categoryType")
    CategoryDTO toDtoCategoryName(Category category);

    @Named("tagName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    TagDTO toDtoTagName(Tag tag);

    @Named("tagNameSet")
    default Set<TagDTO> toDtoTagNameSet(Set<Tag> tags) {
        return tags.stream().map(this::toDtoTagName).collect(Collectors.toSet());
    }

    @Named("tagSelectionSet")
    default Set<TransactionCandidateTagSelectionDTO> toDtoTagSelectionSet(Set<TransactionCandidateTag> associations) {
        return associations
            .stream()
            .filter(association -> association.getTag() != null)
            .map(association -> {
                TransactionCandidateTagSelectionDTO selection = new TransactionCandidateTagSelectionDTO();
                selection.setTagId(association.getTag().getId());
                selection.setTagName(association.getTag().getName());
                selection.setSource(association.getSource());
                selection.setAppliedByRule(toAppliedRule(association.getAppliedRule(), association.getAppliedRuleName()));
                return selection;
            })
            .collect(Collectors.toSet());
    }

    default AppliedRuleAttributionDTO toAppliedRule(DescriptionNormalizationRule liveRule, String snapshotName) {
        if (liveRule == null && (snapshotName == null || snapshotName.isBlank())) return null;
        AppliedRuleAttributionDTO attribution = new AppliedRuleAttributionDTO();
        attribution.setId(liveRule == null ? null : liveRule.getId());
        attribution.setName(snapshotName);
        return attribution;
    }

    default AppliedRuleAttributionDTO toAppliedRule(TransactionRule liveRule, String snapshotName) {
        if (liveRule == null && (snapshotName == null || snapshotName.isBlank())) return null;
        AppliedRuleAttributionDTO attribution = new AppliedRuleAttributionDTO();
        attribution.setId(liveRule == null ? null : liveRule.getId());
        attribution.setName(snapshotName);
        return attribution;
    }
}
