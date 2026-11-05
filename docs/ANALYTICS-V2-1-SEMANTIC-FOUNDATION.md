# Analytics V2.1 semantic foundation

> **Historical V2.1 slice.** Its intentional deferrals have since been addressed by later work.
> For the current Explorer, grouping, and dashboard behavior, read
> [ANALYTICS-V2-5-EXPLORER-DASHBOARDS.md](ANALYTICS-V2-5-EXPLORER-DASHBOARDS.md).

## Decision

Analytics keeps one `SemanticQueryDefinition` for every executable analytical question. A query has a dataset, one to four aggregate projections, global conditions, groupings, ordering and a limit. `VisualizationContext` remains separate: it supplies only the authenticated user's effective date range, account scope and currency scope. It does not mutate or become part of the saved/query definition.

V2.1 extends the transaction-ledger dataset with owner-scoped category and tag semantics. It is an additive extension of the current semantic contract; it does not add SQL, JPA paths, client-defined joins or a second execution engine.

## Categories

`CATEGORY_ID` is the assigned category and is the only category filter field. It supports exact `EQUALS`/`IN`, `DESCENDANT_OF` for an owner-scoped category plus every descendant at any depth, and `IS_UNCATEGORIZED` for transactions with no assigned category.

| Grouping                | Meaning                                                                                                                                                      |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `CATEGORY_ID`           | The assigned category.                                                                                                                                       |
| `CATEGORY_ROOT`         | The root ancestor of the assigned category.                                                                                                                  |
| `CATEGORY_SUBCATEGORY`  | The first category below that root. A transaction directly on the root has synthetic `DIRECT_ASSIGNMENT:<root-id>`.                                          |
| `CATEGORY_BRANCH_CHILD` | The immediate child below the category from the single global `CATEGORY_ID DESCENDANT_OF <id>` condition. A direct assignment to that category is synthetic. |

Uncategorized rows use the stable synthetic identifier `UNCATEGORIZED`. Category result dimensions contain server-resolved metadata (`stableId`, label, color, active state, synthetic flag and breadcrumb path), so public results do not require the client to translate internal IDs. Inactive categories remain resolvable: historical transactions and their saved query results retain their classification identity.

The reference resolver loads only the authenticated user's categories and tags. It verifies every parent belongs to that same owner-scoped snapshot and rejects cycles before execution. Category IDs in global or calculation conditions are also validated against that snapshot. A category branch grouping is rejected unless exactly one global descendant condition gives its branch root.

## Tags

`TAG_ID` is available as a transaction-ledger grouping and condition field.

| Condition         | Meaning                                       |
| ----------------- | --------------------------------------------- |
| `HAS_ANY (id...)` | A transaction has at least one requested tag. |
| `HAS_ALL (id...)` | A transaction has every requested tag.        |
| `HAS_NONE`        | A transaction has no tag.                     |

Tag conditions use correlated `EXISTS` predicates and therefore never multiply a transaction before a count, sum or average. Tag grouping intentionally has one group for every tag assignment. A transaction carrying two tags belongs to both tag groups, which makes totals non-additive across those groups. The result marks the grouping as `NON_ADDITIVE_OVERLAPPING` and returns the explicit `NON_ADDITIVE_OVERLAPPING_TAG_GROUPS` warning. Consumers must not add tag-group totals together.

## Per-calculation conditions

Every `SemanticAggregateProjection` now carries an immutable `conditions` list. Global conditions constrain all projections; projection conditions constrain only that calculation. For example, one ledger query may return the count of outgoing transactions and the average amount of transactions carrying two particular tags:

```text
SELECT COUNT(TRANSACTION_ID FILTER WHERE TRANSACTION_FLOW = 'OUT') AS expense_count,
       AVG(TRANSACTION_AMOUNT FILTER WHERE TAG_ID HAS_ALL ('11', '12')) AS tagged_average
FROM TRANSACTION_LEDGER
WHERE CATEGORY_ID DESCENDANT_OF '7'
LIMIT 100
```

The formatter/parser and JSON contract round-trip this same AST. Per-calculation conditions are intentionally limited to the transaction ledger; the validator rejects them for historical positions rather than silently changing their meaning.

## Result contract and correctness

`SemanticAnalyticsResult` now has a result-level availability and warnings. Result columns declare their additivity policy. Dimensions may carry resolved identity metadata. Monetary projections continue to partition results by currency; FINTRACK never sums or converts currencies without an explicit future FX policy.

The ledger executor still uses catalog-owned SQL fragments only. It derives transfer status with `EXISTS` over `internal_transfer`, preserving the existing policy that linked legs can be excluded from economic calculations. It uses positional SQL grouping for category hierarchy expressions so PostgreSQL evaluates one canonical grouped dimension rather than divergent parameterized copies of a correlated expression.

## Intentional deferrals

V2.1 does not add temporal day/week/month/year grouping, two-dimensional time/category output, transfer or credit datasets, top-N, broad boolean expressions, arithmetic formulas, custom metric persistence, dashboard UI, chart rewrites or category/tag selector UI. The browser contract carries the new fields and readable result metadata; a later focused UI slice must supply searchable owner-scoped category/tag pickers before presenting these filters as a non-technical end-user workflow.

## Validation evidence

Focused Testcontainers PostgreSQL coverage verifies:

- owner-scoped hierarchy grouping through a deep category with a synthetic direct-assignment row;
- inactive category metadata retained in a result;
- per-calculation `COUNT` and `AVG` conditions in a single query;
- `HAS_ALL` without transaction multiplication;
- intentional tag-group overlap warning;
- canonical DSL parsing, formatting and AST round-trip; and
- validation that branch grouping requires exactly one descendant condition.
