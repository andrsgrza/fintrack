# Transaction Batch — approved V1 design

**Status:** approved implementation contract  
**Working base:** `96d12027d815e1569df17ab7daf2f5f57269950f`  
**Scope:** replaces the product FILE-import workflow. It does not migrate or preserve old FILE-ingestion history.

This closes the technical contracts for the approved Transaction Batch product. It is not another architecture comparison and does not authorize entity, service, endpoint, or UI implementation in this preparation slice.

## 1. Approved product behavior

- A user may own several open batches. Each batch belongs to exactly one accessible financial account.
- A batch is a temporary draft workspace. It accepts repeated CSV appends; its append history exists for idempotency, not as a file archive.
- Manual standalone transaction creation stays batchless. Manual and CSV drafts may coexist.
- CSV append creates repairable candidate drafts. A user can edit or delete a row before publication.
- Description normalization applies only to CSV. It reads a temporary original description and writes the effective candidate description. Transaction Rules read effective candidate values for both manual and CSV drafts.
- Rules can fill an empty category and add tags. They never replace a nonempty category and never remove an existing tag. Manual description, explicit category clear, manually removed tags, and manual tags retain the approved protection behavior.
- Batch settings persist automatic description normalization, automatic category application, automatic tag application, and protection of manual changes. Defaults are false, false, false, and true.
- Publishing is atomic: it creates all final FinancialTransactions or none. It does not need category/tag acknowledgement and does not change posted transaction editing.
- A successful publish leaves a short-lived technical receipt only. It contains no file, raw row, category/tag, candidate, or final transaction archive.
- Local filters operate over the bounded working set, never an undisclosed server page.

## 2. Final technical decisions

### 2.1 Aggregate boundaries

`TransactionBatch` is the CSV draft aggregate root. It is owned by `User`, refers to one `FinancialAccount`, has JPA `@Version`, `OPEN` / `PUBLISHED` state, next row position, the four settings, and timestamps.

`TransactionCandidate` remains the sole pre-post transaction entity. It has an optional `transactionBatch` and a nullable `rowPosition`, required when it belongs to a batch. It receives its own JPA `@Version`. A batch candidate has `source=FILE_IMPORT`; a standalone manual candidate has `source=MANUAL` and no batch. API/token ingestion remains a separate legitimate consumer and is not converted by this work.

The FILE-only `transactionIngestion` and `ingestionRecord` relationships are removed in future implementation stages rather than retained as a compatibility path. No old `TransactionIngestion`, `FileIngestion`, or `IngestionRecord` history is migrated, backfilled, or reinterpreted. `FinancialTransaction` remains final ledger data and its direct editing/no-auto-reevaluation behavior is unchanged.

### 2.2 Minimal temporary data

| Need                        | Representation                                                                                        | Retention                               |
| --------------------------- | ----------------------------------------------------------------------------------------------------- | --------------------------------------- |
| CSV normalization input     | `TransactionCandidate.sourceDescription`, CSV only                                                    | Candidate lifetime                      |
| Repairable effective values | editable candidate fields                                                                             | Candidate lifetime                      |
| Invalid required input      | `TransactionCandidateInputIssue(candidate, field, submittedValue, reason)`                            | Deleted with candidate                  |
| Removed-tag intent          | `TransactionCandidateTagSuppression(candidate, tag)`                                                  | Deleted with candidate or when resolved |
| Append idempotency          | `TransactionBatchAppendOperation` with key, immutable digest, accepted count, optional filename, time | Batch lifetime                          |
| Publish replay              | limited fields on batch root                                                                          | 24-hour receipt                         |

There is no CSV binary, full raw row JSON, parser-result snapshot, rule-evaluation archive, final classification snapshot, or final transaction-ID list.

### 2.3 Candidate value and provenance rules

One candidate has one persisted category and `categorySource` (`AUTOMATIC` or `MANUAL`; no category has no source). Tags remain association rows with an individual source. `sourceDescription` is not another current description: `description` is always the editable/effective value.

`descriptionSource` is `SOURCE`, `AUTOMATIC`, or `MANUAL`. A manual description uses `MANUAL`; disabling automation never reverts it. Manually clearing category sets `categoryManualClear=true`, so a rule cannot refill it while protection is enabled. A manual tag add promotes/creates manual membership. A manual remove deletes membership and creates a suppression. Manual Apply may intentionally override applicable suppression/protection as defined in §8; automatic application may not.

## 3. Lifecycle and command boundary

### 3.1 Batch lifecycle

Only `OPEN` is editable: append, row edit/delete, settings, readonly evaluation, explicit Apply, and publish are allowed. `PUBLISHED` is receipt-only: no append, evaluation, Apply, edit, or another publish. Discard deletes an OPEN root with candidates, issues, suppressions, and append operations. It has no replay receipt.

Before an OPEN root becomes `PUBLISHED`, it clears account association, working settings, next-row position, candidate relationships, append operations, and workspace metadata. It retains only owner, state, `publishedAt`, publication key/digest, original publication count, expiry, and a small result summary. It retains no filename, candidate payload, category/tag, or transaction ID.

A PUBLISHED root cannot become an open workspace. Expiry cleanup deletes it after 24 hours. Account/user deletion deletes its roots. This selected policy is deletion, not an unresolved “block or discard” alternative.

### 3.2 Command families and guards

| Family            | Scope and mandatory guard                                                                                     |
| ----------------- | ------------------------------------------------------------------------------------------------------------- |
| Batch workspace   | owned batch + accessible account; create/list/read bounded set/settings/append/discard/evaluate/apply/publish |
| Batch candidate   | named owned OPEN batch, then candidate membership and matching account                                        |
| Standalone manual | owner + `source=MANUAL` + no batch + permitted state                                                          |
| Final ledger      | existing FinancialTransaction ownership rules, unchanged                                                      |

No route trusts a caller-supplied candidate/batch pairing. A batch candidate cannot be published through an old standalone route. A standalone candidate cannot be mutated by naming it under an unrelated batch.

| Current approved-base consumer                  | Required adaptation                                                                          |
| ----------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `GET /api/transaction-candidates/manual-drafts` | Query current-user manual candidates with no batch only.                                     |
| Manual draft PATCH/preview/apply/post/cancel    | Require standalone manual/no-batch scope. `post` rejects batch candidates.                   |
| Generic candidate CRUD                          | Preserve only technical unlinked/manual scope or reject; never attach/mutate a batch member. |
| Batch-scoped commands                           | Require owner, accessible account, OPEN state, and actual membership.                        |
| FILE workflow prepare/classification/confirm    | Removed from product path; no compatibility posting bypass.                                  |
| FinancialTransaction edit/delete                | Final-ledger behavior unchanged.                                                             |
| ApiIngestion/API tokens                         | Remain non-FILE consumers with existing ownership/token semantics.                           |

## 4. Append idempotency precedes first-execution checks

### 4.1 Request identity and sequence

Append receives an operation key and immutable-request digest. The digest covers normalized submitted file bytes, accepted filename/content type, declared parser format/version, and immutable submitted append options. It excludes mutable batch version/count, current rules/catalog, and current settings.

`TransactionBatchAppendOperation` has unique `(transaction_batch_id, operation_key)`, digest, original accepted candidate count, time, and only a bounded display filename if currently needed. It stores no file or raw row.

1. Authenticate and resolve owned batch/account scope.
2. Enter the batch mutation boundary and find the key.
3. If accepted: equal digest returns original acceptance immediately, without expected-version check, parse, append, evaluation, or overwrite. Different digest returns `409 OPERATION_KEY_REUSED_WITH_DIFFERENT_CONTENT`.
4. Only when no operation exists: require OPEN, validate `expectedBatchVersion`, account/settings/size/CSV contract.
5. Parse/prepare candidates and issues. Recheck key inside the lock/unique boundary. A unique-key race reloads the accepted row and follows step 3.
6. Persist candidates, operation, and actual root mutation, then schedule approved automatic evaluation.

### 4.2 Append decisions

| Situation                         | Result                                                                                   | Mutation           |
| --------------------------------- | ---------------------------------------------------------------------------------------- | ------------------ |
| A accepted, B accepted, retry A   | Return A's original acceptance/count even if batch revision changed for B                | None               |
| Retry A after row edits/deletions | Return original receipt; never restore drafts                                            | None               |
| Same key, different content       | `409 OPERATION_KEY_REUSED_WITH_DIFFERENT_CONTENT`                                        | None               |
| Concurrent duplicate key          | One accepts; the other returns that receipt                                              | Exactly one append |
| Same CSV, new key                 | New append; financial deduplication is out of scope                                      | New candidates     |
| Replay after publish              | Existing append receipt may be returned while root/receipt remains; never creates drafts | None               |
| Replay after discard/cleanup      | Reject not-found/expired; no reconstruction                                              | None               |

Original accepted count is an append receipt fact; currently remaining draft count is a live read fact. They must not be conflated.

## 5. Publish replay and receipt expiry

Publish has an operation key/digest; the digest covers immutable submitted publication parameters, not current rules/catalog. `expectedBatchVersion` protects only first publication of OPEN work.

| State/request                               | Decision                                          | Revalidation                                                       | Mutation                           |
| ------------------------------------------- | ------------------------------------------------- | ------------------------------------------------------------------ | ---------------------------------- |
| OPEN/new key/current revision               | First publication                                 | Validate current candidates/account/business rules once under lock | All finals and transition, or none |
| OPEN/stale revision                         | `409` with authoritative versions                 | None                                                               | None                               |
| PUBLISHED/same owner/key/digest/not expired | Authorized replay, original response              | None; does not require candidates/current rules                    | None                               |
| PUBLISHED/same key/different digest         | `409 OPERATION_KEY_REUSED_WITH_DIFFERENT_CONTENT` | None                                                               | None                               |
| PUBLISHED/different key                     | `409 BATCH_ALREADY_PUBLISHED`                     | None                                                               | None                               |
| Expired/root deleted                        | Not found/expired                                 | None                                                               | None                               |

First publication locks root and candidates in stable order, rejects unresolved issues and invalid/incomplete data, creates final transactions with normal FILE-import origin, removes only pre-post children/candidates, and transitions root in one transaction. Rollback leaves OPEN state unchanged. Receipt expiry is checked on every read/replay; scheduled cleanup is hygiene only. Cleanup locks/rechecks root and cannot reach final transactions, categories, or tags.

## 6. Candidate version versus batch revision

### 6.1 Separate responsibilities

| Version             | Protects                                | Changes on                                                                                                |
| ------------------- | --------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `candidate.version` | One candidate's effective draft state   | field/category edits; effective tag/suppression/input-issue changes; guarded candidate deletion           |
| `batch.version`     | Workspace structure and publish consent | new append, row add/remove, settings, discard, first publish, explicit batch-wide Apply that changes rows |

Editing candidate A uses `expectedCandidateVersion`. It must not fail because candidate B changed if A remains current and its batch is OPEN. Candidate changes do not artificially advance batch revision merely to serialize independent rows. Batch locking coordinates mutation with append/discard/publish, but JPA `@Version` fields are never hand-incremented.

Structural/consent commands require `expectedBatchVersion`: new append, settings save, explicit batch Apply, discard, and first publish. Per-row edit/delete requires candidate version plus membership/OPEN guard. Responses return authoritative current versions. Pure reads and real no-ops change no version.

### 6.2 Semantic preview acceptance

The server resolves suggestions at execution. Explicit Apply submits the candidate version plus the user's intended category/tag result. The server re-evaluates and compares the eligible effective result. If it changed materially, it returns stale-preview conflict; it never silently substitutes a rule-only/catalog-only changed result. This requires no evaluation archive or new token framework.

## 7. Currency and repairable inputs

Account currency is canonical. A parser must not turn explicit USD 100 into valid MXN 100 merely by ignoring USD.

| Input for MXN batch                                   | Draft state                                                                                 | User action           |
| ----------------------------------------------------- | ------------------------------------------------------------------------------------------- | --------------------- |
| Missing currency where CSV contract makes it optional | Canonical MXN, no issue                                                                     | None                  |
| `MXN` / trim-case canonical equivalent                | Canonical MXN, no issue                                                                     | None                  |
| Explicit `USD`, amount 100                            | No valid substituted amount/currency; CURRENCY issue preserves bounded submitted USD/reason | Correct row or delete |
| Malformed/unsupported currency                        | Repairable CURRENCY issue                                                                   | Correct or delete     |

No conversion, exchange-rate lookup, guessed values, or implicit conversion exists. A correction removes its issue in the same candidate mutation. Any unresolved issue blocks publish. The parser stores only minimum repair evidence, never a full raw row.

`TransactionCandidateInputIssue.field` extends only as necessary: `TRANSACTION_DATE`, `POSTING_DATE`, `AMOUNT`, and `CURRENCY`; it has unique `(candidate_id, field)`.

## 8. Settings, evaluation, and application transactions

### 8.1 Readonly evaluation

Preview computes suggestions from a consistent owned candidate/batch view. It does not mutate candidate/batch versions, take a broad exclusive lock merely to display, acknowledge review, or alter posting readiness. A concurrent write can make it pending/unavailable, but never makes a speculative preview look saved.

### 8.2 Settings and delayed automatic work

Saving a setting persists it and the root revision first. Enabling an automatic behavior schedules fresh evaluation/application; disabling never undoes current values. Changing protection alone mutates no rows.

If append/edit/settings save succeeds but subsequent evaluation fails, the accepted working change remains. The response says evaluation is pending with a retryable reason. Retry uses current rows/settings and cannot duplicate append or overwrite later work. Before delayed automatic apply, reload the candidate/batch and recheck OPEN state, membership, evaluated candidate version/effective values, enabled setting, and protection. Obsolete work is discarded/recomputed, never applied after automation has been disabled.

### 8.3 Manual and automatic behavior

| Behavior          | Automatic                                              | Explicit manual Apply                                                   |
| ----------------- | ------------------------------------------------------ | ----------------------------------------------------------------------- |
| Description       | only enabled auto behavior, only if protection permits | may apply currently accepted eligible description                       |
| Empty category    | fill only empty/non-manually-cleared category          | fill only empty category                                                |
| Tags              | add only eligible tags; respect suppression/protection | may intentionally override applicable suppressions/protection; add only |
| Nonempty category | never replace                                          | never replace                                                           |
| Existing tags     | never remove                                           | never remove                                                            |

Tag membership and suppression are mutually exclusive. Adding a tag deletes its suppression atomically; manual remove deletes association and writes suppression atomically.

### 8.4 Explicit batch Apply policy

Batch Apply has all eligible candidates in the named OPEN batch as its declared scope, never merely the filtered/visible rows. It requires batch version, locks root then rows, recalculates suggestions, and checks accepted-preview semantics for selected rows.

Expected exclusions—no matching rule, nonempty category, protected/suppressed value, row changed since preview, already-present tag—are reported as exclusions/no-ops and do not roll back. Real error—ownership/membership loss, inaccessible catalog reference, optimistic/lock failure, persistence error—rolls back the entire explicit batch Apply and reports no false partial success. If zero rows change, it is a no-op; if rows change, versions advance naturally.

## 9. Reads, schema, indexes, and retention

### 9.1 Read/filter contract

V1 returns the complete bounded candidate projection for an owned OPEN batch so filters are genuinely local. Rendering/windowing is separate. Measure 40- and 500-row behavior before claiming a larger bound. If actual limits require server paging, report that product/filter conflict; never silently make a page-local filter look global. Published receipt reads expose receipt-safe fields only and never reconstruct drafts/finals.

### 9.2 Constraints and query purposes

| Rule                     | Constraint/delete behavior                                                          | Query purpose                               |
| ------------------------ | ----------------------------------------------------------------------------------- | ------------------------------------------- |
| Batch owner/account      | non-null; service validates account owner equals batch owner                        | owner/state/updated batch list              |
| Candidate batch relation | nullable batch; row position non-null iff batch; candidate user/account equal batch | rows ordered by position/id                 |
| Row position             | unique `(transaction_batch_id, row_position)`                                       | uniqueness and ordering; no duplicate index |
| Category                 | FK; live candidate reference blocks category deletion                               | existing candidate-category guard           |
| Tag membership           | unique `(candidate_id, tag_id)`                                                     | candidate tags                              |
| Suppression              | unique `(candidate_id, tag_id)`; cascade only from candidate                        | apply/removal check                         |
| Input issue              | unique `(candidate_id, field)`; cascade only from candidate                         | publish validation                          |
| Append key               | unique `(batch_id, operation_key)`; cascade only from root cleanup/discard          | idempotency lookup                          |
| OPEN/PUBLISHED root      | state checks: OPEN has account/settings; PUBLISHED receipt metadata/no working data | open workspace/replay                       |

There is one canonical monetary representation: existing candidate signed amount/amount/flow derivation remains coherent. The batch creates no independently editable signed amount.

No discard, receipt cleanup, account cleanup, category/tag deletion, or candidate delete cascades into a live FinancialTransaction, Category, or Tag. Copy to final transaction happens only inside first publication.

### 9.3 Tag suppression cleanup

Tag deletion treats both candidate tag associations and suppressions as live draft references. It is blocked while either exists, rather than cascading suppressions or silently un-linking draft intent. Candidate discard/delete removes its own suppression; only then may normal Tag delete proceed. This prevents a negative draft reference becoming unintended historical retention or bypassing the catalog policy.

## 10. Lock ordering and compatibility

### 10.1 Resource-specific order

No user-global/distributed lock is introduced. Mutating commands acquire only needed resources in this order:

1. accessible `FinancialAccount` when competing with account deletion;
2. `TransactionBatch` root for batch work;
3. affected `TransactionCandidate` rows in `(rowPosition, id)` order;
4. child association/issue rows through the locked candidate.

This adapts the approved base: account hard-delete already takes an account pessimistic lock before aggregate deletion. Batch append/settings/apply/publish use account → batch → candidates. Standalone manual work uses its candidate (and account only when required) and never takes batch lock. Publish locks all remaining candidates under the same order. Readonly evaluation has no broad exclusive root lock. Receipt cleanup locks only root and rechecks expiry.

Category/tag services do not take candidate locks after catalog locks. They keep owner/access/reference checks and database FK enforcement. A concurrent candidate reference either wins (so delete blocks) or sees deleted/inaccessible catalog data and fails cleanly. This avoids category/tag-to-candidate versus candidate-to-category lock inversion.

### 10.2 Deletion interactions

| Operation           | Required behavior                                                                                                                                        |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Batch discard       | OPEN only; account → root → candidates; remove temporary children/root only                                                                              |
| Account hard delete | Existing account preflight/lock stays authoritative; extend aggregate cleanup to batch candidates/children/append ops/open roots/receipts before account |
| Category delete     | Preserve live-candidate blocker                                                                                                                          |
| Tag delete          | Block on candidate tag association **or suppression**, then ordinary final/product unlink policy                                                         |
| Publish             | account → root → candidates; write finals then delete only pre-post state                                                                                |
| Receipt cleanup     | lock/recheck expiry, delete root only                                                                                                                    |

### 10.3 FILE removal and remaining consumers

The approved base's FILE product path—`TransactionIngestion`, `FileIngestion`, `IngestionRecord`, prepared FILE candidates, and workflow commands—is replaced by the batch workspace in implementation stages. It does not coexist as a second posting path.

Remaining legitimate consumers are standalone MANUAL drafts, direct final FinancialTransaction behavior, API ingestion/token behavior, and normal category/tag/account ownership/inactive-reference policy. Existing ingestion data is disposable, but actual environment deletion needs later explicit authorization and is not migration work.

## 11. Future bounded implementation stages

1. Add batch, candidate-version, issue, suppression, append-operation persistence and focused migrations/constraints.
2. Add batch creation/reads, append idempotency, repairable CSV preparation, and command guards.
3. Add settings/evaluation/manual apply with this concurrency/protection policy.
4. Add atomic publish, receipt replay/expiry, discard/account cleanup, and remove FILE product path.
5. Add UI and browser acceptance after service/contracts are covered.

No stage may introduce a legacy FILE compatibility posting bypass.

## 12. Baseline validation contract

```bash
npm run lint -- --no-fix
npm run jest
./mvnw -ntp --batch-mode -Dskip.installnodenpm -Dskip.npm test
# Docker/Testcontainers is required for Failsafe integration scopes:
./mvnw -ntp --batch-mode -Dskip.installnodenpm -Dskip.npm verify
npm run webapp:prod
./mvnw -ntp --batch-mode -DskipTests -Pprod package
git diff --check
git diff --cached --check
```

`test` runs Surefire and excludes `*IT*`/`*IntTest*`; `verify` runs Failsafe integration scopes. A Docker/Testcontainers access failure is an environment blocker, not a product regression. Production packaging must not skip Modernizer, frontend work, Mockito, or JaCoCo.
