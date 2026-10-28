# Analytics R3-1: Clean Semantic Foundation

## Scope and versioning

This is the first Analytics contract on the clean `refactor-analytics` branch. It uses `schemaVersion: 1` because there is no deployed Analytics contract to preserve. The version is carried by `QueryDefinition`, `VisualizationContext`, `FinancialAnalyticsExecutionRequest`, and the read-only catalog. Unknown schema versions and invalid semantic identifiers are rejected by the backend validator; serialized request records are annotated to reject unknown fields rather than silently dropping them.

There is one semantic model, `FINTRACK_FINANCIAL`, and one backend registry, `FinancialSemanticCatalog`. No Analytics V1 types, migration adapter, legacy endpoint, dashboard configuration, SQL-like parser, or frontend implementation is active on this branch.

## Canonical contracts

- `QueryDefinition` says **what** is calculated: one to four registered measures, zero to two groupings, up to ten AND-composed predicates, date basis, sort, and bounded result limit. It has no owner, account selection, execution time, dashboard ID, SQL, physical persistence reference, or result data.
- `VisualizationContext` says **where and when** it runs: a time selection, account IDs, and currency codes. Empty account and currency lists mean all values accessible to the authenticated user; they do not mean a global scope.
- `FinancialAnalyticsExecutionRequest` combines exactly one versioned definition and one versioned context. It is a future execution input, not an endpoint or a result.
- `FinancialAnalyticsExecutionCompiler` is an intentionally unimplemented R3-2 seam. It accepts a validated request and will compile it onto existing FINTRACK financial executors. R3-1 performs no aggregation and returns no financial result rows.

Normalization is deterministic: IDs are trimmed and uppercased, account IDs and currency codes are de-duplicated and ordered, predicate values are canonicalized, AND predicates are ordered by attribute/operator/value, default date basis is `TRANSACTION_DATE`, default limit is 100, and an omitted sort becomes ascending on the first requested grouping. Measure and grouping order remains the caller's meaningful order. Normalizing an already normalized valid request yields the same request.

## Time policy

The authoritative business timezone is `America/Mexico_City`, based on an injected backend `Clock`, never browser time. Ranges are inclusive `LocalDate` ranges.

| Relative selection       | Inclusive range                                       |
| ------------------------ | ----------------------------------------------------- |
| `TODAY`                  | today through today                                   |
| `THIS_MONTH`             | first day of this month through today                 |
| `PREVIOUS_MONTH`         | first through last day of the previous calendar month |
| `LAST_30_DAYS`           | today minus 29 days through today                     |
| `LAST_6_CALENDAR_MONTHS` | first day of the month five months ago through today  |
| `THIS_YEAR`              | January 1 through today                               |

`ABSOLUTE_RANGE` requires `from` and `through`, with `from <= through`; it may not carry a relative selection. `RELATIVE` may not carry explicit dates. For a future `AS_OF` scalar executor, the context's inclusive `through` date is the cutoff. A grouped `AS_OF` series will derive its observation cutoffs from the requested grouping in R3-2. Nothing freezes a relative period when a definition is saved.

## Financial metric registry

The registry exposes these 16 governed metrics. Every definition includes bilingual metadata, subject area, time mode, executor specialization, account applicability, supported date bases, compatible groupings/predicates, unit, currency behavior, additivity, internal-transfer treatment, availability constraints, and warnings.

| Metric                       | Time / executor                          | Meaning and transfer treatment                                                                          |
| ---------------------------- | ---------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `INCOME_TOTAL`               | PERIOD / transaction aggregate           | Eligible incoming economic money; excludes linked internal-transfer legs.                               |
| `EXPENSE_TOTAL`              | PERIOD / transaction aggregate           | Eligible outgoing economic money; excludes linked internal-transfer legs.                               |
| `NET_ECONOMIC_FLOW`          | PERIOD / transaction aggregate           | Eligible income minus eligible expenses; excludes linked legs.                                          |
| `ECONOMIC_TRANSACTION_COUNT` | PERIOD / transaction aggregate           | Count of eligible economic ledger transactions, excluding linked legs.                                  |
| `AVERAGE_EXPENSE`            | PERIOD / transaction aggregate           | Eligible expenses divided by eligible outgoing transaction count; unavailable when denominator is zero. |
| `ACCOUNT_INFLOW`             | PERIOD / transaction aggregate           | Account-relative inflows; linked transfer legs remain relevant.                                         |
| `ACCOUNT_OUTFLOW`            | PERIOD / transaction aggregate           | Account-relative outflows; linked transfer legs remain relevant.                                        |
| `ACCOUNT_NET_MOVEMENT`       | PERIOD / transaction aggregate           | Account inflows minus outflows; linked transfer legs remain relevant.                                   |
| `ACCOUNT_BALANCE`            | AS_OF / account snapshot                 | Recorded balance of debit, cash, and investment accounts.                                               |
| `ACCOUNT_SIGNED_POSITION`    | AS_OF / account snapshot                 | Asset balances are positive and credit-card debt is negative.                                           |
| `NET_RECORDED_POSITION`      | AS_OF / account snapshot                 | Sum of signed account positions inside each currency partition.                                         |
| `CREDIT_CARD_DEBT`           | AS_OF / account snapshot                 | Recorded credit debt; it does **not** need a credit limit.                                              |
| `CREDIT_LIMIT`               | AS_OF / account snapshot                 | Current persisted credit limit; historical limit is unavailable without history.                        |
| `AVAILABLE_CREDIT`           | AS_OF / account snapshot                 | Current persisted credit limit minus recorded debt.                                                     |
| `CREDIT_UTILIZATION`         | AS_OF / account snapshot                 | Recorded debt divided by a nonzero current credit limit.                                                |
| `INTERNAL_TRANSFER_COUNT`    | PERIOD / transfer-relationship aggregate | Counts persisted transfer relationships, never both transaction legs.                                   |

Metric applicability is evaluated per metric after caller account scope is resolved. `ACCOUNT_BALANCE` applies to debit, cash, and investment accounts; signed and net positions apply to all account types; credit measures apply only to credit cards. A multi-measure request is not globally narrowed merely because one metric is inapplicable to an account type. Future execution will express per-metric coverage rather than treating inapplicability as zero.

Future result states are intentionally distinct: `ZERO` is a calculated zero, `NO_DATA` has no eligible observations, `NOT_APPLICABLE` is outside metric applicability, `UNAVAILABLE` lacks required historical/current source data, `PARTIAL` has a mix of usable and unavailable/inapplicable scoped observations, and `COMPLETE` means all scoped observations needed by that metric were calculated. `COMPLETE` is the overall coverage state, not a numerical observation.

### Credit and historical correctness

`CREDIT_CARD_DEBT` requires a valid account position and opening date only. It does not carry a credit-limit requirement. `CREDIT_LIMIT` and `AVAILABLE_CREDIT` require a current persisted credit limit. `CREDIT_UTILIZATION` additionally requires that limit to be nonzero. No metric invents an account position before `initialBalanceDate`, and no historical credit limit is invented because FINTRACK stores only the current limit.

### Money and currencies

Money metrics use `PARTITION_BY_CURRENCY`. Their R3-2 result contract must contain a currency facet even when the caller did not spend a grouping slot on `CURRENCY`; the facet does not consume one of the two user grouping slots. Distinct currencies are never summed and no FX behavior exists. Count and ratio metrics are non-monetary. A future single-currency response may use a scalar representation only when its explicit currency context remains unambiguous.

## Safe analytical attributes

The catalog includes only governed concepts backed by current FINTRACK behavior: transaction date, posting date, day/week/month/year, account, account type, currency, category, root category, tag, origin, flow, and transfer status. Definitions state value type, allowed operators, grouping/filter capability, compatible subject areas, cardinality, and whether ownership/reference validation is required.

No physical table/column names, raw JPA relationships, user/audit fields, external references, free-text search, arbitrary joins, Merchant, or CategoryGroup are exposed. Financial institution and weekday are not advertised as executable capabilities.

## Validation and authorization

`FinancialAnalyticsValidator` independently validates the definition, context, cross-metric compatibility, predicate values, and ownership references. It returns `AnalyticsValidationResult` with field-addressable `AnalyticsDiagnostic` values and machine-readable `AnalyticsDiagnosticCode`; it does not run an aggregation.

It rejects invalid versions, unknown model/metric/attribute/operator, bad sort/date basis/grouping, duplicate measures or groupings, malformed/excessive predicates, unsupported currency values, bad dates, invalid reference IDs, incompatible time mode/executor/unit/currency behavior, and foreign account/category/tag IDs. Existing but inactive references remain valid when owned. Administrators are deliberately scoped to their own login for financial references; administrator authority is not cross-owner Analytics access.

The read-only authenticated catalog is `GET /api/analytics/catalog`. It contains static capabilities only: no selected account IDs, user data, financial transactions, or execution results. R3-1 adds no other Analytics REST endpoint.

## R3-2 prerequisites and deliberate omissions

R3-2 can implement one compiler against this validated request and the existing financial executors. It must implement transaction/posting-date execution, exact account opening-date behavior, account applicability coverage, transfer policy, currency partition result rows, and the result-state contract above. It must not create a second query engine.

R3-1 deliberately does **not** implement aggregation, an execution resource, widgets, dashboards, templates, a Metric Explorer UI, a SQL-like language, charts, persistence for Analytics artifacts, or migration/compatibility code.

## Test boundary

Tested now: registry semantics, all 16 IDs, debt-vs-limit constraints, applicability metadata, transfer policy, multi-currency result shape, strict JSON contract parsing, deterministic normalization, compatibility and complexity diagnostics, business-timezone range resolution, real PostgreSQL ownership checks, inactive owned references, no-mutation validation, admin self-scope, and catalog endpoint authentication.

From prior implementation, not yet ported: financial aggregation/executor integration tests. Deferred to R3-2: actual values, date-basis execution, transfer calculation queries, historical availability outputs, currency-partition result rows, and result-state production behavior.
