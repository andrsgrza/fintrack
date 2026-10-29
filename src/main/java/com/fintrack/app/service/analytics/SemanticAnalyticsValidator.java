package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.service.CurrentUserService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates the POC's one semantic AST and resolves an authenticated, bounded execution scope. The catalog is
 * intentionally code-owned: callers cannot submit table names, paths, joins or SQL.
 */
@Service
@Transactional(readOnly = true)
public class SemanticAnalyticsValidator {

    private static final Set<AccountType> ASSET_ACCOUNT_TYPES = EnumSet.of(AccountType.DEBIT, AccountType.CASH, AccountType.INVESTMENT);

    private final SemanticAnalyticsTimePolicy timePolicy;
    private final CurrentUserService currentUserService;
    private final FinancialAccountRepository accountRepository;
    private final SemanticAnalyticsCatalogService catalog;
    private final SemanticAnalyticsReferenceResolver referenceResolver;

    @Autowired
    public SemanticAnalyticsValidator(
        SemanticAnalyticsTimePolicy timePolicy,
        CurrentUserService currentUserService,
        FinancialAccountRepository accountRepository,
        SemanticAnalyticsCatalogService catalog,
        SemanticAnalyticsReferenceResolver referenceResolver
    ) {
        this.timePolicy = timePolicy;
        this.currentUserService = currentUserService;
        this.accountRepository = accountRepository;
        this.catalog = catalog;
        this.referenceResolver = referenceResolver;
    }

    /** Retains the lightweight constructor used by pure definition-contract tests. */
    SemanticAnalyticsValidator(
        SemanticAnalyticsTimePolicy timePolicy,
        CurrentUserService currentUserService,
        FinancialAccountRepository accountRepository,
        SemanticAnalyticsCatalogService catalog
    ) {
        this(timePolicy, currentUserService, accountRepository, catalog, null);
    }

    public SemanticAnalyticsExecutionScope validateOrThrow(SemanticAnalyticsExecutionRequest request) {
        List<AnalyticsDiagnostic> diagnostics = new ArrayList<>();
        if (request == null) {
            throw new SemanticAnalyticsValidationException(
                List.of(diagnostic(AnalyticsDiagnosticCode.MALFORMED_REQUEST, "request", "A semantic query request is required."))
            );
        }
        validateVersion(request.schemaVersion(), "schemaVersion", diagnostics);
        Optional<SemanticQueryDefinition> definition = validateDefinition(request.definition(), diagnostics);
        String ownerLogin = currentUserService.getCurrentUserLogin();
        Optional<ContextResolution> context = validateContext(request.context(), ownerLogin, diagnostics);
        if (definition.isPresent() && context.isPresent()) {
            validateContextSpecificSemantics(definition.orElseThrow(), context.orElseThrow().context(), diagnostics);
        }
        SemanticAnalyticsReferenceCatalog references = null;
        if (diagnostics.isEmpty()) {
            try {
                references = referenceResolver.resolve(ownerLogin);
                validateOwnerReferences(definition.orElseThrow(), references, diagnostics);
            } catch (IllegalArgumentException exception) {
                diagnostics.add(diagnostic(AnalyticsDiagnosticCode.INVALID_CATEGORY_HIERARCHY, "definition", exception.getMessage()));
            }
        }
        if (!diagnostics.isEmpty()) {
            throw new SemanticAnalyticsValidationException(diagnostics);
        }
        ContextResolution resolved = context.orElseThrow();
        return new SemanticAnalyticsExecutionScope(
            definition.orElseThrow(),
            resolved.context(),
            ownerLogin,
            resolved.accounts(),
            references
        );
    }

    /**
     * Normalizes a reusable semantic definition without resolving an owner or a visualization context.  Textual
     * query parsing uses this exact path, so it cannot claim a broader surface than JSON execution accepts.
     */
    public SemanticQueryDefinition normalizeDefinitionOrThrow(SemanticQueryDefinition definition) {
        List<AnalyticsDiagnostic> diagnostics = new ArrayList<>();
        Optional<SemanticQueryDefinition> normalized = validateDefinition(definition, diagnostics);
        if (!diagnostics.isEmpty()) {
            throw new SemanticAnalyticsValidationException(diagnostics);
        }
        return normalized.orElseThrow();
    }

    /** Package-visible so the unit proof can reject invalid datasets, fields and functions without a database. */
    Optional<SemanticQueryDefinition> validateDefinition(SemanticQueryDefinition definition, List<AnalyticsDiagnostic> diagnostics) {
        if (definition == null) {
            diagnostics.add(
                diagnostic(AnalyticsDiagnosticCode.MALFORMED_REQUEST, "definition", "A semantic query definition is required.")
            );
            return Optional.empty();
        }
        validateVersion(definition.schemaVersion(), "definition.schemaVersion", diagnostics);
        if (!SemanticAnalyticsSchema.SEMANTIC_MODEL.equals(normalize(definition.semanticModel()))) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.UNKNOWN_SEMANTIC_MODEL,
                    "definition.semanticModel",
                    "The query must use the FINTRACK_FINANCIAL semantic model."
                )
            );
        }
        SemanticDataset dataset = definition.dataset();
        if (dataset == null) {
            diagnostics.add(diagnostic(AnalyticsDiagnosticCode.UNKNOWN_DATASET, "definition.dataset", "A governed dataset is required."));
            return Optional.empty();
        }
        validateTimeBinding(dataset, definition.timeBinding(), diagnostics);
        List<SemanticAggregateProjection> projections = normalizeProjections(definition.projections(), dataset, diagnostics);
        List<SemanticPredicate> predicates = normalizePredicates(definition.predicates(), dataset, "definition.predicates", diagnostics);
        List<SemanticField> groups = normalizeGroups(definition.groupBy(), dataset, diagnostics);
        validateGroupingComposition(dataset, groups, diagnostics);
        validatePositionSemantics(dataset, projections, groups, diagnostics);
        validateCreditSemantics(dataset, projections, groups, diagnostics);
        validateCategoryBranchGrouping(dataset, groups, predicates, diagnostics);
        List<SemanticOrder> order = normalizeOrder(definition.orderBy(), groups, projections, diagnostics);
        Integer limit = normalizeLimit(definition.limit(), diagnostics);
        if (!diagnostics.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
            new SemanticQueryDefinition(
                SemanticAnalyticsSchema.VERSION,
                SemanticAnalyticsSchema.SEMANTIC_MODEL,
                dataset,
                definition.timeBinding(),
                projections,
                predicates,
                groups,
                order,
                limit
            )
        );
    }

    private Optional<ContextResolution> validateContext(
        VisualizationContext context,
        String ownerLogin,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        if (context == null) {
            diagnostics.add(diagnostic(AnalyticsDiagnosticCode.MALFORMED_REQUEST, "context", "A visualization context is required."));
            return Optional.empty();
        }
        validateVersion(context.schemaVersion(), "context.schemaVersion", diagnostics);
        Optional<AnalyticsDateRange> dateRange = resolveTime(context.time(), diagnostics);
        List<FinancialAccount> ownedAccounts = accountRepository.findAllWithToOneRelationshipsByUserLogin(ownerLogin);
        Set<Long> requestedIds = normalizedAccountIds(context.accountIds(), diagnostics);
        Set<CurrencyCode> currencies = normalizedCurrencies(context.currencyCodes(), diagnostics);
        Set<Long> ownedIds = ownedAccounts.stream().map(FinancialAccount::getId).collect(java.util.stream.Collectors.toSet());
        requestedIds
            .stream()
            .filter(id -> !ownedIds.contains(id))
            .forEach(id ->
                diagnostics.add(
                    diagnostic(
                        AnalyticsDiagnosticCode.FOREIGN_ACCOUNT_REFERENCE,
                        "context.accountIds",
                        "Account is not owned by the current user: " + id
                    )
                )
            );
        if (!diagnostics.isEmpty() || dateRange.isEmpty()) {
            return Optional.empty();
        }
        List<FinancialAccount> scopedAccounts = ownedAccounts
            .stream()
            .filter(account -> requestedIds.isEmpty() || requestedIds.contains(account.getId()))
            .filter(account -> currencies.isEmpty() || currencies.contains(account.getCurrency()))
            .sorted(Comparator.comparing(FinancialAccount::getId))
            .toList();
        return Optional.of(
            new ContextResolution(
                new ResolvedVisualizationContext(
                    dateRange.orElseThrow().from(),
                    dateRange.orElseThrow().through(),
                    scopedAccounts.stream().map(FinancialAccount::getId).toList(),
                    currencies.stream().map(Enum::name).sorted().toList()
                ),
                scopedAccounts
            )
        );
    }

    private void validateTimeBinding(SemanticDataset dataset, SemanticField timeBinding, List<AnalyticsDiagnostic> diagnostics) {
        SemanticField expected = catalog.timeBinding(dataset);
        if (timeBinding != expected) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.UNSUPPORTED_DATE_BASIS,
                    "definition.timeBinding",
                    "%s requires %s as its time binding.".formatted(dataset, expected)
                )
            );
        }
    }

    private List<SemanticAggregateProjection> normalizeProjections(
        List<SemanticAggregateProjection> projections,
        SemanticDataset dataset,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        if (projections == null || projections.isEmpty() || projections.size() > 4) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INVALID_MEASURE_COUNT,
                    "definition.projections",
                    "Choose between one and four aggregate projections."
                )
            );
            return List.of();
        }
        List<SemanticAggregateProjection> normalized = new ArrayList<>();
        Set<String> aliases = new LinkedHashSet<>();
        for (int index = 0; index < projections.size(); index++) {
            SemanticAggregateProjection projection = projections.get(index);
            String path = "definition.projections[" + index + "]";
            if (projection == null || projection.function() == null || projection.field() == null) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.INVALID_AGGREGATION, path, "An aggregate function and field are required.")
                );
                continue;
            }
            String alias = projection.alias() == null ? "" : projection.alias().trim();
            if (!alias.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.INVALID_AGGREGATION, path + ".alias", "Aliases must be simple identifiers.")
                );
            } else if (!aliases.add(alias.toLowerCase(Locale.ROOT))) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.DUPLICATE_MEASURE, path + ".alias", "Aggregate aliases must be unique.")
                );
            }
            if (!projection.field().belongsTo(dataset) || !catalog.supportsAggregate(dataset, projection.function(), projection.field())) {
                diagnostics.add(
                    diagnostic(
                        AnalyticsDiagnosticCode.INVALID_AGGREGATION,
                        path,
                        "%s(%s) is not an approved aggregate for %s.".formatted(projection.function(), projection.field(), dataset)
                    )
                );
            }
            List<SemanticPredicate> conditions = normalizePredicates(projection.conditions(), dataset, path + ".conditions", diagnostics);
            if (dataset != SemanticDataset.TRANSACTION_LEDGER && !conditions.isEmpty()) {
                diagnostics.add(
                    diagnostic(
                        AnalyticsDiagnosticCode.UNSUPPORTED_CALCULATION_CONDITION,
                        path + ".conditions",
                        "Calculation-specific conditions are currently available only for the transaction ledger."
                    )
                );
            }
            normalized.add(new SemanticAggregateProjection(alias, projection.function(), projection.field(), conditions));
        }
        return List.copyOf(normalized);
    }

    private List<SemanticPredicate> normalizePredicates(
        List<SemanticPredicate> predicates,
        SemanticDataset dataset,
        String basePath,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        if (predicates == null) return List.of();
        if (predicates.size() > 10) {
            diagnostics.add(diagnostic(AnalyticsDiagnosticCode.INVALID_PREDICATE_COUNT, basePath, "Choose at most ten predicates."));
        }
        List<SemanticPredicate> normalized = new ArrayList<>();
        for (int index = 0; index < predicates.size(); index++) {
            SemanticPredicate predicate = predicates.get(index);
            String path = basePath + "[" + index + "]";
            if (predicate == null || predicate.field() == null || predicate.operator() == null) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.MALFORMED_PREDICATE, path, "A predicate field and operator are required.")
                );
                continue;
            }
            if (!predicate.field().belongsTo(dataset) || !catalog.supportsPredicate(dataset, predicate.field(), predicate.operator())) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.UNSUPPORTED_PREDICATE, path, "This field cannot filter the selected dataset.")
                );
                continue;
            }
            List<String> values = predicate
                .values()
                .stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::normalize)
                .distinct()
                .toList();
            if (
                !predicate.operator().equals(SemanticPredicateOperator.IS_UNCATEGORIZED) &&
                !predicate.operator().equals(SemanticPredicateOperator.HAS_NONE) &&
                values.isEmpty()
            ) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.INVALID_PREDICATE_VALUE, path, "Predicate values do not match the operator.")
                );
                continue;
            }
            if (
                (predicate.operator() == SemanticPredicateOperator.EQUALS ||
                    predicate.operator() == SemanticPredicateOperator.DESCENDANT_OF) &&
                values.size() != 1
            ) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.INVALID_PREDICATE_VALUE, path, "This predicate requires exactly one value.")
                );
                continue;
            }
            if (
                (predicate.operator() == SemanticPredicateOperator.IS_UNCATEGORIZED ||
                    predicate.operator() == SemanticPredicateOperator.HAS_NONE) &&
                !values.isEmpty()
            ) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.INVALID_PREDICATE_VALUE, path, "This predicate does not accept values.")
                );
                continue;
            }
            validatePredicateValues(dataset, predicate.field(), predicate.operator(), values, path, diagnostics);
            normalized.add(new SemanticPredicate(predicate.field(), predicate.operator(), values));
        }
        return List.copyOf(normalized);
    }

    private List<SemanticField> normalizeGroups(
        List<SemanticField> groupBy,
        SemanticDataset dataset,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        if (groupBy == null) return List.of();
        if (groupBy.size() > 2) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INVALID_GROUPING_COUNT,
                    "definition.groupBy",
                    "Choose a primary breakdown and, optionally, one comparison breakdown."
                )
            );
        }
        Set<SemanticField> seen = EnumSet.noneOf(SemanticField.class);
        List<SemanticField> normalized = new ArrayList<>();
        for (int index = 0; index < groupBy.size(); index++) {
            SemanticField field = groupBy.get(index);
            String path = "definition.groupBy[" + index + "]";
            if (field == null || !field.belongsTo(dataset)) {
                diagnostics.add(diagnostic(AnalyticsDiagnosticCode.UNKNOWN_FIELD, path, "The grouping is not exposed by this dataset."));
            } else if (!catalog.supportsGrouping(dataset, field)) {
                diagnostics.add(diagnostic(AnalyticsDiagnosticCode.UNSUPPORTED_GROUPING, path, "The field cannot group this dataset."));
            } else if (!seen.add(field)) {
                diagnostics.add(diagnostic(AnalyticsDiagnosticCode.DUPLICATE_GROUPING, path, "Groupings must be unique."));
            } else {
                normalized.add(field);
            }
        }
        return List.copyOf(normalized);
    }

    private void validateGroupingComposition(SemanticDataset dataset, List<SemanticField> groups, List<AnalyticsDiagnostic> diagnostics) {
        long temporalGroups =
            switch (dataset) {
                case TRANSACTION_LEDGER -> groups.stream().filter(this::isTransactionTemporalGrouping).count();
                case INTERNAL_TRANSFER_EVENT -> groups.stream().filter(this::isTransferTemporalGrouping).count();
                default -> 0;
            };
        if (temporalGroups > 1) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.UNSUPPORTED_GROUPING_COMBINATION,
                    "definition.groupBy",
                    "Choose one time breakdown and, optionally, one comparison breakdown."
                )
            );
        }
    }

    private boolean isTransactionTemporalGrouping(SemanticField field) {
        return switch (field) {
            case TRANSACTION_DAY, TRANSACTION_WEEK, TRANSACTION_MONTH, TRANSACTION_YEAR -> true;
            default -> false;
        };
    }

    private boolean isTransferTemporalGrouping(SemanticField field) {
        return switch (field) {
            case TRANSFER_DAY, TRANSFER_WEEK, TRANSFER_MONTH, TRANSFER_YEAR -> true;
            default -> false;
        };
    }

    private void validatePositionSemantics(
        SemanticDataset dataset,
        List<SemanticAggregateProjection> projections,
        List<SemanticField> groups,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        if (dataset != SemanticDataset.ACCOUNT_POSITION_SNAPSHOT) return;
        if (
            projections.size() != 1 ||
            projections.get(0).function() != SemanticAggregateFunction.SUM ||
            projections.get(0).field() != SemanticField.POSITION_BALANCE
        ) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.NON_ADDITIVE_TIME_AGGREGATION,
                    "definition.projections",
                    "Account positions support only SUM(POSITION_BALANCE) in this POC."
                )
            );
        }
        if (!groups.contains(SemanticField.ACCOUNT_ID) || !groups.contains(SemanticField.OBSERVATION_MONTH)) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.NON_ADDITIVE_TIME_AGGREGATION,
                    "definition.groupBy",
                    "Historical balances must group by both ACCOUNT_ID and OBSERVATION_MONTH."
                )
            );
        }
    }

    private void validateCreditSemantics(
        SemanticDataset dataset,
        List<SemanticAggregateProjection> projections,
        List<SemanticField> groups,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        if (dataset != SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT) return;
        if (!groups.contains(SemanticField.ACCOUNT_ID)) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INVALID_CREDIT_CALCULATION,
                    "definition.groupBy",
                    "Credit observations must retain ACCOUNT_ID so point-in-time values are never added across accounts."
                )
            );
        }
        boolean includesLimitDerivedValue = projections
            .stream()
            .map(SemanticAggregateProjection::field)
            .anyMatch(this::isCreditLimitDerivedField);
        if (includesLimitDerivedValue && groups.contains(SemanticField.OBSERVATION_MONTH)) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INVALID_CREDIT_CALCULATION,
                    "definition.groupBy",
                    "Credit limit, available credit and utilization do not support historical month grouping."
                )
            );
        }
    }

    private void validateContextSpecificSemantics(
        SemanticQueryDefinition definition,
        ResolvedVisualizationContext context,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        if (definition.dataset() != SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT) return;
        boolean includesLimitDerivedValue = definition
            .projections()
            .stream()
            .map(SemanticAggregateProjection::field)
            .anyMatch(this::isCreditLimitDerivedField);
        if (includesLimitDerivedValue && !(context.from().equals(timePolicy.today()) && context.through().equals(timePolicy.today()))) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INCOMPATIBLE_HISTORICAL_CREDIT_LIMIT,
                    "context.time",
                    "Credit limit, available credit and utilization are current-only because FINTRACK has no credit-limit history."
                )
            );
        }
    }

    private boolean isCreditLimitDerivedField(SemanticField field) {
        return (
            field == SemanticField.CREDIT_LIMIT ||
            field == SemanticField.CREDIT_AVAILABLE ||
            field == SemanticField.CREDIT_UTILIZATION_PERCENT
        );
    }

    private void validateCategoryBranchGrouping(
        SemanticDataset dataset,
        List<SemanticField> groups,
        List<SemanticPredicate> predicates,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        if (dataset != SemanticDataset.TRANSACTION_LEDGER || !groups.contains(SemanticField.CATEGORY_BRANCH_CHILD)) return;
        long branchRoots = predicates
            .stream()
            .filter(predicate -> predicate.field() == SemanticField.CATEGORY_ID)
            .filter(predicate -> predicate.operator() == SemanticPredicateOperator.DESCENDANT_OF)
            .count();
        if (branchRoots != 1) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INVALID_CATEGORY_BRANCH_GROUPING,
                    "definition.groupBy",
                    "Grouping children of a category requires exactly one global CATEGORY_ID DESCENDANT_OF condition."
                )
            );
        }
    }

    private List<SemanticOrder> normalizeOrder(
        List<SemanticOrder> orderBy,
        List<SemanticField> groups,
        List<SemanticAggregateProjection> projections,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        if (orderBy == null || orderBy.isEmpty()) {
            return groups.stream().map(field -> new SemanticOrder(field, SemanticSortDirection.ASC)).toList();
        }
        List<SemanticOrder> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < orderBy.size(); index++) {
            SemanticOrder order = orderBy.get(index);
            String path = "definition.orderBy[" + index + "]";
            if (order == null || order.direction() == null) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.INVALID_SORT, path, "Choose a grouping or calculation result to order.")
                );
                continue;
            }
            boolean groupingOrder = order.field() != null && (order.projectionAlias() == null || order.projectionAlias().isBlank());
            boolean projectionOrder = order.field() == null && order.projectionAlias() != null && !order.projectionAlias().isBlank();
            if (groupingOrder && groups.contains(order.field()) && seen.add("field:" + order.field().name())) {
                normalized.add(new SemanticOrder(order.field(), order.direction()));
                continue;
            }
            if (projectionOrder) {
                String alias = order.projectionAlias().trim();
                Optional<String> matchingAlias = projections
                    .stream()
                    .map(SemanticAggregateProjection::alias)
                    .filter(candidate -> candidate.equalsIgnoreCase(alias))
                    .findFirst();
                if (matchingAlias.isPresent() && seen.add("projection:" + matchingAlias.orElseThrow().toLowerCase(Locale.ROOT))) {
                    normalized.add(SemanticOrder.projection(matchingAlias.orElseThrow(), order.direction()));
                    continue;
                }
            } else {
                diagnostics.add(
                    diagnostic(
                        AnalyticsDiagnosticCode.INVALID_SORT,
                        path,
                        "Order fields must be unique requested groupings, or name one selected calculation result."
                    )
                );
                continue;
            }
            diagnostics.add(
                diagnostic(AnalyticsDiagnosticCode.INVALID_SORT, path, "Order results must be selected calculation aliases and unique.")
            );
        }
        return List.copyOf(normalized);
    }

    private Integer normalizeLimit(Integer limit, List<AnalyticsDiagnostic> diagnostics) {
        int normalized = limit == null ? SemanticAnalyticsSchema.DEFAULT_LIMIT : limit;
        if (normalized < 1 || normalized > SemanticAnalyticsSchema.MAX_LIMIT) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INVALID_LIMIT,
                    "definition.limit",
                    "Limit must be between 1 and %d.".formatted(SemanticAnalyticsSchema.MAX_LIMIT)
                )
            );
        }
        return normalized;
    }

    private Optional<AnalyticsDateRange> resolveTime(VisualizationTime time, List<AnalyticsDiagnostic> diagnostics) {
        if (time == null) {
            diagnostics.add(diagnostic(AnalyticsDiagnosticCode.INVALID_TIME_CONTEXT, "context.time", "A time selection is required."));
            return Optional.empty();
        }
        String mode = normalize(time.mode());
        try {
            if ("RELATIVE".equals(mode)) {
                if (time.from() != null || time.through() != null || time.relativePeriod() == null) {
                    diagnostics.add(
                        diagnostic(
                            AnalyticsDiagnosticCode.INVALID_TIME_CONTEXT,
                            "context.time",
                            "A relative period may not carry explicit dates."
                        )
                    );
                    return Optional.empty();
                }
                return Optional.of(timePolicy.resolveRelative(normalize(time.relativePeriod())));
            }
            if ("ABSOLUTE_RANGE".equals(mode)) {
                if (time.relativePeriod() != null || time.from() == null || time.through() == null || time.from().isAfter(time.through())) {
                    diagnostics.add(
                        diagnostic(
                            AnalyticsDiagnosticCode.INVALID_DATE_RANGE,
                            "context.time",
                            "An absolute range needs only inclusive from and through dates."
                        )
                    );
                    return Optional.empty();
                }
                if (time.through().isAfter(timePolicy.today())) {
                    diagnostics.add(
                        diagnostic(
                            AnalyticsDiagnosticCode.FUTURE_OBSERVATION,
                            "context.time.through",
                            "Future observations are not defined."
                        )
                    );
                    return Optional.empty();
                }
                return Optional.of(new AnalyticsDateRange(time.from(), time.through()));
            }
        } catch (IllegalArgumentException exception) {
            diagnostics.add(
                diagnostic(AnalyticsDiagnosticCode.UNKNOWN_RELATIVE_PERIOD, "context.time.relativePeriod", exception.getMessage())
            );
            return Optional.empty();
        }
        diagnostics.add(diagnostic(AnalyticsDiagnosticCode.INVALID_TIME_CONTEXT, "context.time.mode", "Use RELATIVE or ABSOLUTE_RANGE."));
        return Optional.empty();
    }

    private Set<Long> normalizedAccountIds(List<Long> ids, List<AnalyticsDiagnostic> diagnostics) {
        if (ids == null) return Set.of();
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.INVALID_REFERENCE_ID, "context.accountIds", "Account IDs must be positive.")
                );
            } else {
                normalized.add(id);
            }
        }
        return Set.copyOf(normalized);
    }

    private Set<CurrencyCode> normalizedCurrencies(List<String> currencyCodes, List<AnalyticsDiagnostic> diagnostics) {
        if (currencyCodes == null) return Set.of();
        Set<CurrencyCode> normalized = EnumSet.noneOf(CurrencyCode.class);
        for (String currencyCode : currencyCodes) {
            try {
                normalized.add(CurrencyCode.valueOf(normalize(currencyCode)));
            } catch (IllegalArgumentException exception) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.INVALID_CURRENCY_CODE, "context.currencyCodes", "Unknown currency: " + currencyCode)
                );
            }
        }
        return Set.copyOf(normalized);
    }

    private void validatePredicateValues(
        SemanticDataset dataset,
        SemanticField field,
        SemanticPredicateOperator operator,
        List<String> values,
        String path,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        try {
            switch (field) {
                case TRANSACTION_FLOW -> values.forEach(TransactionFlow::valueOf);
                case TRANSFER_LINK_STATUS -> {
                    if (!Set.of("LINKED", "UNLINKED").containsAll(values)) {
                        throw new IllegalArgumentException("Transfer link status must be LINKED or UNLINKED.");
                    }
                }
                case ACCOUNT_TYPE -> {
                    Set<AccountType> accountTypes = EnumSet.noneOf(AccountType.class);
                    values.forEach(value -> accountTypes.add(AccountType.valueOf(value)));
                    if (dataset == SemanticDataset.ACCOUNT_POSITION_SNAPSHOT && !ASSET_ACCOUNT_TYPES.containsAll(accountTypes)) {
                        throw new IllegalArgumentException("Historical balance positions are available only for asset account types.");
                    }
                    if (dataset == SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT && !Set.of(AccountType.CREDIT_CARD).containsAll(accountTypes)) {
                        throw new IllegalArgumentException("Credit observations are available only for credit-card accounts.");
                    }
                }
                case SOURCE_ACCOUNT_TYPE, DESTINATION_ACCOUNT_TYPE -> values.forEach(AccountType::valueOf);
                case CATEGORY_ID -> {
                    if (
                        !Set.of(
                            SemanticPredicateOperator.EQUALS,
                            SemanticPredicateOperator.IN,
                            SemanticPredicateOperator.DESCENDANT_OF,
                            SemanticPredicateOperator.IS_UNCATEGORIZED
                        ).contains(operator)
                    ) {
                        throw new IllegalArgumentException("Unsupported category condition.");
                    }
                    values.forEach(this::positiveReferenceId);
                }
                case TAG_ID -> {
                    if (
                        !Set.of(
                            SemanticPredicateOperator.HAS_ANY,
                            SemanticPredicateOperator.HAS_ALL,
                            SemanticPredicateOperator.HAS_NONE
                        ).contains(operator)
                    ) {
                        throw new IllegalArgumentException("Unsupported tag condition.");
                    }
                    values.forEach(this::positiveReferenceId);
                }
                default -> throw new IllegalArgumentException("The predicate field is not registered.");
            }
        } catch (IllegalArgumentException exception) {
            diagnostics.add(diagnostic(AnalyticsDiagnosticCode.INVALID_PREDICATE_VALUE, path + ".values", exception.getMessage()));
        }
    }

    private void validateOwnerReferences(
        SemanticQueryDefinition definition,
        SemanticAnalyticsReferenceCatalog references,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        validateOwnerReferences(definition.predicates(), references, "definition.predicates", diagnostics);
        for (int index = 0; index < definition.projections().size(); index++) {
            validateOwnerReferences(
                definition.projections().get(index).conditions(),
                references,
                "definition.projections[" + index + "].conditions",
                diagnostics
            );
        }
    }

    private void validateOwnerReferences(
        List<SemanticPredicate> predicates,
        SemanticAnalyticsReferenceCatalog references,
        String basePath,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        for (int predicateIndex = 0; predicateIndex < predicates.size(); predicateIndex++) {
            SemanticPredicate predicate = predicates.get(predicateIndex);
            if (predicate.field() != SemanticField.CATEGORY_ID && predicate.field() != SemanticField.TAG_ID) continue;
            for (String value : predicate.values()) {
                Long id = Long.valueOf(value);
                boolean owned = predicate.field() == SemanticField.CATEGORY_ID ? references.hasCategory(id) : references.hasTag(id);
                if (!owned) {
                    diagnostics.add(
                        diagnostic(
                            predicate.field() == SemanticField.CATEGORY_ID
                                ? AnalyticsDiagnosticCode.FOREIGN_CATEGORY_REFERENCE
                                : AnalyticsDiagnosticCode.FOREIGN_TAG_REFERENCE,
                            basePath + "[" + predicateIndex + "].values",
                            "Referenced financial classification is not owned by the current user."
                        )
                    );
                }
            }
        }
    }

    private void positiveReferenceId(String value) {
        long id = Long.parseLong(value);
        if (id <= 0) throw new IllegalArgumentException("Reference IDs must be positive.");
    }

    private void validateVersion(Integer version, String path, List<AnalyticsDiagnostic> diagnostics) {
        if (version == null || version != SemanticAnalyticsSchema.VERSION) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.UNKNOWN_SCHEMA_VERSION,
                    path,
                    "Only semantic Analytics schema version %d is supported.".formatted(SemanticAnalyticsSchema.VERSION)
                )
            );
        }
    }

    private AnalyticsDiagnostic diagnostic(AnalyticsDiagnosticCode code, String field, String message) {
        return new AnalyticsDiagnostic(code, field, message);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record ContextResolution(ResolvedVisualizationContext context, List<FinancialAccount> accounts) {}
}
