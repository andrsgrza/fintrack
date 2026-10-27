# Analytics R3-0 selective reuse inventory

This is a read-only assessment of `implement-analytics`. It is a porting
plan, not an import list: R3-0 contains none of these packages or contracts.

## REUSE_LOGIC

| Source on `implement-analytics`                                                                             | Preserve                                                                                                                                                                                                     | Required dependencies / policy                                                                                                         |
| ----------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| `src/main/java/com/fintrack/app/service/metrics/FinancialMetricTimePolicy.java`                             | The explicit product timezone (`America/Mexico_City`) and deterministic relative-period resolution.                                                                                                          | A clock supplied at the new semantic-runtime boundary; never the browser or host timezone.                                             |
| `src/main/java/com/fintrack/app/service/metrics/FinancialMetricAvailabilityPolicy.java`                     | Do not manufacture an account balance before `initialBalanceDate`; credit-limit, available-credit and utilization are unavailable for historical dates without a limit history; a zero limit is unavailable. | `FinancialAccount`, `CreditAccountDetails`, `Clock`, and the new temporal policy.                                                      |
| `src/main/java/com/fintrack/app/service/metrics/FinancialTransactionMetricSemantics.java`                   | Economic income/expense excludes a persisted `InternalTransfer` leg, while account movement includes the account-relative leg.                                                                               | `FinancialTransaction`, `TransactionFlow`, and relationship-aware transfer access; scope selection must never redefine a transfer leg. |
| `src/main/java/com/fintrack/app/service/FinancialAccountBalanceService.java` (already in the approved base) | The authoritative balance/debt calculation, rather than recalculating account snapshots from ad-hoc analytics SQL.                                                                                           | Existing account and credit-domain services.                                                                                           |

The corresponding reusable test assertions are in:

- `src/test/java/com/fintrack/app/service/metrics/FinancialMetricTimePolicyTest.java`
- `src/test/java/com/fintrack/app/service/metrics/FinancialMetricPoliciesTest.java`
- `src/test/java/com/fintrack/app/service/metrics/FinancialMetricTransferRelationshipIT.java`

They should be recreated around the new contract, keeping their financial
facts: timezone boundaries, unavailable versus zero, transfer relationship
semantics, and current-versus-historical credit availability.

## REWRITE_FOR_NEW_CONTRACT

| Source on `implement-analytics`                                                                                                                                             | Why it is not portable as-is                                                  | What to retain when rewriting                                                                                                                                                      |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `service/metrics/FinancialMetricDefinition.java`, `FinancialMetricRegistry.java`, `FinancialMetric*.java`, `FinancialDimension*.java`                                       | These are the V1 enum catalog and compatibility surface.                      | Metric meanings, supported time mode, additivity, currency and transfer policies; model them in the single new canonical semantic schema.                                          |
| `service/analytics/FinancialMetricExecutionService.java`                                                                                                                    | It accepts the V1 `FinancialMetricQuery` and delegates through V1 validation. | One read-only authenticated entry point and one execution specialization per query.                                                                                                |
| `service/analytics/LedgerAggregationExecutor.java`                                                                                                                          | SQL aliases, V1 filters and `FinancialMetricQueryPlan` are V1-specific.       | Owner-scoped predicates, transaction-date/posting-date behavior, omitted posting-date disclosure, multi-currency safety and relationship-aware exclusion of transfer legs.         |
| `service/analytics/AccountPositionExecutor.java`                                                                                                                            | Its grouping/result types are V1-specific.                                    | Delegation to `FinancialAccountBalanceService`, negative credit-card position, and the availability policy above.                                                                  |
| `service/analytics/TransferAggregationExecutor.java`                                                                                                                        | It emits a V1 result schema.                                                  | Count one `InternalTransfer` relationship, never two legs; account scope must use either transfer side unless grouped by leg account.                                              |
| `src/main/webapp/app/dashboards/metric-visualization.tsx`, `metric-data-table.tsx`, `metric-result-adapter.ts`, `metric-date-context.ts`, `dashboard-account-identity*.tsx` | They read V1 `FinancialMetricResult` and dashboard reference maps.            | Accessibility, multi-currency fallback-to-table, completeness warnings, table fallback, account identity, and chart rendering patterns after they consume the new result contract. |

Useful behavior scenarios to recreate (not copy) are in:

- `src/test/java/com/fintrack/app/service/analytics/FinancialMetricExecutionServiceIT.java`
- `src/test/javascript/cypress/e2e/entity/metric-explorer.cy.ts`
- `src/main/webapp/app/dashboards/metric-visualization.spec.tsx`
- `src/main/webapp/app/dashboards/metric-data-table.spec.tsx`

## DO_NOT_PORT

- The V1 transport/AST/runtime family: `service/analytics/FinancialMetricQuery*.java`, `FinancialMetricFilter*.java`, `FinancialMetricPeriod.java`, `FinancialMetricResult.java`, `FinancialMetricDataset.java`, `FinancialMetricSort*.java`, `FinancialMetricColumn.java` and `FinancialAnalyticsResource.java`.
- The V1 SQL-like parser/formatter/service family: `FinancialQueryLanguageParser.java`, `FinancialQueryLanguageFormatter.java`, `FinancialQueryLanguageService.java`, request/response/diagnostic/exception types and their parser tests. The new language must compile into the one new canonical model, not adapt V1 ASTs.
- `FinancialMetricQueryValidator.java` and `FinancialMetricQueryPlan.java`. They mix V1 shape validation, ownership lookup and execution planning; the new architecture must give each concern an explicit new boundary.
- The complete Personal Dashboard V1 persistence/API/UI family: `PersonalDashboard*` domain types, repositories, services, DTOs, resources, `.jhipster` metadata, Liquibase changelog, `personal-dashboard-*.ts*`, and `recommended-dashboard.tsx`. These include the discarded widget contracts and bindings.
- V1 default/recommended catalogs: `PersonalDashboardTemplateCatalog.java`, `dashboard-templates.ts`, and `dashboard-query.ts`. R3-1 must have one catalog and one schema.
- The V1 dashboard API/types and Metric Explorer state machine: `analytics-api.ts`, `analytics.types.ts`, `metric-explorer*.ts*`, `use-dashboard-queries.ts`, `personal-dashboard-api.ts`, plus all dashboard routes and translations tied to those contracts.

## Non-negotiable policies for R3-1

1. Every executor is owner-scoped before aggregation; category and tag filters must be validated as accessible to that owner.
2. Monetary aggregates require one currency or an explicit currency grouping/filter; no silent conversion or summation across currencies.
3. Economic metrics exclude persisted internal-transfer legs. Account movement is explicitly different and includes them.
4. Position facts respect each account's opening date. Historical credit limits are unavailable unless versioned data exists.
5. `0`, partial, and unavailable remain distinct results and must be visible in the UI.
6. New tests must target the new canonical AST/schema and its endpoint only—never a V1 compatibility reader, conversion flow, or binding state.
