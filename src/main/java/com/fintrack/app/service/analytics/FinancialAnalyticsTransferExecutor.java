package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.FinancialAccount;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Counts persisted transfer relationships once, even when both linked legs are in the selected account scope. */
@Component
class FinancialAnalyticsTransferExecutor {

    private final NamedParameterJdbcTemplate jdbc;
    private final FinancialSemanticCatalog catalog;

    FinancialAnalyticsTransferExecutor(NamedParameterJdbcTemplate jdbc, FinancialSemanticCatalog catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    FinancialAnalyticsResult execute(FinancialAnalyticsExecutionScope scope) {
        QueryDefinition definition = scope.validatedRequest().request().definition();
        FinancialMetricDefinition metric = catalog.metric("INTERNAL_TRANSFER_COUNT").orElseThrow();
        if (scope.accounts().isEmpty()) {
            return empty(scope, metric, FinancialMetricResultState.NOT_APPLICABLE, AnalyticsDiagnosticCode.NO_APPLICABLE_ACCOUNTS);
        }
        boolean accountFacet = definition
            .groupings()
            .stream()
            .anyMatch(
                grouping ->
                    grouping.equals(FinancialSemanticCatalog.ACCOUNT) ||
                    grouping.equals(FinancialSemanticCatalog.ACCOUNT_TYPE) ||
                    grouping.equals(FinancialSemanticCatalog.CURRENCY)
            );
        List<DimensionSql> dimensions = definition.groupings().stream().map(this::dimension).toList();
        StringBuilder sql = new StringBuilder("select ");
        List<String> select = new ArrayList<>(
            dimensions.stream().map(dimension -> dimension.expression() + " as " + dimension.alias()).toList()
        );
        select.add("count(distinct internal_transfer.id) as internal_transfer_count");
        sql.append(String.join(", ", select));
        sql.append(
            " from internal_transfer join financial_transaction outgoing on outgoing.id = internal_transfer.outgoing_transaction_id "
        );
        sql.append("join financial_account outgoing_account on outgoing_account.id = outgoing.account_id ");
        sql.append("join financial_transaction incoming on incoming.id = internal_transfer.incoming_transaction_id ");
        sql.append("join financial_account incoming_account on incoming_account.id = incoming.account_id ");
        if (accountFacet) {
            sql.append(
                "cross join lateral (values (outgoing_account.id, outgoing_account.account_type, outgoing_account.currency), (incoming_account.id, incoming_account.account_type, incoming_account.currency)) as transfer_account(id, account_type, currency) "
            );
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("accountIds", scope.accounts().stream().map(FinancialAccount::getId).toList());
        parameters.addValue("fromDate", scope.validatedRequest().resolvedContext().from());
        parameters.addValue("throughDate", scope.validatedRequest().resolvedContext().through());
        List<String> where = new ArrayList<>();
        where.add("outgoing_account.user_id = incoming_account.user_id");
        where.add("outgoing.transaction_date between :fromDate and :throughDate");
        where.add(
            accountFacet
                ? "transfer_account.id in (:accountIds)"
                : "(outgoing_account.id in (:accountIds) or incoming_account.id in (:accountIds))"
        );
        appendPredicates(where, parameters, definition.analyticalPredicates(), accountFacet);
        sql.append(" where ").append(String.join(" and ", where));
        if (!dimensions.isEmpty()) {
            sql.append(" group by ").append(String.join(", ", dimensions.stream().map(DimensionSql::expression).toList()));
            String order = definition.sort() == null
                ? String.join(", ", dimensions.stream().map(DimensionSql::alias).toList())
                : definition.sort().groupingId().toLowerCase() + " " + definition.sort().direction();
            sql.append(" order by ").append(order);
        }
        sql.append(" limit :resultLimit");
        parameters.addValue("resultLimit", definition.limit());
        List<FinancialAnalyticsResultRow> rows = jdbc
            .queryForList(sql.toString(), parameters)
            .stream()
            .map(source -> row(source, definition, metric))
            .toList();
        if (rows.isEmpty() && definition.groupings().isEmpty()) {
            rows = List.of(
                new FinancialAnalyticsResultRow(
                    List.of(),
                    null,
                    List.of(
                        new FinancialAnalyticsMeasureValue(
                            metric.id(),
                            BigDecimal.ZERO,
                            FinancialMetricResultState.ZERO,
                            metric.unit(),
                            null
                        )
                    )
                )
            );
        }
        return FinancialAnalyticsResultFactory.result(scope, catalog, rows, FinancialMetricResultState.COMPLETE, List.of());
    }

    private void appendPredicates(
        List<String> where,
        MapSqlParameterSource parameters,
        List<AnalyticalPredicate> predicates,
        boolean accountFacet
    ) {
        for (int index = 0; index < predicates.size(); index++) {
            AnalyticalPredicate predicate = predicates.get(index);
            String parameter = "predicate" + index;
            String account = accountFacet
                ? "transfer_account.id"
                : "(outgoing_account.id in (:" + parameter + ") or incoming_account.id in (:" + parameter + "))";
            switch (predicate.attributeId()) {
                case FinancialSemanticCatalog.ACCOUNT -> {
                    parameters.addValue(parameter, predicate.values().stream().map(Long::valueOf).toList());
                    where.add(accountFacet ? "transfer_account.id in (:" + parameter + ")" : account);
                }
                case FinancialSemanticCatalog.ACCOUNT_TYPE -> {
                    parameters.addValue(parameter, predicate.values());
                    where.add(
                        accountFacet
                            ? "transfer_account.account_type in (:" + parameter + ")"
                            : "(outgoing_account.account_type in (:" +
                            parameter +
                            ") or incoming_account.account_type in (:" +
                            parameter +
                            "))"
                    );
                }
                case FinancialSemanticCatalog.CURRENCY -> {
                    parameters.addValue(parameter, predicate.values());
                    where.add(
                        accountFacet
                            ? "transfer_account.currency in (:" + parameter + ")"
                            : "(outgoing_account.currency in (:" + parameter + ") or incoming_account.currency in (:" + parameter + "))"
                    );
                }
                default -> throw new IllegalArgumentException("Unsupported verified transfer predicate: " + predicate.attributeId());
            }
        }
    }

    private FinancialAnalyticsResultRow row(Map<String, Object> source, QueryDefinition definition, FinancialMetricDefinition metric) {
        List<FinancialAnalyticsDimensionValue> dimensions = definition
            .groupings()
            .stream()
            .map(grouping -> new FinancialAnalyticsDimensionValue(grouping, source.get(grouping.toLowerCase())))
            .toList();
        BigDecimal value = decimal(source.get("internal_transfer_count"));
        return new FinancialAnalyticsResultRow(
            dimensions,
            null,
            List.of(
                new FinancialAnalyticsMeasureValue(metric.id(), value, FinancialAnalyticsResultFactory.stateFor(value), metric.unit(), null)
            )
        );
    }

    private FinancialAnalyticsResult empty(
        FinancialAnalyticsExecutionScope scope,
        FinancialMetricDefinition metric,
        FinancialMetricResultState state,
        AnalyticsDiagnosticCode warning
    ) {
        return FinancialAnalyticsResultFactory.result(
            scope,
            catalog,
            List.of(
                new FinancialAnalyticsResultRow(
                    List.of(),
                    null,
                    List.of(FinancialAnalyticsResultFactory.unavailable(metric.id(), metric, state, null))
                )
            ),
            state,
            List.of(new FinancialAnalyticsWarning(warning, "No selected account is applicable to this execution context."))
        );
    }

    private DimensionSql dimension(String grouping) {
        return switch (grouping) {
            case FinancialSemanticCatalog.DAY -> new DimensionSql("outgoing.transaction_date", "day");
            case FinancialSemanticCatalog.WEEK -> new DimensionSql("date_trunc('week', outgoing.transaction_date)::date", "week");
            case FinancialSemanticCatalog.MONTH -> new DimensionSql("date_trunc('month', outgoing.transaction_date)::date", "month");
            case FinancialSemanticCatalog.YEAR -> new DimensionSql("extract(year from outgoing.transaction_date)", "year");
            case FinancialSemanticCatalog.ACCOUNT -> new DimensionSql("transfer_account.id", "account");
            case FinancialSemanticCatalog.ACCOUNT_TYPE -> new DimensionSql("transfer_account.account_type", "account_type");
            case FinancialSemanticCatalog.CURRENCY -> new DimensionSql("transfer_account.currency", "currency");
            default -> throw new IllegalArgumentException("Unsupported verified transfer grouping: " + grouping);
        };
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal.stripTrailingZeros();
        if (value instanceof Number number) return new BigDecimal(number.toString()).stripTrailingZeros();
        return new BigDecimal(value.toString()).stripTrailingZeros();
    }

    private record DimensionSql(String expression, String alias) {}
}
