package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.FinancialAccount;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Executes the ledger dataset using only static, catalog-owned SQL fragments. Tags are joined only when the user
 * explicitly groups by tag; all tag filters use EXISTS and never multiply ordinary transaction totals.
 */
@Component
class SemanticTransactionLedgerExecutor {

    private final NamedParameterJdbcTemplate jdbc;

    SemanticTransactionLedgerExecutor(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    SemanticAnalyticsResult execute(SemanticAnalyticsExecutionScope scope) {
        SemanticQueryDefinition definition = scope.definition();
        List<SemanticAnalyticsResultColumn> columns = SemanticAnalyticsResultFactory.columns(definition);
        if (scope.accounts().isEmpty()) {
            return SemanticAnalyticsResultFactory.result(scope, List.of(), columns);
        }
        if (definition.groupBy().contains(SemanticField.TAG_ID) && scope.references().tagIds().isEmpty()) {
            return SemanticAnalyticsResultFactory.result(scope, List.of(), columns);
        }

        Map<SemanticField, String> dimensions = dimensionExpressions(definition.groupBy());
        Map<SemanticField, String> dimensionAliases = new LinkedHashMap<>();
        Map<Long, FinancialAccount> accountsById = scope
            .accounts()
            .stream()
            .collect(java.util.stream.Collectors.toMap(FinancialAccount::getId, account -> account));
        Long branchParent = branchParent(definition.predicates());
        boolean monetary = definition
            .projections()
            .stream()
            .anyMatch(projection -> SemanticAnalyticsResultFactory.unit(projection) == SemanticUnit.MONEY);
        List<String> select = new ArrayList<>();
        List<String> groupBy = new ArrayList<>();
        for (int index = 0; index < definition.groupBy().size(); index++) {
            SemanticField field = definition.groupBy().get(index);
            String expression = dimensions.get(field);
            select.add(expression + " as dimension_" + index);
            dimensionAliases.put(field, "dimension_" + index);
            // Positional grouping avoids repeating correlated hierarchy expressions with distinct bound parameters.
            groupBy.add(String.valueOf(index + 1));
        }
        if (monetary) {
            select.add("fa.currency as analytics_currency");
            groupBy.add(String.valueOf(select.size()));
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("accountIds", scope.accounts().stream().map(FinancialAccount::getId).toList())
            .addValue("fromDate", scope.context().from())
            .addValue("throughDate", scope.context().through())
            .addValue("resultLimit", definition.limit());
        if (branchParent != null) parameters.addValue("branchParent", branchParent);
        if (definition.groupBy().contains(SemanticField.TAG_ID)) parameters.addValue("ownedTagIds", scope.references().tagIds());
        if (needsCategoryHierarchy(definition.groupBy())) parameters.addValue("ownedCategoryIds", scope.references().categoryIds());
        for (int index = 0; index < definition.projections().size(); index++) {
            select.add(
                aggregate(definition.projections().get(index), parameters, scope.references(), "projection" + index) +
                " as measure_" +
                index
            );
        }

        StringBuilder sql = new StringBuilder();
        if (needsCategoryHierarchy(definition.groupBy())) sql.append(categoryHierarchyCte(scope.references().categoryIds().isEmpty()));
        sql.append("select ").append(String.join(", ", select));
        sql.append(" from financial_transaction ft join financial_account fa on fa.id = ft.account_id");
        if (definition.groupBy().contains(SemanticField.TAG_ID)) {
            sql.append(" join rel_financial_transaction__tags tag_relation on tag_relation.financial_transaction_id = ft.id");
            sql.append(" join tag tag_dimension on tag_dimension.id = tag_relation.tags_id and tag_dimension.id in (:ownedTagIds)");
        }
        List<String> where = new ArrayList<>();
        where.add("fa.id in (:accountIds)");
        where.add("ft.transaction_date between :fromDate and :throughDate");
        appendPredicates(where, parameters, definition.predicates(), scope.references(), "global");
        sql.append(" where ").append(String.join(" and ", where));
        if (!groupBy.isEmpty()) {
            sql.append(" group by ").append(String.join(", ", groupBy));
        }
        appendOrder(sql, definition, dimensionAliases);
        sql.append(" limit :resultLimit");

        List<SemanticAnalyticsResultRow> rows = jdbc
            .queryForList(sql.toString(), parameters)
            .stream()
            .map(source -> row(source, definition, monetary, scope.references(), accountsById))
            .toList();
        rows = completeEmptyScalarCurrencyPartitions(scope, rows, monetary);
        rows = completeEmptyTemporalPeriods(scope, rows, monetary);
        List<SemanticAnalyticsWarning> warnings = definition.groupBy().contains(SemanticField.TAG_ID)
            ? List.of(
                new SemanticAnalyticsWarning(
                    "NON_ADDITIVE_OVERLAPPING_TAG_GROUPS",
                    "Tag groups overlap: one transaction can appear in more than one tag total."
                )
            )
            : List.of();
        return SemanticAnalyticsResultFactory.result(scope, rows, columns, warnings);
    }

    /**
     * A scalar money query is still partitioned by account currency. PostgreSQL cannot emit a grouped currency row
     * when no ledger entry matches, so create an applicable semantic row for every compatible scoped currency. This
     * is intentionally restricted to scalar ledger aggregates: it makes economic SUM and COUNT known zero while an
     * AVG remains NO_DATA, without pretending that absent category/tag or historical-position observations are zero.
     */
    private List<SemanticAnalyticsResultRow> completeEmptyScalarCurrencyPartitions(
        SemanticAnalyticsExecutionScope scope,
        List<SemanticAnalyticsResultRow> source,
        boolean monetary
    ) {
        if (!monetary || !scope.definition().groupBy().isEmpty() || scope.accounts().isEmpty()) return source;

        Map<String, SemanticAnalyticsResultRow> rowsByCurrency = new LinkedHashMap<>();
        source.forEach(row -> rowsByCurrency.put(row.currencyCode(), row));
        scope
            .accounts()
            .stream()
            .map(FinancialAccount::getCurrency)
            .map(Enum::name)
            .distinct()
            .sorted()
            .forEach(currency ->
                rowsByCurrency.putIfAbsent(
                    currency,
                    new SemanticAnalyticsResultRow(
                        List.of(),
                        currency,
                        scope.definition().projections().stream().map(SemanticAnalyticsResultFactory::emptyPeriodValue).toList()
                    )
                )
            );
        return List.copyOf(rowsByCurrency.values());
    }

    private Map<SemanticField, String> dimensionExpressions(List<SemanticField> groupings) {
        Map<SemanticField, String> expressions = new LinkedHashMap<>();
        for (SemanticField grouping : groupings) {
            String expression =
                switch (grouping) {
                    // Date-only financial values are partitioned by PostgreSQL calendar values.  No timestamp or browser
                    // zone conversion is involved.  ISO week groups intentionally use IYYY/IW together.
                    case TRANSACTION_DAY -> "to_char(ft.transaction_date, 'YYYY-MM-DD')";
                    case TRANSACTION_WEEK -> "to_char(ft.transaction_date, 'IYYY') || '-W' || to_char(ft.transaction_date, 'IW')";
                    case TRANSACTION_MONTH -> "to_char(ft.transaction_date, 'YYYY-MM')";
                    case TRANSACTION_YEAR -> "to_char(ft.transaction_date, 'YYYY')";
                    case ACCOUNT_INSTITUTION -> "coalesce(nullif(trim(fa.institution_name), ''), 'UNSPECIFIED')";
                    case ACCOUNT_ID -> "fa.id";
                    case ACCOUNT_TYPE -> "fa.account_type";
                    case CATEGORY_ID -> "ft.category_id";
                    case CATEGORY_ROOT -> "(select h.ancestor_id from category_hierarchy h where h.leaf_id = ft.category_id and h.parent_category_id is null)";
                    case CATEGORY_SUBCATEGORY -> "coalesce((select h.ancestor_id from category_hierarchy h where h.leaf_id = ft.category_id and h.parent_category_id = " +
                    "(select root.ancestor_id from category_hierarchy root where root.leaf_id = ft.category_id and root.parent_category_id is null)), " +
                    "-(select root.ancestor_id from category_hierarchy root where root.leaf_id = ft.category_id and root.parent_category_id is null))";
                    case CATEGORY_BRANCH_CHILD -> "case when ft.category_id = :branchParent then -:branchParent else " +
                    "(select h.ancestor_id from category_hierarchy h where h.leaf_id = ft.category_id and h.parent_category_id = :branchParent) end";
                    case TAG_ID -> "tag_dimension.id";
                    default -> throw new IllegalArgumentException("Verified ledger grouping was not handled: " + grouping);
                };
            expressions.put(grouping, expression);
        }
        return expressions;
    }

    private boolean needsCategoryHierarchy(List<SemanticField> groupings) {
        return groupings
            .stream()
            .anyMatch(
                field ->
                    field == SemanticField.CATEGORY_ROOT ||
                    field == SemanticField.CATEGORY_SUBCATEGORY ||
                    field == SemanticField.CATEGORY_BRANCH_CHILD
            );
    }

    private String categoryHierarchyCte(boolean noOwnedCategories) {
        return (
            "with recursive category_hierarchy as (" +
            "select category.id as leaf_id, category.id as ancestor_id, category.parent_category_id as parent_category_id from category where " +
            (noOwnedCategories ? "false " : "category.id in (:ownedCategoryIds) ") +
            "union all select h.leaf_id, parent.id as ancestor_id, parent.parent_category_id as parent_category_id " +
            "from category_hierarchy h join category parent on parent.id = h.parent_category_id " +
            (noOwnedCategories ? "where false" : "where parent.id in (:ownedCategoryIds)") +
            ") "
        );
    }

    private String aggregate(
        SemanticAggregateProjection projection,
        MapSqlParameterSource parameters,
        SemanticAnalyticsReferenceCatalog references,
        String prefix
    ) {
        String conditions = predicateExpression(projection.conditions(), parameters, references, prefix);
        if (projection.function() == SemanticAggregateFunction.NET) {
            String qualified = (conditions.isBlank() ? "true" : conditions) + " and " + unlinkedTransferExpression();
            return (
                "coalesce(sum(case when " +
                qualified +
                " then case when ft.flow = 'IN' then ft.amount when ft.flow = 'OUT' then -ft.amount else 0 end else 0 end), 0)"
            );
        }
        if (!conditions.isBlank()) {
            return switch (projection.function()) {
                case COUNT -> "count(case when " + conditions + " then ft.id end)";
                case AVG -> "avg(case when " + conditions + " then ft.amount end)";
                case SUM -> "coalesce(sum(case when " + conditions + " then ft.amount else 0 end), 0)";
                case NET -> throw new IllegalStateException("NET was handled before conditional aggregation.");
            };
        }
        return switch (projection.function()) {
            case COUNT -> "count(ft.id)";
            case AVG -> "avg(ft.amount)";
            case SUM -> "coalesce(sum(ft.amount), 0)";
            case NET -> throw new IllegalStateException("NET was handled before ordinary aggregation.");
        };
    }

    private String unlinkedTransferExpression() {
        return "not exists (select 1 from internal_transfer transfer where transfer.outgoing_transaction_id = ft.id or transfer.incoming_transaction_id = ft.id)";
    }

    private void appendPredicates(
        List<String> where,
        MapSqlParameterSource parameters,
        List<SemanticPredicate> predicates,
        SemanticAnalyticsReferenceCatalog references,
        String prefix
    ) {
        String expression = predicateExpression(predicates, parameters, references, prefix);
        if (!expression.isBlank()) where.add(expression);
    }

    private String predicateExpression(
        List<SemanticPredicate> predicates,
        MapSqlParameterSource parameters,
        SemanticAnalyticsReferenceCatalog references,
        String prefix
    ) {
        List<String> expressions = new ArrayList<>();
        for (int index = 0; index < predicates.size(); index++) {
            SemanticPredicate predicate = predicates.get(index);
            String parameter = prefix + "Predicate" + index;
            switch (predicate.field()) {
                case TRANSACTION_FLOW -> expressions.add(valueExpression(parameters, parameter, "ft.flow", predicate));
                case ACCOUNT_TYPE -> expressions.add(valueExpression(parameters, parameter, "fa.account_type", predicate));
                case TRANSFER_LINK_STATUS -> expressions.add(transferExpression(predicate));
                case CATEGORY_ID -> expressions.add(categoryExpression(parameters, parameter, predicate, references));
                case TAG_ID -> expressions.add(tagExpression(parameters, parameter, predicate));
                default -> throw new IllegalArgumentException("Validated ledger predicate was not handled: " + predicate.field());
            }
        }
        return String.join(" and ", expressions);
    }

    private String valueExpression(MapSqlParameterSource parameters, String parameter, String column, SemanticPredicate predicate) {
        if (predicate.operator() == SemanticPredicateOperator.EQUALS) {
            parameters.addValue(parameter, predicate.values().get(0));
            return column + " = :" + parameter;
        }
        parameters.addValue(parameter, predicate.values());
        return column + " in (:" + parameter + ")";
    }

    private String transferExpression(SemanticPredicate predicate) {
        String exists =
            "exists (select 1 from internal_transfer transfer where transfer.outgoing_transaction_id = ft.id or transfer.incoming_transaction_id = ft.id)";
        boolean unlinked = predicate.values().contains("UNLINKED");
        boolean linked = predicate.values().contains("LINKED");
        return unlinked == linked ? "true" : linked ? exists : "not " + exists;
    }

    private String categoryExpression(
        MapSqlParameterSource parameters,
        String parameter,
        SemanticPredicate predicate,
        SemanticAnalyticsReferenceCatalog references
    ) {
        return switch (predicate.operator()) {
            case EQUALS -> {
                parameters.addValue(parameter, Long.valueOf(predicate.values().get(0)));
                yield "ft.category_id = :" + parameter;
            }
            case IN -> {
                parameters.addValue(parameter, predicate.values().stream().map(Long::valueOf).toList());
                yield "ft.category_id in (:" + parameter + ")";
            }
            case DESCENDANT_OF -> {
                parameters.addValue(parameter, references.descendantsInclusive(Long.valueOf(predicate.values().get(0))));
                yield "ft.category_id in (:" + parameter + ")";
            }
            case DESCENDANT_OF_ANY -> {
                java.util.Set<Long> categoryIds = new java.util.LinkedHashSet<>();
                predicate.values().forEach(value -> categoryIds.addAll(references.descendantsInclusive(Long.valueOf(value))));
                parameters.addValue(parameter, List.copyOf(categoryIds));
                yield "ft.category_id in (:" + parameter + ")";
            }
            case IS_UNCATEGORIZED -> "ft.category_id is null";
            default -> throw new IllegalArgumentException("Validated category predicate was not handled: " + predicate.operator());
        };
    }

    private String tagExpression(MapSqlParameterSource parameters, String parameter, SemanticPredicate predicate) {
        return switch (predicate.operator()) {
            case HAS_ANY -> {
                parameters.addValue(parameter, predicate.values().stream().map(Long::valueOf).toList());
                yield "exists (select 1 from rel_financial_transaction__tags tag_filter where tag_filter.financial_transaction_id = ft.id and tag_filter.tags_id in (:" +
                parameter +
                "))";
            }
            case HAS_ALL -> {
                parameters.addValue(parameter, predicate.values().stream().map(Long::valueOf).toList());
                parameters.addValue(parameter + "Count", predicate.values().size());
                yield "(select count(distinct tag_filter.tags_id) from rel_financial_transaction__tags tag_filter where tag_filter.financial_transaction_id = ft.id and tag_filter.tags_id in (:" +
                parameter +
                ")) = :" +
                parameter +
                "Count";
            }
            case HAS_NONE -> "not exists (select 1 from rel_financial_transaction__tags tag_filter where tag_filter.financial_transaction_id = ft.id)";
            default -> throw new IllegalArgumentException("Validated tag predicate was not handled: " + predicate.operator());
        };
    }

    private void appendOrder(StringBuilder sql, SemanticQueryDefinition definition, Map<SemanticField, String> dimensions) {
        if (definition.orderBy().isEmpty()) return;
        List<String> order = new ArrayList<>();
        for (SemanticOrder sort : definition.orderBy()) {
            if (sort.field() != null) {
                order.add(dimensions.get(sort.field()) + " " + sort.direction().name());
                continue;
            }
            int projectionIndex = projectionIndex(definition, sort.projectionAlias());
            order.add("measure_" + projectionIndex + " " + sort.direction().name());
        }
        sql.append(" order by ").append(String.join(", ", order));
    }

    private int projectionIndex(SemanticQueryDefinition definition, String alias) {
        for (int index = 0; index < definition.projections().size(); index++) {
            if (definition.projections().get(index).alias().equalsIgnoreCase(alias)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Validated projection alias was not handled: " + alias);
    }

    private SemanticAnalyticsResultRow row(
        Map<String, Object> source,
        SemanticQueryDefinition definition,
        boolean monetary,
        SemanticAnalyticsReferenceCatalog references,
        Map<Long, FinancialAccount> accountsById
    ) {
        List<SemanticAnalyticsResultDimension> dimensions = new ArrayList<>();
        for (int index = 0; index < definition.groupBy().size(); index++) {
            SemanticField field = definition.groupBy().get(index);
            dimensions.add(dimension(field, source.get("dimension_" + index), references, accountsById));
        }
        List<SemanticAnalyticsResultValue> values = new ArrayList<>();
        for (int index = 0; index < definition.projections().size(); index++) {
            values.add(
                SemanticAnalyticsResultFactory.ledgerValue(definition.projections().get(index), decimal(source.get("measure_" + index)))
            );
        }
        Object currency = source.get("analytics_currency");
        return new SemanticAnalyticsResultRow(dimensions, monetary && currency != null ? String.valueOf(currency) : null, values);
    }

    private SemanticAnalyticsResultDimension dimension(
        SemanticField field,
        Object raw,
        SemanticAnalyticsReferenceCatalog references,
        Map<Long, FinancialAccount> accountsById
    ) {
        if (isTransactionTemporalGrouping(field)) {
            String bucket = String.valueOf(raw);
            return new SemanticAnalyticsResultDimension(field, bucket, temporalMetadata(field, bucket));
        }
        if (field == SemanticField.ACCOUNT_ID) {
            Long id = ((Number) raw).longValue();
            FinancialAccount account = accountsById.get(id);
            if (account == null) return new SemanticAnalyticsResultDimension(field, id);
            String label = account.getName();
            return new SemanticAnalyticsResultDimension(
                field,
                id,
                new SemanticDimensionMetadata("ACCOUNT:" + id, label, account.getColor(), account.getActive(), false, List.of(label))
            );
        }
        if (field == SemanticField.ACCOUNT_INSTITUTION) {
            String institution = String.valueOf(raw);
            return new SemanticAnalyticsResultDimension(
                field,
                institution,
                new SemanticDimensionMetadata("INSTITUTION:" + institution, institution, null, null, false, List.of(institution))
            );
        }
        if (
            field == SemanticField.CATEGORY_ID ||
            field == SemanticField.CATEGORY_ROOT ||
            field == SemanticField.CATEGORY_SUBCATEGORY ||
            field == SemanticField.CATEGORY_BRANCH_CHILD
        ) {
            if (raw == null) {
                return new SemanticAnalyticsResultDimension(
                    field,
                    SemanticAnalyticsReferenceCatalog.UNCATEGORIZED_ID,
                    references.uncategorizedMetadata()
                );
            }
            Long id = ((Number) raw).longValue();
            if (id < 0) {
                Long parentId = -id;
                return new SemanticAnalyticsResultDimension(
                    field,
                    SemanticAnalyticsReferenceCatalog.DIRECT_ASSIGNMENT_ID + ":" + parentId,
                    references.directAssignmentMetadata(parentId)
                );
            }
            return new SemanticAnalyticsResultDimension(field, id, references.categoryMetadata(id));
        }
        if (field == SemanticField.TAG_ID) {
            Long id = ((Number) raw).longValue();
            return new SemanticAnalyticsResultDimension(field, id, references.tagMetadata(id));
        }
        return new SemanticAnalyticsResultDimension(field, raw);
    }

    private boolean isTransactionTemporalGrouping(SemanticField field) {
        return switch (field) {
            case TRANSACTION_DAY, TRANSACTION_WEEK, TRANSACTION_MONTH, TRANSACTION_YEAR -> true;
            default -> false;
        };
    }

    private SemanticDimensionMetadata temporalMetadata(SemanticField field, String bucket) {
        String label =
            switch (field) {
                case TRANSACTION_DAY -> bucket;
                case TRANSACTION_WEEK -> "ISO " + bucket;
                case TRANSACTION_MONTH -> bucket;
                case TRANSACTION_YEAR -> bucket;
                default -> throw new IllegalArgumentException("Verified transaction time grouping was not handled: " + field);
            };
        return new SemanticDimensionMetadata(field.name() + ":" + bucket, label, null, null, false, List.of(bucket));
    }

    private Long branchParent(List<SemanticPredicate> predicates) {
        return predicates
            .stream()
            .filter(predicate -> predicate.field() == SemanticField.CATEGORY_ID)
            .filter(predicate -> predicate.operator() == SemanticPredicateOperator.DESCENDANT_OF)
            .map(predicate -> Long.valueOf(predicate.values().get(0)))
            .findFirst()
            .orElse(null);
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        return new BigDecimal(value.toString());
    }

    /**
     * SQL can aggregate only periods that contain ledger rows. Calendar completion belongs to the semantic result,
     * not to the chart: a valid empty period is a known zero for SUM and COUNT, but not an invented AVG. Existing
     * query rows are retained unchanged and each generated value is explicitly marked as derived.
     */
    private List<SemanticAnalyticsResultRow> completeEmptyTemporalPeriods(
        SemanticAnalyticsExecutionScope scope,
        List<SemanticAnalyticsResultRow> source,
        boolean monetary
    ) {
        SemanticField temporal = temporalGrouping(scope.definition().groupBy());
        if (temporal == null) return source;

        List<TemporalBucket> buckets = temporalBuckets(temporal, scope.context().from(), scope.context().through());
        if (buckets.isEmpty()) return source;

        int temporalIndex = scope.definition().groupBy().indexOf(temporal);
        Map<String, SeriesTemplate> templates = new LinkedHashMap<>();
        source.forEach(row -> {
            SeriesTemplate template = new SeriesTemplate(row.currencyCode(), withoutTemporalDimension(row.dimensions(), temporalIndex));
            templates.putIfAbsent(template.key(), template);
        });
        if (templates.isEmpty()) {
            emptyScopeTemplates(scope, temporal, monetary).forEach(template -> templates.putIfAbsent(template.key(), template));
        }
        if (templates.isEmpty()) return source;

        Map<String, SemanticAnalyticsResultRow> rows = new LinkedHashMap<>();
        source.forEach(row -> rows.put(rowKey(row), row));
        for (SeriesTemplate template : templates.values()) {
            for (TemporalBucket bucket : buckets) {
                if (!hasAvailableAccount(scope.accounts(), template, bucket)) continue;
                SemanticAnalyticsResultRow generated = new SemanticAnalyticsResultRow(
                    dimensionsFor(scope.definition().groupBy(), temporal, template, bucket),
                    template.currencyCode(),
                    scope.definition().projections().stream().map(SemanticAnalyticsResultFactory::emptyPeriodValue).toList()
                );
                rows.putIfAbsent(rowKey(generated), generated);
            }
        }
        return orderCompletedRows(scope.definition(), new ArrayList<>(rows.values()), temporal);
    }

    private SemanticField temporalGrouping(List<SemanticField> groups) {
        return groups.stream().filter(this::isTransactionTemporalGrouping).findFirst().orElse(null);
    }

    private List<SeriesTemplate> emptyScopeTemplates(SemanticAnalyticsExecutionScope scope, SemanticField temporal, boolean monetary) {
        List<SemanticField> comparisons = scope.definition().groupBy().stream().filter(field -> field != temporal).toList();
        if (comparisons.isEmpty()) {
            if (!monetary) return List.of(new SeriesTemplate(null, List.of()));
            return scope
                .accounts()
                .stream()
                .map(FinancialAccount::getCurrency)
                .map(Enum::name)
                .distinct()
                .map(currency -> new SeriesTemplate(currency, List.of()))
                .toList();
        }
        if (comparisons.size() != 1 || !accountComparison(comparisons.get(0))) return List.of();

        Map<String, SeriesTemplate> templates = new LinkedHashMap<>();
        for (FinancialAccount account : scope.accounts()) {
            SemanticAnalyticsResultDimension comparison = accountComparisonDimension(comparisons.get(0), account);
            String currency = monetary ? account.getCurrency().name() : null;
            SeriesTemplate template = new SeriesTemplate(currency, List.of(comparison));
            templates.putIfAbsent(template.key(), template);
        }
        return List.copyOf(templates.values());
    }

    private boolean accountComparison(SemanticField field) {
        return field == SemanticField.ACCOUNT_ID || field == SemanticField.ACCOUNT_INSTITUTION || field == SemanticField.ACCOUNT_TYPE;
    }

    private SemanticAnalyticsResultDimension accountComparisonDimension(SemanticField field, FinancialAccount account) {
        if (field == SemanticField.ACCOUNT_ID) {
            String label = account.getName();
            return new SemanticAnalyticsResultDimension(
                field,
                account.getId(),
                new SemanticDimensionMetadata(
                    "ACCOUNT:" + account.getId(),
                    label,
                    account.getColor(),
                    account.getActive(),
                    false,
                    List.of(label)
                )
            );
        }
        if (field == SemanticField.ACCOUNT_INSTITUTION) {
            String institution = institution(account);
            return new SemanticAnalyticsResultDimension(
                field,
                institution,
                new SemanticDimensionMetadata("INSTITUTION:" + institution, institution, null, null, false, List.of(institution))
            );
        }
        return new SemanticAnalyticsResultDimension(field, account.getAccountType().name());
    }

    private List<SemanticAnalyticsResultDimension> withoutTemporalDimension(
        List<SemanticAnalyticsResultDimension> dimensions,
        int temporalIndex
    ) {
        List<SemanticAnalyticsResultDimension> result = new ArrayList<>(dimensions);
        result.remove(temporalIndex);
        return List.copyOf(result);
    }

    private List<SemanticAnalyticsResultDimension> dimensionsFor(
        List<SemanticField> groups,
        SemanticField temporal,
        SeriesTemplate template,
        TemporalBucket bucket
    ) {
        List<SemanticAnalyticsResultDimension> dimensions = new ArrayList<>();
        int comparisonIndex = 0;
        for (SemanticField group : groups) {
            if (group == temporal) {
                dimensions.add(new SemanticAnalyticsResultDimension(temporal, bucket.value(), temporalMetadata(temporal, bucket.value())));
            } else {
                dimensions.add(template.comparisons().get(comparisonIndex++));
            }
        }
        return List.copyOf(dimensions);
    }

    private boolean hasAvailableAccount(List<FinancialAccount> accounts, SeriesTemplate template, TemporalBucket bucket) {
        return accounts
            .stream()
            .filter(account -> template.currencyCode() == null || template.currencyCode().equals(account.getCurrency().name()))
            .filter(account -> matchesAccountComparisons(account, template.comparisons()))
            .anyMatch(account -> !account.getInitialBalanceDate().isAfter(bucket.availableThrough()));
    }

    private boolean matchesAccountComparisons(FinancialAccount account, List<SemanticAnalyticsResultDimension> comparisons) {
        return comparisons
            .stream()
            .allMatch(comparison ->
                switch (comparison.field()) {
                    case ACCOUNT_ID -> Objects.equals(account.getId(), ((Number) comparison.value()).longValue());
                    case ACCOUNT_INSTITUTION -> institution(account).equals(String.valueOf(comparison.value()));
                    case ACCOUNT_TYPE -> account.getAccountType().name().equals(String.valueOf(comparison.value()));
                    default -> true;
                }
            );
    }

    private String institution(FinancialAccount account) {
        String institution = account.getInstitutionName();
        return institution == null || institution.trim().isEmpty() ? "UNSPECIFIED" : institution.trim();
    }

    private List<TemporalBucket> temporalBuckets(SemanticField temporal, LocalDate from, LocalDate through) {
        List<TemporalBucket> buckets = new ArrayList<>();
        switch (temporal) {
            case TRANSACTION_DAY -> {
                for (LocalDate date = from; !date.isAfter(through); date = date.plusDays(1)) {
                    buckets.add(new TemporalBucket(date.toString(), date, date, date));
                }
            }
            case TRANSACTION_WEEK -> {
                LocalDate start = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                for (LocalDate date = start; !date.isAfter(through); date = date.plusWeeks(1)) {
                    LocalDate end = date.plusDays(6);
                    String value = "%d-W%02d".formatted(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
                    buckets.add(new TemporalBucket(value, date, end, end.isAfter(through) ? through : end));
                }
            }
            case TRANSACTION_MONTH -> {
                for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(through)); month = month.plusMonths(1)) {
                    LocalDate end = month.atEndOfMonth();
                    buckets.add(new TemporalBucket(month.toString(), month.atDay(1), end, end.isAfter(through) ? through : end));
                }
            }
            case TRANSACTION_YEAR -> {
                for (int year = from.getYear(); year <= through.getYear(); year++) {
                    LocalDate end = LocalDate.of(year, 12, 31);
                    buckets.add(
                        new TemporalBucket(String.valueOf(year), LocalDate.of(year, 1, 1), end, end.isAfter(through) ? through : end)
                    );
                }
            }
            default -> throw new IllegalArgumentException("Verified ledger temporal grouping was not handled: " + temporal);
        }
        return List.copyOf(buckets);
    }

    private List<SemanticAnalyticsResultRow> orderCompletedRows(
        SemanticQueryDefinition definition,
        List<SemanticAnalyticsResultRow> rows,
        SemanticField temporal
    ) {
        Comparator<SemanticAnalyticsResultRow> comparator = Comparator.comparing(
            row -> String.valueOf(dimensionValue(row, temporal)),
            Comparator.nullsLast(Comparator.naturalOrder())
        );
        for (SemanticOrder order : definition.orderBy()) {
            Comparator<SemanticAnalyticsResultRow> requested = order.field() == null
                ? Comparator.comparing(
                    row -> projectionValue(row, order.projectionAlias()),
                    Comparator.nullsLast(Comparator.naturalOrder())
                )
                : Comparator.comparing(
                    row -> sortable(dimensionValue(row, order.field())),
                    Comparator.nullsLast(Comparator.naturalOrder())
                );
            comparator = order.direction() == SemanticSortDirection.ASC
                ? requested.thenComparing(comparator)
                : requested.reversed().thenComparing(comparator);
        }
        rows.sort(comparator);
        return List.copyOf(rows);
    }

    private Object dimensionValue(SemanticAnalyticsResultRow row, SemanticField field) {
        return row
            .dimensions()
            .stream()
            .filter(dimension -> dimension.field() == field)
            .map(SemanticAnalyticsResultDimension::value)
            .findFirst()
            .orElse(null);
    }

    private String sortable(Object value) {
        if (value instanceof Number number) return "%020d".formatted(number.longValue());
        return value == null ? null : value.toString();
    }

    private BigDecimal projectionValue(SemanticAnalyticsResultRow row, String alias) {
        return row
            .values()
            .stream()
            .filter(value -> value.alias().equalsIgnoreCase(alias))
            .map(SemanticAnalyticsResultValue::value)
            .findFirst()
            .orElse(null);
    }

    private String rowKey(SemanticAnalyticsResultRow row) {
        return (
            row.currencyCode() +
            "|" +
            row
                .dimensions()
                .stream()
                .map(dimension -> dimension.field() + "=" + dimension.value())
                .collect(java.util.stream.Collectors.joining("|"))
        );
    }

    private record SeriesTemplate(String currencyCode, List<SemanticAnalyticsResultDimension> comparisons) {
        private String key() {
            return (
                currencyCode +
                "|" +
                comparisons
                    .stream()
                    .map(dimension -> dimension.field() + "=" + dimension.value())
                    .collect(java.util.stream.Collectors.joining("|"))
            );
        }
    }

    private record TemporalBucket(String value, LocalDate start, LocalDate end, LocalDate availableThrough) {}
}
