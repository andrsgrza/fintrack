# Analytics Redesign 4 — canonical composable query runtime

> **Historical R4 baseline.** The current Explorer and dashboard behavior is documented in
> [ANALYTICS-V2-5-EXPLORER-DASHBOARDS.md](ANALYTICS-V2-5-EXPLORER-DASHBOARDS.md). This document
> remains the design record for the canonical runtime introduced by R4.

FINTRACK now has one analytical contract: `SemanticQueryDefinition` plus an execution-only
`VisualizationContext`. A query declares a governed dataset, aggregate projections, predicates,
grouping, ordering and a bounded limit. The context declares only time, accounts and currencies.
Neither form admits an owner, table, JPA path, join string, arbitrary SQL or dashboard state.

## Public surface

The authenticated analytical API is deliberately limited to:

- `GET /api/analytics/semantic/catalog`
- `POST /api/analytics/semantic/queries/parse`
- `POST /api/analytics/semantic/queries/format`
- `POST /api/analytics/semantic/queries/execute`

The catalog is the capability source for both authoring modes. It exposes dataset grain,
relationships, typed fields, permitted aggregate functions, predicates, groupings, temporal
semantics, additivity and currency policy. The browser contains no financial-metric registry.

`TRANSACTION_LEDGER` has one row per `FinancialTransaction`; it supports governed count, sum and
average projections and never duplicates amounts through a caller-selected join. Monetary rows are
partitioned by account currency. `ACCOUNT_POSITION_SNAPSHOT` delegates to the authoritative
opening-balance and posted-flow calculation; history before `initialBalanceDate` is unavailable,
not zero. The validator resolves account ownership on the server and validates all currency,
period, function, field, predicate and point-in-time constraints before execution.

## Explorer behavior

`/analytics/explorer` has one visual and one SQL-like editor over the exact same AST. The visual
editor builds only catalog capabilities. Any candidate edit goes through backend format and parse
before becoming current state. A dataset replacement is explicit and confirmed because it replaces
incompatible projections, predicates and groups; ordinary failed edits preserve the valid query and
show diagnostics. SQL-like text remains editable after a parse error and runs only after a valid
parse followed by **Run query**.

Time, account IDs and currencies are visibly separate execution controls. They make an existing
result stale but do not alter the query. Results render server-returned values only, distinguish no
data from a calculated zero or unavailable history, and facet money by currency rather than
combining currencies in the browser.

V2.5 adds the production visual builder, written-query parity, ordered multi-dimensional charts,
recommended dashboards, and personal dashboard persistence on top of this same contract. It does
not introduce a second query runtime or embed the context in a saved widget definition.

## Removed surface

The metric-first `QueryDefinition` family, metric catalog, metric parser/formatter, legacy
execution compiler and executors, `FinancialAnalytics*` resources/DTOs/errors, old
`/api/analytics/catalog` and `/api/analytics/queries/**` routes, and their backend tests were
physically removed. The previous metric-first Explorer model, API client and Cypress workflow were
replaced with semantic equivalents. No adapter, fallback reader, compatibility writer or migration
path remains.

The retained financial code is limited to the governed semantic executors and the authoritative
account-balance domain service they call. The date resolver was renamed `SemanticAnalyticsTimePolicy`
to avoid retaining the obsolete metric runtime vocabulary.

## Test-count reconciliation

The prior `638` and `611` numbers cannot be compared as one clean suite total. The R3 `verify` log
reported `611` Surefire tests, while the pre-R4 `target/surefire-reports` directory summed to `641`
because it still contained reports from individually invoked `*IT` classes and legacy analytics
classes. `target/failsafe-reports` independently contained `1435` integration-test entries. The
R4 verification ran from the rebuilt worktree, so historical report directories are not used as
acceptance evidence. `./mvnw verify` completed successfully on 2026-10-28 with **1,414 tests**,
zero failures and zero errors.

## Acceptance evidence

The semantic execution integration suite covers transaction counts by financial institution,
economic-expense average and sum with transfer-leg exclusion, authoritative monthly balances,
ownership isolation, currency partitioning, unavailable history and no-data versus zero. The REST
suite covers catalog, strict parse/format, execution boundaries and the retired routes returning 404. The frontend unit suite covers catalog-to-AST authoring, explicit execution and editable invalid
SQL-like text. The Cypress specification has been migrated to the semantic API contract; it was not
launched here because this slice explicitly prohibits starting or stopping application processes.
