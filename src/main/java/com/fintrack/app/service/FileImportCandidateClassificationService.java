package com.fintrack.app.service;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateSource;
import com.fintrack.app.domain.enumeration.TransactionCandidateStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.CategorySuggestionDTO;
import com.fintrack.app.service.dto.FileImportCandidateApplyRulesResponseDTO;
import com.fintrack.app.service.dto.FileImportCandidateApplyRulesRowDTO;
import com.fintrack.app.service.dto.FileImportCandidateBatchRequestDTO;
import com.fintrack.app.service.dto.FileImportCandidateClassificationRequestDTO;
import com.fintrack.app.service.dto.FileImportCandidateClassificationResponseDTO;
import com.fintrack.app.service.dto.FileImportCandidateRulePreviewResponseDTO;
import com.fintrack.app.service.dto.FileImportCandidateRulePreviewRowDTO;
import com.fintrack.app.service.dto.RuleMatchResultDTO;
import com.fintrack.app.service.dto.RuleOutputConflictDTO;
import com.fintrack.app.service.dto.SkippedRuleOutputDTO;
import com.fintrack.app.service.dto.TagSuggestionDTO;
import com.fintrack.app.service.mapper.TransactionCandidateWorkflowSummaryMapper;
import com.fintrack.app.service.rules.CategorySuggestion;
import com.fintrack.app.service.rules.RuleMatchResult;
import com.fintrack.app.service.rules.RuleOutputConflict;
import com.fintrack.app.service.rules.SkippedRuleOutput;
import com.fintrack.app.service.rules.TagSuggestion;
import com.fintrack.app.service.rules.TransactionRuleEvaluationInput;
import com.fintrack.app.service.rules.TransactionRuleEvaluationResult;
import com.fintrack.app.service.rules.TransactionRuleEvaluationService;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FileImportCandidateClassificationService {

    private final TransactionIngestionRepository transactionIngestionRepository;
    private final TransactionCandidateRepository transactionCandidateRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final CurrentUserService currentUserService;
    private final TransactionRuleEvaluationService transactionRuleEvaluationService;
    private final TransactionCandidateWorkflowSummaryMapper transactionCandidateWorkflowSummaryMapper;

    public FileImportCandidateClassificationService(
        TransactionIngestionRepository transactionIngestionRepository,
        TransactionCandidateRepository transactionCandidateRepository,
        CategoryRepository categoryRepository,
        TagRepository tagRepository,
        CurrentUserService currentUserService,
        TransactionRuleEvaluationService transactionRuleEvaluationService,
        TransactionCandidateWorkflowSummaryMapper transactionCandidateWorkflowSummaryMapper
    ) {
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.transactionCandidateRepository = transactionCandidateRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.currentUserService = currentUserService;
        this.transactionRuleEvaluationService = transactionRuleEvaluationService;
        this.transactionCandidateWorkflowSummaryMapper = transactionCandidateWorkflowSummaryMapper;
    }

    public FileImportCandidateClassificationResponseDTO updateClassification(
        Long transactionIngestionId,
        Long candidateId,
        FileImportCandidateClassificationRequestDTO request
    ) {
        String userLogin = currentUserService.getCurrentUserLogin();
        TransactionIngestion ingestion = resolveAccessibleFileIngestion(transactionIngestionId, userLogin);
        TransactionCandidate candidate = resolveCandidate(ingestion, candidateId, userLogin);
        validateMutableFileImportCandidate(candidate);

        if (request == null) {
            throw new IllegalArgumentException("Classification request is required");
        }
        if (!request.hasCategoryId() && !request.hasTagIds()) {
            throw new IllegalArgumentException("Classification request must include categoryId or tagIds");
        }
        if (request.hasCategoryId()) {
            candidate.setCategory(resolveCategory(request.getCategoryId(), userLogin, candidate.getFlow()));
        }
        if (request.hasTagIds()) {
            candidate.setTags(resolveTags(request.getTagIds(), userLogin));
        }
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        candidate.setUpdatedAt(Instant.now());
        TransactionCandidate saved = transactionCandidateRepository.save(candidate);

        FileImportCandidateClassificationResponseDTO response = new FileImportCandidateClassificationResponseDTO();
        response.setTransactionIngestionId(ingestion.getId());
        response.setCandidate(transactionCandidateWorkflowSummaryMapper.toDto(saved));
        return response;
    }

    @Transactional(readOnly = true)
    public FileImportCandidateRulePreviewResponseDTO previewRules(Long transactionIngestionId, FileImportCandidateBatchRequestDTO request) {
        String userLogin = currentUserService.getCurrentUserLogin();
        TransactionIngestion ingestion = resolveAccessibleFileIngestion(transactionIngestionId, userLogin);
        List<TransactionCandidate> candidates = resolveBatchCandidates(ingestion, request, userLogin);

        FileImportCandidateRulePreviewResponseDTO response = new FileImportCandidateRulePreviewResponseDTO();
        response.setTransactionIngestionId(ingestion.getId());
        response.setRows(candidates.stream().map(candidate -> previewRow(candidate, userLogin)).toList());
        return response;
    }

    public FileImportCandidateApplyRulesResponseDTO applyRules(Long transactionIngestionId, FileImportCandidateBatchRequestDTO request) {
        String userLogin = currentUserService.getCurrentUserLogin();
        TransactionIngestion ingestion = resolveAccessibleFileIngestion(transactionIngestionId, userLogin);
        List<TransactionCandidate> candidates = resolveBatchCandidates(ingestion, request, userLogin);

        FileImportCandidateApplyRulesResponseDTO response = new FileImportCandidateApplyRulesResponseDTO();
        response.setTransactionIngestionId(ingestion.getId());
        response.setRows(candidates.stream().map(candidate -> applyRulesRow(candidate, userLogin)).toList());
        return response;
    }

    public FileImportCandidateClassificationResponseDTO confirmNoSuggestions(Long transactionIngestionId, Long candidateId) {
        String userLogin = currentUserService.getCurrentUserLogin();
        TransactionIngestion ingestion = resolveAccessibleFileIngestion(transactionIngestionId, userLogin);
        TransactionCandidate candidate = resolveCandidate(ingestion, candidateId, userLogin);
        validateMutableFileImportCandidate(candidate);
        TransactionRuleEvaluationResult evaluation = evaluate(candidate, userLogin);
        if (evaluation.hasSuggestions()) {
            throw new IllegalArgumentException("Rule suggestions exist and must be reviewed before confirming no suggestions");
        }
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE);
        candidate.setUpdatedAt(Instant.now());
        TransactionCandidate saved = transactionCandidateRepository.save(candidate);

        FileImportCandidateClassificationResponseDTO response = new FileImportCandidateClassificationResponseDTO();
        response.setTransactionIngestionId(ingestion.getId());
        response.setCandidate(transactionCandidateWorkflowSummaryMapper.toDto(saved));
        return response;
    }

    private FileImportCandidateRulePreviewRowDTO previewRow(TransactionCandidate candidate, String userLogin) {
        FileImportCandidateRulePreviewRowDTO row = basePreviewRow(candidate);
        try {
            validateMutableFileImportCandidate(candidate);
            TransactionRuleEvaluationResult evaluation = evaluate(candidate, userLogin);
            populateEvaluation(row, evaluation);
            row.setAction("PREVIEWED");
        } catch (IllegalArgumentException e) {
            row.setAction("SKIPPED");
            row.setError(e.getMessage());
        }
        return row;
    }

    private FileImportCandidateApplyRulesRowDTO applyRulesRow(TransactionCandidate candidate, String userLogin) {
        FileImportCandidateApplyRulesRowDTO row = baseApplyRow(candidate);
        try {
            validateMutableFileImportCandidate(candidate);
            TransactionRuleEvaluationResult evaluation = evaluate(candidate, userLogin);
            boolean hadManualClassification =
                candidate.getClassificationReviewStatus() == TransactionCandidateClassificationReviewStatus.USER_SELECTED ||
                candidate.getCategory() != null ||
                (candidate.getTags() != null && !candidate.getTags().isEmpty());
            boolean categoryApplied = applySuggestedCategory(candidate, evaluation, userLogin);
            List<Long> tagIdsApplied = applySuggestedTags(candidate, evaluation, userLogin);

            if (hadManualClassification) {
                candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
            } else if (categoryApplied || !tagIdsApplied.isEmpty() || evaluation.hasSuggestions()) {
                candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.SUGGESTED);
            } else {
                candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE);
            }

            candidate.setUpdatedAt(Instant.now());
            TransactionCandidate saved = transactionCandidateRepository.save(candidate);
            populateEvaluation(row, evaluation);
            row.setAction(categoryApplied || !tagIdsApplied.isEmpty() ? "APPLIED" : "UNCHANGED");
            row.setCandidate(transactionCandidateWorkflowSummaryMapper.toDto(saved));
            row.setCategoryApplied(categoryApplied);
            row.setTagIdsApplied(tagIdsApplied);
        } catch (IllegalArgumentException e) {
            row.setAction("SKIPPED");
            row.setError(e.getMessage());
        }
        return row;
    }

    private TransactionIngestion resolveAccessibleFileIngestion(Long transactionIngestionId, String userLogin) {
        if (transactionIngestionId == null) {
            throw new IllegalArgumentException("Transaction ingestion is required");
        }
        TransactionIngestion ingestion = transactionIngestionRepository
            .findOneWithToOneRelationshipsByIdAndAccountUserLogin(transactionIngestionId, userLogin)
            .orElseThrow(() -> new IllegalArgumentException("Transaction ingestion is not accessible"));
        if (ingestion.getIngestionType() != IngestionType.FILE) {
            throw new IllegalArgumentException("Only FILE ingestions can classify transaction candidates");
        }
        return ingestion;
    }

    private TransactionCandidate resolveCandidate(TransactionIngestion ingestion, Long candidateId, String userLogin) {
        if (candidateId == null) {
            throw new IllegalArgumentException("Transaction candidate is required");
        }
        TransactionCandidate candidate = transactionCandidateRepository
            .findOneWithRelationshipsByIdAndUserLogin(candidateId, userLogin)
            .orElseThrow(() -> new IllegalArgumentException("Transaction candidate is not accessible"));
        if (
            candidate.getTransactionIngestion() == null || !Objects.equals(candidate.getTransactionIngestion().getId(), ingestion.getId())
        ) {
            throw new IllegalArgumentException("Transaction candidate does not belong to this ingestion");
        }
        return candidate;
    }

    private List<TransactionCandidate> resolveBatchCandidates(
        TransactionIngestion ingestion,
        FileImportCandidateBatchRequestDTO request,
        String userLogin
    ) {
        List<TransactionCandidate> candidates = transactionCandidateRepository
            .findAllWithRelationshipsByTransactionIngestionIdAndUserLogin(ingestion.getId(), userLogin)
            .stream()
            .sorted(
                Comparator.comparing(this::recordIndex, Comparator.nullsLast(Integer::compareTo)).thenComparing(TransactionCandidate::getId)
            )
            .toList();
        List<Long> candidateIds = request == null ? null : request.getCandidateIds();
        if (candidateIds == null) {
            validateBatchCandidateSources(candidates);
            return candidates;
        }
        Set<Long> requestedIds = new LinkedHashSet<>();
        for (Long candidateId : candidateIds) {
            if (candidateId == null) {
                throw new IllegalArgumentException("Transaction candidate id is required");
            }
            if (!requestedIds.add(candidateId)) {
                throw new IllegalArgumentException("Transaction candidate ids must be unique");
            }
        }
        Map<Long, TransactionCandidate> byId = candidates
            .stream()
            .collect(Collectors.toMap(TransactionCandidate::getId, candidate -> candidate));
        List<TransactionCandidate> selectedCandidates = requestedIds
            .stream()
            .map(candidateId -> {
                TransactionCandidate candidate = byId.get(candidateId);
                if (candidate == null) {
                    throw new IllegalArgumentException("Transaction candidate does not belong to this ingestion");
                }
                return candidate;
            })
            .toList();
        validateBatchCandidateSources(selectedCandidates);
        return selectedCandidates;
    }

    private void validateBatchCandidateSources(List<TransactionCandidate> candidates) {
        boolean hasNonFileImportCandidate = candidates
            .stream()
            .anyMatch(candidate -> candidate.getSource() != TransactionCandidateSource.FILE_IMPORT);
        if (hasNonFileImportCandidate) {
            throw new IllegalArgumentException("Only FILE_IMPORT candidates can be classified from file ingestion");
        }
    }

    private void validateMutableFileImportCandidate(TransactionCandidate candidate) {
        if (candidate.getSource() != TransactionCandidateSource.FILE_IMPORT) {
            throw new IllegalArgumentException("Only FILE_IMPORT candidates can be classified from file ingestion");
        }
        if (
            candidate.getStatus() == TransactionCandidateStatus.POSTED ||
            candidate.getStatus() == TransactionCandidateStatus.CANCELLED ||
            candidate.getStatus() == TransactionCandidateStatus.FAILED
        ) {
            throw new IllegalArgumentException("Final transaction candidates cannot be classified");
        }
        if (candidate.getIngestionRecord() == null || candidate.getIngestionRecord().getStatus() != IngestionRecordStatus.VALID) {
            throw new IllegalArgumentException("Transaction candidate must be linked to a valid ingestion record");
        }
        if (candidate.getAccount() == null || candidate.getAccount().getId() == null) {
            throw new IllegalArgumentException("Financial account is required for classification");
        }
    }

    private TransactionRuleEvaluationResult evaluate(TransactionCandidate candidate, String userLogin) {
        if (candidate.getDescription() == null || candidate.getDescription().isBlank()) {
            throw new IllegalArgumentException("Description is required for rule preview");
        }
        if (candidate.getAmount() == null || candidate.getAmount().signum() <= 0 || candidate.getFlow() == null) {
            throw new IllegalArgumentException("Signed amount is required for rule preview");
        }
        if (candidate.getTransactionDate() == null) {
            throw new IllegalArgumentException("Transaction date is required for rule preview");
        }
        return transactionRuleEvaluationService.evaluate(
            new TransactionRuleEvaluationInput(
                userLogin,
                candidate.getDescription(),
                candidate.getAmount(),
                candidate.getFlow(),
                candidate.getExternalReference(),
                TransactionOrigin.FILE_IMPORT,
                candidate.getTransactionDate(),
                candidate.getPostingDate(),
                candidate.getAccount().getId(),
                candidate.getCategory() == null ? null : candidate.getCategory().getId(),
                candidate.getCategory() == null ? null : candidate.getCategory().getName(),
                currentTagIds(candidate),
                currentTagNames(candidate)
            )
        );
    }

    private Category resolveCategory(Long categoryId, String userLogin, TransactionFlow flow) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(categoryId, userLogin)
            .orElseThrow(() -> new IllegalArgumentException("Category is not accessible"));
        validateCategoryCompatibility(category, flow);
        return category;
    }

    private Set<Tag> resolveTags(List<Long> tagIds, String userLogin) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<Long> seenIds = new HashSet<>();
        Set<Tag> tags = new LinkedHashSet<>();
        for (Long tagId : tagIds) {
            if (tagId == null) {
                throw new IllegalArgumentException("Tag id is required");
            }
            if (!seenIds.add(tagId)) {
                throw new IllegalArgumentException("Tag ids must be unique");
            }
            tags.add(
                tagRepository
                    .findOneWithToOneRelationshipsByIdAndUserLogin(tagId, userLogin)
                    .orElseThrow(() -> new IllegalArgumentException("Tag is not accessible"))
            );
        }
        return tags;
    }

    private void validateCategoryCompatibility(Category category, TransactionFlow flow) {
        if (category == null || flow == null) {
            return;
        }
        CategoryType categoryType = category.getCategoryType();
        if (flow == TransactionFlow.OUT && categoryType != CategoryType.EXPENSE && categoryType != CategoryType.BOTH) {
            throw new IllegalArgumentException("Category type is not compatible with transaction flow");
        }
        if (flow == TransactionFlow.IN && categoryType != CategoryType.INCOME && categoryType != CategoryType.BOTH) {
            throw new IllegalArgumentException("Category type is not compatible with transaction flow");
        }
    }

    private boolean applySuggestedCategory(TransactionCandidate candidate, TransactionRuleEvaluationResult evaluation, String userLogin) {
        if (
            candidate.getCategory() == null &&
            evaluation.suggestedCategory() != null &&
            !evaluation.suggestedCategory().conflictsWithCurrentValue()
        ) {
            candidate.setCategory(resolveCategory(evaluation.suggestedCategory().categoryId(), userLogin, candidate.getFlow()));
            return true;
        }
        return false;
    }

    private List<Long> applySuggestedTags(TransactionCandidate candidate, TransactionRuleEvaluationResult evaluation, String userLogin) {
        List<Long> tagIdsApplied = new java.util.ArrayList<>();
        Set<Long> tagIds = currentTagIds(candidate);
        for (TagSuggestion suggestedTag : evaluation.suggestedTags()) {
            if (suggestedTag.alreadyPresent() || suggestedTag.duplicateOfEarlierSuggestion() || tagIds.contains(suggestedTag.tagId())) {
                continue;
            }
            Tag tag = tagRepository
                .findOneWithToOneRelationshipsByIdAndUserLogin(suggestedTag.tagId(), userLogin)
                .orElseThrow(() -> new IllegalArgumentException("Suggested tag is not accessible"));
            candidate.addTags(tag);
            tagIds.add(tag.getId());
            tagIdsApplied.add(tag.getId());
        }
        return tagIdsApplied;
    }

    private FileImportCandidateRulePreviewRowDTO basePreviewRow(TransactionCandidate candidate) {
        FileImportCandidateRulePreviewRowDTO row = new FileImportCandidateRulePreviewRowDTO();
        row.setCandidateId(candidate.getId());
        row.setIngestionRecordId(ingestionRecordId(candidate));
        row.setRecordIndex(recordIndex(candidate));
        row.setCandidate(transactionCandidateWorkflowSummaryMapper.toDto(candidate));
        return row;
    }

    private FileImportCandidateApplyRulesRowDTO baseApplyRow(TransactionCandidate candidate) {
        FileImportCandidateApplyRulesRowDTO row = new FileImportCandidateApplyRulesRowDTO();
        row.setCandidateId(candidate.getId());
        row.setIngestionRecordId(ingestionRecordId(candidate));
        row.setRecordIndex(recordIndex(candidate));
        row.setCandidate(transactionCandidateWorkflowSummaryMapper.toDto(candidate));
        return row;
    }

    private void populateEvaluation(FileImportCandidateRulePreviewRowDTO row, TransactionRuleEvaluationResult evaluation) {
        row.setSuggestedCategory(toCategorySuggestionDTO(evaluation.suggestedCategory()));
        row.setSuggestedTags(evaluation.suggestedTags().stream().map(this::toTagSuggestionDTO).toList());
        row.setMatchedRules(evaluation.matchedRules().stream().map(this::toRuleMatchResultDTO).toList());
        row.setConflicts(evaluation.conflicts().stream().map(this::toRuleOutputConflictDTO).toList());
        row.setSkippedOutputs(evaluation.skippedOutputs().stream().map(this::toSkippedRuleOutputDTO).toList());
        row.setHasSuggestions(evaluation.hasSuggestions());
        row.setHasConflicts(evaluation.hasConflicts());
    }

    private void populateEvaluation(FileImportCandidateApplyRulesRowDTO row, TransactionRuleEvaluationResult evaluation) {
        row.setSuggestedCategory(toCategorySuggestionDTO(evaluation.suggestedCategory()));
        row.setSuggestedTags(evaluation.suggestedTags().stream().map(this::toTagSuggestionDTO).toList());
        row.setMatchedRules(evaluation.matchedRules().stream().map(this::toRuleMatchResultDTO).toList());
        row.setConflicts(evaluation.conflicts().stream().map(this::toRuleOutputConflictDTO).toList());
        row.setSkippedOutputs(evaluation.skippedOutputs().stream().map(this::toSkippedRuleOutputDTO).toList());
        row.setHasSuggestions(evaluation.hasSuggestions());
        row.setHasConflicts(evaluation.hasConflicts());
    }

    private CategorySuggestionDTO toCategorySuggestionDTO(CategorySuggestion suggestion) {
        if (suggestion == null) {
            return null;
        }
        CategorySuggestionDTO dto = new CategorySuggestionDTO();
        dto.setCategoryId(suggestion.categoryId());
        dto.setCategoryName(suggestion.categoryName());
        dto.setSourceRuleId(suggestion.sourceRuleId());
        dto.setSourceRuleName(suggestion.sourceRuleName());
        dto.setConflictsWithCurrentValue(suggestion.conflictsWithCurrentValue());
        dto.setCurrentCategoryId(suggestion.currentCategoryId());
        dto.setCurrentCategoryName(suggestion.currentCategoryName());
        return dto;
    }

    private TagSuggestionDTO toTagSuggestionDTO(TagSuggestion suggestion) {
        TagSuggestionDTO dto = new TagSuggestionDTO();
        dto.setTagId(suggestion.tagId());
        dto.setTagName(suggestion.tagName());
        dto.setSourceRuleId(suggestion.sourceRuleId());
        dto.setSourceRuleName(suggestion.sourceRuleName());
        dto.setAlreadyPresent(suggestion.alreadyPresent());
        dto.setDuplicateOfEarlierSuggestion(suggestion.duplicateOfEarlierSuggestion());
        return dto;
    }

    private RuleMatchResultDTO toRuleMatchResultDTO(RuleMatchResult match) {
        RuleMatchResultDTO dto = new RuleMatchResultDTO();
        dto.setRuleId(match.ruleId());
        dto.setRuleName(match.ruleName());
        dto.setPriority(match.priority());
        dto.setConditionLogic(match.conditionLogic());
        dto.setProposedOutputs(match.proposedOutputs());
        return dto;
    }

    private RuleOutputConflictDTO toRuleOutputConflictDTO(RuleOutputConflict conflict) {
        RuleOutputConflictDTO dto = new RuleOutputConflictDTO();
        dto.setField(conflict.field());
        dto.setCurrentValueId(conflict.currentValueId());
        dto.setCurrentValueLabel(conflict.currentValueLabel());
        dto.setSuggestedValueId(conflict.suggestedValueId());
        dto.setSuggestedValueLabel(conflict.suggestedValueLabel());
        dto.setSourceRuleId(conflict.sourceRuleId());
        dto.setSourceRuleName(conflict.sourceRuleName());
        dto.setReason(conflict.reason());
        return dto;
    }

    private SkippedRuleOutputDTO toSkippedRuleOutputDTO(SkippedRuleOutput skipped) {
        SkippedRuleOutputDTO dto = new SkippedRuleOutputDTO();
        dto.setField(skipped.field());
        dto.setSourceRuleId(skipped.sourceRuleId());
        dto.setSourceRuleName(skipped.sourceRuleName());
        dto.setReason(skipped.reason());
        dto.setValueId(skipped.valueId());
        dto.setValueLabel(skipped.valueLabel());
        return dto;
    }

    private Set<Long> currentTagIds(TransactionCandidate candidate) {
        if (candidate.getTags() == null || candidate.getTags().isEmpty()) {
            return new LinkedHashSet<>();
        }
        return candidate.getTags().stream().map(Tag::getId).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<Long, String> currentTagNames(TransactionCandidate candidate) {
        if (candidate.getTags() == null || candidate.getTags().isEmpty()) {
            return Map.of();
        }
        Map<Long, String> tagNames = new LinkedHashMap<>();
        for (Tag tag : candidate.getTags()) {
            if (tag.getId() != null) {
                tagNames.put(tag.getId(), tag.getName());
            }
        }
        return tagNames;
    }

    private Long ingestionRecordId(TransactionCandidate candidate) {
        return candidate.getIngestionRecord() == null ? null : candidate.getIngestionRecord().getId();
    }

    private Integer recordIndex(TransactionCandidate candidate) {
        IngestionRecord record = candidate.getIngestionRecord();
        return record == null ? null : record.getRecordIndex();
    }
}
