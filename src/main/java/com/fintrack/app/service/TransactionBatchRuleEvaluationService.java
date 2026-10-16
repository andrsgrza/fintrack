package com.fintrack.app.service;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.DescriptionNormalizationRule;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionBatch;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.TransactionBatchRepository;
import com.fintrack.app.repository.TransactionCandidateRepository;
import com.fintrack.app.service.dto.*;
import com.fintrack.app.service.rules.*;
import com.fintrack.app.service.validation.CategoryFlowCompatibilityValidator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal, batch-aware rule boundary.
 *
 * Preview methods are strictly observational. Apply methods load the current candidate again, resolve current rule
 * output on the server, and share the same conservative fill-only policy for automatic and explicit acceptance.
 * This keeps candidate membership/version guards out of the legacy standalone and FILE workflows.
 */
@Service
public class TransactionBatchRuleEvaluationService {

    private final TransactionBatchRepository batchRepository;
    private final TransactionCandidateRepository candidateRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final CurrentUserService currentUserService;
    private final DescriptionNormalizationRuleEvaluationService descriptionRuleEvaluationService;
    private final TransactionRuleEvaluationService transactionRuleEvaluationService;

    public TransactionBatchRuleEvaluationService(
        TransactionBatchRepository batchRepository,
        TransactionCandidateRepository candidateRepository,
        CategoryRepository categoryRepository,
        TagRepository tagRepository,
        CurrentUserService currentUserService,
        DescriptionNormalizationRuleEvaluationService descriptionRuleEvaluationService,
        TransactionRuleEvaluationService transactionRuleEvaluationService
    ) {
        this.batchRepository = batchRepository;
        this.candidateRepository = candidateRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.currentUserService = currentUserService;
        this.descriptionRuleEvaluationService = descriptionRuleEvaluationService;
        this.transactionRuleEvaluationService = transactionRuleEvaluationService;
    }

    /** Returns previews only. No review state, issue, candidate, or root version is changed. */
    @Transactional(readOnly = true)
    public List<TransactionBatchRulePreviewDTO> previewBatch(Long batchId) {
        String login = currentUserService.getCurrentUserLogin();
        TransactionBatch batch = requireOpenBatchForRead(batchId, login);
        RuleSets rules = loadRuleSets(login);
        return candidateRepository
            .findAllBatchCandidatesWithRelationships(batch.getId(), login)
            .stream()
            .map(candidate -> preview(candidate, login, rules))
            .toList();
    }

    /** Returns an observational preview for one owned batch candidate. */
    @Transactional(readOnly = true)
    public TransactionBatchRulePreviewDTO previewCandidate(Long batchId, Long candidateId) {
        String login = currentUserService.getCurrentUserLogin();
        TransactionBatch batch = requireOpenBatchForRead(batchId, login);
        TransactionCandidate candidate = candidateRepository
            .findOneBatchCandidateWithRelationships(batch.getId(), candidateId, login)
            .orElseThrow(() -> new IllegalArgumentException("Transaction candidate is not part of the open batch"));
        return preview(candidate, login, loadRuleSets(login));
    }

    /**
     * Explicitly accepts only results the server can still resolve. Caller-provided IDs/description are compared to a
     * fresh preview rather than used as arbitrary classification data.
     */
    @Transactional
    public TransactionBatchRuleApplyResultDTO applyExplicit(Long batchId, Long candidateId, TransactionBatchRuleApplyCommand command) {
        if (command == null) throw new IllegalArgumentException("Rule apply command is required");
        String login = currentUserService.getCurrentUserLogin();
        TransactionBatch batch = lockOpenBatch(batchId, login);
        TransactionCandidate candidate = lockBatchCandidate(batch, candidateId, command.getExpectedCandidateVersion(), login);
        RuleSets rules = loadRuleSets(login);
        ResolvedPreview resolved = resolve(candidate, login, rules);
        validateExplicitAcceptance(command, resolved);

        AppliedChanges changes = applyResolved(candidate, batch, resolved, command, true);
        return persistIfChanged(candidate, batch, changes);
    }

    /**
     * Reevaluates only the supplied current rows. It is an orchestration action, not a preview: enabled automatic
     * settings may make conservative changes. An empty id set is intentionally a no-op.
     */
    @Transactional
    public List<TransactionBatchRuleApplyResultDTO> reevaluateAndAutoApply(Long batchId, List<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) return List.of();
        String login = currentUserService.getCurrentUserLogin();
        TransactionBatch batch = lockOpenBatch(batchId, login);
        RuleSets rules = loadRuleSets(login);
        List<Long> suppliedIds = candidateIds.stream().filter(Objects::nonNull).toList();
        List<Long> uniqueIds = suppliedIds.stream().distinct().sorted().toList();
        if (uniqueIds.size() != suppliedIds.size()) {
            throw new IllegalArgumentException("Candidate ids must be unique");
        }

        List<PendingApplication> pending = new ArrayList<>();
        for (Long candidateId : uniqueIds) {
            TransactionCandidate candidate = candidateRepository
                .findOneBatchCandidateForMutation(batch.getId(), candidateId, login)
                .orElseThrow(() -> new IllegalArgumentException("Transaction candidate is not part of the open batch"));
            pending.add(new PendingApplication(candidate, resolve(candidate, login, rules)));
        }

        List<TransactionBatchRuleApplyResultDTO> results = new ArrayList<>();
        boolean batchChanged = false;
        for (PendingApplication item : pending) {
            AppliedChanges changes = applyResolved(item.candidate(), batch, item.preview(), null, false);
            if (changes.changed()) {
                touch(item.candidate());
                TransactionCandidate saved = candidateRepository.saveAndFlush(item.candidate());
                item = new PendingApplication(saved, item.preview());
                batchChanged = true;
            }
            results.add(toApplyResult(item.candidate(), batch, changes));
        }
        if (batchChanged) {
            touch(batch);
            batch = batchRepository.saveAndFlush(batch);
            for (TransactionBatchRuleApplyResultDTO result : results) result.setBatchVersion(batch.getVersion());
        }
        return results;
    }

    private TransactionBatchRuleApplyResultDTO persistIfChanged(
        TransactionCandidate candidate,
        TransactionBatch batch,
        AppliedChanges changes
    ) {
        if (changes.changed()) {
            touch(candidate);
            candidate = candidateRepository.saveAndFlush(candidate);
            touch(batch);
            batch = batchRepository.saveAndFlush(batch);
        }
        return toApplyResult(candidate, batch, changes);
    }

    private AppliedChanges applyResolved(
        TransactionCandidate candidate,
        TransactionBatch batch,
        ResolvedPreview preview,
        TransactionBatchRuleApplyCommand explicit,
        boolean isExplicit
    ) {
        boolean protectManual = Boolean.TRUE.equals(batch.getProtectManualChanges());
        boolean descriptionApplied = false;
        boolean categoryApplied = false;
        List<Long> tagsApplied = new ArrayList<>();

        boolean mayApplyDescription = isExplicit ? explicit.isApplyDescription() : Boolean.TRUE.equals(batch.getAutoApplyDescriptions());
        if (
            mayApplyDescription &&
            preview.description().matched() &&
            (!protectManual || candidate.getDescriptionSource() != TransactionCandidateDescriptionSource.MANUAL || isExplicit)
        ) {
            String result = preview.description().resultingDescription();
            if (
                !Objects.equals(candidate.getDescription(), result) ||
                candidate.getDescriptionSource() != TransactionCandidateDescriptionSource.AUTOMATIC ||
                candidate.getDescriptionReviewStatus() != TransactionCandidateDescriptionReviewStatus.AUTO_APPLIED
            ) {
                candidate.setDescription(result);
                candidate.setDescriptionSource(TransactionCandidateDescriptionSource.AUTOMATIC);
                candidate.setDescriptionAppliedRuleAttribution(requireDescriptionRule(preview.rules(), preview.description().ruleId()));
                candidate.setDescriptionReviewStatus(TransactionCandidateDescriptionReviewStatus.AUTO_APPLIED);
                descriptionApplied = true;
            }
        }

        // Classification intentionally observes the effective description after a description application.
        ResolvedPreview classificationPreview = descriptionApplied
            ? resolveClassification(candidate, currentUserService.getCurrentUserLogin(), preview.rules())
            : preview;

        boolean mayApplyCategory = isExplicit ? explicit.isApplyCategory() : Boolean.TRUE.equals(batch.getAutoApplyCategories());
        if (
            mayApplyCategory &&
            candidate.getCategory() == null &&
            classificationPreview.classification().suggestedCategory() != null &&
            (isExplicit || !protectManual || !Boolean.TRUE.equals(candidate.getCategoryManualClear()))
        ) {
            Category category = requireAccessibleCategory(classificationPreview.classification().suggestedCategory().categoryId());
            if (CategoryFlowCompatibilityValidator.isCompatible(category.getCategoryType(), candidate.getFlow())) {
                CategoryReferenceValidator.validateActiveForNewReference(category);
                candidate.setCategory(category);
                candidate.setCategorySource(TransactionCandidateClassificationSource.AUTOMATIC);
                candidate.setCategoryAppliedRuleAttribution(
                    requireTransactionRule(
                        classificationPreview.rules(),
                        classificationPreview.classification().suggestedCategory().sourceRuleId()
                    )
                );
                candidate.setCategoryManualClear(false);
                categoryApplied = true;
            }
        }

        boolean mayApplyTags = isExplicit ? explicit.isApplyTags() : Boolean.TRUE.equals(batch.getAutoApplyTags());
        if (mayApplyTags) {
            Set<Long> requested = isExplicit ? explicit.getExpectedSuggestedTagIds() : Set.copyOf(classificationPreview.eligibleTagIds());
            for (Long tagId : requested) {
                if (candidate.getTags().stream().anyMatch(tag -> Objects.equals(tag.getId(), tagId))) continue;
                Tag tag = requireAccessibleTag(tagId);
                if (!isExplicit && protectManual && candidate.isTagSuppressed(tag)) continue;
                TagReferenceValidator.validateActiveForNewReference(tag);
                candidate.addTag(
                    tag,
                    TransactionCandidateClassificationSource.AUTOMATIC,
                    requireTransactionRule(
                        classificationPreview.rules(),
                        suggestedTag(classificationPreview.classification(), tagId).sourceRuleId()
                    )
                );
                candidate.removeTagSuppression(tag);
                tagsApplied.add(tag.getId());
            }
        }

        boolean classificationChanged = categoryApplied || !tagsApplied.isEmpty();
        if (classificationChanged) {
            candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.SUGGESTED);
        }
        return new AppliedChanges(descriptionApplied, categoryApplied, tagsApplied);
    }

    private void validateExplicitAcceptance(TransactionBatchRuleApplyCommand command, ResolvedPreview preview) {
        if (command.isApplyDescription()) {
            if (
                !preview.description().matched() ||
                !Objects.equals(command.getExpectedSuggestedDescription(), preview.description().resultingDescription())
            ) {
                throw new IllegalStateException("Description suggestion is stale");
            }
        }
        if (command.isApplyCategory()) {
            Long actual = preview.classification().suggestedCategory() == null
                ? null
                : preview.classification().suggestedCategory().categoryId();
            if (
                preview.candidate().getCategory() != null ||
                !Objects.equals(command.getExpectedSuggestedCategoryId(), actual) ||
                actual == null
            ) {
                throw new IllegalStateException("Category suggestion is stale");
            }
        }
        if (command.isApplyTags()) {
            Set<Long> requested = command.getExpectedSuggestedTagIds();
            if (requested.isEmpty() || !preview.suggestedTagIds().containsAll(requested)) {
                throw new IllegalStateException("Tag suggestions are stale");
            }
        } else if (!command.getExpectedSuggestedTagIds().isEmpty()) {
            throw new IllegalArgumentException("Tag suggestion ids require applyTags");
        }
    }

    private TransactionBatchRulePreviewDTO preview(TransactionCandidate candidate, String login, RuleSets rules) {
        return toPreview(resolve(candidate, login, rules));
    }

    private ResolvedPreview resolve(TransactionCandidate candidate, String login, RuleSets rules) {
        DescriptionNormalizationRuleEvaluationResult description = descriptionResult(candidate, rules.descriptionRules());
        return resolveClassification(candidate, login, rules, description);
    }

    private ResolvedPreview resolveClassification(TransactionCandidate candidate, String login, RuleSets rules) {
        DescriptionNormalizationRuleEvaluationResult description = descriptionResult(candidate, rules.descriptionRules());
        return resolveClassification(candidate, login, rules, description);
    }

    private ResolvedPreview resolveClassification(
        TransactionCandidate candidate,
        String login,
        RuleSets rules,
        DescriptionNormalizationRuleEvaluationResult description
    ) {
        Map<Long, String> currentTagNames = candidate
            .getTags()
            .stream()
            .filter(tag -> tag.getId() != null)
            .collect(Collectors.toMap(Tag::getId, Tag::getName));
        TransactionRuleEvaluationResult classification = transactionRuleEvaluationService.evaluate(
            new TransactionRuleEvaluationInput(
                login,
                candidate.getDescription(),
                candidate.getAmount(),
                candidate.getFlow(),
                candidate.getExternalReference(),
                TransactionOrigin.FILE_IMPORT,
                candidate.getTransactionDate(),
                candidate.getPostingDate(),
                candidate.getAccount() == null ? null : candidate.getAccount().getId(),
                candidate.getCategory() == null ? null : candidate.getCategory().getId(),
                candidate.getCategory() == null ? null : candidate.getCategory().getName(),
                candidate.getTags().stream().map(Tag::getId).filter(Objects::nonNull).collect(Collectors.toSet()),
                currentTagNames
            ),
            rules.transactionRules()
        );
        List<Long> suggested = classification
            .suggestedTags()
            .stream()
            .filter(tag -> !tag.alreadyPresent() && !tag.duplicateOfEarlierSuggestion())
            .map(TagSuggestion::tagId)
            .filter(Objects::nonNull)
            .toList();
        List<Long> eligible = suggested
            .stream()
            .filter(id -> candidate.getTags().stream().noneMatch(tag -> Objects.equals(tag.getId(), id)))
            .filter(id -> candidate.getTagSuppressions().stream().noneMatch(suppression -> Objects.equals(suppression.getTag().getId(), id))
            )
            .toList();
        return new ResolvedPreview(candidate, description, classification, suggested, eligible, rules);
    }

    private DescriptionNormalizationRuleEvaluationResult descriptionResult(
        TransactionCandidate candidate,
        List<DescriptionNormalizationRule> descriptionRules
    ) {
        if (candidate.getSource() != TransactionCandidateSource.FILE_IMPORT || isBlank(candidate.getSourceDescription())) {
            return DescriptionNormalizationRuleEvaluationResult.noMatch();
        }
        return descriptionRuleEvaluationService.evaluate(candidate.getSourceDescription(), descriptionRules);
    }

    private TransactionBatchRulePreviewDTO toPreview(ResolvedPreview resolved) {
        TransactionCandidate candidate = resolved.candidate();
        TransactionBatchRulePreviewDTO view = new TransactionBatchRulePreviewDTO();
        view.setCandidateId(candidate.getId());
        view.setCandidateVersion(candidate.getVersion());
        view.setDescriptionEvaluable(
            candidate.getSource() == TransactionCandidateSource.FILE_IMPORT && !isBlank(candidate.getSourceDescription())
        );
        view.setDescriptionMatched(resolved.description().matched());
        view.setDescriptionRuleId(resolved.description().ruleId());
        view.setDescriptionRuleName(resolved.description().ruleName());
        view.setSuggestedDescription(resolved.description().resultingDescription());
        view.setClassificationEvaluable(candidate.getAccount() != null);
        if (candidate.getCategory() == null && resolved.classification().suggestedCategory() != null) {
            view.setSuggestedCategoryId(resolved.classification().suggestedCategory().categoryId());
            view.setSuggestedCategoryRuleId(resolved.classification().suggestedCategory().sourceRuleId());
            view.setSuggestedCategoryRuleName(resolved.classification().suggestedCategory().sourceRuleName());
        }
        view.setSuggestedTagIds(resolved.suggestedTagIds());
        view.setEligibleTagIds(resolved.eligibleTagIds());
        view.setEligibleTagSuggestions(
            resolved
                .classification()
                .suggestedTags()
                .stream()
                .filter(tag -> resolved.eligibleTagIds().contains(tag.tagId()))
                .map(tag -> {
                    TransactionBatchTagSuggestionDTO suggestion = new TransactionBatchTagSuggestionDTO();
                    suggestion.setTagId(tag.tagId());
                    suggestion.setTagName(tag.tagName());
                    suggestion.setRuleId(tag.sourceRuleId());
                    suggestion.setRuleName(tag.sourceRuleName());
                    return suggestion;
                })
                .toList()
        );
        return view;
    }

    private TransactionBatchRuleApplyResultDTO toApplyResult(
        TransactionCandidate candidate,
        TransactionBatch batch,
        AppliedChanges changes
    ) {
        TransactionBatchRuleApplyResultDTO result = new TransactionBatchRuleApplyResultDTO();
        result.setCandidateId(candidate.getId());
        result.setCandidateVersion(candidate.getVersion());
        result.setBatchVersion(batch.getVersion());
        result.setChanged(changes.changed());
        result.setDescriptionApplied(changes.descriptionApplied());
        result.setCategoryApplied(changes.categoryApplied());
        result.setTagIdsApplied(changes.tagIdsApplied());
        return result;
    }

    private RuleSets loadRuleSets(String login) {
        return new RuleSets(
            descriptionRuleEvaluationService.loadActiveRules(login),
            transactionRuleEvaluationService.loadActiveRules(login)
        );
    }

    private TransactionBatch requireOpenBatchForRead(Long batchId, String login) {
        if (batchId == null) throw new IllegalArgumentException("Transaction batch id is required");
        return batchRepository
            .findOneWithRelationshipsByIdAndUserLogin(batchId, login)
            .filter(batch -> batch.getStatus() == TransactionBatchStatus.OPEN)
            .orElseThrow(() -> new IllegalArgumentException("Open transaction batch is not accessible"));
    }

    private TransactionBatch lockOpenBatch(Long batchId, String login) {
        if (batchId == null) throw new IllegalArgumentException("Transaction batch id is required");
        return batchRepository
            .findOneForOwnedStatusMutation(batchId, login, TransactionBatchStatus.OPEN)
            .orElseThrow(() -> new IllegalArgumentException("Open transaction batch is not accessible"));
    }

    private TransactionCandidate lockBatchCandidate(TransactionBatch batch, Long candidateId, Long expectedVersion, String login) {
        if (candidateId == null) throw new IllegalArgumentException("Transaction candidate id is required");
        TransactionCandidate candidate = candidateRepository
            .findOneBatchCandidateForMutation(batch.getId(), candidateId, login)
            .orElseThrow(() -> new IllegalArgumentException("Transaction candidate is not part of the open batch"));
        if (expectedVersion == null || !Objects.equals(candidate.getVersion(), expectedVersion)) {
            throw new IllegalStateException("Transaction candidate version is stale");
        }
        return candidate;
    }

    private Category requireAccessibleCategory(Long categoryId) {
        return categoryRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(categoryId, currentUserService.getCurrentUserLogin())
            .orElseThrow(() -> new IllegalArgumentException("Category is not accessible"));
    }

    private Tag requireAccessibleTag(Long tagId) {
        return tagRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(tagId, currentUserService.getCurrentUserLogin())
            .orElseThrow(() -> new IllegalArgumentException("Tag is not accessible"));
    }

    private DescriptionNormalizationRule requireDescriptionRule(RuleSets rules, Long ruleId) {
        return rules
            .descriptionRules()
            .stream()
            .filter(rule -> Objects.equals(rule.getId(), ruleId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Applied description rule is no longer available"));
    }

    private TransactionRule requireTransactionRule(RuleSets rules, Long ruleId) {
        return rules
            .transactionRules()
            .stream()
            .filter(rule -> Objects.equals(rule.getId(), ruleId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Applied transaction rule is no longer available"));
    }

    private TagSuggestion suggestedTag(TransactionRuleEvaluationResult evaluation, Long tagId) {
        return evaluation
            .suggestedTags()
            .stream()
            .filter(suggestion -> Objects.equals(suggestion.tagId(), tagId))
            .filter(suggestion -> !suggestion.alreadyPresent() && !suggestion.duplicateOfEarlierSuggestion())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Applied tag suggestion is no longer available"));
    }

    private void touch(TransactionCandidate candidate) {
        candidate.setUpdatedAt(Instant.now());
    }

    private void touch(TransactionBatch batch) {
        batch.setUpdatedAt(Instant.now());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record RuleSets(List<DescriptionNormalizationRule> descriptionRules, List<TransactionRule> transactionRules) {}

    private record ResolvedPreview(
        TransactionCandidate candidate,
        DescriptionNormalizationRuleEvaluationResult description,
        TransactionRuleEvaluationResult classification,
        List<Long> suggestedTagIds,
        List<Long> eligibleTagIds,
        RuleSets rules
    ) {}

    private record AppliedChanges(boolean descriptionApplied, boolean categoryApplied, List<Long> tagIdsApplied) {
        private boolean changed() {
            return descriptionApplied || categoryApplied || !tagIdsApplied.isEmpty();
        }
    }

    private record PendingApplication(TransactionCandidate candidate, ResolvedPreview preview) {}
}
