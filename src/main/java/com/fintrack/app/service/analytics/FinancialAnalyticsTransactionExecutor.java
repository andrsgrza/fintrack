package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.FinancialAccount;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** PostgreSQL implementation of governed economic and account-movement measures. */
@Component
class FinancialAnalyticsTransactionExecutor {

    private final NamedParameterJdbcTemplate jdbc;
    private final FinancialSemanticCatalog catalog;

    FinancialAnalyticsTransactionExecutor(NamedParameterJdbcTemplate jdbc, FinancialSemanticCatalog catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    FinancialAnalyticsResult execute(FinancialAnalyticsExecutionScope scope) {
        QueryDefinition definition = scope.validatedRequest().request().definition();
        List<FinancialMetricDefinition> metrics = definition.measures().stream().map(id -> catalog.metric(id).orElseThrow()).toList();
        if (scope.accounts().isEmpty()) {
            return noApplicableAccounts(scope, metrics);
        }

        boolean rootCategory = uses(definition, FinancialSemanticCatalog.ROOT_CATEGORY);
        boolean tagGrouping = definition.groupings().contains(FinancialSemanticCatalog.TAG);
        boolean monetary = metrics.get(0).currencyBehavior() == FinancialMetricCurrencyBehavior.PARTITION_BY_CURRENCY;
        List<DimensionSql> dimensions = definition
            .groupings()
            .stream()
            .map(grouping -> dimension(grouping, definition.dateBasis()))
            .toList();
        boolean currencyGrouped = definition.groupings().contains(FinancialSemanticCatalog.CURRENCY);
        String date = FinancialSemanticCatalog.POSTING_DATE.equals(definition.dateBasis()) ? "ft.posting_date" : "ft.transaction_date";

        StringBuilder sql = new StringBuilder();
        if (rootCategory) {
            sql.append(categoryTree());
        }
        List<String> select = new ArrayList<>();
        dimensions.forEach(dimension -> select.add(dimension.expression() + " as " + dimension.alias()));
        if (monetary && !currencyGrouped) {
            select.add("fa.currency as analytics_currency");
        }
        metrics.forEach(metric -> select.add(aggregate(metric) + " as " + metric.id().toLowerCase()));
        sql.append("select ").append(String.join(", ", select));
        sql.append(" from financial_transaction ft join financial_account fa on fa.id = ft.account_id ");
        sql.append("left join internal_transfer outgoing_transfer on outgoing_transfer.outgoing_transaction_id = ft.id ");
        sql.append("left join internal_transfer incoming_transfer on incoming_transfer.incoming_transaction_id = ft.id ");
        if (rootCategory) {
            sql.append("left join analytics_category_tree category_tree on category_tree.id = ft.category_id ");
        }
        if (tagGrouping) {
            sql.append(
                "left join rel_financial_transaction__tags grouped_transaction_tag on grouped_transaction_tag.financial_transaction_id = ft.id "
            );
            sql.append("left join tag grouped_tag on grouped_tag.id = grouped_transaction_tag.tags_id ");
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("accountIds", scope.accounts().stream().map(FinancialAccount::getId).toList());
        parameters.addValue("fromDate", scope.validatedRequest().resolvedContext().from());
        parameters.addValue("throughDate", scope.validatedRequest().resolvedContext().through());
        List<String> where = new ArrayList<>();
        where.add("fa.id in (:accountIds)");
        where.add(date + " between :fromDate and :throughDate");
        appendPredicates(where, parameters, definition.analyticalPredicates(), rootCategory);
        sql.append(" where ").append(String.join(" and ", where));

        List<String> groupBy = new ArrayList<>(dimensions.stream().map(DimensionSql::expression).toList());
        if (monetary && !currencyGrouped) {
            groupBy.add("fa.currency");
        }
        if (!groupBy.isEmpty()) {
            sql.append(" group by ").append(String.join(", ", groupBy));
            appendOrder(sql, definition, dimensions, monetary && !currencyGrouped);
        }
        sql.append(" limit :resultLimit");
        parameters.addValue("resultLimit", definition.limit());

        List<Map<String, Object>> databaseRows = jdbc.queryForList(sql.toString(), parameters);
        List<FinancialAnalyticsResultRow> rows = databaseRows
            .stream()
            .map(row -> row(row, definition, metrics, monetary, currencyGrouped))
            .toList();
        if (rows.isEmpty() && definition.groupings().isEmpty()) {
            rows = emptyScalarRows(scope.accounts(), definition, metrics, monetary);
        }
        List<FinancialAnalyticsWarning> warnings = warnings(definition, rows);
        FinancialMetricResultState completeness = rows
                .stream()
                .flatMap(row -> row.measures().stream())
                .anyMatch(value -> value.state() == FinancialMetricResultState.UNAVAILABLE)
            ? FinancialMetricResultState.PARTIAL
            : FinancialMetricResultState.COMPLETE;
        return FinancialAnalyticsResultFactory.result(scope, catalog, rows, completeness, warnings);
    }

    private FinancialAnalyticsResult noApplicableAccounts(FinancialAnalyticsExecutionScope scope, List<FinancialMetricDefinition> metrics) {
        List<FinancialAnalyticsMeasureValue> values = metrics
            .stream()
            .map(metric -> FinancialAnalyticsResultFactory.unavailable(metric.id(), metric, FinancialMetricResultState.NOT_APPLICABLE, null)
            )
            .toList();
        FinancialAnalyticsResultRow row = new FinancialAnalyticsResultRow(List.of(), null, values);
        return FinancialAnalyticsResultFactory.result(
            scope,
            catalog,
            List.of(row),
            FinancialMetricResultState.NOT_APPLICABLE,
            List.of(
                new FinancialAnalyticsWarning(
                    AnalyticsDiagnosticCode.NO_APPLICABLE_ACCOUNTS,
                    "No selected account is applicable to this execution context."
                )
            )
        );
    }

    private String aggregate(FinancialMetricDefinition metric) {
        String unlinked = "outgoing_transfer.id is null and incoming_transfer.id is null";
        return switch (metric.id()) {
            case "INCOME_TOTAL" -> "coalesce(sum(case when ft.flow = 'IN' and " + unlinked + " then ft.amount else 0 end), 0)";
            case "EXPENSE_TOTAL" -> "coalesce(sum(case when ft.flow = 'OUT' and " + unlinked + " then ft.amount else 0 end), 0)";
            case "NET_ECONOMIC_FLOW" -> "coalesce(sum(case when " +
            unlinked +
            " and ft.flow = 'IN' then ft.amount when " +
            unlinked +
            " and ft.flow = 'OUT' then -ft.amount else 0 end), 0)";
            case "ECONOMIC_TRANSACTION_COUNT" -> "coalesce(sum(case when " + unlinked + " then 1 else 0 end), 0)";
            case "AVERAGE_EXPENSE" -> "case when sum(case when ft.flow = 'OUT' and " +
            unlinked +
            " then 1 else 0 end) = 0 then null else sum(case when ft.flow = 'OUT' and " +
            unlinked +
            " then ft.amount else 0 end) / sum(case when ft.flow = 'OUT' and " +
            unlinked +
            " then 1 else 0 end) end";
            case "ACCOUNT_INFLOW" -> "coalesce(sum(case when ft.flow = 'IN' then ft.amount else 0 end), 0)";
            case "ACCOUNT_OUTFLOW" -> "coalesce(sum(case when ft.flow = 'OUT' then ft.amount else 0 end), 0)";
            case "ACCOUNT_NET_MOVEMENT" -> "coalesce(sum(case when ft.flow = 'IN' then ft.amount else -ft.amount end), 0)";
            default -> throw new IllegalArgumentException("Unsupported transaction aggregate metric: " + metric.id());
        };
    }

    private void appendPredicates(
        List<String> where,
        MapSqlParameterSource parameters,
        List<AnalyticalPredicate> predicates,
        boolean rootCategory
    ) {
        for (int index = 0; index < predicates.size(); index++) {
            AnalyticalPredicate predicate = predicates.get(index);
            String parameter = "predicate" + index;
            switch (predicate.attributeId()) {
                case FinancialSemanticCatalog.ACCOUNT -> addIds(where, parameters, parameter, "fa.id", predicate);
                case FinancialSemanticCatalog.ACCOUNT_TYPE -> addValues(where, parameters, parameter, "fa.account_type", predicate);
                case FinancialSemanticCatalog.CURRENCY -> addValues(where, parameters, parameter, "fa.currency", predicate);
                case FinancialSemanticCatalog.CATEGORY -> addIds(where, parameters, parameter, "ft.category_id", predicate);
                case FinancialSemanticCatalog.ROOT_CATEGORY -> {
                    if (!rootCategory) throw new IllegalStateException("Verified root-category filter requires its hierarchy join");
                    addIds(where, parameters, parameter, "category_tree.root_id", predicate);
                }
                case FinancialSemanticCatalog.TAG -> {
                    parameters.addValue(parameter, ids(predicate.values()));
                    where.add(
                        "exists (select 1 from rel_financial_transaction__tags filtered_transaction_tag where filtered_transaction_tag.financial_transaction_id = ft.id and filtered_transaction_tag.tags_id in (:" +
                        parameter +
                        "))"
                    );
                }
                case FinancialSemanticCatalog.ORIGIN -> addValues(where, parameters, parameter, "ft.origin", predicate);
                case FinancialSemanticCatalog.FLOW -> addValues(where, parameters, parameter, "ft.flow", predicate);
                case FinancialSemanticCatalog.TRANSACTION_DATE -> addDate(where, parameters, parameter, "ft.transaction_date", predicate);
                case FinancialSemanticCatalog.POSTING_DATE -> addDate(where, parameters, parameter, "ft.posting_date", predicate);
                case FinancialSemanticCatalog.TRANSFER_STATUS -> {
                    boolean linked = predicate.values().contains("LINKED");
                    boolean notLinked = predicate.values().contains("NOT_LINKED");
                    if (linked && !notLinked) where.add("(outgoing_transfer.id is not null or incoming_transfer.id is not null)");
                    if (notLinked && !linked) where.add("outgoing_transfer.id is null and incoming_transfer.id is null");
                }
                default -> throw new IllegalArgumentException("Unsupported verified predicate: " + predicate.attributeId());
            }
        }
    }

    private void addIds(
        List<String> where,
        MapSqlParameterSource parameters,
        String parameter,
        String column,
        AnalyticalPredicate predicate
    ) {
        addValues(where, parameters, parameter, column, predicate, ids(predicate.values()));
    }

    private void addValues(
        List<String> where,
        MapSqlParameterSource parameters,
        String parameter,
        String column,
        AnalyticalPredicate predicate
    ) {
        addValues(where, parameters, parameter, column, predicate, predicate.values());
    }

    private void addValues(
        List<String> where,
        MapSqlParameterSource parameters,
        String parameter,
        String column,
        AnalyticalPredicate predicate,
        Object values
    ) {
        parameters.addValue(parameter, values);
        where.add(
            column + (FinancialSemanticCatalog.EQUALS.equals(predicate.operatorId()) ? " = :" + parameter : " in (:" + parameter + ")")
        );
    }

    private void addDate(
        List<String> where,
        MapSqlParameterSource parameters,
        String parameter,
        String column,
        AnalyticalPredicate predicate
    ) {
        if (FinancialSemanticCatalog.BETWEEN.equals(predicate.operatorId())) {
            parameters.addValue(parameter + "From", java.time.LocalDate.parse(predicate.values().get(0)));
            parameters.addValue(parameter + "Through", java.time.LocalDate.parse(predicate.values().get(1)));
            where.add(column + " between :" + parameter + "From and :" + parameter + "Through");
        } else {
            parameters.addValue(parameter, java.time.LocalDate.parse(predicate.values().get(0)));
            where.add(column + " = :" + parameter);
        }
    }

    private FinancialAnalyticsResultRow row(
        Map<String, Object> source,
        QueryDefinition definition,
        List<FinancialMetricDefinition> metrics,
        boolean monetary,
        boolean currencyGrouped
    ) {
        List<FinancialAnalyticsDimensionValue> dimensions = definition
            .groupings()
            .stream()
            .map(id -> new FinancialAnalyticsDimensionValue(id, source.get(id.toLowerCase())))
            .toList();
        String currency = monetary
            ? String.valueOf(source.get(currencyGrouped ? FinancialSemanticCatalog.CURRENCY.toLowerCase() : "analytics_currency"))
            : null;
        List<FinancialAnalyticsMeasureValue> values = new ArrayList<>();
        for (FinancialMetricDefinition metric : metrics) {
            BigDecimal value = decimal(source.get(metric.id().toLowerCase()));
            if (value == null) {
                values.add(
                    FinancialAnalyticsResultFactory.unavailable(metric.id(), metric, FinancialMetricResultState.UNAVAILABLE, currency)
                );
            } else {
                values.add(
                    new FinancialAnalyticsMeasureValue(
                        metric.id(),
                        value,
                        FinancialAnalyticsResultFactory.stateFor(value),
                        metric.unit(),
                        currency
                    )
                );
            }
        }
        return new FinancialAnalyticsResultRow(dimensions, currency, values);
    }

    private List<FinancialAnalyticsResultRow> emptyScalarRows(
        Collection<FinancialAccount> accounts,
        QueryDefinition definition,
        List<FinancialMetricDefinition> metrics,
        boolean monetary
    ) {
        List<String> currencies = monetary
            ? accounts.stream().map(account -> account.getCurrency().name()).distinct().toList()
            : List.of("");
        List<FinancialAnalyticsResultRow> rows = new ArrayList<>();
        for (String currency : currencies) {
            List<FinancialAnalyticsMeasureValue> values = metrics
                .stream()
                .map(metric -> FinancialAnalyticsResultFactory.zero(metric.id(), metric, monetary ? currency : null))
                .toList();
            rows.add(new FinancialAnalyticsResultRow(List.of(), monetary ? currency : null, values));
        }
        return rows;
    }

    private List<FinancialAnalyticsWarning> warnings(QueryDefinition definition, List<FinancialAnalyticsResultRow> rows) {
        List<FinancialAnalyticsWarning> warnings = new ArrayList<>();
        if (FinancialSemanticCatalog.POSTING_DATE.equals(definition.dateBasis())) {
            warnings.add(
                new FinancialAnalyticsWarning(
                    AnalyticsDiagnosticCode.POSTING_DATE_OMITS_MISSING_VALUES,
                    "Transactions without a posting date are omitted."
                )
            );
        }
        if (definition.groupings().contains(FinancialSemanticCatalog.TAG)) {
            warnings.add(
                new FinancialAnalyticsWarning(
                    AnalyticsDiagnosticCode.NON_ADDITIVE_TAG_GROUPING,
                    "Tag groups can overlap and must not be summed as a portfolio total."
                )
            );
        }
        if (
            rows.stream().flatMap(row -> row.measures().stream()).anyMatch(value -> value.state() == FinancialMetricResultState.UNAVAILABLE)
        ) {
            warnings.add(
                new FinancialAnalyticsWarning(
                    AnalyticsDiagnosticCode.UNAVAILABLE_HISTORICAL_DATA,
                    "One or more requested calculations are unavailable."
                )
            );
        }
        return List.copyOf(warnings);
    }

    private void appendOrder(StringBuilder sql, QueryDefinition definition, List<DimensionSql> dimensions, boolean currencyFacet) {
        String ordered = definition.sort() == null
            ? String.join(", ", dimensions.stream().map(DimensionSql::alias).toList())
            : definition.sort().groupingId().toLowerCase() + " " + definition.sort().direction();
        if (currencyFacet) ordered = ordered.isBlank() ? "analytics_currency" : ordered + ", analytics_currency";
        sql.append(" order by ").append(ordered);
    }

    private DimensionSql dimension(String id, String dateBasis) {
        String date = FinancialSemanticCatalog.POSTING_DATE.equals(dateBasis) ? "ft.posting_date" : "ft.transaction_date";
        return switch (id) {
            case FinancialSemanticCatalog.DAY -> new DimensionSql(date, "day");
            case FinancialSemanticCatalog.WEEK -> new DimensionSql("date_trunc('week', " + date + ")::date", "week");
            case FinancialSemanticCatalog.MONTH -> new DimensionSql("date_trunc('month', " + date + ")::date", "month");
            case FinancialSemanticCatalog.YEAR -> new DimensionSql("extract(year from " + date + ")", "year");
            case FinancialSemanticCatalog.ACCOUNT -> new DimensionSql("fa.id", "account");
            case FinancialSemanticCatalog.ACCOUNT_TYPE -> new DimensionSql("fa.account_type", "account_type");
            case FinancialSemanticCatalog.CURRENCY -> new DimensionSql("fa.currency", "currency");
            case FinancialSemanticCatalog.CATEGORY -> new DimensionSql("coalesce(ft.category_id, 0)", "category");
            case FinancialSemanticCatalog.ROOT_CATEGORY -> new DimensionSql("coalesce(category_tree.root_id, 0)", "root_category");
            case FinancialSemanticCatalog.TAG -> new DimensionSql("coalesce(grouped_tag.id, 0)", "tag");
            case FinancialSemanticCatalog.ORIGIN -> new DimensionSql("ft.origin", "origin");
            case FinancialSemanticCatalog.FLOW -> new DimensionSql("ft.flow", "flow");
            case FinancialSemanticCatalog.TRANSFER_STATUS -> new DimensionSql(
                "case when outgoing_transfer.id is null and incoming_transfer.id is null then 'NOT_LINKED' else 'LINKED' end",
                "transfer_status"
            );
            default -> throw new IllegalArgumentException("Unsupported verified grouping: " + id);
        };
    }

    private boolean uses(QueryDefinition definition, String attribute) {
        return (
            definition.groupings().contains(attribute) ||
            definition.analyticalPredicates().stream().anyMatch(predicate -> attribute.equals(predicate.attributeId()))
        );
    }

    private String categoryTree() {
        return (
            "with recursive analytics_category_tree(id, parent_category_id, root_id) as (" +
            "select id, parent_category_id, id from category where parent_category_id is null union all " +
            "select category.id, category.parent_category_id, tree.root_id from category join analytics_category_tree tree on category.parent_category_id = tree.id) "
        );
    }

    private List<Long> ids(List<String> values) {
        return values.stream().map(Long::valueOf).toList();
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal.stripTrailingZeros();
        if (value instanceof Number number) return new BigDecimal(number.toString()).stripTrailingZeros();
        return new BigDecimal(value.toString()).stripTrailingZeros();
    }

    private record DimensionSql(String expression, String alias) {}
}
