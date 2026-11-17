# BUDGET-6A — Durable budget notifications

## Purpose and boundaries

BUDGET-6A adds an optional, backend-only notification foundation for Budget threshold crossings. It does not add a notification center, browser polling, email, push delivery, creation-form controls, or scheduled period-close delivery. Those belong to BUDGET-6B/C.

The financial result used for a threshold is always the existing canonical Budget evaluation: `BudgetSemanticQueryCompiler` compiles the effective Budget revision and `SemanticAnalyticsExecutionService` executes it for the Budget owner. This feature does not introduce a second definition of income, expense, transfers, categories, tags, currencies, or account scope.

## Persistence model

The forward-only Liquibase change `20261116100000_budget_notification_engine.xml` adds:

- `jhi_user.time_zone`: nullable persistent IANA profile preference. A missing value is never replaced with a server or Mexico City default.
- `budget_time_zone_assignment`: immutable Budget-effective timezone history. Its unique key is `(budget_id, effective_period_start)`.
- `budget_notification_preference`: at most one mutable, optimistic-versioned preference row per Budget. It is intentionally independent of `BudgetRevision`.
- `budget_notification_threshold`: the complete normalized threshold collection for one preference. Values are `NUMERIC(19,4)` and unique per preference.
- `budget_threshold_claim`: permanent crossing evidence, unique by `(budget_id, period_start, normalized_percentage)`. Removing and re-adding a preference threshold never removes a claim.
- `budget_notification_outbox`: immutable event evidence written in the same transaction as the financial mutation.
- `budget_notification`: one immutable, user-owned materialization for an outbox event, with `read_at` reserved for BUDGET-6B.

Deleting a Budget removes its preferences, timezone assignments, claims, outbox events, and materialized notifications before removing Budget history. It never deletes or changes financial transactions.

## Preferences API

The owner-scoped endpoints are:

- `GET /api/budgets/{budgetId}/notification-preferences`
- `PUT /api/budgets/{budgetId}/notification-preferences`

The PUT body is a complete replacement:

```json
{
  "expectedVersion": 3,
  "percentageAlertsEnabled": true,
  "thresholdPercentages": [50, 80, 100, 125.5],
  "periodCloseEnabled": false
}
```

Creating a first preference uses `expectedVersion: null`; updating an existing one requires its exact version. Thresholds must be positive, unique after normalization, have at most four decimal places, and there may be at most twenty. Enabling percentage alerts requires at least one threshold. Preference updates are protected by the same owner lock as financial capture but never create threshold claims, outbox rows, or notifications.

Period-close settings are persisted now but deliberately have no scheduler in this slice.

## Timezone authority

At Budget creation, an explicitly supplied valid IANA zone initializes both the user profile when blank and a Budget timezone assignment at the Budget anchor. The notification engine evaluates a Budget only with its effective persisted assignment.

A missing, malformed, or unavailable assignment means that optional alerts for that Budget are skipped; the engine does not guess a timezone. `BudgetTimeZoneService.scheduleProfileTimeZoneChange` is the backend foundation for a future profile flow: it records a new assignment at each Budget's next period boundary, never in the active period, and retains historical boundaries unchanged.

## Financial mutation capture

`BudgetThresholdNotificationService.capture` is the single transactional boundary for a logical financial mutation:

1. lock the owner row with PostgreSQL `PESSIMISTIC_WRITE`;
2. load enabled preferences and resolve each Budget's authoritative timezone;
3. evaluate every affected eligible Budget before the mutation through the canonical semantic pipeline;
4. apply and flush the real mutation;
5. re-evaluate each same-current-period Budget;
6. claim each exact threshold for which `previousProgress < threshold && newProgress >= threshold`;
7. write one immutable snapshot/outbox record per affected Budget and logical mutation, consolidating all thresholds crossed by that mutation.

There is no alert when an evaluation is unavailable, non-applicable, future, scheduled, paused, completed, in a different period, or not numerically authoritative. Therefore a configuration edit, a Budget target/scope/revision edit, a future-dated transaction, a historical correction, an internal-transfer operation whose final economic result is unchanged, and elapsed calendar time cannot synthesize a threshold notification.

The inspected write paths and their boundaries are:

| Path                                                                                | Capture boundary                                                                                                                             |
| ----------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `FinancialTransactionService` create, update, patch, delete                         | one transaction CRUD mutation                                                                                                                |
| `TransactionCandidateService` post manual draft / post as transfer                  | the complete post (including link/counterpart)                                                                                               |
| `InternalTransferCommandService` create, link, counterpart creation, update, unlink | complete direct transfer command; link/create/update normally have no positive economic crossing but are still governed by the same boundary |
| `TransactionBatchPublishService`                                                    | one capture per persisted candidate, in persisted candidate order, inside the atomic outer Batch transaction                                 |
| `TransactionIngestionService` delete and account-tree cleanup                       | complete ingestion deletion including transfer unlink and transaction removal                                                                |
| `FinancialTransactionService.deleteAllForAccount`                                   | complete account-scoped transfer unlink and transaction deletion                                                                             |
| `FinancialAccountHardDeleteService`                                                 | delegates final ledger deletion to `FinancialTransactionService.deleteAllForAccount` before removing the account aggregate                   |

Compound candidate posting and Batch publication call transfer operations through an internal no-second-capture path. Their one outer capture sees the complete final transfer relation, so a provisional unlinked leg can never generate a false crossing.

The lock order is owner, then existing financial/account locks, then Budget preference/claim data. Batch publishing explicitly acquires the owner lock before taking Batch or account locks. This serializes same-owner changes and preference updates, so the before/after comparison and the active configuration have an unambiguous commit order. The claim unique constraint remains the database backstop.

## Snapshot and outbox

The outbox snapshot records the owner and Budget IDs, Budget name/type/currency, current period, effective timezone, event time, prior/new actual amount and progress, target, newly crossed thresholds, source mutation key, and effective revision ID. It is JSON evidence fixed at commit time; workers never re-evaluate current financial results.

`BudgetNotificationOutboxWorker` polls due pending IDs every 30 seconds. Each ID is processed in its own transaction and locks the row pessimistically. A unique outbox reference on `budget_notification` makes materialization idempotent. Failures are recorded in a separate transaction and retried with bounded exponential delay (2, 4, 8, 16, 32 seconds; then `FAILED` after the fifth attempt). Pending rows survive restart. Indexes support pending delivery, per-owner/Budget inspection, claims, and future unread pagination.

## Cost and limitations

When an owner has no enabled preferences, capture is a direct pass-through after no Analytics evaluation. With enabled preferences, each mutation evaluates each enabled owner Budget twice; this favors financial correctness and immutable crossing evidence over premature optimization. BUDGET-6A does not yet expose notification reads, unread counts, profile-timezone editing, period-close delivery, email/push delivery, or operational administration for failed events.
