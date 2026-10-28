# Canonical Financial SQL-like Definition Language

This is a deliberately restricted definition language for FINTRACK Analytics. It describes one canonical
`QueryDefinition`; it is **not SQL**, does not name persistence tables, and cannot execute financial work by itself.
The Java semantic catalog and `FinancialAnalyticsValidator` remain the only source of capability and validation
rules. The same `QueryDefinition` is consumed by the R3 execution service and will be produced by the R4-2 visual
builder.

## Definition versus context

The text declares what to calculate, grouping, governed predicates, a date basis, ordering, and a result limit.
It never declares execution time, account selection, selected currencies, effective as-of date, owner, dashboard, or
result rows. Those belong to a separate `VisualizationContext` supplied only when R3 executes a definition.

For example, this text is a definition:

```text
SELECT INCOME_TOTAL, EXPENSE_TOTAL, NET_ECONOMIC_FLOW
BY MONTH
WHERE ACCOUNT_TYPE IN (DEBIT, CASH)
USING TRANSACTION_DATE
ORDER BY MONTH ASC
LIMIT 100
```

An execution context can separately select `THIS_MONTH`, account IDs, and `MXN`; no equivalent `FOR`, `AS OF`, or
currency-selection clause exists in this language.

## Grammar

Keywords and identifiers are case-insensitive on input. Canonical output uses uppercase keywords and semantic IDs.

```ebnf
query          = "SELECT", identifier-list,
                 [ "BY", identifier-list ],
                 [ "WHERE", predicate, { "AND", predicate } ],
                 [ "USING", identifier ],
                 [ "ORDER", "BY", identifier, ( "ASC" | "DESC" ) ],
                 [ "LIMIT", integer ];

identifier-list = identifier, { ",", identifier };
predicate      = identifier, ( ( "=" | "EQUALS" ), literal
                              | "IN", "(", literal, { ",", literal }, ")"
                              | "BETWEEN", literal, "AND", literal );
literal        = identifier | integer | ISO-8601-local-date | quoted-string;
quoted-string  = "'", { character | "''" }, "'";
```

Clauses occur in the written order, at most once. `ORDER BY` must target a requested grouping. `LIMIT` is an integer
from 1 through the catalog maximum (currently 500). The schema accepts one through four measures, zero through two
groupings, and up to ten predicates, as defined by `AnalyticsSchema`.

The lexer limits a definition to 8,192 characters and 512 tokens. A single quote inside a quoted literal is written
as `''`; the lexer preserves literal content until the semantic contract normalizes a controlled value such as an enum
or currency code.

## Governed semantics

The parser only supplies structure. It delegates every semantic decision to `FinancialSemanticCatalog` and
`FinancialAnalyticsValidator`:

- metrics and compatible executor/time/currency families;
- grouping capability;
- predicate eligibility for all selected metrics;
- allowed operators and typed values;
- date bases (`TRANSACTION_DATE` or `POSTING_DATE` only when each metric supports it);
- limit, sort, duplicate, and complexity limits;
- ownership checks for account, category/root-category, and tag IDs.

Currently registered operators are `EQUALS` (or `=` in input), `IN`, and `BETWEEN`. Their value typing comes from
the catalog: controlled enums, currency codes, positive owned IDs, or ISO local dates. There is no generic field,
formula, function, or hierarchy operator outside a registered capability. The catalog is also the future source for
autocomplete and visual-builder controls; the parser has no parallel metric registry.

`ACCOUNT` as a grouping and `ACCOUNT_TYPE` as a predicate are analytical definition concepts. A concrete account ID
selection remains context. Likewise, `CURRENCY` may be a grouping/predicate only if the selected metric supports it;
selected execution currencies remain context.

## Normalization and lossless mapping

Parsing creates a `QueryDefinition` with the current schema and semantic model. Shared validation then normalizes IDs,
sorts/deduplicates predicate values where their semantics are set-like, orders predicates deterministically, supplies
the default date basis (`TRANSACTION_DATE`), default limit (currently 100), and default first-grouping ascending sort.

The formatter accepts only a valid normalized definition and writes all normalized semantics in stable clause order:

```text
SELECT EXPENSE_TOTAL BY CATEGORY, MONTH
WHERE TRANSACTION_DATE BETWEEN 2026-01-01 AND 2026-01-31
USING TRANSACTION_DATE ORDER BY CATEGORY ASC LIMIT 100
```

It preserves metric and grouping order, includes every predicate, and emits no `VisualizationContext`. Therefore:

```text
parse(format(normalizedDefinition)) == normalizedDefinition
format(parse(validText))            == canonicalText
```

## HTTP contract and diagnostics

Authenticated endpoints are:

- `POST /api/analytics/queries/parse` with `{ "text": "..." }`, returning `{ definition, canonicalText }`.
- `POST /api/analytics/queries/format` with `{ "definition": { ... } }`, returning `{ "text": "..." }`.

Neither endpoint executes or persists a query. Invalid input is a typed 400 `ANALYTICS_DSL_INVALID` response. Each
diagnostic has a stable `code`, field, message, and—when text was parsed—one-based `line`, `column`, `span`, and the
offending `identifier`. Syntax codes include `INVALID_TOKEN`, `UNEXPECTED_TOKEN`, `DUPLICATE_CLAUSE`,
`TRAILING_GARBAGE`, `INVALID_LITERAL`, `INPUT_TOO_LONG`, and `TOO_MANY_TOKENS`; semantic diagnostics reuse the
canonical codes such as `UNKNOWN_METRIC`, `UNSUPPORTED_GROUPING`, `MALFORMED_PREDICATE`, `INVALID_LIMIT`, and the
owner-reference diagnostics.

## Security boundary

The lexer/parser rejects `FROM`, `JOIN`, `UNION`, DML, subqueries, functions, and arbitrary expressions because none
belong to the grammar. No SQL, JPQL, JDBC statement, physical table/column, owner override, or execution context is
constructed from this input. Reference validation is server-side and self-scoped even for administrators.

R4-2 can use the endpoints and `QueryDefinition` directly; it must not add a browser parser or a second query AST.
