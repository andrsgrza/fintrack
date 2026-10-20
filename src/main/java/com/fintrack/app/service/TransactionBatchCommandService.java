package com.fintrack.app.service;

import com.fintrack.app.domain.*;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.*;
import com.fintrack.app.service.dto.*;
import com.fintrack.app.service.transfer.InternalTransferCommandException;
import com.fintrack.app.service.transfer.InternalTransferCounterpartQueryService;
import com.fintrack.app.service.transfer.InternalTransferReadModelMapper;
import com.fintrack.app.service.validation.CategoryFlowCompatibilityValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal command boundary for an OPEN TransactionBatch workspace.
 *
 * No public resource calls this service in this slice. It owns batch membership, stable row allocation, manual
 * classification intent, and candidate-level optimistic concurrency. CSV append and rule evaluation have their
 * own internal services; publication remains outside this slice.
 */
@Service
@Transactional
public class TransactionBatchCommandService {

    private final TransactionBatchRepository transactionBatchRepository;
    private final TransactionCandidateRepository transactionCandidateRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final CurrentUserService currentUserService;
    private final InternalTransferCounterpartQueryService internalTransferCounterpartQueryService;

    public TransactionBatchCommandService(
        TransactionBatchRepository transactionBatchRepository,
        TransactionCandidateRepository transactionCandidateRepository,
        FinancialAccountRepository financialAccountRepository,
        CategoryRepository categoryRepository,
        TagRepository tagRepository,
        CurrentUserService currentUserService,
        InternalTransferCounterpartQueryService internalTransferCounterpartQueryService
    ) {
        this.transactionBatchRepository = transactionBatchRepository;
        this.transactionCandidateRepository = transactionCandidateRepository;
        this.financialAccountRepository = financialAccountRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.currentUserService = currentUserService;
        this.internalTransferCounterpartQueryService = internalTransferCounterpartQueryService;
    }

    /** Creates another owned OPEN workspace for an active accessible account. */
    public TransactionBatchWorkspaceDTO createOpenBatch(Long accountId) {
        FinancialAccount account = requireAccessibleActiveAccount(accountId);
        Instant now = Instant.now();
        TransactionBatch batch = new TransactionBatch();
        batch.setUser(currentUserService.getCurrentUser());
        batch.setAccount(account);
        batch.setStatus(TransactionBatchStatus.OPEN);
        batch.setNextRowPosition(1L);
        batch.setAutoApplyDescriptions(false);
        batch.setAutoApplyCategories(false);
        batch.setAutoApplyTags(false);
        batch.setProtectManualChanges(true);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        return toWorkspace(transactionBatchRepository.saveAndFlush(batch), List.of());
    }

    /** Reads an authorized OPEN workspace and its bounded current candidate projection without changing any version. */
    @Transactional(readOnly = true)
    public Optional<TransactionBatchWorkspaceDTO> findOpenBatch(Long batchId) {
        String login = currentUserService.getCurrentUserLogin();
        return transactionBatchRepository
            .findOneWithRelationshipsByIdAndUserLogin(batchId, login)
            .filter(batch -> batch.getStatus() == TransactionBatchStatus.OPEN)
            .map(batch -> toWorkspace(batch, transactionCandidateRepository.findAllBatchCandidatesWithRelationships(batch.getId(), login)));
    }

    /** Lists only workspaces owned by the caller; published receipts intentionally have their own narrow read path. */
    @Transactional(readOnly = true)
    public List<TransactionBatchWorkspaceDTO> findOpenBatches() {
        String login = currentUserService.getCurrentUserLogin();
        return transactionBatchRepository
            .findAllByUserLoginAndStatus(login, TransactionBatchStatus.OPEN)
            .stream()
            .map(batch -> toWorkspace(batch, transactionCandidateRepository.findAllBatchCandidatesWithRelationships(batch.getId(), login)))
            .toList();
    }

    /** Adds one incomplete manual row. Batch membership, account, canonical currency, and stable position are server-owned. */
    public TransactionBatchRowMutationDTO addManualDraft(Long batchId, TransactionBatchDraftCommand command) {
        TransactionBatch batch = lockOpenBatch(batchId);
        if (command == null) throw new IllegalArgumentException("Batch draft command is required");
        requireExpectedBatchVersion(batch, command.getExpectedBatchVersion());
        TransactionCandidate candidate = new TransactionCandidate();
        candidate.setTransactionBatch(batch);
        candidate.setRowPosition(reserveRowPosition(batch));
        candidate.setUser(batch.getUser());
        candidate.setAccount(batch.getAccount());
        candidate.setSource(TransactionCandidateSource.MANUAL);
        candidate.setStatus(TransactionCandidateStatus.DRAFT);
        candidate.setValidationStatus(TransactionCandidateValidationStatus.UNKNOWN);
        candidate.setDescriptionReviewStatus(TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED);
        candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.NOT_EVALUATED);
        candidate.setCategoryManualClear(false);
        candidate.setCurrencySnapshot(batch.getAccount().getCurrency());
        candidate.setCreatedAt(Instant.now());
        candidate.setUpdatedAt(candidate.getCreatedAt());
        applyDraftValues(candidate, command);
        normalizeAndDerive(candidate);
        applyInitialClassification(candidate, command);
        recalculateValidation(candidate);
        validateBatchCandidate(batch, candidate);
        candidate = transactionCandidateRepository.saveAndFlush(candidate);
        batch = transactionBatchRepository.saveAndFlush(batch);
        return rowMutation(candidate, batch);
    }

    /** Applies only explicitly supplied current-value fields; clearFields is the sole representation of a clear. */
    public TransactionBatchRowMutationDTO patchCandidate(Long batchId, Long candidateId, TransactionBatchCandidatePatchCommand command) {
        TransactionBatch batch = lockOpenBatch(batchId);
        TransactionCandidate candidate = lockBatchCandidate(batch, candidateId, command.getExpectedCandidateVersion());
        boolean changed = applyPatch(candidate, command);
        // Derive the final flow before validating a category chosen in the same product edit. This permits a user to
        // switch an expense to income and replace its category in one atomic request without a transient conflict.
        normalizeAndDerive(candidate);
        changed |= applyClassificationPatch(candidate, command);
        if (changed) {
            // A repaired monetary value is always expressed in the batch account's canonical currency.  CSV rows
            // with an explicit foreign/invalid currency deliberately start without a currency snapshot; accepting a
            // user-entered replacement amount must repair that absence rather than leave the row permanently invalid.
            if (command.getSignedAmount() != null) {
                candidate.setCurrencySnapshot(batch.getAccount().getCurrency());
            }
            resolveIssuesForAppliedPatch(candidate, command);
            recalculateValidation(candidate);
            validateBatchCandidate(batch, candidate);
            touch(candidate);
            candidate = transactionCandidateRepository.saveAndFlush(candidate);
            batch = persistMaterialBatchChange(batch);
        }
        return rowMutation(candidate, batch);
    }

    /** Sets an active owned category and records manual classification provenance. */
    public TransactionBatchRowMutationDTO setCategory(Long batchId, Long candidateId, Long expectedCandidateVersion, Long categoryId) {
        TransactionBatch batch = lockOpenBatch(batchId);
        TransactionCandidate candidate = lockBatchCandidate(batch, candidateId, expectedCandidateVersion);
        Category category = requireAccessibleCategory(categoryId);
        boolean sameCategory = candidate.getCategory() != null && Objects.equals(candidate.getCategory().getId(), category.getId());
        if (!sameCategory) CategoryReferenceValidator.validateActiveForNewReference(category);
        if (
            !sameCategory ||
            candidate.getCategorySource() != TransactionCandidateClassificationSource.MANUAL ||
            Boolean.TRUE.equals(candidate.getCategoryManualClear())
        ) {
            candidate.setCategory(category);
            candidate.setCategorySource(TransactionCandidateClassificationSource.MANUAL);
            candidate.setCategoryManualClear(false);
            candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
            validateBatchCandidate(batch, candidate);
            touch(candidate);
            candidate = transactionCandidateRepository.saveAndFlush(candidate);
            batch = persistMaterialBatchChange(batch);
        }
        return rowMutation(candidate, batch);
    }

    /** Clears the current category and retains the negative manual intent required by later automatic evaluation. */
    public TransactionBatchRowMutationDTO clearCategory(Long batchId, Long candidateId, Long expectedCandidateVersion) {
        TransactionBatch batch = lockOpenBatch(batchId);
        TransactionCandidate candidate = lockBatchCandidate(batch, candidateId, expectedCandidateVersion);
        if (candidate.getCategory() != null || !Boolean.TRUE.equals(candidate.getCategoryManualClear())) {
            candidate.setCategory(null);
            candidate.setCategorySource(null);
            candidate.setCategoryManualClear(true);
            candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
            touch(candidate);
            candidate = transactionCandidateRepository.saveAndFlush(candidate);
            batch = persistMaterialBatchChange(batch);
        }
        return rowMutation(candidate, batch);
    }

    /** Manually adds a tag and atomically removes a prior suppression for the same tag. */
    public TransactionBatchRowMutationDTO addTag(Long batchId, Long candidateId, Long expectedCandidateVersion, Long tagId) {
        TransactionBatch batch = lockOpenBatch(batchId);
        TransactionCandidate candidate = lockBatchCandidate(batch, candidateId, expectedCandidateVersion);
        Tag tag = requireAccessibleTag(tagId);
        boolean present = candidate.getTags().stream().anyMatch(current -> Objects.equals(current.getId(), tag.getId()));
        if (!present) TagReferenceValidator.validateActiveForNewReference(tag);
        boolean suppression = candidate.isTagSuppressed(tag);
        if (!present || candidate.getTagSource(tag) != TransactionCandidateClassificationSource.MANUAL || suppression) {
            candidate.addTag(tag, TransactionCandidateClassificationSource.MANUAL);
            candidate.removeTagSuppression(tag);
            candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
            touch(candidate);
            candidate = transactionCandidateRepository.saveAndFlush(candidate);
            batch = persistMaterialBatchChange(batch);
        }
        return rowMutation(candidate, batch);
    }

    /** Removes a current tag, if present, and retains one deduplicated suppression. */
    public TransactionBatchRowMutationDTO removeTag(Long batchId, Long candidateId, Long expectedCandidateVersion, Long tagId) {
        TransactionBatch batch = lockOpenBatch(batchId);
        TransactionCandidate candidate = lockBatchCandidate(batch, candidateId, expectedCandidateVersion);
        Tag tag = requireAccessibleTag(tagId);
        boolean present = candidate.getTags().stream().anyMatch(current -> Objects.equals(current.getId(), tag.getId()));
        boolean suppression = candidate.isTagSuppressed(tag);
        if (!present && !suppression) TagReferenceValidator.validateActiveForNewReference(tag);
        if (present || !suppression) {
            candidate.removeTags(tag);
            candidate.suppressTag(tag);
            candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
            touch(candidate);
            candidate = transactionCandidateRepository.saveAndFlush(candidate);
            batch = persistMaterialBatchChange(batch);
        }
        return rowMutation(candidate, batch);
    }

    /** Replaces the visible tag selection while retaining manual provenance and suppression for explicit removals. */
    public TransactionBatchRowMutationDTO replaceTags(Long batchId, Long candidateId, Long expectedCandidateVersion, Set<Long> tagIds) {
        TransactionBatch batch = lockOpenBatch(batchId);
        TransactionCandidate candidate = lockBatchCandidate(batch, candidateId, expectedCandidateVersion);
        Set<Long> selectedIds = tagIds == null ? Set.of() : new LinkedHashSet<>(tagIds);
        Set<Tag> selectedTags = new LinkedHashSet<>();
        for (Long tagId : selectedIds) selectedTags.add(requireAccessibleTag(tagId));

        Set<Tag> currentlySelected = candidate.getTags();
        boolean changed = false;
        for (Tag existing : currentlySelected) {
            if (!selectedIds.contains(existing.getId())) {
                candidate.removeTags(existing);
                candidate.suppressTag(existing);
                changed = true;
            }
        }
        for (Tag selected : selectedTags) {
            boolean present = currentlySelected.stream().anyMatch(existing -> Objects.equals(existing.getId(), selected.getId()));
            if (!present) TagReferenceValidator.validateActiveForNewReference(selected);
            if (
                !present ||
                candidate.getTagSource(selected) != TransactionCandidateClassificationSource.MANUAL ||
                candidate.isTagSuppressed(selected)
            ) {
                candidate.addTag(selected, TransactionCandidateClassificationSource.MANUAL);
                candidate.removeTagSuppression(selected);
                changed = true;
            }
        }
        if (changed) {
            candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
            touch(candidate);
            candidate = transactionCandidateRepository.saveAndFlush(candidate);
            batch = persistMaterialBatchChange(batch);
        }
        return rowMutation(candidate, batch);
    }

    /**
     * Persists only the intent to link this temporary row to a published
     * opposite leg. It deliberately does not create an InternalTransfer yet.
     */
    public TransactionBatchRowMutationDTO setInternalTransfer(
        Long batchId,
        Long candidateId,
        TransactionBatchInternalTransferCommand command
    ) {
        if (command == null) throw new IllegalArgumentException("Batch transfer command is required");
        TransactionBatch batch = lockOpenBatch(batchId);
        TransactionCandidate candidate = lockBatchCandidate(batch, candidateId, command.getExpectedCandidateVersion());
        FinancialTransaction counterpart = internalTransferCounterpartQueryService.requireEligibleCounterpartForCandidate(
            candidate,
            command.getCounterpartTransactionId()
        );
        boolean unchanged =
            candidate.getInternalTransferEnabled() &&
            candidate.getInternalTransferMode() == TransactionCandidateInternalTransferMode.LINK_EXISTING &&
            candidate.getInternalTransferCounterpartTransaction() != null &&
            Objects.equals(candidate.getInternalTransferCounterpartTransaction().getId(), counterpart.getId());
        if (!unchanged) {
            candidate.clearInternalTransferIntent();
            candidate.setInternalTransferEnabled(true);
            candidate.setInternalTransferMode(TransactionCandidateInternalTransferMode.LINK_EXISTING);
            candidate.setInternalTransferCounterpartTransaction(counterpart);
            // Batch V1 always links an already-published leg. No duplicate
            // counterpart account/data or create-counterpart mode is retained.
            touch(candidate);
            candidate = transactionCandidateRepository.saveAndFlush(candidate);
            batch = persistMaterialBatchChange(batch);
        }
        return rowMutation(candidate, batch);
    }

    /** Clears transfer-only draft state while preserving the row and its classification untouched. */
    public TransactionBatchRowMutationDTO clearInternalTransfer(Long batchId, Long candidateId, Long expectedCandidateVersion) {
        TransactionBatch batch = lockOpenBatch(batchId);
        TransactionCandidate candidate = lockBatchCandidate(batch, candidateId, expectedCandidateVersion);
        if (candidate.getInternalTransferEnabled() || candidate.getInternalTransferCounterpartTransaction() != null) {
            candidate.setInternalTransferEnabled(false);
            touch(candidate);
            candidate = transactionCandidateRepository.saveAndFlush(candidate);
            batch = persistMaterialBatchChange(batch);
        }
        return rowMutation(candidate, batch);
    }

    /** Owner-scoped, paginated lookup for a candidate that remains pre-post. */
    @Transactional(readOnly = true)
    public Page<InternalTransferCounterpartDTO> findEligibleTransferCounterparts(
        Long batchId,
        Long candidateId,
        String search,
        Pageable pageable
    ) {
        return findEligibleTransferCounterparts(batchId, candidateId, search, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<InternalTransferCounterpartDTO> findEligibleTransferCounterparts(
        Long batchId,
        Long candidateId,
        String search,
        Long accountId,
        Pageable pageable
    ) {
        TransactionBatch batch = transactionBatchRepository
            .findOneWithRelationshipsByIdAndUserLogin(batchId, currentUserService.getCurrentUserLogin())
            .filter(current -> current.getStatus() == TransactionBatchStatus.OPEN)
            .orElseThrow(() -> new IllegalArgumentException("Transaction batch is not accessible"));
        TransactionCandidate candidate = transactionCandidateRepository
            .findOneBatchCandidateWithRelationships(batch.getId(), candidateId, currentUserService.getCurrentUserLogin())
            .orElseThrow(() -> new IllegalArgumentException("Transaction candidate is not accessible"));
        return internalTransferCounterpartQueryService.findEligibleCounterpartsForCandidate(candidate, search, accountId, pageable);
    }

    /** Internal future-parser hook for bounded repair evidence; not a CSV parser or public API. */
    public TransactionBatchRowMutationDTO recordInputIssue(
        Long batchId,
        Long candidateId,
        Long expectedCandidateVersion,
        TransactionCandidateInputIssueField field,
        String submittedValue,
        TransactionCandidateInputIssueReason reasonCode
    ) {
        TransactionBatch batch = lockOpenBatch(batchId);
        TransactionCandidate candidate = lockBatchCandidate(batch, candidateId, expectedCandidateVersion);
        if (
            field == null ||
            reasonCode == null ||
            submittedValue == null ||
            submittedValue.isBlank() ||
            submittedValue.trim().length() > 100
        ) {
            throw new IllegalArgumentException("Input issue must contain a bounded field, reason, and submitted value");
        }
        String normalizedValue = submittedValue.trim();
        boolean sameIssue = candidate
            .getInputIssues()
            .stream()
            .anyMatch(
                existing ->
                    existing.getField() == field &&
                    existing.getReasonCode() == reasonCode &&
                    Objects.equals(existing.getSubmittedValue(), normalizedValue)
            );
        if (!sameIssue || candidate.getValidationStatus() != TransactionCandidateValidationStatus.INVALID) {
            TransactionCandidateInputIssue issue = new TransactionCandidateInputIssue();
            issue.setField(field);
            issue.setReasonCode(reasonCode);
            issue.setSubmittedValue(normalizedValue);
            candidate.addOrReplaceInputIssue(issue);
            candidate.setValidationStatus(TransactionCandidateValidationStatus.INVALID);
            touch(candidate);
            candidate = transactionCandidateRepository.saveAndFlush(candidate);
            batch = persistMaterialBatchChange(batch);
        }
        return rowMutation(candidate, batch);
    }

    /** Physically removes one owned batch row and its candidate-owned tags, suppressions, and issues. */
    public Long deleteCandidate(Long batchId, Long candidateId, Long expectedCandidateVersion) {
        TransactionBatch batch = lockOpenBatch(batchId);
        TransactionCandidate candidate = lockBatchCandidate(batch, candidateId, expectedCandidateVersion);
        transactionCandidateRepository.delete(candidate);
        return persistMaterialBatchChange(batch).getVersion();
    }

    /** Discards only an OPEN workspace. It cannot create CANCELLED candidates and cannot affect live catalog/ledger data. */
    public boolean discardOpenBatch(Long batchId, Long expectedBatchVersion) {
        TransactionBatch batch = lockOpenBatch(batchId);
        requireExpectedBatchVersion(batch, expectedBatchVersion);
        // The older candidate-tag join table does not cascade from candidate, so remove it before bulk candidate deletion.
        transactionCandidateRepository.deleteTagLinksByTransactionBatchId(batch.getId());
        transactionCandidateRepository.deleteByTransactionBatchId(batch.getId());
        transactionBatchRepository.delete(batch);
        return true;
    }

    private TransactionBatch lockOpenBatch(Long batchId) {
        if (batchId == null) throw new IllegalArgumentException("Transaction batch id is required");
        return transactionBatchRepository
            .findOneForOwnedStatusMutation(batchId, currentUserService.getCurrentUserLogin(), TransactionBatchStatus.OPEN)
            .orElseThrow(() -> new IllegalArgumentException("Open transaction batch is not accessible"));
    }

    private TransactionCandidate lockBatchCandidate(TransactionBatch batch, Long candidateId, Long expectedVersion) {
        if (candidateId == null) throw new IllegalArgumentException("Transaction candidate id is required");
        TransactionCandidate candidate = transactionCandidateRepository
            .findOneBatchCandidateForMutation(batch.getId(), candidateId, currentUserService.getCurrentUserLogin())
            .orElseThrow(() -> new IllegalArgumentException("Transaction candidate is not part of the open batch"));
        requireExpectedCandidateVersion(candidate, expectedVersion);
        validateBatchCandidate(batch, candidate);
        return candidate;
    }

    private FinancialAccount requireAccessibleActiveAccount(Long accountId) {
        if (accountId == null) throw new IllegalArgumentException("Financial account id is required");
        FinancialAccount account = financialAccountRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(accountId, currentUserService.getCurrentUserLogin())
            .orElseThrow(() -> new IllegalArgumentException("Financial account is not accessible"));
        FinancialAccountReferenceValidator.validateActiveForNewReference(account);
        return account;
    }

    private Category requireAccessibleCategory(Long categoryId) {
        if (categoryId == null) throw new IllegalArgumentException("Category id is required");
        return categoryRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(categoryId, currentUserService.getCurrentUserLogin())
            .orElseThrow(() -> new IllegalArgumentException("Category is not accessible"));
    }

    private Tag requireAccessibleTag(Long tagId) {
        if (tagId == null) throw new IllegalArgumentException("Tag id is required");
        return tagRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(tagId, currentUserService.getCurrentUserLogin())
            .orElseThrow(() -> new IllegalArgumentException("Tag is not accessible"));
    }

    private long reserveRowPosition(TransactionBatch batch) {
        long rowPosition = batch.getNextRowPosition();
        batch.setNextRowPosition(Math.addExact(rowPosition, 1));
        touch(batch);
        return rowPosition;
    }

    private void applyDraftValues(TransactionCandidate candidate, TransactionBatchDraftCommand command) {
        if (command == null) return;
        candidate.setTransactionDate(command.getTransactionDate());
        candidate.setPostingDate(command.getPostingDate());
        candidate.setDescription(command.getDescription());
        candidate.setDescriptionSource(
            command.getDescription() == null || command.getDescription().isBlank() ? null : TransactionCandidateDescriptionSource.MANUAL
        );
        candidate.setDescriptionReviewStatus(
            candidate.getDescriptionSource() == TransactionCandidateDescriptionSource.MANUAL
                ? TransactionCandidateDescriptionReviewStatus.USER_EDITED
                : TransactionCandidateDescriptionReviewStatus.NOT_EVALUATED
        );
        candidate.setSignedAmount(command.getSignedAmount());
        candidate.setExternalReference(command.getExternalReference());
        candidate.setNotes(command.getNotes());
    }

    /** Applies the initial manual category/tags before the first candidate save, avoiding an accidental empty row. */
    private void applyInitialClassification(TransactionCandidate candidate, TransactionBatchDraftCommand command) {
        if (command.getCategoryId() != null) {
            Category category = requireAccessibleCategory(command.getCategoryId());
            CategoryReferenceValidator.validateActiveForNewReference(category);
            candidate.setCategory(category);
            candidate.setCategorySource(TransactionCandidateClassificationSource.MANUAL);
            candidate.setCategoryManualClear(false);
            candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        }
        for (Long tagId : command.getTagIds()) {
            Tag tag = requireAccessibleTag(tagId);
            TagReferenceValidator.validateActiveForNewReference(tag);
            candidate.addTag(tag, TransactionCandidateClassificationSource.MANUAL);
        }
        if (!command.getTagIds().isEmpty()) {
            candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        }
    }

    private boolean applyPatch(TransactionCandidate candidate, TransactionBatchCandidatePatchCommand command) {
        if (command == null) throw new IllegalArgumentException("Candidate patch is required");
        Set<TransactionBatchCandidatePatchField> clears = command.getClearFields();
        rejectConflictingPatchValues(command, clears);
        boolean changed = false;
        if (clears.contains(TransactionBatchCandidatePatchField.TRANSACTION_DATE)) changed |= setIfDifferent(
            candidate::getTransactionDate,
            candidate::setTransactionDate,
            null
        );
        else if (command.getTransactionDate() != null) changed |= setIfDifferent(
            candidate::getTransactionDate,
            candidate::setTransactionDate,
            command.getTransactionDate()
        );
        if (clears.contains(TransactionBatchCandidatePatchField.POSTING_DATE)) changed |= setIfDifferent(
            candidate::getPostingDate,
            candidate::setPostingDate,
            null
        );
        else if (command.getPostingDate() != null) changed |= setIfDifferent(
            candidate::getPostingDate,
            candidate::setPostingDate,
            command.getPostingDate()
        );
        if (clears.contains(TransactionBatchCandidatePatchField.DESCRIPTION)) {
            changed |= setIfDifferent(candidate::getDescription, candidate::setDescription, null);
            changed |= candidate.getDescriptionSource() != TransactionCandidateDescriptionSource.MANUAL;
            changed |= candidate.getDescriptionReviewStatus() != TransactionCandidateDescriptionReviewStatus.USER_EDITED;
            candidate.setDescriptionSource(TransactionCandidateDescriptionSource.MANUAL);
            candidate.setDescriptionReviewStatus(TransactionCandidateDescriptionReviewStatus.USER_EDITED);
        } else if (command.getDescription() != null) {
            changed |= setIfDifferent(candidate::getDescription, candidate::setDescription, command.getDescription());
            changed |= candidate.getDescriptionSource() != TransactionCandidateDescriptionSource.MANUAL;
            changed |= candidate.getDescriptionReviewStatus() != TransactionCandidateDescriptionReviewStatus.USER_EDITED;
            candidate.setDescriptionSource(TransactionCandidateDescriptionSource.MANUAL);
            candidate.setDescriptionReviewStatus(TransactionCandidateDescriptionReviewStatus.USER_EDITED);
        }
        if (clears.contains(TransactionBatchCandidatePatchField.SIGNED_AMOUNT)) changed |= setIfDifferent(
            candidate::getSignedAmount,
            candidate::setSignedAmount,
            null
        );
        else if (command.getSignedAmount() != null) changed |= setIfDifferent(
            candidate::getSignedAmount,
            candidate::setSignedAmount,
            command.getSignedAmount()
        );
        if (clears.contains(TransactionBatchCandidatePatchField.EXTERNAL_REFERENCE)) changed |= setIfDifferent(
            candidate::getExternalReference,
            candidate::setExternalReference,
            null
        );
        else if (command.getExternalReference() != null) changed |= setIfDifferent(
            candidate::getExternalReference,
            candidate::setExternalReference,
            command.getExternalReference()
        );
        if (clears.contains(TransactionBatchCandidatePatchField.NOTES)) changed |= setIfDifferent(
            candidate::getNotes,
            candidate::setNotes,
            null
        );
        else if (command.getNotes() != null) changed |= setIfDifferent(candidate::getNotes, candidate::setNotes, command.getNotes());
        return changed;
    }

    /** Applies category/tag controls from the editor in the same transaction as the core fields. */
    private boolean applyClassificationPatch(TransactionCandidate candidate, TransactionBatchCandidatePatchCommand command) {
        boolean changed = false;
        if (Boolean.TRUE.equals(command.getCategoryChanged())) {
            if (command.getCategoryId() == null) {
                changed |= candidate.getCategory() != null || !Boolean.TRUE.equals(candidate.getCategoryManualClear());
                candidate.setCategory(null);
                candidate.setCategorySource(null);
                candidate.setCategoryManualClear(true);
            } else {
                Category category = requireAccessibleCategory(command.getCategoryId());
                boolean sameCategory = candidate.getCategory() != null && Objects.equals(candidate.getCategory().getId(), category.getId());
                if (!sameCategory) CategoryReferenceValidator.validateActiveForNewReference(category);
                changed |=
                    !sameCategory ||
                    candidate.getCategorySource() != TransactionCandidateClassificationSource.MANUAL ||
                    Boolean.TRUE.equals(candidate.getCategoryManualClear());
                candidate.setCategory(category);
                candidate.setCategorySource(TransactionCandidateClassificationSource.MANUAL);
                candidate.setCategoryManualClear(false);
            }
            candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        }
        if (Boolean.TRUE.equals(command.getTagsChanged())) {
            Set<Long> selectedIds = new LinkedHashSet<>(command.getTagIds());
            Set<Tag> selectedTags = new LinkedHashSet<>();
            for (Long tagId : selectedIds) selectedTags.add(requireAccessibleTag(tagId));
            Set<Tag> currentTags = new LinkedHashSet<>(candidate.getTags());
            for (Tag existing : currentTags) {
                if (!selectedIds.contains(existing.getId())) {
                    candidate.removeTags(existing);
                    candidate.suppressTag(existing);
                    changed = true;
                }
            }
            for (Tag selected : selectedTags) {
                boolean present = currentTags.stream().anyMatch(existing -> Objects.equals(existing.getId(), selected.getId()));
                if (!present) TagReferenceValidator.validateActiveForNewReference(selected);
                if (
                    !present ||
                    candidate.getTagSource(selected) != TransactionCandidateClassificationSource.MANUAL ||
                    candidate.isTagSuppressed(selected)
                ) {
                    candidate.addTag(selected, TransactionCandidateClassificationSource.MANUAL);
                    candidate.removeTagSuppression(selected);
                    changed = true;
                }
            }
            if (changed) candidate.setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
        }
        return changed;
    }

    private void rejectConflictingPatchValues(
        TransactionBatchCandidatePatchCommand command,
        Set<TransactionBatchCandidatePatchField> clears
    ) {
        if (
            (clears.contains(TransactionBatchCandidatePatchField.TRANSACTION_DATE) && command.getTransactionDate() != null) ||
            (clears.contains(TransactionBatchCandidatePatchField.POSTING_DATE) && command.getPostingDate() != null) ||
            (clears.contains(TransactionBatchCandidatePatchField.DESCRIPTION) && command.getDescription() != null) ||
            (clears.contains(TransactionBatchCandidatePatchField.SIGNED_AMOUNT) && command.getSignedAmount() != null) ||
            (clears.contains(TransactionBatchCandidatePatchField.EXTERNAL_REFERENCE) && command.getExternalReference() != null) ||
            (clears.contains(TransactionBatchCandidatePatchField.NOTES) && command.getNotes() != null)
        ) throw new IllegalArgumentException("A batch candidate field cannot be set and cleared in the same patch");
    }

    private <T> boolean setIfDifferent(java.util.function.Supplier<T> getter, java.util.function.Consumer<T> setter, T value) {
        if (Objects.equals(getter.get(), value)) return false;
        setter.accept(value);
        return true;
    }

    private void normalizeAndDerive(TransactionCandidate candidate) {
        candidate.setDescription(normalizeText(candidate.getDescription(), 500, "Description"));
        candidate.setExternalReference(normalizeText(candidate.getExternalReference(), 150, "External reference"));
        candidate.setNotes(normalizeText(candidate.getNotes(), 1000, "Notes"));
        if (candidate.getSignedAmount() == null) {
            candidate.setAmount(null);
            candidate.setFlow(null);
            return;
        }
        BigDecimal signedAmount;
        try {
            signedAmount = candidate.getSignedAmount().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Signed amount must have at most 2 decimal places", exception);
        }
        candidate.setSignedAmount(signedAmount);
        candidate.setAmount(signedAmount.abs());
        candidate.setFlow(signedAmount.signum() > 0 ? TransactionFlow.IN : signedAmount.signum() < 0 ? TransactionFlow.OUT : null);
    }

    private String normalizeText(String value, int maximumLength, String field) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isBlank()) return null;
        if (normalized.length() > maximumLength) throw new IllegalArgumentException(
            field + " cannot exceed " + maximumLength + " characters"
        );
        return normalized;
    }

    private void resolveIssuesForAppliedPatch(TransactionCandidate candidate, TransactionBatchCandidatePatchCommand command) {
        if (command.getTransactionDate() != null) candidate.removeInputIssue(TransactionCandidateInputIssueField.TRANSACTION_DATE);
        if (command.getPostingDate() != null) candidate.removeInputIssue(TransactionCandidateInputIssueField.POSTING_DATE);
        if (command.getSignedAmount() != null) {
            candidate.removeInputIssue(TransactionCandidateInputIssueField.AMOUNT);
            // A new authoritative canonical amount repairs the parser's currency token without inferring conversion.
            candidate.removeInputIssue(TransactionCandidateInputIssueField.CURRENCY);
        }
    }

    private void recalculateValidation(TransactionCandidate candidate) {
        if (!candidate.getInputIssues().isEmpty()) {
            candidate.setValidationStatus(TransactionCandidateValidationStatus.INVALID);
            return;
        }
        boolean complete =
            candidate.getAccount() != null &&
            candidate.getCurrencySnapshot() == candidate.getAccount().getCurrency() &&
            candidate.getTransactionDate() != null &&
            candidate.getDescription() != null &&
            candidate.getAmount() != null &&
            candidate.getAmount().signum() > 0 &&
            candidate.getFlow() != null;
        candidate.setValidationStatus(complete ? TransactionCandidateValidationStatus.VALID : TransactionCandidateValidationStatus.UNKNOWN);
    }

    private void validateBatchCandidate(TransactionBatch batch, TransactionCandidate candidate) {
        if (
            candidate.getTransactionBatch() == null ||
            !Objects.equals(candidate.getTransactionBatch().getId(), batch.getId()) ||
            candidate.getRowPosition() == null ||
            candidate.getAccount() == null ||
            batch.getAccount() == null ||
            !Objects.equals(candidate.getAccount().getId(), batch.getAccount().getId()) ||
            candidate.getUser() == null ||
            batch.getUser() == null ||
            !Objects.equals(candidate.getUser().getId(), batch.getUser().getId())
        ) throw new IllegalArgumentException("Transaction candidate must remain in its batch account and owner scope");
        boolean hasCurrencyIssue = candidate
            .getInputIssues()
            .stream()
            .anyMatch(issue -> issue.getField() == TransactionCandidateInputIssueField.CURRENCY);
        if (!hasCurrencyIssue && candidate.getCurrencySnapshot() != batch.getAccount().getCurrency()) {
            throw new IllegalArgumentException("Candidate currency must match the batch account currency");
        }
        if (candidate.getCategory() != null) {
            if (!Objects.equals(candidate.getCategory().getUser().getId(), candidate.getUser().getId())) {
                throw new IllegalArgumentException("Category must belong to the batch owner");
            }
            if (
                candidate.getFlow() != null &&
                !CategoryFlowCompatibilityValidator.isCompatible(candidate.getCategory().getCategoryType(), candidate.getFlow())
            ) {
                throw new IllegalArgumentException("Category is incompatible with the transaction flow");
            }
        }
        for (Tag tag : candidate.getTags()) {
            if (!Objects.equals(tag.getUser().getId(), candidate.getUser().getId())) {
                throw new IllegalArgumentException("Tags must belong to the batch owner");
            }
            if (candidate.isTagSuppressed(tag)) throw new IllegalArgumentException("A candidate tag cannot also be suppressed");
        }
        for (TransactionCandidateTagSuppression suppression : candidate.getTagSuppressions()) {
            if (!Objects.equals(suppression.getTag().getUser().getId(), candidate.getUser().getId())) {
                throw new IllegalArgumentException("Suppressed tags must belong to the batch owner");
            }
        }
    }

    private void requireExpectedCandidateVersion(TransactionCandidate candidate, Long expectedVersion) {
        if (expectedVersion == null || !Objects.equals(candidate.getVersion(), expectedVersion)) {
            throw new IllegalStateException("Transaction candidate version is stale");
        }
    }

    private void requireExpectedBatchVersion(TransactionBatch batch, Long expectedVersion) {
        if (expectedVersion == null || !Objects.equals(batch.getVersion(), expectedVersion)) {
            throw new IllegalStateException("Transaction batch version is stale");
        }
    }

    private void touch(TransactionCandidate candidate) {
        candidate.setUpdatedAt(Instant.now());
    }

    private void touch(TransactionBatch batch) {
        batch.setUpdatedAt(Instant.now());
    }

    /** A row-content change is material to a future publish, but row commands do not require this global revision. */
    private TransactionBatch persistMaterialBatchChange(TransactionBatch batch) {
        touch(batch);
        return transactionBatchRepository.saveAndFlush(batch);
    }

    private TransactionBatchRowMutationDTO rowMutation(TransactionCandidate candidate, TransactionBatch batch) {
        TransactionBatchRowMutationDTO result = new TransactionBatchRowMutationDTO();
        result.setCandidate(toCandidateView(candidate));
        result.setBatchVersion(batch.getVersion());
        return result;
    }

    private TransactionBatchWorkspaceDTO toWorkspace(TransactionBatch batch, List<TransactionCandidate> candidates) {
        TransactionBatchWorkspaceDTO view = new TransactionBatchWorkspaceDTO();
        view.setId(batch.getId());
        view.setVersion(batch.getVersion());
        view.setStatus(batch.getStatus());
        if (batch.getAccount() != null) {
            view.setAccountId(batch.getAccount().getId());
            view.setAccountName(batch.getAccount().getName());
            view.setAccountCurrency(batch.getAccount().getCurrency());
        }
        view.setAutoApplyDescriptions(batch.getAutoApplyDescriptions());
        view.setAutoApplyCategories(batch.getAutoApplyCategories());
        view.setAutoApplyTags(batch.getAutoApplyTags());
        view.setProtectManualChanges(batch.getProtectManualChanges());
        view.setUpdatedAt(batch.getUpdatedAt());
        view.setCandidates(candidates.stream().map(this::toCandidateView).toList());
        return view;
    }

    private TransactionBatchCandidateViewDTO toCandidateView(TransactionCandidate candidate) {
        TransactionBatchCandidateViewDTO view = new TransactionBatchCandidateViewDTO();
        view.setId(candidate.getId());
        view.setVersion(candidate.getVersion());
        view.setRowPosition(candidate.getRowPosition());
        view.setSource(candidate.getSource());
        view.setStatus(candidate.getStatus());
        view.setValidationStatus(candidate.getValidationStatus());
        view.setTransactionDate(candidate.getTransactionDate());
        view.setPostingDate(candidate.getPostingDate());
        view.setDescription(candidate.getDescription());
        // A manual row has no immutable import baseline. Do not fabricate one from its editable current description.
        view.setSourceDescription(
            candidate.getSource() == TransactionCandidateSource.FILE_IMPORT ? candidate.getSourceDescription() : null
        );
        view.setDescriptionSource(candidate.getDescriptionSource());
        view.setDescriptionAppliedByRule(toAppliedRule(candidate.getDescriptionAppliedRule(), candidate.getDescriptionAppliedRuleName()));
        view.setSignedAmount(candidate.getSignedAmount());
        view.setAmount(candidate.getAmount());
        view.setFlow(candidate.getFlow());
        view.setCurrencySnapshot(candidate.getCurrencySnapshot());
        view.setExternalReference(candidate.getExternalReference());
        view.setNotes(candidate.getNotes());
        view.setCategorySource(candidate.getCategorySource());
        view.setCategoryAppliedByRule(toAppliedRule(candidate.getCategoryAppliedRule(), candidate.getCategoryAppliedRuleName()));
        view.setCategoryManualClear(candidate.getCategoryManualClear());
        if (candidate.getCategory() != null) {
            view.setCategoryId(candidate.getCategory().getId());
            view.setCategoryName(candidate.getCategory().getName());
            view.setCategoryColor(candidate.getCategory().getColor());
            view.setCategoryActive(candidate.getCategory().getActive());
        }
        view.setTags(
            candidate
                .getTagAssociations()
                .stream()
                .sorted(Comparator.comparing(association -> association.getTag().getId()))
                .map(association -> {
                    Tag tag = association.getTag();
                    TransactionBatchTagViewDTO tagView = new TransactionBatchTagViewDTO();
                    tagView.setId(tag.getId());
                    tagView.setName(tag.getName());
                    tagView.setColor(tag.getColor());
                    tagView.setActive(tag.getActive());
                    tagView.setSource(association.getSource());
                    tagView.setAppliedByRule(toAppliedRule(association.getAppliedRule(), association.getAppliedRuleName()));
                    return tagView;
                })
                .toList()
        );
        view.setSuppressedTagIds(
            candidate.getTagSuppressions().stream().map(suppression -> suppression.getTag().getId()).sorted().toList()
        );
        view.setInputIssues(
            candidate
                .getInputIssues()
                .stream()
                .sorted(Comparator.comparing(TransactionCandidateInputIssue::getField))
                .map(issue -> {
                    TransactionBatchInputIssueViewDTO issueView = new TransactionBatchInputIssueViewDTO();
                    issueView.setField(issue.getField());
                    issueView.setReasonCode(issue.getReasonCode());
                    issueView.setSubmittedValue(issue.getSubmittedValue());
                    return issueView;
                })
                .toList()
        );
        view.setInternalTransfer(toTransferIntentView(candidate));
        return view;
    }

    private TransactionBatchInternalTransferViewDTO toTransferIntentView(TransactionCandidate candidate) {
        TransactionBatchInternalTransferViewDTO result = new TransactionBatchInternalTransferViewDTO();
        result.setEnabled(candidate.getInternalTransferEnabled());
        if (!candidate.getInternalTransferEnabled()) return result;
        if (
            candidate.getInternalTransferMode() != TransactionCandidateInternalTransferMode.LINK_EXISTING ||
            candidate.getInternalTransferCounterpartTransaction() == null ||
            candidate.getInternalTransferCounterpartTransaction().getId() == null
        ) {
            result.setStale(true);
            return result;
        }
        try {
            FinancialTransaction counterpart = internalTransferCounterpartQueryService.requireEligibleCounterpartForCandidate(
                candidate,
                candidate.getInternalTransferCounterpartTransaction().getId()
            );
            result.setCounterpart(InternalTransferReadModelMapper.toCounterpart(counterpart));
        } catch (InternalTransferCommandException exception) {
            result.setStale(true);
            // A deleted or now-ineligible counterpart is intentionally not
            // silently cleared: the user must correct the row explicitly.
        }
        return result;
    }

    private AppliedRuleAttributionDTO toAppliedRule(Object liveRule, String snapshotName) {
        if (liveRule == null && (snapshotName == null || snapshotName.isBlank())) {
            return null;
        }
        AppliedRuleAttributionDTO attribution = new AppliedRuleAttributionDTO();
        if (liveRule instanceof TransactionRule transactionRule) {
            attribution.setId(transactionRule.getId());
        } else if (liveRule instanceof DescriptionNormalizationRule descriptionRule) {
            attribution.setId(descriptionRule.getId());
        }
        attribution.setName(snapshotName);
        return attribution;
    }
}
