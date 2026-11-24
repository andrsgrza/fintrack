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

> **Superseded timezone note.** The original BUDGET-6A creation design described initializing a blank profile timezone from a supplied browser zone. The approved current contract is in [BUDGET-6C-B](BUDGET-6C-B.md): browser detection is only a suggestion, and a profile timezone is persisted only after explicit user confirmation. Budget creation records the Budget-effective assignment from its validated command; it never silently overwrites the profile preference. The notification engine evaluates a Budget only with its effective persisted assignment.

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

## Backend acceptance evidence — 2026-11-17

This is backend-only acceptance. Every PostgreSQL result below used the Testcontainers PostgreSQL instance created for the test process; no development database or normal application port is involved.

### Stabilization findings

- `BudgetResourceIT` initially contained stale IDE-generated test bytecode that threw `java.lang.Error: Unresolved compilation problems` from three methods. A clean `test-compile` regenerated the class from valid source; the complete class now runs 12/12.
- Non-transactional outbox tests originally leaked committed fixtures into later resource tests in the same Maven JVM. Their fixtures are now removed in FK-safe order after every case. This preserves the durable-worker proof while keeping unrelated integration tests independent.
- `FinancialAccountHardDeleteServiceTest` had not supplied the `FinancialTransactionService` dependency added to preserve the Budget capture boundary during ledger deletion. The test now verifies that it is called in the existing cleanup order rather than incorrectly expecting direct repository deletes.
- A full-suite run also revealed two test-fixture cleanup omissions rather than a production defect: `BudgetCreationConcurrencyIT` now removes its `budget_time_zone_assignment` rows before its Budget rows, and the non-transactional Batch rollback fixture now removes its committed Category after its financial and Budget references. The Batch test followed immediately by all `CategoryResourceIT` cases passes, as does the full Failsafe suite.

### A–X matrix

| Scenario                                             | Status  | Evidence                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| ---------------------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| A. Manual creation crossing                          | COVERED | `BudgetNotificationEngineIT.captures_manual_create_and_scope_updates_through_the_real_transaction_service` calls the real `FinancialTransactionService`.                                                                                                                                                                                                                                                                                                            |
| B. Modification entering scope                       | COVERED | The same test creates an unscoped movement, then applies its category and records the 80% crossing.                                                                                                                                                                                                                                                                                                                                                                 |
| C. Modification leaving scope                        | COVERED | The same test clears the category and proves that the claim count does not increase.                                                                                                                                                                                                                                                                                                                                                                                |
| D. Account, category, tag, date and amount mutations | PARTIAL | Category and amount paths are covered above and in `TransactionBatchPublishServiceIT.preserves_intermediate_budget_threshold_crossings_for_an_atomic_batch_and_a_later_valid_correction`; account/tag/date scope correctness is covered by `BudgetEvaluationServiceIT.recalculates_selected_account_category_and_tag_scopes_after_financial_record_mutations_without_duplicate_descendants`, but there is not yet one notification-event test per individual field. |
| E. InternalTransfer link/unlink                      | PARTIAL | `BudgetEvaluationServiceIT.evaluates_all_owned_accounts_with_currency_isolation_exact_date_boundaries_and_transfer_link_mutations` and `InternalTransferResourceIT.aggregateCommandsRequireVersionAndPreserveBothLegsOnUnlink` prove the governed financial result and command semantics; a dedicated outbox assertion for each direct link/unlink remains useful coverage.                                                                                         |
| F. EXPENSE and INCOME                                | COVERED | Expense manual-service and `captures_economic_income_with_the_same_canonical_pipeline` both use the canonical executor.                                                                                                                                                                                                                                                                                                                                             |
| G. One mutation affects multiple Budgets             | COVERED | `BudgetThresholdNotificationServiceTest.evaluates_every_enabled_budget_for_one_financial_mutation_without_mixing_their_claims` creates independent claims/events for two enabled Budgets.                                                                                                                                                                                                                                                                           |
| H. Several thresholds in one notification            | COVERED | `BudgetThresholdNotificationServiceTest.consolidates_multiple_crossed_thresholds_in_one_outbox_event` and the Batch PostgreSQL proof.                                                                                                                                                                                                                                                                                                                               |
| I. Atomic Batch intermediate crossings               | COVERED | `TransactionBatchPublishServiceIT.preserves_intermediate_budget_threshold_crossings_for_an_atomic_batch_and_a_later_valid_correction`.                                                                                                                                                                                                                                                                                                                              |
| J. Batch rollback                                    | COVERED | `TransactionBatchPublishServiceIT.rollsBack_every_budget_notification_side_effect_when_atomic_publication_fails` asserts no claim or outbox row survives.                                                                                                                                                                                                                                                                                                           |
| K. Future movements                                  | PARTIAL | `BudgetEvaluationServiceIT.preserves_revision_history_zero_paused_future_and_completed_budget_semantics` verifies the canonical result is not applicable; the capture service rejects ineligible evaluations.                                                                                                                                                                                                                                                       |
| L. Historical closed corrections                     | PARTIAL | Historical/current revision and period behavior is covered by `BudgetEvaluationServiceIT.applies_current_and_future_revisions_without_proration_or_mutating_historical_financial_results`; no separate notification fixture targets a closed period correction.                                                                                                                                                                                                     |
| M. Paused changes                                    | PARTIAL | The canonical paused-period result is covered by `BudgetEvaluationServiceIT.preserves_revision_history_zero_paused_future_and_completed_budget_semantics`; notification suppression follows `BudgetThresholdNotificationService.eligible`.                                                                                                                                                                                                                          |
| N. Revision-only changes                             | PARTIAL | `BudgetCommandServiceTest` and `BudgetEvaluationServiceIT` cover revision commands and history; no financial capture is invoked by those commands. A direct no-outbox assertion is still additive coverage.                                                                                                                                                                                                                                                         |
| O. Preference change affects later mutations only    | COVERED | `BudgetNotificationPreferenceServiceTest.creates_complete_preference_without_emitting_financial_work` plus the real manual mutation test prove configuration itself emits nothing and subsequent mutation is captured.                                                                                                                                                                                                                                              |
| P. Remove/re-add claimed threshold                   | PARTIAL | Claims have a permanent unique key and `BudgetThresholdNotificationServiceTest.does_not_realert_a_claimed_or_already_above_threshold` proves the claim gate; the exact remove/re-add REST sequence is not yet an integration fixture.                                                                                                                                                                                                                               |
| Q. Concurrent same-owner mutations                   | COVERED | `BudgetNotificationEngineIT.serializes_two_concurrent_financial_mutations_for_one_owner_without_losing_a_crossing` uses two real PostgreSQL transactions, barriers, and asserts 50%/80% exactly once.                                                                                                                                                                                                                                                               |
| R. Idempotent replay                                 | COVERED | The Batch intermediate-crossing test replays the same operation key and keeps three claims/two outbox rows.                                                                                                                                                                                                                                                                                                                                                         |
| S. Owner isolation                                   | COVERED | `BudgetResourceIT.replacesNotificationPreferencesForTheOwnerAndRejectsForeignOrStaleUpdates` and `BudgetResourceIT.deniesEveryBudgetReadAndMutationCommandToAnotherOwner`.                                                                                                                                                                                                                                                                                          |
| T. Retry without duplicate materialization           | COVERED | `BudgetNotificationEngineIT.persists_retry_evidence_and_allows_two_workers_to_materialize_only_once`.                                                                                                                                                                                                                                                                                                                                                               |
| U. Durable recovery                                  | COVERED | The same test persists a pending event, records a failure, makes the durable retry due, and materializes it once from two worker invocations.                                                                                                                                                                                                                                                                                                                       |
| V. Deleting a Budget with notification state         | PARTIAL | `BudgetResourceIT.permanentlyDeletesOnlyTheOwnedBudgetAggregateAndPreservesFinancialScopeRecords` covers owner-scoped deletion; the deletion code removes preferences, assignments, claims, outbox and materialized rows first. A persisted-notification deletion fixture remains additive coverage.                                                                                                                                                                |
| W. Invalid/missing timezone                          | COVERED | `BudgetTimeZoneServiceTest.rejects_invalid_initial_zone_and_never_falls_back_when_assignment_is_missing`.                                                                                                                                                                                                                                                                                                                                                           |
| X. Period-stable timezone change                     | COVERED | `BudgetTimeZoneServiceTest.resolves_each_future_assignment_using_the_preceding_effective_zone` proves the effective assignment advances only at its boundary.                                                                                                                                                                                                                                                                                                       |

### Critical Batch proof

The real PostgreSQL test creates a MXN economic-expense Budget with target `10,000` and thresholds `50`, `80`, `100`. A pre-existing valid expense establishes `40%`; ordered Batch rows then yield `40% → 70% → 110%`. The snapshots contain `[50]` at `7,000 / 70%` and `[80, 100]` at `11,000 / 110%`. A valid amount correction changes final progress to `9,000 / 90%`, without further claims. The replay retains exactly three claims and two outbox rows; worker materialization produces exactly two notifications and a second worker pass produces none.

### Verified command outcomes

- Full acceptance: `./mvnw -ntp --batch-mode -Dskip.npm -Dskip.installnodenpm verify` — Surefire 639 tests and Failsafe 1,359 tests, all with 0 failures, errors, or skips.
- Focused BUDGET-6A units: threshold service 8/8, timezone service 2/2, command service 15/15, preference service 3/3 and outbox processor 2/2.
- PostgreSQL/Failsafe: Batch 13/13, notification engine 5/5, Budget evaluation 8/8, semantic compiler 3/3, Budget resource 12/12, FinancialTransaction resource 135/135, InternalTransfer resource 5/5, Budget creation concurrency 3/3, and Batch followed by Category resource 130/130.
- Frontend: `npm run jest` — 64 suites / 715 tests, all passing; `npm run lint -- --no-fix` — 0 errors and 3 existing complexity warnings; `npm run webapp:prod` — successful with the same warnings.
- Production package: `./mvnw -ntp --batch-mode -DskipTests -Pprod package` — successful. The first sandboxed attempt could not resolve `registry.npmjs.org`; the permitted unsandboxed retry completed `npm install`, the production Webpack build, and Spring Boot repackaging.
- Git checks: `git diff --check` and `git diff --cached --check` pass with no whitespace errors.

The remaining PARTIAL rows are direct-fixture coverage additions, not reproduced functional defects. The canonical financial semantics and capture eligibility are covered by the named evaluation and mutation tests; dedicated notification assertions for every individual field/scope transition would be a non-blocking regression expansion, not a contract or product-decision blocker.
