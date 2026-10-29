# Analytics V2.2: temporal and multi-dimensional semantic queries

## Canonical model

V2.2 retains the single `SemanticQueryDefinition`. The visualization context owns the inclusive date range, account selection and currency selection. The definition owns only the calculation and its ordered `groupBy` fields.

`TRANSACTION_DATE` remains the sole ledger time binding. It selects rows in the context range. The following governed grouping fields partition those selected date-only rows without changing that range:

- `TRANSACTION_DAY` — `YYYY-MM-DD`
- `TRANSACTION_WEEK` — ISO week-year and week, `IYYY-WIW` (for example `2026-W01`)
- `TRANSACTION_MONTH` — `YYYY-MM`
- `TRANSACTION_YEAR` — `YYYY`

There is no timestamp conversion, browser timezone input or `postingDate` alternative in this slice. PostgreSQL evaluates the date-only `transaction_date` directly.

The ordered `groupBy` list accepts a primary breakdown and one optional comparison breakdown. It rejects duplicate fields, more than two fields, and two temporal buckets together. Supported ledger combinations include month + category/root/branch/account/institution/tag, week + account, and year + account type. Position queries retain their stricter `ACCOUNT_ID + OBSERVATION_MONTH` shape.

## Result contract

Each result row carries an ordered tuple of `SemanticAnalyticsResultDimension` values and independent measure values. Temporal dimensions use stable bucket identities and unambiguous labels; account and institution dimensions expose owner-safe metadata. Monetary rows remain partitioned by account currency, so values in MXN and USD are never summed into a shared amount.

Category metadata, hierarchy colors and tag metadata remain inherited from V2.1. Tag grouping still uses a deliberate join only when it is selected as a grouping; tag filters use `EXISTS`. Consequently, ordinary aggregates never fan out, and every tag-grouped result retains `NON_ADDITIVE_OVERLAPPING_TAG_GROUPS`.

Each projection can retain independent conditions. For example, economic income and economic expense can be two `SUM(TRANSACTION_AMOUNT)` projections with distinct flow and unlinked-transfer conditions while sharing `GROUP BY TRANSACTION_MONTH`.

## Sparse and historical availability policy

The ledger does **not** invent empty buckets. A period/bucket with no eligible transaction produces no row and the result is `NO_DATA` when no rows exist. A group that exists but whose conditional calculation is zero has value `0` and availability `ZERO`.

Account positions are different: they are authoritative observations calculated by `FinancialAccountBalanceService`, not transaction sums. They continue to emit an `UNAVAILABLE` value for months before `initialBalanceDate`; a result containing both usable and unavailable observations is `PARTIAL`. No day/week/year position expansion was added.

## Explicit non-goals

V2.2 does not implement the approved Explorer React redesign, chart rendering, dashboard persistence, Top-N presentation, a new credit or internal-transfer dataset, arbitrary SQL, zero-fill policy controls, or a `postingDate` policy. It does not change ports, Spring profiles, datasource configuration or development data.

## Evidence

`SemanticAnalyticsExecutionServiceIT` exercises day/week/month/year grouping, the 2025→2026 ISO week-year boundary, context boundaries, month + category/root/selected branch/account/institution/tag, currency partitioning, income + expense projections, count + average, sparse/no-data versus calculated zero, transfer exclusion, owner isolation, five pre-existing golden queries and monthly historical positions.

`SemanticAnalyticsDslServiceTest` proves temporal parse/format/AST round-trip, and `SemanticAnalyticsValidatorTest` proves bounded primary + comparison composition and rejects two temporal breakdowns.
