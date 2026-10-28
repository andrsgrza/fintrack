# Analytics R3-2: canonical financial execution

R3-2 exposes one authenticated execution path: `POST /api/analytics/queries/execute`.
It accepts only the strict R3 canonical `FinancialAnalyticsExecutionRequest`; it has no
owner field, SQL input, dashboard identity, legacy AST, adapter or compatibility mode.

`FinancialAnalyticsExecutionService` validates the request, resolves the current login,
authorizes all selected references, resolves the effective time/account/currency scope,
and dispatches exactly one semantic executor. The catalog is the authority for validation,
metric applicability, permitted dimensions/predicates, units, date bases and executor
family.

## Metric execution

| Catalog family   | Measures                                                                                              | Executor and financial policy                                                                                                                                                                                                     |
| ---------------- | ----------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Economic flow    | `INCOME_TOTAL`, `EXPENSE_TOTAL`, `NET_ECONOMIC_FLOW`, `ECONOMIC_TRANSACTION_COUNT`, `AVERAGE_EXPENSE` | Parameterized PostgreSQL transaction aggregation. Eligible IN/OUT legs linked to an `InternalTransfer` are excluded. Net flow is income minus expense; average expense is unavailable when its denominator is zero.               |
| Account movement | `ACCOUNT_INFLOW`, `ACCOUNT_OUTFLOW`, `ACCOUNT_NET_MOVEMENT`                                           | Parameterized PostgreSQL aggregation. Unlike economic flow, valid transfer legs remain movements.                                                                                                                                 |
| Positions        | `ACCOUNT_BALANCE`, `ACCOUNT_SIGNED_POSITION`, `NET_RECORDED_POSITION`                                 | The existing authoritative `FinancialAccountBalanceService` calculates balance from initial balance, opening date and ledger flows. Credit debt has a negative signed position; investment uses book balance.                     |
| Credit           | `CREDIT_CARD_DEBT`, `CREDIT_LIMIT`, `AVAILABLE_CREDIT`, `CREDIT_UTILIZATION`                          | Credit debt does not need a limit. Limit-dependent values require a nonzero current, date-proven limit; no historic limit is invented. Utilization is aggregate debt divided by aggregate limit, never an average of percentages. |
| Transfers        | `INTERNAL_TRANSFER_COUNT`                                                                             | Parameterized PostgreSQL relation aggregation counts each `InternalTransfer` once, never both legs.                                                                                                                               |

Every calculation is scoped to accounts owned by the authenticated login. Account,
category and tag references are owner-checked during validation; a caller cannot select
another user's data or supply an owner override. The transfer query also requires both
legs to belong to the same owner.

## Time, currency, and availability

The business timezone is `America/Mexico_City`. Relative windows resolve once through
the injected time policy. Period measures use the requested inclusive transaction or
posting-date range; posting-date execution does not fall back to transaction date and
warns that entries without a posting date were omitted. Position/credit measures are
AS_OF values; a temporal grouping produces genuine historical observations via the
balance service. No position is made up before `initialBalanceDate`.

Money is intrinsically partitioned by account currency even when `CURRENCY` is not a
requested grouping. There is no implicit FX conversion. Counts remain non-monetary.
The typed result includes canonical version metadata, resolved context, ordered column
metadata, dimensions, currency facets, units, per-value state and warnings. States
distinguish zero, unavailable history, no applicable account type, and partial results;
mixed applicable/inapplicable account selections remain partial rather than pretending
to be complete. Tag group totals are marked non-additive and tag filters use `exists` to
avoid multiplying base transactions.

## Bounds and supported semantic surface

The R3 validator enforces one to four compatible measures, at most two groupings, ten
predicates, 100 selected reference values and 500 result groups. Absolute time ranges
are bounded to ten years and cannot extend past the business date. The catalog publishes
the only supported dimensions and predicates: time, account, account type, currency,
category/root category, tag, origin, flow and transfer status where the selected metric
permits them.

## PostgreSQL golden fixture

The isolated Testcontainers integration fixture has two users; debit, cash, investment
and three credit-card accounts in MXN/USD; initial balances/opening dates; income, a
categorized and overlapping-tag expense, a linked transfer/card repayment, an unlinked
card purchase, a card with no limit, an over-limit card, and a USD investment expense.
Independent expected values include MXN income `1000`, economic expense `100`, account
outflow `300`, transfer count `1`, debit book balance `800`, cash balance `50`, card
debt `100`, debt without a limit `75`, utilization above one (`1.5`), MXN signed/net
position `700`, and USD investment book balance `480`. It also proves historical
unavailability, AS_OF time series, multi-currency partitioning, posting-date omission,
transfer unlink reclassification, hierarchy grouping, tag non-additivity and owner
scoping. The fixture is transactional and Testcontainers-only; it does not use a
development PostgreSQL database.

R3-2 deliberately does not include the SQL-like parser, visual builder, dashboards,
widgets, chart rendering, saved queries, custom formulas or foreign exchange. Those
future surfaces consume this single result contract rather than introduce another
calculation engine.
