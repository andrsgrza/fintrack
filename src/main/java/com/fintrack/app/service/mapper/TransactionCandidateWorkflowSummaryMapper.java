package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionCandidateTag;
import com.fintrack.app.service.dto.TransactionCandidateTagSelectionDTO;
import com.fintrack.app.service.dto.TransactionCandidateWorkflowSummaryDTO;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TransactionCandidateWorkflowSummaryMapper {

    public TransactionCandidateWorkflowSummaryDTO toDto(TransactionCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        TransactionCandidateWorkflowSummaryDTO summary = new TransactionCandidateWorkflowSummaryDTO();
        summary.setId(candidate.getId());
        summary.setSource(candidate.getSource());
        summary.setStatus(candidate.getStatus());
        summary.setValidationStatus(candidate.getValidationStatus());
        summary.setClassificationReviewStatus(candidate.getClassificationReviewStatus());
        summary.setDescriptionReviewStatus(candidate.getDescriptionReviewStatus());
        summary.setTransactionDate(candidate.getTransactionDate());
        summary.setPostingDate(candidate.getPostingDate());
        summary.setDescription(candidate.getDescription());
        summary.setSignedAmount(candidate.getSignedAmount());
        summary.setAmount(candidate.getAmount());
        summary.setFlow(candidate.getFlow());
        summary.setCurrencySnapshot(candidate.getCurrencySnapshot());
        summary.setExternalReference(candidate.getExternalReference());
        summary.setNotes(candidate.getNotes());
        summary.setAccountId(candidate.getAccount() == null ? null : candidate.getAccount().getId());
        summary.setAccountName(candidate.getAccount() == null ? null : candidate.getAccount().getName());
        summary.setCategoryId(candidate.getCategory() == null ? null : candidate.getCategory().getId());
        summary.setCategoryName(candidate.getCategory() == null ? null : candidate.getCategory().getName());
        summary.setCategorySource(candidate.getCategorySource());
        Set<Tag> tags = candidate.getTags() == null ? Set.of() : candidate.getTags();
        summary.setTagIds(tags.stream().map(Tag::getId).filter(Objects::nonNull).sorted().toList());
        summary.setTagNames(
            tags.stream().filter(tag -> tag.getId() != null).sorted(Comparator.comparing(Tag::getId)).map(Tag::getName).toList()
        );
        summary.setSelectedTags(
            candidate
                .getTagAssociations()
                .stream()
                .filter(association -> association.getTag() != null)
                .sorted(Comparator.comparing(association -> association.getTag().getId()))
                .map(this::toTagSelection)
                .toList()
        );
        summary.setFinancialTransactionId(candidate.getFinancialTransaction() == null ? null : candidate.getFinancialTransaction().getId());
        summary.setCreatedAt(candidate.getCreatedAt());
        summary.setUpdatedAt(candidate.getUpdatedAt());
        return summary;
    }

    private TransactionCandidateTagSelectionDTO toTagSelection(TransactionCandidateTag association) {
        TransactionCandidateTagSelectionDTO selection = new TransactionCandidateTagSelectionDTO();
        selection.setTagId(association.getTag().getId());
        selection.setTagName(association.getTag().getName());
        selection.setSource(association.getSource());
        return selection;
    }
}
