# Analytics V2.3: specialized financial observations

## Purpose

V2.3 extends the canonical semantic Analytics model with three governed datasets. They use the same `SemanticQueryDefinition`, `VisualizationContext`, validator, SQL-like DSL and result contract introduced in V2.1/V2.2; they do not restore named-metric query paths or add a second execution engine.

| Dataset                     | Grain                                        | Authoritative source                                             | Time binding       |
| --------------------------- | -------------------------------------------- | ---------------------------------------------------------------- | ------------------ |
| `ACCOUNT_POSITION_SNAPSHOT` | one eligible asset account at an observation | `FinancialAccountBalanceService`                                 | `OBSERVATION_DATE` |
| `CREDIT_ACCOUNT_SNAPSHOT`   | one credit-card account at an observation    | `FinancialAccountBalanceService` / `CreditCardBalanceCalculator` | `OBSERVATION_DATE` |
| `INTERNAL_TRANSFER_EVENT`   | one persisted `InternalTransfer` relation    | validated outgoing transfer leg                                  | `TRANSFER_DATE`    |

`VisualizationContext` continues to carry period, account scope and currencies. A query definition contains projections, filters, grouping, ordering and limit only.

## Account positions

`ACCOUNT_POSITION_SNAPSHOT` remains the source for account book positions. Its `POSITION_BALANCE` is a point-in-time amount, never an additive movement total. A valid position query therefore includes `ACCOUNT_ID` and `OBSERVATION_MONTH` when there is more than one observation.

Balances come exclusively from `FinancialAccountBalanceService`; Analytics does not reproduce transaction arithmetic. The executor preserves the service's authoritative `initialBalance` and `initialBalanceDate` behavior. A period before an account's opening balance date yields `UNAVAILABLE`, not a made-up zero.

Investment accounts are allowed as book-balance positions. Their result carries `INVESTMENT_BOOK_BALANCE_NOT_MARKET_VALUE`: the number is the FINTRACK ledger balance, not a security valuation, price or market-value history.

## Credit-card observations

`CREDIT_ACCOUNT_SNAPSHOT` is limited to `CREDIT_CARD` accounts and exposes the following fields:

| Field                        | Unit             | Historical rule                  |
| ---------------------------- | ---------------- | -------------------------------- |
| `CREDIT_DEBT`                | account currency | supported at account/month grain |
| `CREDIT_LIMIT`               | account currency | current observation only         |
| `CREDIT_AVAILABLE`           | account currency | current observation only         |
| `CREDIT_UTILIZATION_PERCENT` | percent          | current observation only         |

Debt is calculated by the authoritative balance service from the account opening balance and posted IN/OUT activity. It can be zero, positive or negative. It is point-in-time and cannot be summed over accounts or months.

Credit limit, available credit and utilization deliberately are **not** reconstructed historically: FINTRACK has no dated credit-limit history. They are valid only where `context.from == context.through == today` and grouped by `ACCOUNT_ID`. A historical request gets `INCOMPATIBLE_HISTORICAL_CREDIT_LIMIT`, rather than a misleading current-limit value.

The current balance DTO supplies limit and available credit. Utilization is `debt * 100 / limit`, at scale 4, without capping; a debt above the limit can therefore be above 100%. A missing credit-details record or a zero limit yields `UNAVAILABLE` for derived fields while keeping debt available. Negative limits cannot exist because `CreditAccountDetails.creditLimit` is validated as non-negative. If the selected account scope has no credit-card account, the result availability is `NOT_APPLICABLE` with `NO_APPLICABLE_CREDIT_ACCOUNTS`, not `NO_DATA`.

Example:

```sql
SELECT SUM(CREDIT_DEBT) AS debt
FROM CREDIT_ACCOUNT_SNAPSHOT
TIME BINDING OBSERVATION_DATE
GROUP BY ACCOUNT_ID, OBSERVATION_MONTH
ORDER BY OBSERVATION_MONTH ASC
LIMIT 100
```

## Internal-transfer events

`INTERNAL_TRANSFER_EVENT` uses `InternalTransfer` as its grain. It never queries the two financial transaction legs as two analytic rows. The existing domain invariant guarantees that both legs have one owner, currency, amount and transaction date; the outgoing leg supplies the canonical event amount and date.

The dataset supports `COUNT(TRANSFER_ID)` and `SUM(TRANSFER_AMOUNT)`, source and destination account/institution/type dimensions, and `TRANSFER_DAY`, `TRANSFER_WEEK` (ISO), `TRANSFER_MONTH` and `TRANSFER_YEAR`. Currency remains a result facet. FINTRACK never adds amounts in different currencies and does not apply FX conversion.

Account context is intentionally endpoint-inclusive: selecting either endpoint returns the event once. Selecting both endpoints still returns it once. The repository query verifies ownership for both legs and scopes by date and selected endpoint before grouping. Consequently source/destination grouping cannot create a double-counted ledger amount.

Example:

```sql
SELECT COUNT(TRANSFER_ID) AS transfers, SUM(TRANSFER_AMOUNT) AS volume
FROM INTERNAL_TRANSFER_EVENT
TIME BINDING TRANSFER_DATE
GROUP BY TRANSFER_MONTH, DESTINATION_ACCOUNT_ID
ORDER BY TRANSFER_MONTH ASC
LIMIT 100
```

Transfer volume is explicitly labeled `TRANSFER_EVENT_VOLUME_NOT_ECONOMIC_FLOW`. It is never included in the economic income or expense calculations of `TRANSACTION_LEDGER`.

## Validation and contract parity

The catalog exposes all new fields, groupings, filters, units and additivity policies. The same catalog drives AST validation and SQL-like parse/format round trips. The validator enforces:

- account/month grain for debt and account grain for other credit point-in-time values;
- exactly one transfer temporal grouping;
- `CREDIT_CARD` only for a credit account-type predicate;
- source/destination account-type predicate values from the controlled account-type enum;
- current-only context for current-limit-derived credit values;
- owner-safe account scoping before either specialized executor runs.

`CREDIT_UTILIZATION_PERCENT` is a `PERCENT` result unit. The existing result renderer formats that unit as a percentage; no new Explorer control, dashboard persistence feature or chart behavior was introduced in this slice.

## Deliberate limits and next increments

This implementation does not invent credit-limit history, foreign exchange, investment market values, arbitrary formulas or raw SQL. It has no Analytics V1 compatibility path. A future V2.4 may add further specialized datasets only by registering their controlled fields and semantics in the same catalog/AST/executor architecture.

## Isolated PostgreSQL proof coverage

`SemanticSpecializedAnalyticsExecutionServiceIT` uses disposable Testcontainers PostgreSQL fixtures and verifies:

- monthly credit debt of MXN 400, MXN 350 and MXN 1,250 plus pre-opening `UNAVAILABLE`;
- current limit MXN 1,000, available credit -MXN 250 and uncapped utilization 125%;
- missing credit details, zero limit, zero debt, USD partitioning and a non-credit `NOT_APPLICABLE` scope;
- two MXN transfer events and one USD event, each counted once even when both endpoints are selected;
- an ISO week boundary transfer (`2025-12-31` -> `2026-W01`);
- exclusion of linked transfer legs from economic income and expense; and
- investment book positions with the non-market-value warning.

`SemanticAnalyticsDslServiceTest` and `SemanticAnalyticsValidatorTest` additionally cover parser/formatter and validator parity for the new datasets.
