package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.CurrentUserService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates and normalizes the clean Analytics contracts. It resolves references only for authorization; it never
 * reads or aggregates financial values.
 */
@Service
@Transactional(readOnly = true)
public class FinancialAnalyticsValidator {

    private final FinancialSemanticCatalog catalog;
    private final FinancialMetricTimePolicy timePolicy;
    private final CurrentUserService currentUserService;
    private final FinancialAccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    public FinancialAnalyticsValidator(
        FinancialSemanticCatalog catalog,
        FinancialMetricTimePolicy timePolicy,
        CurrentUserService currentUserService,
        FinancialAccountRepository accountRepository,
        CategoryRepository categoryRepository,
        TagRepository tagRepository
    ) {
        this.catalog = catalog;
        this.timePolicy = timePolicy;
        this.currentUserService = currentUserService;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
    }

    public AnalyticsValidationResult validate(FinancialAnalyticsExecutionRequest request) {
        return validateForOwner(request, currentUserService.getCurrentUserLogin());
    }

    public ValidatedFinancialAnalyticsRequest validateOrThrow(FinancialAnalyticsExecutionRequest request) {
        AnalyticsValidationResult result = validate(request);
        if (!result.isValid()) {
            throw new FinancialAnalyticsValidationException(result.diagnostics());
        }
        return result.normalizedRequest().orElseThrow();
    }

    /**
     * Validates the context-free part of the canonical contract. This is deliberately shared by execution, the DSL,
     * and the future visual builder: a definition never needs a fabricated time/account/currency context to be valid.
     */
    public AnalyticsDefinitionValidationResult validateDefinition(QueryDefinition definition) {
        return validateDefinitionForOwner(definition, currentUserService.getCurrentUserLogin());
    }

    /** Visible for real PostgreSQL tests; administrators deliberately use their own login as the ownership scope. */
    AnalyticsDefinitionValidationResult validateDefinitionForOwner(QueryDefinition definition, String ownerLogin) {
        List<AnalyticsDiagnostic> diagnostics = new ArrayList<>();
        if (definition == null) {
            diagnostics.add(diagnostic(AnalyticsDiagnosticCode.MALFORMED_REQUEST, "definition", "A query definition is required."));
            return invalidDefinition(diagnostics);
        }

        List<String> measures = normalizeIds(definition.measures());
        List<String> groupings = normalizeIds(definition.groupings());
        String dateBasis = normalizeIdOrDefault(definition.dateBasis(), FinancialSemanticCatalog.TRANSACTION_DATE);
        List<FinancialMetricDefinition> metricDefinitions = validateDefinition(definition, measures, groupings, dateBasis, diagnostics);
        validateCompatibility(metricDefinitions, groupings, dateBasis, diagnostics);
        List<AnalyticalPredicate> predicates = validatePredicates(
            definition.analyticalPredicates(),
            metricDefinitions,
            ownerLogin,
            diagnostics
        );
        if (!diagnostics.isEmpty()) {
            return invalidDefinition(diagnostics);
        }
        return new AnalyticsDefinitionValidationResult(
            Optional.of(
                new QueryDefinition(
                    AnalyticsSchema.VERSION,
                    AnalyticsSchema.SEMANTIC_MODEL,
                    measures,
                    groupings,
                    predicates,
                    dateBasis,
                    normalizeSort(definition.sort(), groupings),
                    definition.limit() == null ? AnalyticsSchema.DEFAULT_LIMIT : definition.limit()
                )
            ),
            List.of()
        );
    }

    /** Visible for real PostgreSQL tests; administrators deliberately use their own login as the ownership scope. */
    AnalyticsValidationResult validateForOwner(FinancialAnalyticsExecutionRequest request, String ownerLogin) {
        List<AnalyticsDiagnostic> diagnostics = new ArrayList<>();
        if (request == null) {
            diagnostics.add(diagnostic(AnalyticsDiagnosticCode.MALFORMED_REQUEST, "request", "A request is required."));
            return invalid(diagnostics);
        }
        validateSchema(request.schemaVersion(), "schemaVersion", diagnostics);
        QueryDefinition definition = request.definition();
        VisualizationContext context = request.context();
        if (definition == null) {
            diagnostics.add(diagnostic(AnalyticsDiagnosticCode.MALFORMED_REQUEST, "definition", "A query definition is required."));
        }
        if (context == null) {
            diagnostics.add(diagnostic(AnalyticsDiagnosticCode.MALFORMED_REQUEST, "context", "A visualization context is required."));
        }
        if (definition == null || context == null) {
            return invalid(diagnostics);
        }

        AnalyticsDefinitionValidationResult definitionValidation = validateDefinitionForOwner(definition, ownerLogin);
        diagnostics.addAll(definitionValidation.diagnostics());
        ContextValidation contextValidation = validateContext(context, ownerLogin, diagnostics);
        if (!diagnostics.isEmpty()) {
            return invalid(diagnostics);
        }
        QueryDefinition normalizedDefinition = definitionValidation.normalizedDefinition().orElseThrow();
        VisualizationContext normalizedContext = new VisualizationContext(
            AnalyticsSchema.VERSION,
            contextValidation.normalizedTime(),
            contextValidation.accountIds(),
            contextValidation.currencyCodes()
        );
        FinancialAnalyticsExecutionRequest normalizedRequest = new FinancialAnalyticsExecutionRequest(
            AnalyticsSchema.VERSION,
            normalizedDefinition,
            normalizedContext
        );
        return new AnalyticsValidationResult(
            Optional.of(new ValidatedFinancialAnalyticsRequest(normalizedRequest, contextValidation.resolvedContext())),
            List.of()
        );
    }

    private List<FinancialMetricDefinition> validateDefinition(
        QueryDefinition definition,
        List<String> measures,
        List<String> groupings,
        String dateBasis,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        validateSchema(definition.schemaVersion(), "definition.schemaVersion", diagnostics);
        if (!AnalyticsSchema.SEMANTIC_MODEL.equals(normalizeId(definition.semanticModel()))) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.UNKNOWN_SEMANTIC_MODEL,
                    "definition.semanticModel",
                    "The query must use the FINTRACK_FINANCIAL semantic model."
                )
            );
        }
        if (measures.isEmpty() || measures.size() > AnalyticsSchema.MAX_MEASURES) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INVALID_MEASURE_COUNT,
                    "definition.measures",
                    "Choose between one and %d metrics.".formatted(AnalyticsSchema.MAX_MEASURES)
                )
            );
        }
        rejectDuplicates(measures, AnalyticsDiagnosticCode.DUPLICATE_MEASURE, "definition.measures", diagnostics);
        if (groupings.size() > AnalyticsSchema.MAX_GROUPINGS) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INVALID_GROUPING_COUNT,
                    "definition.groupings",
                    "Choose at most %d groupings.".formatted(AnalyticsSchema.MAX_GROUPINGS)
                )
            );
        }
        rejectDuplicates(groupings, AnalyticsDiagnosticCode.DUPLICATE_GROUPING, "definition.groupings", diagnostics);
        if (definition.limit() != null && (definition.limit() < 1 || definition.limit() > AnalyticsSchema.MAX_LIMIT)) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INVALID_LIMIT,
                    "definition.limit",
                    "Limit must be between 1 and %d.".formatted(AnalyticsSchema.MAX_LIMIT)
                )
            );
        }
        List<FinancialMetricDefinition> metricDefinitions = new ArrayList<>();
        for (String measure : measures) {
            catalog
                .metric(measure)
                .ifPresentOrElse(metricDefinitions::add, () ->
                    diagnostics.add(diagnostic(AnalyticsDiagnosticCode.UNKNOWN_METRIC, "definition.measures", "Unknown metric: " + measure))
                );
        }
        if (!FinancialSemanticCatalog.TRANSACTION_DATE.equals(dateBasis) && !FinancialSemanticCatalog.POSTING_DATE.equals(dateBasis)) {
            diagnostics.add(
                diagnostic(AnalyticsDiagnosticCode.UNSUPPORTED_DATE_BASIS, "definition.dateBasis", "Unsupported date basis: " + dateBasis)
            );
        }
        for (String grouping : groupings) {
            FinancialSemanticAttributeDefinition attribute = catalog.attribute(grouping).orElse(null);
            if (attribute == null) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.UNKNOWN_ATTRIBUTE, "definition.groupings", "Unknown grouping: " + grouping)
                );
            } else if (!attribute.groupingSupported()) {
                diagnostics.add(
                    diagnostic(
                        AnalyticsDiagnosticCode.UNSUPPORTED_GROUPING,
                        "definition.groupings",
                        "Attribute cannot be used as a grouping: " + grouping
                    )
                );
            }
        }
        validateSort(definition.sort(), groupings, diagnostics);
        return metricDefinitions;
    }

    private void validateCompatibility(
        List<FinancialMetricDefinition> metrics,
        List<String> groupings,
        String dateBasis,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        if (metrics.isEmpty()) {
            return;
        }
        FinancialMetricDefinition first = metrics.get(0);
        for (FinancialMetricDefinition metric : metrics) {
            if (!metric.supportedDateBases().contains(dateBasis)) {
                diagnostics.add(
                    diagnostic(
                        AnalyticsDiagnosticCode.UNSUPPORTED_DATE_BASIS,
                        "definition.dateBasis",
                        "%s does not support %s.".formatted(metric.id(), dateBasis)
                    )
                );
            }
            for (String grouping : groupings) {
                if (!metric.compatibleGroupings().contains(grouping)) {
                    diagnostics.add(
                        diagnostic(
                            AnalyticsDiagnosticCode.UNSUPPORTED_GROUPING,
                            "definition.groupings",
                            "%s is not compatible with %s.".formatted(grouping, metric.id())
                        )
                    );
                }
            }
            if (metric.timeMode() != first.timeMode()) {
                diagnostics.add(
                    diagnostic(
                        AnalyticsDiagnosticCode.INCOMPATIBLE_METRIC_TIME_MODE,
                        "definition.measures",
                        "Metrics with PERIOD and AS_OF time modes cannot share one query."
                    )
                );
            }
            if (metric.executorSpecialization() != first.executorSpecialization()) {
                diagnostics.add(
                    diagnostic(
                        AnalyticsDiagnosticCode.INCOMPATIBLE_METRIC_EXECUTOR,
                        "definition.measures",
                        "Metrics requiring different financial executors cannot share one query."
                    )
                );
            }
            if (metric.unit() != first.unit() || metric.currencyBehavior() != first.currencyBehavior()) {
                diagnostics.add(
                    diagnostic(
                        AnalyticsDiagnosticCode.INCOMPATIBLE_CURRENCY_BEHAVIOR,
                        "definition.measures",
                        "Metrics with different result units or currency partitioning cannot share one query."
                    )
                );
            }
        }
    }

    private List<AnalyticalPredicate> validatePredicates(
        List<AnalyticalPredicate> predicates,
        List<FinancialMetricDefinition> metrics,
        String ownerLogin,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        if (predicates.size() > AnalyticsSchema.MAX_PREDICATES) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INVALID_PREDICATE_COUNT,
                    "definition.analyticalPredicates",
                    "Choose at most %d predicates.".formatted(AnalyticsSchema.MAX_PREDICATES)
                )
            );
        }
        List<AnalyticalPredicate> normalized = new ArrayList<>();
        for (int index = 0; index < predicates.size(); index++) {
            AnalyticalPredicate predicate = predicates.get(index);
            String field = "definition.analyticalPredicates[" + index + "]";
            if (predicate == null) {
                diagnostics.add(diagnostic(AnalyticsDiagnosticCode.MALFORMED_PREDICATE, field, "A predicate cannot be null."));
                continue;
            }
            String attributeId = normalizeId(predicate.attributeId());
            String operatorId = normalizeId(predicate.operatorId());
            FinancialSemanticAttributeDefinition attribute = catalog.attribute(attributeId).orElse(null);
            if (attribute == null) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.UNKNOWN_ATTRIBUTE, field + ".attributeId", "Unknown attribute: " + attributeId)
                );
                continue;
            }
            if (!attribute.predicateSupported()) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.UNSUPPORTED_PREDICATE, field, "Attribute cannot be filtered: " + attributeId)
                );
                continue;
            }
            if (!attribute.allowedOperators().contains(operatorId)) {
                AnalyticsDiagnosticCode code = List.of(
                        FinancialSemanticCatalog.EQUALS,
                        FinancialSemanticCatalog.IN,
                        FinancialSemanticCatalog.BETWEEN
                    ).contains(operatorId)
                    ? AnalyticsDiagnosticCode.UNSUPPORTED_PREDICATE
                    : AnalyticsDiagnosticCode.UNKNOWN_OPERATOR;
                diagnostics.add(
                    diagnostic(code, field + ".operatorId", "Unsupported operator for %s: %s".formatted(attributeId, operatorId))
                );
                continue;
            }
            if (metrics.stream().anyMatch(metric -> !metric.supportedPredicates().contains(attributeId))) {
                diagnostics.add(
                    diagnostic(
                        AnalyticsDiagnosticCode.UNSUPPORTED_PREDICATE,
                        field,
                        "The selected metrics do not all support predicate " + attributeId + "."
                    )
                );
            }
            List<String> values = normalizePredicateValues(attributeId, operatorId, predicate.values(), field, ownerLogin, diagnostics);
            normalized.add(new AnalyticalPredicate(attributeId, operatorId, values));
        }
        return normalized
            .stream()
            .sorted(
                Comparator.comparing(AnalyticalPredicate::attributeId)
                    .thenComparing(AnalyticalPredicate::operatorId)
                    .thenComparing(predicate -> String.join("\u0001", predicate.values()))
            )
            .toList();
    }

    private ContextValidation validateContext(VisualizationContext context, String ownerLogin, List<AnalyticsDiagnostic> diagnostics) {
        validateSchema(context.schemaVersion(), "context.schemaVersion", diagnostics);
        VisualizationTime time = context.time();
        AnalyticsDateRange dateRange = null;
        VisualizationTime normalizedTime = null;
        if (time == null) {
            diagnostics.add(
                diagnostic(AnalyticsDiagnosticCode.INVALID_TIME_CONTEXT, "context.time", "A visualization time selection is required.")
            );
        } else {
            String mode = normalizeId(time.mode());
            if ("RELATIVE".equals(mode)) {
                if (time.from() != null || time.through() != null) {
                    diagnostics.add(
                        diagnostic(
                            AnalyticsDiagnosticCode.INVALID_TIME_CONTEXT,
                            "context.time",
                            "Relative time cannot include explicit dates."
                        )
                    );
                }
                String relativePeriod = normalizeId(time.relativePeriod());
                try {
                    dateRange = timePolicy.resolveRelative(relativePeriod);
                    normalizedTime = new VisualizationTime("RELATIVE", relativePeriod, null, null);
                } catch (IllegalArgumentException exception) {
                    diagnostics.add(
                        diagnostic(
                            AnalyticsDiagnosticCode.UNKNOWN_RELATIVE_PERIOD,
                            "context.time.relativePeriod",
                            "Unsupported relative period: " + relativePeriod
                        )
                    );
                }
            } else if ("ABSOLUTE_RANGE".equals(mode)) {
                if (time.relativePeriod() != null) {
                    diagnostics.add(
                        diagnostic(
                            AnalyticsDiagnosticCode.INVALID_TIME_CONTEXT,
                            "context.time",
                            "Absolute time cannot include a relative period."
                        )
                    );
                }
                if (time.from() == null || time.through() == null || time.from().isAfter(time.through())) {
                    diagnostics.add(
                        diagnostic(
                            AnalyticsDiagnosticCode.INVALID_DATE_RANGE,
                            "context.time",
                            "Use an inclusive from date on or before through date."
                        )
                    );
                } else {
                    if (ChronoUnit.DAYS.between(time.from(), time.through()) > AnalyticsSchema.MAX_DATE_RANGE_DAYS) {
                        diagnostics.add(
                            diagnostic(
                                AnalyticsDiagnosticCode.INVALID_DATE_RANGE,
                                "context.time",
                                "The requested date range exceeds the supported ten-year limit."
                            )
                        );
                    }
                    if (time.through().isAfter(timePolicy.today())) {
                        diagnostics.add(
                            diagnostic(
                                AnalyticsDiagnosticCode.INVALID_DATE_RANGE,
                                "context.time",
                                "Future analytics periods are not supported."
                            )
                        );
                    }
                    dateRange = new AnalyticsDateRange(time.from(), time.through());
                    normalizedTime = new VisualizationTime("ABSOLUTE_RANGE", null, time.from(), time.through());
                }
            } else {
                diagnostics.add(
                    diagnostic(
                        AnalyticsDiagnosticCode.INVALID_TIME_CONTEXT,
                        "context.time.mode",
                        "Time mode must be RELATIVE or ABSOLUTE_RANGE."
                    )
                );
            }
        }
        List<Long> accounts = normalizeAccountIds(context.accountIds(), ownerLogin, diagnostics);
        List<String> currencies = normalizeCurrencies(context.currencyCodes(), "context.currencyCodes", diagnostics);
        if (dateRange == null || normalizedTime == null) {
            return new ContextValidation(
                new VisualizationTime("ABSOLUTE_RANGE", null, null, null),
                List.of(),
                List.of(),
                new ResolvedVisualizationContext(LocalDate.MIN, LocalDate.MIN, List.of(), List.of())
            );
        }
        return new ContextValidation(
            normalizedTime,
            accounts,
            currencies,
            new ResolvedVisualizationContext(dateRange.from(), dateRange.through(), accounts, currencies)
        );
    }

    private List<String> normalizePredicateValues(
        String attributeId,
        String operatorId,
        List<String> rawValues,
        String field,
        String ownerLogin,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        List<String> values = rawValues == null ? List.of() : rawValues.stream().map(this::normalizeId).toList();
        int expectedValues = FinancialSemanticCatalog.BETWEEN.equals(operatorId)
            ? 2
            : FinancialSemanticCatalog.EQUALS.equals(operatorId) ? 1 : -1;
        if (values.isEmpty() || (expectedValues != -1 && values.size() != expectedValues)) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.MALFORMED_PREDICATE,
                    field + ".values",
                    "Operator " + operatorId + " has an invalid value count."
                )
            );
            return values;
        }
        if (values.size() > AnalyticsSchema.MAX_REFERENCE_VALUES) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INVALID_CONTEXT,
                    field + ".values",
                    "A predicate may reference at most %d values.".formatted(AnalyticsSchema.MAX_REFERENCE_VALUES)
                )
            );
        }
        if (FinancialSemanticCatalog.BETWEEN.equals(operatorId) && isDateAttribute(attributeId) && values.size() == 2) {
            try {
                if (LocalDate.parse(values.get(0)).isAfter(LocalDate.parse(values.get(1)))) {
                    diagnostics.add(
                        diagnostic(
                            AnalyticsDiagnosticCode.INVALID_DATE_RANGE,
                            field + ".values",
                            "The first date must not be after the second date."
                        )
                    );
                }
            } catch (DateTimeParseException ignored) {
                // The per-value validation below gives the stable public diagnostic.
            }
        }
        for (String value : values) {
            validatePredicateValue(attributeId, value, field + ".values", ownerLogin, diagnostics);
        }
        return FinancialSemanticCatalog.BETWEEN.equals(operatorId) ? values : values.stream().distinct().sorted().toList();
    }

    private void validatePredicateValue(
        String attributeId,
        String value,
        String field,
        String ownerLogin,
        List<AnalyticsDiagnostic> diagnostics
    ) {
        switch (attributeId) {
            case FinancialSemanticCatalog.ACCOUNT -> validateOwnedAccount(value, field, ownerLogin, diagnostics);
            case FinancialSemanticCatalog.CATEGORY, FinancialSemanticCatalog.ROOT_CATEGORY -> validateOwnedCategory(
                value,
                field,
                ownerLogin,
                diagnostics
            );
            case FinancialSemanticCatalog.TAG -> validateOwnedTag(value, field, ownerLogin, diagnostics);
            case FinancialSemanticCatalog.CURRENCY -> validateEnum(CurrencyCode.class, value, field, diagnostics);
            case FinancialSemanticCatalog.ACCOUNT_TYPE -> validateEnum(AccountType.class, value, field, diagnostics);
            case FinancialSemanticCatalog.FLOW -> validateEnum(TransactionFlow.class, value, field, diagnostics);
            case FinancialSemanticCatalog.ORIGIN -> validateEnum(TransactionOrigin.class, value, field, diagnostics);
            case FinancialSemanticCatalog.TRANSFER_STATUS -> {
                if (!Set.of("LINKED", "NOT_LINKED").contains(value)) {
                    diagnostics.add(
                        diagnostic(AnalyticsDiagnosticCode.INVALID_PREDICATE_VALUE, field, "Transfer status must be LINKED or NOT_LINKED.")
                    );
                }
            }
            case FinancialSemanticCatalog.TRANSACTION_DATE, FinancialSemanticCatalog.POSTING_DATE -> validateDate(
                value,
                field,
                diagnostics
            );
            default -> diagnostics.add(diagnostic(AnalyticsDiagnosticCode.UNKNOWN_ATTRIBUTE, field, "Unknown attribute: " + attributeId));
        }
    }

    private List<Long> normalizeAccountIds(List<Long> accountIds, String ownerLogin, List<AnalyticsDiagnostic> diagnostics) {
        List<Long> normalized = accountIds == null
            ? List.of()
            : accountIds.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList();
        if (normalized.size() > AnalyticsSchema.MAX_REFERENCE_VALUES) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INVALID_CONTEXT,
                    "context.accountIds",
                    "Select at most %d accounts.".formatted(AnalyticsSchema.MAX_REFERENCE_VALUES)
                )
            );
        }
        if (accountIds != null && accountIds.stream().anyMatch(id -> id == null || id <= 0)) {
            diagnostics.add(
                diagnostic(AnalyticsDiagnosticCode.INVALID_REFERENCE_ID, "context.accountIds", "Account IDs must be positive.")
            );
        }
        for (Long accountId : normalized) {
            validateOwnedAccount(String.valueOf(accountId), "context.accountIds", ownerLogin, diagnostics);
        }
        return normalized;
    }

    private List<String> normalizeCurrencies(List<String> currencyCodes, String field, List<AnalyticsDiagnostic> diagnostics) {
        List<String> normalized = currencyCodes == null
            ? List.of()
            : currencyCodes.stream().map(this::normalizeId).distinct().sorted().toList();
        normalized.forEach(currency -> validateEnum(CurrencyCode.class, currency, field, diagnostics));
        return normalized;
    }

    private void validateOwnedAccount(String value, String field, String ownerLogin, List<AnalyticsDiagnostic> diagnostics) {
        parsePositiveId(value, field, diagnostics).ifPresent(id -> {
            if (!accountRepository.existsById(id)) {
                diagnostics.add(diagnostic(AnalyticsDiagnosticCode.INVALID_REFERENCE_ID, field, "Unknown account ID: " + id));
            } else if (!accountRepository.existsByIdAndUser_Login(id, ownerLogin)) {
                diagnostics.add(
                    diagnostic(
                        AnalyticsDiagnosticCode.FOREIGN_ACCOUNT_REFERENCE,
                        field,
                        "Account is not accessible to the current user: " + id
                    )
                );
            }
        });
    }

    private void validateOwnedCategory(String value, String field, String ownerLogin, List<AnalyticsDiagnostic> diagnostics) {
        parsePositiveId(value, field, diagnostics).ifPresent(id -> {
            if (!categoryRepository.existsById(id)) {
                diagnostics.add(diagnostic(AnalyticsDiagnosticCode.INVALID_REFERENCE_ID, field, "Unknown category ID: " + id));
            } else if (categoryRepository.findOneByIdAndUserLogin(id, ownerLogin).isEmpty()) {
                diagnostics.add(
                    diagnostic(
                        AnalyticsDiagnosticCode.FOREIGN_CATEGORY_REFERENCE,
                        field,
                        "Category is not accessible to the current user: " + id
                    )
                );
            }
        });
    }

    private void validateOwnedTag(String value, String field, String ownerLogin, List<AnalyticsDiagnostic> diagnostics) {
        parsePositiveId(value, field, diagnostics).ifPresent(id -> {
            if (!tagRepository.existsById(id)) {
                diagnostics.add(diagnostic(AnalyticsDiagnosticCode.INVALID_REFERENCE_ID, field, "Unknown tag ID: " + id));
            } else if (tagRepository.findOneByIdAndUserLogin(id, ownerLogin).isEmpty()) {
                diagnostics.add(
                    diagnostic(AnalyticsDiagnosticCode.FOREIGN_TAG_REFERENCE, field, "Tag is not accessible to the current user: " + id)
                );
            }
        });
    }

    private Optional<Long> parsePositiveId(String value, String field, List<AnalyticsDiagnostic> diagnostics) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return Optional.of(id);
        } catch (NumberFormatException exception) {
            diagnostics.add(diagnostic(AnalyticsDiagnosticCode.INVALID_REFERENCE_ID, field, "Reference IDs must be positive integers."));
            return Optional.empty();
        }
    }

    private <T extends Enum<T>> void validateEnum(Class<T> enumType, String value, String field, List<AnalyticsDiagnostic> diagnostics) {
        try {
            Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            diagnostics.add(diagnostic(AnalyticsDiagnosticCode.INVALID_PREDICATE_VALUE, field, "Unsupported value: " + value));
        }
    }

    private void validateDate(String value, String field, List<AnalyticsDiagnostic> diagnostics) {
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            diagnostics.add(
                diagnostic(AnalyticsDiagnosticCode.INVALID_PREDICATE_VALUE, field, "Dates must use ISO-8601 local-date format.")
            );
        }
    }

    private boolean isDateAttribute(String attributeId) {
        return FinancialSemanticCatalog.TRANSACTION_DATE.equals(attributeId) || FinancialSemanticCatalog.POSTING_DATE.equals(attributeId);
    }

    private void validateSchema(Integer schemaVersion, String field, List<AnalyticsDiagnostic> diagnostics) {
        if (schemaVersion == null || schemaVersion != AnalyticsSchema.VERSION) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.UNKNOWN_SCHEMA_VERSION,
                    field,
                    "Analytics schemaVersion must be %d.".formatted(AnalyticsSchema.VERSION)
                )
            );
        }
    }

    private void validateSort(AnalyticsSort sort, List<String> groupings, List<AnalyticsDiagnostic> diagnostics) {
        if (sort == null) {
            return;
        }
        String groupingId = normalizeId(sort.groupingId());
        String direction = normalizeId(sort.direction());
        if (!groupings.contains(groupingId) || !Set.of("ASC", "DESC").contains(direction)) {
            diagnostics.add(
                diagnostic(
                    AnalyticsDiagnosticCode.INVALID_SORT,
                    "definition.sort",
                    "Sort must target a requested grouping and use ASC or DESC."
                )
            );
        }
    }

    private AnalyticsSort normalizeSort(AnalyticsSort sort, List<String> groupings) {
        if (sort == null) {
            return groupings.isEmpty() ? null : new AnalyticsSort(groupings.get(0), "ASC");
        }
        return new AnalyticsSort(normalizeId(sort.groupingId()), normalizeId(sort.direction()));
    }

    private void rejectDuplicates(List<String> values, AnalyticsDiagnosticCode code, String field, List<AnalyticsDiagnostic> diagnostics) {
        if (new HashSet<>(values).size() != values.size()) {
            diagnostics.add(diagnostic(code, field, "Each identifier may appear only once."));
        }
    }

    private List<String> normalizeIds(List<String> values) {
        return values == null ? List.of() : values.stream().map(this::normalizeId).toList();
    }

    private String normalizeIdOrDefault(String value, String fallback) {
        String normalized = normalizeId(value);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private AnalyticsDiagnostic diagnostic(AnalyticsDiagnosticCode code, String field, String message) {
        return new AnalyticsDiagnostic(code, field, message);
    }

    private AnalyticsValidationResult invalid(List<AnalyticsDiagnostic> diagnostics) {
        return new AnalyticsValidationResult(Optional.empty(), List.copyOf(diagnostics));
    }

    private AnalyticsDefinitionValidationResult invalidDefinition(List<AnalyticsDiagnostic> diagnostics) {
        return new AnalyticsDefinitionValidationResult(Optional.empty(), List.copyOf(diagnostics));
    }

    private record ContextValidation(
        VisualizationTime normalizedTime,
        List<Long> accountIds,
        List<String> currencyCodes,
        ResolvedVisualizationContext resolvedContext
    ) {}
}
