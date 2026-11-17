package com.fintrack.app.service.budget;

import com.fintrack.app.domain.*;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.*;
import com.fintrack.app.service.CurrentUserService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Budget-2 write boundary. It appends immutable revision/lifecycle history and touches the aggregate so the
 * Budget @Version remains the sole concurrency authority for commands that affect independently stored rows.
 */
@Service
@Transactional
public class BudgetCommandService {

    private static final String SCHEDULE_REVISION = "SCHEDULE_REVISION";
    private static final String REPLACE_REVISION = "REPLACE_REVISION";
    private static final String CANCEL_REVISION = "CANCEL_REVISION";
    private static final String PAUSE = "PAUSE";
    private static final String CANCEL_PAUSE = "CANCEL_PAUSE";
    private static final String RESUME = "RESUME";
    private static final String FINISH = "FINISH";

    private final BudgetRepository budgetRepository;
    private final BudgetRevisionRepository revisionRepository;
    private final BudgetRevisionScheduleRepository scheduleRepository;
    private final BudgetLifecycleEventRepository lifecycleEventRepository;
    private final BudgetCommandReceiptRepository receiptRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public BudgetCommandService(
        BudgetRepository budgetRepository,
        BudgetRevisionRepository revisionRepository,
        BudgetRevisionScheduleRepository scheduleRepository,
        BudgetLifecycleEventRepository lifecycleEventRepository,
        BudgetCommandReceiptRepository receiptRepository,
        FinancialAccountRepository financialAccountRepository,
        CategoryRepository categoryRepository,
        TagRepository tagRepository,
        CurrentUserService currentUserService,
        Clock clock
    ) {
        this.budgetRepository = budgetRepository;
        this.revisionRepository = revisionRepository;
        this.scheduleRepository = scheduleRepository;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.receiptRepository = receiptRepository;
        this.financialAccountRepository = financialAccountRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    public BudgetMutationResult scheduleRevision(Long budgetId, BudgetRevisionScheduleCommand command) {
        return scheduleRevision(budgetId, null, command, SCHEDULE_REVISION);
    }

    public BudgetMutationResult replaceFutureRevision(Long budgetId, Long targetScheduleId, BudgetRevisionScheduleCommand command) {
        return scheduleRevision(budgetId, targetScheduleId, command, REPLACE_REVISION);
    }

    private BudgetMutationResult scheduleRevision(
        Long budgetId,
        Long replacementTargetId,
        BudgetRevisionScheduleCommand command,
        String commandType
    ) {
        require(command != null, "A revision scheduling command is required.");
        Budget budget = ownedBudget(budgetId);
        String requestId = clientRequestId(command.clientRequestId());
        String digest = digest(commandType + "|" + replacementTargetId + "|" + command);
        Optional<BudgetCommandReceipt> replay = replay(budget, commandType, requestId, digest);
        if (replay.isPresent()) return replayResult(replay.orElseThrow());
        requireVersion(budget, command.expectedVersion());

        LocalDate today = localToday(command.timeZone());
        TimelineData timelineData = timelineData(budget);
        BudgetTimeline timeline = timelineData.timeline();
        BudgetPeriod current = timeline.currentPeriod(today);
        require(
            !timeline.isCompleteAt(current.startInclusive(), current.index()),
            "Completed budgets cannot receive another configuration change."
        );
        LocalDate effective = effectiveRevisionStart(command, current);
        BudgetRevisionSchedule target = replacementTargetId == null ? null : activeFutureSchedule(timeline, replacementTargetId, current);
        if (target != null && !target.getEffectivePeriodStart().equals(effective)) {
            throw invalid("A replacement must preserve the future change's effective period boundary.");
        }
        if (
            target == null &&
            !command.applyToCurrentPeriod() &&
            timeline.activeSchedules().stream().anyMatch(schedule -> schedule.getEffectivePeriodStart().equals(effective))
        ) {
            throw invalid("A revision is already scheduled at that boundary; replace or cancel it explicitly.");
        }

        RevisionDraft draft = revisionDraft(budget, command.revision());
        validateRevisionTransition(timeline, target, current, effective, draft.configuration(), command.applyToCurrentPeriod());
        Instant now = Instant.now(clock);
        int revisionNumber = timelineData.revisions().stream().mapToInt(BudgetRevision::getRevisionNumber).max().orElse(0) + 1;
        BudgetRevision revision = BudgetRevision.record(
            revisionNumber,
            draft.configuration(),
            draft.accounts(),
            draft.categories(),
            draft.tags(),
            now
        );
        budget.addRevision(revision);
        // The root already exists, so make the new immutable child managed before flushing its schedule reference.
        revisionRepository.saveAndFlush(revision);
        budget.touch(now);
        Budget persisted = budgetRepository.saveAndFlush(budget);

        long sequence = nextScheduleSequence(timelineData.schedules());
        if (target != null) {
            scheduleRepository.saveAndFlush(BudgetRevisionSchedule.cancelled(persisted, target, sequence++, requestId, now));
        }
        BudgetRevisionSchedule scheduled = scheduleRepository.saveAndFlush(
            BudgetRevisionSchedule.scheduled(persisted, revision, effective, sequence, requestId, now)
        );
        return recordResult(persisted, commandType, requestId, digest, scheduled.getId(), null, effective, now);
    }

    public BudgetMutationResult cancelFutureRevision(Long budgetId, Long scheduleId, BudgetVersionedCommand command) {
        require(command != null, "A future revision cancellation command is required.");
        Budget budget = ownedBudget(budgetId);
        String requestId = clientRequestId(command.clientRequestId());
        String digest = digest(CANCEL_REVISION + "|" + scheduleId + "|" + command);
        Optional<BudgetCommandReceipt> replay = replay(budget, CANCEL_REVISION, requestId, digest);
        if (replay.isPresent()) return replayResult(replay.orElseThrow());
        requireVersion(budget, command.expectedVersion());
        TimelineData data = timelineData(budget);
        BudgetPeriod current = data.timeline().currentPeriod(localToday(command.timeZone()));
        BudgetRevisionSchedule target = activeFutureSchedule(data.timeline(), scheduleId, current);
        Instant now = Instant.now(clock);
        BudgetRevisionSchedule cancellation = scheduleRepository.saveAndFlush(
            BudgetRevisionSchedule.cancelled(budget, target, nextScheduleSequence(data.schedules()), requestId, now)
        );
        budget.touch(now);
        Budget persisted = budgetRepository.saveAndFlush(budget);
        return recordResult(
            persisted,
            CANCEL_REVISION,
            requestId,
            digest,
            cancellation.getId(),
            null,
            target.getEffectivePeriodStart(),
            now
        );
    }

    public BudgetMutationResult requestPause(Long budgetId, BudgetLifecycleCommand command) {
        return lifecycle(budgetId, command, PAUSE, BudgetLifecycleEventType.PAUSE_REQUESTED, null);
    }

    public BudgetMutationResult requestResume(Long budgetId, BudgetLifecycleCommand command) {
        return lifecycle(budgetId, command, RESUME, BudgetLifecycleEventType.RESUME_REQUESTED, null);
    }

    public BudgetMutationResult requestFinish(Long budgetId, BudgetLifecycleCommand command) {
        return lifecycle(budgetId, command, FINISH, BudgetLifecycleEventType.FINISH_REQUESTED, null);
    }

    public BudgetMutationResult cancelPendingPause(Long budgetId, Long pauseEventId, BudgetLifecycleCommand command) {
        return lifecycle(budgetId, command, CANCEL_PAUSE, BudgetLifecycleEventType.PAUSE_CANCELLED, pauseEventId);
    }

    /**
     * Permanently removes this owner-scoped aggregate and only rows owned by it. Financial records and reusable
     * account/category/tag entities are linked by revision join tables and are deliberately never deleted here.
     */
    public void deleteBudget(Long budgetId, BudgetDeleteCommand command) {
        require(command != null, "A Budget deletion command is required.");
        Budget budget = ownedBudget(budgetId);
        requireVersion(budget, command.expectedVersion());

        receiptRepository.deleteByBudgetId(budgetId);
        lifecycleEventRepository.deleteTargetingByBudgetId(budgetId);
        lifecycleEventRepository.deleteByBudgetId(budgetId);
        scheduleRepository.deleteTargetingByBudgetId(budgetId);
        scheduleRepository.deleteByBudgetId(budgetId);
        revisionRepository.deleteAccountLinksByBudgetId(budgetId);
        revisionRepository.deleteCategoryLinksByBudgetId(budgetId);
        revisionRepository.deleteTagLinksByBudgetId(budgetId);
        revisionRepository.deleteByBudgetId(budgetId);
        require(budgetRepository.deleteAggregateById(budgetId) == 1, "Budget not found.");
    }

    private BudgetMutationResult lifecycle(
        Long budgetId,
        BudgetLifecycleCommand command,
        String commandType,
        BudgetLifecycleEventType type,
        Long targetEventId
    ) {
        require(command != null, "A lifecycle command is required.");
        Budget budget = ownedBudget(budgetId);
        String requestId = clientRequestId(command.clientRequestId());
        String digest = digest(commandType + "|" + targetEventId + "|" + command);
        Optional<BudgetCommandReceipt> replay = replay(budget, commandType, requestId, digest);
        if (replay.isPresent()) return replayResult(replay.orElseThrow());
        requireVersion(budget, command.expectedVersion());

        LocalDate today = localToday(command.timeZone());
        TimelineData data = timelineData(budget);
        BudgetTimeline timeline = data.timeline();
        BudgetPeriod current = timeline.currentPeriod(today);
        require(
            !timeline.isCompleteAt(current.startInclusive(), current.index()),
            "This Budget is already complete and cannot receive lifecycle changes."
        );
        BudgetPeriod next = timeline.nextPeriod(today);
        require(
            !timeline.isCompleteAt(next.startInclusive(), next.index()),
            "This Budget has no future complete period for the requested lifecycle change."
        );
        BudgetLifecycleEvent target = targetEventId == null ? null : ownedEvent(data.events(), targetEventId);

        if (type == BudgetLifecycleEventType.PAUSE_REQUESTED) {
            require(!hasPendingPause(data.events(), current.startInclusive()), "A pause is already pending.");
            require(!hasPendingFinish(data.events()), "A completion is already pending.");
        } else if (type == BudgetLifecycleEventType.PAUSE_CANCELLED) {
            require(target.getEventType() == BudgetLifecycleEventType.PAUSE_REQUESTED, "Only a pending pause may be cancelled.");
            require(target.getEffectivePeriodStart().isAfter(current.startInclusive()), "An effective pause cannot be cancelled.");
            require(!isPauseCancelled(data.events(), target.getId()), "This pause was already cancelled.");
        } else if (type == BudgetLifecycleEventType.RESUME_REQUESTED) {
            BudgetPeriodOperationalStatus status = timeline.periods(current.index(), current.index(), today).get(0).status();
            require(status == BudgetPeriodOperationalStatus.PAUSED, "A resume may be requested only while the Budget is paused.");
            require(!hasPendingResume(data.events(), current.startInclusive()), "A resume is already pending.");
            require(!hasPendingFinish(data.events()), "A resume cannot be scheduled while completion is pending.");
        } else if (type == BudgetLifecycleEventType.FINISH_REQUESTED) {
            require(!hasPendingFinish(data.events()), "A completion is already pending.");
            require(!hasPendingPause(data.events(), current.startInclusive()), "Resolve the pending pause before scheduling completion.");
            require(!hasPendingResume(data.events(), current.startInclusive()), "Resolve the pending resume before scheduling completion.");
        }

        LocalDate effective = type == BudgetLifecycleEventType.PAUSE_CANCELLED ? target.getEffectivePeriodStart() : next.startInclusive();
        Instant now = Instant.now(clock);
        BudgetLifecycleEvent event = lifecycleEventRepository.saveAndFlush(
            BudgetLifecycleEvent.record(budget, type, effective, nextLifecycleSequence(data.events()), requestId, now, target)
        );
        budget.touch(now);
        Budget persisted = budgetRepository.saveAndFlush(budget);
        return recordResult(persisted, commandType, requestId, digest, null, event.getId(), effective, now);
    }

    public BudgetMutationResult updateMetadata(Long budgetId, BudgetMetadataUpdateCommand command) {
        require(command != null, "A metadata command is required.");
        Budget budget = ownedBudget(budgetId);
        requireVersion(budget, command.expectedVersion());
        Instant now = Instant.now(clock);
        budget.updateMetadata(name(command.name()), description(command.description()), now);
        Budget persisted = budgetRepository.saveAndFlush(budget);
        return new BudgetMutationResult(persisted.getId(), persisted.getVersion(), null, null, null, null);
    }

    @Transactional(readOnly = true)
    public BudgetHistoryView history(Long budgetId) {
        Budget budget = ownedBudget(budgetId);
        TimelineData data = timelineData(budget);
        Set<Long> activeScheduleIds = data
            .timeline()
            .activeSchedules()
            .stream()
            .map(BudgetRevisionSchedule::getId)
            .collect(Collectors.toSet());
        return new BudgetHistoryView(
            budget.getId(),
            budget.getVersion(),
            data.revisions().stream().map(this::revisionView).toList(),
            data.schedules().stream().map(schedule -> scheduleView(schedule, activeScheduleIds.contains(schedule.getId()))).toList(),
            data.events().stream().map(this::eventView).toList()
        );
    }

    @Transactional(readOnly = true)
    public BudgetHistoryView.Revision effectiveRevision(Long budgetId, LocalDate periodStart) {
        Budget budget = ownedBudget(budgetId);
        return revisionView(timelineData(budget).timeline().resolveEffectiveRevision(periodStart).revision());
    }

    @Transactional(readOnly = true)
    public List<BudgetOperationalPeriodView> periods(Long budgetId, long firstIndex, long lastIndex, String timeZone) {
        Budget budget = ownedBudget(budgetId);
        LocalDate today = localToday(timeZone);
        return timelineData(budget)
            .timeline()
            .periods(firstIndex, lastIndex, today)
            .stream()
            .map(period ->
                new BudgetOperationalPeriodView(
                    period.period().index(),
                    period.period().startInclusive(),
                    period.period().endExclusive(),
                    period.revision().getId(),
                    period.status(),
                    period.evaluationEligible()
                )
            )
            .toList();
    }

    private BudgetMutationResult recordResult(
        Budget budget,
        String type,
        String requestId,
        String digest,
        Long scheduleId,
        Long lifecycleId,
        LocalDate effective,
        Instant now
    ) {
        receiptRepository.saveAndFlush(
            BudgetCommandReceipt.record(budget, type, requestId, digest, scheduleId, lifecycleId, budget.getVersion(), now)
        );
        Long revisionId = scheduleId == null
            ? null
            : scheduleRepository
                .findById(scheduleId)
                .map(s -> s.getRevision())
                .filter(Objects::nonNull)
                .map(BudgetRevision::getId)
                .orElse(null);
        return new BudgetMutationResult(budget.getId(), budget.getVersion(), revisionId, scheduleId, lifecycleId, effective);
    }

    private BudgetMutationResult replayResult(BudgetCommandReceipt receipt) {
        Long revisionId = receipt.getRevisionScheduleId() == null
            ? null
            : scheduleRepository
                .findById(receipt.getRevisionScheduleId())
                .map(s -> s.getRevision())
                .filter(Objects::nonNull)
                .map(BudgetRevision::getId)
                .orElse(null);
        LocalDate effective = receipt.getRevisionScheduleId() != null
            ? scheduleRepository.findById(receipt.getRevisionScheduleId()).map(BudgetRevisionSchedule::getEffectivePeriodStart).orElse(null)
            : receipt.getLifecycleEventId() == null
                ? null
                : lifecycleEventRepository
                    .findById(receipt.getLifecycleEventId())
                    .map(BudgetLifecycleEvent::getEffectivePeriodStart)
                    .orElse(null);
        return new BudgetMutationResult(
            receipt.getBudget().getId(),
            receipt.getResultingBudgetVersion(),
            revisionId,
            receipt.getRevisionScheduleId(),
            receipt.getLifecycleEventId(),
            effective
        );
    }

    private Optional<BudgetCommandReceipt> replay(Budget budget, String type, String requestId, String digest) {
        Optional<BudgetCommandReceipt> receipt = receiptRepository.findByBudgetCommandAndRequestId(budget.getId(), type, requestId);
        if (receipt.isPresent() && !Objects.equals(receipt.orElseThrow().getRequestDigest(), digest)) {
            throw conflict("clientRequestId was already used for different input.");
        }
        return receipt;
    }

    private TimelineData timelineData(Budget budget) {
        List<BudgetRevision> revisions = revisionRepository.findAllByBudgetIdOrderByRevisionNumber(budget.getId());
        List<BudgetRevisionSchedule> schedules = scheduleRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId());
        List<BudgetLifecycleEvent> events = lifecycleEventRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId());
        return new TimelineData(revisions, schedules, events, new BudgetTimeline(schedules, events));
    }

    private BudgetRevisionSchedule activeFutureSchedule(BudgetTimeline timeline, Long scheduleId, BudgetPeriod current) {
        BudgetRevisionSchedule target = timeline
            .activeSchedules()
            .stream()
            .filter(schedule -> Objects.equals(schedule.getId(), scheduleId))
            .findFirst()
            .orElseThrow(() -> invalid("The requested future revision is not active for this Budget."));
        require(target.getEffectivePeriodStart().isAfter(current.startInclusive()), "Only a future revision may be replaced or cancelled.");
        return target;
    }

    private LocalDate effectiveRevisionStart(BudgetRevisionScheduleCommand command, BudgetPeriod current) {
        if (command.applyToCurrentPeriod()) {
            require(
                command.effectivePeriodStart() == null || command.effectivePeriodStart().equals(current.startInclusive()),
                "Current-period changes use the current period start."
            );
            return current.startInclusive();
        }
        LocalDate effective = command.effectivePeriodStart() == null ? current.endExclusive() : command.effectivePeriodStart();
        require(!effective.isBefore(current.endExclusive()), "A future change must start at the next complete period or later.");
        return effective;
    }

    private void validateRevisionTransition(
        BudgetTimeline timeline,
        BudgetRevisionSchedule replacementTarget,
        BudgetPeriod current,
        LocalDate effective,
        BudgetRevisionConfiguration candidate,
        boolean applyToCurrent
    ) {
        BudgetRevision currentRevision = timeline.resolveEffectiveRevision(current.startInclusive()).revision();
        BudgetRevision initialRevision = timeline
            .activeSchedules()
            .stream()
            .min(
                Comparator.comparing(BudgetRevisionSchedule::getEffectivePeriodStart).thenComparing(
                    BudgetRevisionSchedule::getSequenceNumber
                )
            )
            .orElseThrow(() -> invalid("Budget is missing its initial schedule."))
            .getRevision();
        require(
            sameRecurrence(initialRevision, candidate),
            "Budget-2 revisions may change amount, scope, or a future calendar boundary, but not the original recurrence duration."
        );
        BudgetPeriodCalculator currentCalendar = new BudgetPeriodCalculator(currentRevision.calendarDefinition());
        BudgetPeriod boundary = currentCalendar
            .periodContaining(effective)
            .orElseThrow(() -> invalid("The chosen effective date is outside the current Budget calendar."));
        require(boundary.startInclusive().equals(effective), "Changes must begin at a complete existing Budget period boundary.");
        boolean calendarChanged = !currentRevision.calendarDefinition().equals(candidate.calendarDefinition());
        if (!calendarChanged) return;
        require(!applyToCurrent, "Calendar changes may take effect only in a future complete period.");
        require(candidate.anchorDate().equals(effective), "A new calendar must use the chosen effective boundary as its anchor date.");
        new BudgetPeriodCalculator(candidate.calendarDefinition());
        boolean unresolvedFuture = timeline
            .activeSchedules()
            .stream()
            .filter(schedule -> replacementTarget == null || !Objects.equals(schedule.getId(), replacementTarget.getId()))
            .anyMatch(schedule -> schedule.getEffectivePeriodStart().isAfter(effective));
        require(!unresolvedFuture, "Resolve later scheduled revisions explicitly before changing the Budget calendar.");
    }

    private boolean sameRecurrence(BudgetRevision initial, BudgetRevisionConfiguration candidate) {
        return (
            initial.getRecurrenceMode() == candidate.recurrenceMode() &&
            initial.getTermination() == candidate.termination() &&
            Objects.equals(initial.getTerminationPeriodCount(), candidate.terminationPeriodCount()) &&
            Objects.equals(initial.getRecurrenceEndDate(), candidate.recurrenceEndDate())
        );
    }

    private RevisionDraft revisionDraft(Budget budget, BudgetInitialRevisionCommand command) {
        require(command != null, "Revision configuration is required.");
        BigDecimal target = command.targetAmount();
        require(target != null && target.signum() > 0, "Budget target amount must be greater than zero.");
        require(command.intervalCount() != null && command.intervalCount() > 0, "Budget interval count must be greater than zero.");
        require(
            command.intervalUnit() != null &&
            command.alignment() != null &&
            command.anchorDate() != null &&
            command.recurrenceMode() != null &&
            command.termination() != null &&
            command.accountScopeMode() != null &&
            command.categoryScopeMode() != null &&
            command.tagScopeMode() != null,
            "Complete calendar, recurrence, and scope configuration is required."
        );
        BudgetTagMatchMode tagMatch = command.tagMatchMode() == null ? BudgetTagMatchMode.ANY : command.tagMatchMode();
        require(
            command.tagScopeMode() != BudgetTagScopeMode.NONE || tagMatch == BudgetTagMatchMode.ANY,
            "Tag match mode applies only to selected tags."
        );
        BudgetRevisionConfiguration configuration = new BudgetRevisionConfiguration(
            target,
            command.intervalCount(),
            command.intervalUnit(),
            command.alignment(),
            command.anchorDate(),
            command.customDayOfMonth(),
            command.recurrenceMode(),
            command.termination(),
            command.terminationPeriodCount(),
            command.recurrenceEndDate(),
            command.accountScopeMode(),
            command.categoryScopeMode(),
            command.tagScopeMode(),
            tagMatch
        );
        try {
            new BudgetPeriodCalculator(configuration.calendarDefinition());
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
        String owner = budget.getUser().getLogin();
        return new RevisionDraft(
            configuration,
            resolveScope(configuration.accountScopeMode() == BudgetAccountScopeMode.SELECTED, command.accountIds(), "account", id ->
                financialAccountRepository
                    .findOneWithToOneRelationshipsByIdAndUserLogin(id, owner)
                    .filter(account -> account.getCurrency() == budget.getCurrency())
                    .orElseThrow(() -> invalid("Every selected account must be owned by the user and use the Budget currency."))
            ),
            resolveScope(configuration.categoryScopeMode() == BudgetCategoryScopeMode.SELECTED, command.categoryIds(), "category", id ->
                categoryRepository
                    .findOneWithToOneRelationshipsByIdAndUserLogin(id, owner)
                    .filter(
                        category ->
                            category.getCategoryType() == CategoryType.BOTH ||
                            (budget.getType() == BudgetType.EXPENSE && category.getCategoryType() == CategoryType.EXPENSE) ||
                            (budget.getType() == BudgetType.INCOME && category.getCategoryType() == CategoryType.INCOME)
                    )
                    .orElseThrow(() -> invalid("Every selected category must be owned by the user and compatible with the Budget type."))
            ),
            resolveScope(configuration.tagScopeMode() == BudgetTagScopeMode.SELECTED, command.tagIds(), "tag", id ->
                tagRepository
                    .findOneWithToOneRelationshipsByIdAndUserLogin(id, owner)
                    .orElseThrow(() -> invalid("Every selected tag must be owned by the user."))
            )
        );
    }

    private <T> Set<T> resolveScope(boolean selected, Set<Long> supplied, String label, Function<Long, T> resolver) {
        Set<Long> ids = supplied == null ? Set.of() : new LinkedHashSet<>(supplied);
        require(!selected || !ids.isEmpty(), "SELECTED " + label + " scope requires at least one selection.");
        require(selected || ids.isEmpty(), "Only SELECTED " + label + " scope may include selections.");
        Set<T> result = new LinkedHashSet<>();
        for (Long id : ids) {
            require(id != null && id > 0, "Selected " + label + " identifiers must be positive.");
            result.add(resolver.apply(id));
        }
        require(!selected || result.size() == ids.size(), "Selected " + label + " references must be unique and accessible.");
        return result;
    }

    private BudgetHistoryView.Revision revisionView(BudgetRevision revision) {
        return new BudgetHistoryView.Revision(
            revision.getId(),
            revision.getRevisionNumber(),
            revision.getRecordedAt(),
            revision.getTargetAmount(),
            revision.getIntervalUnit(),
            revision.getIntervalCount(),
            revision.getAlignment(),
            revision.getAnchorDate(),
            revision.getCustomDayOfMonth(),
            revision.getRecurrenceMode(),
            revision.getTermination(),
            revision.getTerminationPeriodCount(),
            revision.getRecurrenceEndDate(),
            revision.getAccountScopeMode(),
            ids(revision.getAccounts()),
            revision.getCategoryScopeMode(),
            ids(revision.getCategories()),
            revision.getTagScopeMode(),
            ids(revision.getTags()),
            revision.getTagMatchMode()
        );
    }

    private BudgetHistoryView.Schedule scheduleView(BudgetRevisionSchedule schedule, boolean active) {
        return new BudgetHistoryView.Schedule(
            schedule.getId(),
            schedule.getOperation(),
            schedule.getRevision() == null ? null : schedule.getRevision().getId(),
            schedule.getTargetSchedule() == null ? null : schedule.getTargetSchedule().getId(),
            schedule.getEffectivePeriodStart(),
            schedule.getSequenceNumber(),
            schedule.getRecordedAt(),
            active
        );
    }

    private BudgetHistoryView.LifecycleEvent eventView(BudgetLifecycleEvent event) {
        return new BudgetHistoryView.LifecycleEvent(
            event.getId(),
            event.getEventType(),
            event.getTargetEvent() == null ? null : event.getTargetEvent().getId(),
            event.getEffectivePeriodStart(),
            event.getSequenceNumber(),
            event.getRecordedAt()
        );
    }

    private Set<Long> ids(Set<? extends Object> values) {
        return values
            .stream()
            .map(value -> {
                if (value instanceof FinancialAccount account) return account.getId();
                if (value instanceof Category category) return category.getId();
                if (value instanceof Tag tag) return tag.getId();
                throw new IllegalStateException("Unsupported Budget selection.");
            })
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Budget ownedBudget(Long budgetId) {
        if (budgetId == null || budgetId <= 0) throw invalid("A Budget identifier is required.");
        return budgetRepository
            .findOneByIdAndUserLogin(budgetId, currentUserService.getCurrentUserLogin())
            .orElseThrow(() -> invalid("Budget not found."));
    }

    private BudgetLifecycleEvent ownedEvent(List<BudgetLifecycleEvent> events, Long id) {
        return events
            .stream()
            .filter(event -> Objects.equals(event.getId(), id))
            .findFirst()
            .orElseThrow(() -> invalid("Lifecycle event not found."));
    }

    private boolean hasPendingPause(List<BudgetLifecycleEvent> events, LocalDate currentStart) {
        return events
            .stream()
            .anyMatch(
                event ->
                    event.getEventType() == BudgetLifecycleEventType.PAUSE_REQUESTED &&
                    event.getEffectivePeriodStart().isAfter(currentStart) &&
                    !isPauseCancelled(events, event.getId())
            );
    }

    private boolean hasPendingResume(List<BudgetLifecycleEvent> events, LocalDate currentStart) {
        return events
            .stream()
            .anyMatch(
                event ->
                    event.getEventType() == BudgetLifecycleEventType.RESUME_REQUESTED &&
                    event.getEffectivePeriodStart().isAfter(currentStart)
            );
    }

    private boolean hasPendingFinish(List<BudgetLifecycleEvent> events) {
        return events.stream().anyMatch(event -> event.getEventType() == BudgetLifecycleEventType.FINISH_REQUESTED);
    }

    private boolean isPauseCancelled(List<BudgetLifecycleEvent> events, Long pauseId) {
        return events
            .stream()
            .anyMatch(
                event ->
                    event.getEventType() == BudgetLifecycleEventType.PAUSE_CANCELLED &&
                    event.getTargetEvent() != null &&
                    Objects.equals(event.getTargetEvent().getId(), pauseId)
            );
    }

    private long nextScheduleSequence(List<BudgetRevisionSchedule> schedules) {
        return schedules.stream().mapToLong(BudgetRevisionSchedule::getSequenceNumber).max().orElse(0L) + 1;
    }

    private long nextLifecycleSequence(List<BudgetLifecycleEvent> events) {
        return events.stream().mapToLong(BudgetLifecycleEvent::getSequenceNumber).max().orElse(0L) + 1;
    }

    private void requireVersion(Budget budget, Long expected) {
        if (expected == null || !Objects.equals(budget.getVersion(), expected)) throw conflict(
            "This Budget was updated elsewhere. Reload it before saving."
        );
    }

    private LocalDate localToday(String timeZone) {
        try {
            require(timeZone != null && !timeZone.trim().isEmpty(), "timeZone is required.");
            return LocalDate.now(clock.withZone(ZoneId.of(timeZone.trim())));
        } catch (java.time.DateTimeException exception) {
            throw invalid("timeZone must be a valid IANA zone identifier.");
        }
    }

    private String clientRequestId(String value) {
        String result = value == null ? "" : value.trim();
        require(
            !result.isEmpty() && result.length() <= 100 && result.matches("[A-Za-z0-9._:-]+"),
            "clientRequestId must be a bounded opaque identifier."
        );
        return result;
    }

    private String name(String value) {
        String result = normalize(value);
        require(!result.isEmpty() && result.length() <= 100, "Budget name must be between 1 and 100 characters.");
        return result;
    }

    private String description(String value) {
        String result = normalize(value);
        require(result.length() <= 500, "Budget description may contain at most 500 characters.");
        return result.isEmpty() ? null : result;
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw invalid(message);
    }

    private static BudgetCommandException invalid(String message) {
        return new BudgetCommandException(BudgetCommandException.Code.INVALID_PAYLOAD, message);
    }

    private static BudgetCommandException conflict(String message) {
        return new BudgetCommandException(BudgetCommandException.Code.VERSION_CONFLICT, message);
    }

    private record RevisionDraft(
        BudgetRevisionConfiguration configuration,
        Set<FinancialAccount> accounts,
        Set<Category> categories,
        Set<Tag> tags
    ) {}

    private record TimelineData(
        List<BudgetRevision> revisions,
        List<BudgetRevisionSchedule> schedules,
        List<BudgetLifecycleEvent> events,
        BudgetTimeline timeline
    ) {}
}
