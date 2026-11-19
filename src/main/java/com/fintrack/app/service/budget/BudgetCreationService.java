package com.fintrack.app.service.budget;

import com.fintrack.app.domain.*;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.BudgetRevisionScheduleRepository;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.service.CurrentUserService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sole Budget-1 mutating boundary. It resolves every selection by the authenticated owner and records exactly
 * one immutable configuration snapshot in the same transaction as the aggregate identity.
 */
@Service
@Transactional
public class BudgetCreationService {

    private final BudgetRepository budgetRepository;
    private final BudgetRevisionScheduleRepository revisionScheduleRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;
    private final BudgetTimeZoneService budgetTimeZoneService;
    private final BudgetNotificationPreferenceService notificationPreferenceService;

    public BudgetCreationService(
        BudgetRepository budgetRepository,
        BudgetRevisionScheduleRepository revisionScheduleRepository,
        FinancialAccountRepository financialAccountRepository,
        CategoryRepository categoryRepository,
        TagRepository tagRepository,
        UserRepository userRepository,
        CurrentUserService currentUserService,
        Clock clock,
        BudgetTimeZoneService budgetTimeZoneService,
        BudgetNotificationPreferenceService notificationPreferenceService
    ) {
        this.budgetRepository = budgetRepository;
        this.revisionScheduleRepository = revisionScheduleRepository;
        this.financialAccountRepository = financialAccountRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.clock = clock;
        this.budgetTimeZoneService = budgetTimeZoneService;
        this.notificationPreferenceService = notificationPreferenceService;
    }

    public BudgetCreated create(BudgetCreateCommand command) {
        require(command != null, "A budget command is required.");
        User authenticatedOwner = currentUserService.getCurrentUser();
        User owner = userRepository
            .findOneForBudgetCreationByLogin(authenticatedOwner.getLogin())
            .orElseThrow(() -> new IllegalStateException("Current user not found"));
        String requestId = requestId(command.clientRequestId());
        BudgetNotificationPreferenceInput notificationPreferences = notificationPreferenceService.normalizeForCreation(
            command.notificationPreferences()
        );
        String requestHash = digest(command, notificationPreferences);
        Optional<Budget> replay = budgetRepository.findOneWithRevisionsByUserLoginAndCreationRequestId(owner.getLogin(), requestId);
        if (replay.isPresent()) return replay(replay.orElseThrow(), requestHash);

        String name = name(command.name());
        String description = description(command.description());
        if (command.type() == null || command.currency() == null) throw failure("Budget type and currency are required.");
        ZoneId requestedZone = resolveZone(command.timeZone());
        // A Budget may be created before the person explicitly confirms a profile setting. In that case the
        // creation form's validated zone is scoped to this Budget only; it must not silently persist a profile choice.
        ZoneId effectiveZone = owner.getTimeZone() == null || owner.getTimeZone().isBlank()
            ? requestedZone
            : resolveZone(owner.getTimeZone());
        BudgetRevisionConfiguration configuration = configuration(command.initialRevision());
        // Calendar validation is centralized in this pure component before any database mutation occurs.
        new BudgetPeriodCalculator(configuration.calendarDefinition());

        String ownerLogin = owner.getLogin();
        Set<FinancialAccount> accounts = resolveAccounts(
            configuration,
            command.currency(),
            ownerLogin,
            command.initialRevision().accountIds()
        );
        Set<Category> categories = resolveCategories(configuration, command.type(), ownerLogin, command.initialRevision().categoryIds());
        Set<Tag> tags = resolveTags(configuration, ownerLogin, command.initialRevision().tagIds());
        Instant now = Instant.now(clock);
        Budget budget = Budget.create(owner, name, description, command.type(), command.currency(), requestId, requestHash, now);
        budget.addInitialRevision(BudgetRevision.initial(configuration, accounts, categories, tags, now));
        Budget saved = budgetRepository.saveAndFlush(budget);
        revisionScheduleRepository.saveAndFlush(
            BudgetRevisionSchedule.scheduled(saved, saved.getRevisions().get(0), configuration.anchorDate(), 1, "INITIAL", now)
        );
        budgetTimeZoneService.recordInitial(saved, effectiveZone.getId(), configuration.anchorDate(), now);
        // This occurs in the same creation transaction as the Budget, its first revision, schedule and timezone
        // assignment. A failed preference write therefore cannot leave an orphaned delivery configuration.
        notificationPreferenceService.createForBudget(saved, notificationPreferences, now);
        return view(saved);
    }

    private BudgetCreated replay(Budget budget, String requestHash) {
        if (!budget.getCreationRequestHash().equals(requestHash)) {
            throw new BudgetCommandException(
                BudgetCommandException.Code.IDEMPOTENCY_CONFLICT,
                "clientRequestId was already used for another budget command."
            );
        }
        return view(budget);
    }

    private BudgetRevisionConfiguration configuration(BudgetInitialRevisionCommand command) {
        require(command != null, "Initial revision configuration is required.");
        BigDecimal target = BudgetTargetAmountValidator.requireValid(command.targetAmount());
        if (command.intervalCount() == null || command.intervalCount() <= 0) throw failure("Interval count must be greater than zero.");
        if (
            command.intervalUnit() == null ||
            command.alignment() == null ||
            command.anchorDate() == null ||
            command.recurrenceMode() == null ||
            command.termination() == null ||
            command.accountScopeMode() == null ||
            command.categoryScopeMode() == null ||
            command.tagScopeMode() == null
        ) throw failure("Complete calendar, recurrence, and scope configuration is required.");
        BudgetTagMatchMode matchMode = command.tagMatchMode() == null ? BudgetTagMatchMode.ANY : command.tagMatchMode();
        if (command.tagScopeMode() == BudgetTagScopeMode.NONE && matchMode != BudgetTagMatchMode.ANY) {
            throw failure("Tag match mode applies only when tags are selected.");
        }
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
            matchMode
        );
        try {
            new BudgetPeriodCalculator(configuration.calendarDefinition());
        } catch (IllegalArgumentException exception) {
            throw failure(exception.getMessage());
        }
        return configuration;
    }

    private Set<FinancialAccount> resolveAccounts(
        BudgetRevisionConfiguration configuration,
        CurrencyCode currency,
        String ownerLogin,
        Set<Long> ids
    ) {
        return resolveScope(configuration.accountScopeMode() == BudgetAccountScopeMode.SELECTED, ids, "account", id ->
            financialAccountRepository
                .findOneWithToOneRelationshipsByIdAndUserLogin(id, ownerLogin)
                .filter(account -> account.getCurrency() == currency)
                .orElseThrow(() -> failure("Every selected account must be owned by the user and use the budget currency."))
        );
    }

    private Set<Category> resolveCategories(BudgetRevisionConfiguration configuration, BudgetType type, String ownerLogin, Set<Long> ids) {
        return resolveScope(configuration.categoryScopeMode() == BudgetCategoryScopeMode.SELECTED, ids, "category", id ->
            categoryRepository
                .findOneWithToOneRelationshipsByIdAndUserLogin(id, ownerLogin)
                .filter(category -> categoryCompatible(category, type))
                .orElseThrow(() -> failure("Every selected category must be owned by the user and compatible with this budget type."))
        );
    }

    private Set<Tag> resolveTags(BudgetRevisionConfiguration configuration, String ownerLogin, Set<Long> ids) {
        return resolveScope(configuration.tagScopeMode() == BudgetTagScopeMode.SELECTED, ids, "tag", id ->
            tagRepository
                .findOneWithToOneRelationshipsByIdAndUserLogin(id, ownerLogin)
                .orElseThrow(() -> failure("Every selected tag must be owned by the user."))
        );
    }

    private <T> Set<T> resolveScope(boolean selected, Set<Long> suppliedIds, String label, java.util.function.Function<Long, T> resolver) {
        Set<Long> ids = suppliedIds == null ? Set.of() : new LinkedHashSet<>(suppliedIds);
        if (selected && ids.isEmpty()) throw failure("SELECTED " + label + " scope requires at least one selection.");
        if (!selected && !ids.isEmpty()) throw failure("Only SELECTED " + label + " scope may include selections.");
        Set<T> resolved = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) throw failure("Selected " + label + " identifiers must be positive.");
            resolved.add(resolver.apply(id));
        }
        if (selected && resolved.size() != ids.size()) throw failure("Selected " + label + " references must be unique and accessible.");
        return resolved;
    }

    private boolean categoryCompatible(Category category, BudgetType type) {
        return (
            category.getCategoryType() == CategoryType.BOTH ||
            (type == BudgetType.EXPENSE && category.getCategoryType() == CategoryType.EXPENSE) ||
            (type == BudgetType.INCOME && category.getCategoryType() == CategoryType.INCOME)
        );
    }

    private BudgetCreated view(Budget budget) {
        BudgetRevision revision = budget
            .getRevisions()
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Budget is missing revision 1."));
        return new BudgetCreated(
            budget.getId(),
            budget.getVersion(),
            budget.getName(),
            budget.getDescription(),
            budget.getType(),
            budget.getCurrency(),
            budget.getCreatedAt(),
            budget.getUpdatedAt(),
            new BudgetCreated.InitialRevision(
                revision.getId(),
                revision.getRevisionNumber(),
                revision.getRecordedAt(),
                revision.getTargetAmount(),
                revision.getIntervalCount(),
                revision.getIntervalUnit(),
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
            )
        );
    }

    private Set<Long> ids(Set<? extends Object> values) {
        return values
            .stream()
            .map(value -> {
                if (value instanceof FinancialAccount account) return account.getId();
                if (value instanceof Category category) return category.getId();
                if (value instanceof Tag tag) return tag.getId();
                throw new IllegalStateException("Unsupported budget reference.");
            })
            .sorted(Comparator.naturalOrder())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String digest(BudgetCreateCommand command, BudgetNotificationPreferenceInput notificationPreferences) {
        BudgetInitialRevisionCommand revision = command.initialRevision();
        String canonical = String.join(
            "|",
            normalize(command.name()),
            normalizeNullable(command.description()),
            String.valueOf(command.type()),
            String.valueOf(command.currency()),
            normalize(command.timeZone()),
            String.valueOf(revision == null ? null : revision.targetAmount()),
            String.valueOf(revision == null ? null : revision.intervalCount()),
            String.valueOf(revision == null ? null : revision.intervalUnit()),
            String.valueOf(revision == null ? null : revision.alignment()),
            String.valueOf(revision == null ? null : revision.anchorDate()),
            String.valueOf(revision == null ? null : revision.customDayOfMonth()),
            String.valueOf(revision == null ? null : revision.recurrenceMode()),
            String.valueOf(revision == null ? null : revision.termination()),
            String.valueOf(revision == null ? null : revision.terminationPeriodCount()),
            String.valueOf(revision == null ? null : revision.recurrenceEndDate()),
            String.valueOf(revision == null ? null : revision.accountScopeMode()),
            idsForDigest(revision == null ? null : revision.accountIds()),
            String.valueOf(revision == null ? null : revision.categoryScopeMode()),
            idsForDigest(revision == null ? null : revision.categoryIds()),
            String.valueOf(revision == null ? null : revision.tagScopeMode()),
            idsForDigest(revision == null ? null : revision.tagIds()),
            String.valueOf(revision == null ? null : revision.tagMatchMode()),
            notificationPreferences == null
                ? "preferences:absent"
                : "percentageAlerts=" + notificationPreferences.percentageAlertsEnabled(),
            notificationPreferences == null ? "thresholds:" : thresholdValuesForDigest(notificationPreferences.thresholdPercentages()),
            notificationPreferences == null ? "periodClose:" : "periodClose=" + notificationPreferences.periodCloseEnabled()
        );
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String idsForDigest(Set<Long> ids) {
        return (ids == null ? Set.<Long>of() : ids).stream()
            .sorted()
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(","));
    }

    private String thresholdValuesForDigest(Set<java.math.BigDecimal> values) {
        return (values == null ? Set.<java.math.BigDecimal>of() : values).stream()
            .sorted()
            .map(value -> value.stripTrailingZeros().toPlainString())
            .collect(java.util.stream.Collectors.joining(","));
    }

    private String name(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty() || normalized.length() > 100) throw failure("Budget name must be between 1 and 100 characters.");
        return normalized;
    }

    private String description(String value) {
        String normalized = normalizeNullable(value);
        if (normalized != null && normalized.length() > 500) throw failure("Budget description may contain at most 500 characters.");
        return normalized;
    }

    private String requestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 100 || !normalized.matches("[A-Za-z0-9._:-]+")) throw failure(
            "clientRequestId must be a bounded opaque identifier."
        );
        return normalized;
    }

    private ZoneId resolveZone(String value) {
        try {
            if (value == null || value.trim().isEmpty()) throw failure("timeZone is required to validate the user's current local date.");
            return ZoneId.of(value.trim());
        } catch (java.time.DateTimeException exception) {
            throw failure("timeZone must be a valid IANA zone identifier.");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeNullable(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private static BudgetCommandException failure(String message) {
        return new BudgetCommandException(BudgetCommandException.Code.INVALID_PAYLOAD, message);
    }
}
