package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.FinancialAccount;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        if (!conditions.isBlank()) {
            return switch (projection.function()) {
                case COUNT -> "count(case when " + conditions + " then ft.id end)";
                case AVG -> "avg(case when " + conditions + " then ft.amount end)";
                case SUM -> "coalesce(sum(case when " + conditions + " then ft.amount else 0 end), 0)";
            };
        }
        return switch (projection.function()) {
            case COUNT -> "count(ft.id)";
            case AVG -> "avg(ft.amount)";
            case SUM -> "coalesce(sum(ft.amount), 0)";
        };
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
            values.add(SemanticAnalyticsResultFactory.value(definition.projections().get(index), decimal(source.get("measure_" + index))));
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
                new SemanticDimensionMetadata("ACCOUNT:" + id, label, null, account.getActive(), false, List.of(label))
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
}
