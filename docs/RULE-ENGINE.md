# FINTRACK Rule Engine Design

## Purpose

This document defines the future design contract for the FINTRACK Transaction Rule Engine.

Status: **Phase 3A FinancialTransaction preview endpoint implemented; TC-2C.1d manual TransactionCandidate automatic suggestions preview implemented; TC-3D.1 FILE_IMPORT candidate-backed Confirm Import backend implemented.**

Not implemented yet:

- automatic rule application on transaction update;
- transaction reevaluation;
- bulk reclassification;
- rule execution audit log.

The engine should evaluate user-defined `TransactionRule`s against `FinancialTransaction`-like input and produce suggestions for:

- category assignment;
- tag assignment.

Subscription and description assignment are deliberately not outputs in the current TransactionRule model. They can be reconsidered in a later rule-engine slice.

## Implemented vs designed vs deferred

### Implemented today

- TransactionRule CRUD/domain rules.
- TransactionRuleCondition CRUD/domain rules.
- Server-managed rule priority/order.
- Server-managed condition position.
- Backend-only pure evaluator service:
  - `TransactionRuleEvaluationService`;
  - internal `TransactionRuleEvaluationInput`;
  - internal `TransactionRuleEvaluationResult`.
- Backend-only `FILL_EMPTY_ONLY` application on `FinancialTransaction` create:
  - applies category only when the transaction has no explicit category;
  - preserves explicit category and does not override conflicts;
  - preserves explicit tags and adds only new suggested tags;
  - does not re-add `alreadyPresent=true` tag suggestions;
  - does not apply on update/PATCH;
  - does not persist the evaluation result.
- Backend-only draft preview REST endpoint:
  - `POST /api/financial-transactions/rule-preview`;
  - evaluates an unsaved `FinancialTransaction` draft;
  - returns suggestions, conflicts, skipped outputs, matched rules, and summary booleans;
  - does not save or mutate a transaction;
  - does not apply `FILL_EMPTY_ONLY`;
  - has no admin cross-user preview behavior.
- Manual FinancialTransaction product create UI now uses `TransactionCandidate`:
  - `/financial-transaction/new` creates no backend row on page load;
  - the first meaningful user change creates a recoverable manual candidate through `POST /api/transaction-candidates/manual`;
  - the route is replaced with `/financial-transaction/drafts/{id}`;
  - subsequent edits autosave through `PATCH /api/transaction-candidates/{id}/manual-draft`;
  - posting uses `POST /api/transaction-candidates/{id}/post`;
  - candidate post creates the final `FinancialTransaction` and does not secretly re-run TransactionRules.
- Backend-only candidate rule command endpoints:
  - `POST /api/transaction-candidates/{id}/rule-preview`;
  - `POST /api/transaction-candidates/{id}/apply-rules`;
  - preview is transient and does not mutate;
  - apply re-evaluates current candidate state and applies category/tags with `FILL_EMPTY_ONLY`.
- Manual candidate create UI now exposes a candidate-specific suggestions section:
  - saved rule-input changes automatically call candidate `rule-preview` and do not mutate category/tags;
  - Apply suggestions / Confirm no suggestions flushes autosave, calls candidate `apply-rules`, and hydrates the returned category/tags/status;
  - Post is blocked in the UI until classification is `SUGGESTED`, `USER_SELECTED`, or `NOT_APPLICABLE`.
- Backend-only FILE_IMPORT candidate classification endpoints under TransactionIngestion:
  - `PATCH /api/transaction-ingestions/{ingestionId}/candidates/{candidateId}/classification`;
  - `POST /api/transaction-ingestions/{ingestionId}/candidates/rule-preview`;
  - `POST /api/transaction-ingestions/{ingestionId}/candidates/apply-rules`;
  - `POST /api/transaction-ingestions/{ingestionId}/candidates/{candidateId}/confirm-no-suggestions`;
  - category/tags are stored on `TransactionCandidate`, not in `IngestionRecord.rawData`;
  - these classification endpoints create no `FinancialTransaction` rows; TC-3D.1 Confirm Import separately posts reviewed `FILE_IMPORT` candidates.
- TransactionRule v1 outputs:
  - `resultingCategory`;
  - `resultingTags`.

### Designed but not implemented

- Existing-transaction rule preview / reevaluation UI.
- Bulk reevaluation flow.

### Deferred

- transaction reevaluation;
- bulk reevaluation;
- condition-level explanation UI;
- rule match audit log;
- description replacement output;
- financial subscription/subscription output.

## Current implemented foundation

FINTRACK already implements the rule authoring model:

- `TransactionRule.priority` is server-managed.
- `priority` is scoped per user.
- priorities are unique and consecutive per user.
- new rules are appended at the end.
- deleting a rule reindexes priorities for that user.
- users reorder rules through Move up / Move down on the TransactionRule list.
- drag-and-drop priority UI is deferred.
- `TransactionRuleCondition.position` is server-managed.
- condition position orders conditions inside one rule.
- condition position does not reindex on delete.
- TransactionRule product create/edit use the configured parent + conditions command API.
- TransactionRule create/edit manage metadata, outputs, active state, condition logic, and local inline conditions together.
- TransactionRule create does not create empty inactive drafts.
- TransactionRule detail may still expose embedded condition management for direct maintenance/debug compatibility, but TR-3 labels that editor technical/debug because it writes directly to child condition endpoints.
- Standalone TransactionRuleCondition generated screens remain available only for technical/debug/direct maintenance and are marked as such in the UI/menu.
- Generated TransactionRule write endpoints and TransactionRuleCondition CRUD endpoints remain available temporarily, but configured endpoints are the product create/edit source of truth.
- active/configured TransactionRule requires at least one condition and at least one output.
- adding a condition does not auto-activate the rule.
- deleting the last condition deactivates the rule.

Configured backend rule creation/update is also available through:

- `POST /api/transaction-rules/configured`
- `GET /api/transaction-rules/{id}/configured`
- `PUT /api/transaction-rules/{id}/configured`

The configured API accepts/returns a parent `TransactionRule` plus its ordered conditions. It preserves server-managed parent fields, server-assigns condition positions, and replaces the full condition set on configured PUT. The product create/edit UI uses this API and keeps condition edits in local frontend state until save.

The old empty-draft product flow is no longer the active product UI path. Technical/debug generated surfaces remain temporarily for direct maintenance, and backend validation remains the source of truth while those surfaces exist. File ingestion category/tag review is now candidate-backed after Pantalla 1; UserPreference remains deferred.

Rules with a resulting EXPENSE or INCOME category are guarded before activation/configured persistence:

- EXPENSE categories require `conditionLogic = ALL` and an effective `FLOW` set of exactly `OUT`.
- INCOME categories require `conditionLogic = ALL` and an effective `FLOW` set of exactly `IN`.
- BOTH categories, tag-only rules, and rules without a resulting category do not require a FLOW condition.
- Effective FLOW is derived from all FLOW conditions by intersecting `EQUALS`/`IN` values and removing `NOT_EQUALS`/`NOT_IN` values.

The product create/edit UI mirrors this guard:

- EXPENSE auto-adds a locked `FLOW EQUALS OUT` condition when no FLOW condition exists;
- INCOME auto-adds a locked `FLOW EQUALS IN` condition when no FLOW condition exists;
- BOTH/null outputs remove only frontend auto-created FLOW conditions;
- user-authored incompatible FLOW conditions are not silently changed and block save;
- `ANY` is disabled/blocked for EXPENSE/INCOME resulting categories.

The pure evaluator is implemented. FinancialTransaction create applies category/tag suggestions in `FILL_EMPTY_ONLY` mode. Existing transaction reevaluation and bulk execution remain deferred.

## Rule ordering

Rules are evaluated by:

```text
TransactionRule.priority ASC
```

Only active rules are eligible.

Inactive rules are skipped.

Rules without conditions are not eligible.

`priority` means evaluation order between rules. Lower priority value means earlier evaluation.

The backend stores `priority` as 0-based. The UI displays order as 1-based.

## Condition ordering

Conditions inside one rule are evaluated by:

```text
TransactionRuleCondition.position ASC, id ASC
```

`position` is display/evaluation order inside a single rule.

Position does not change `ALL` / `ANY` logical semantics.

For `ALL`:

- all conditions must match;
- the engine may short-circuit on the first false condition.

For `ANY`:

- at least one condition must match;
- the engine may short-circuit on the first true condition.

## General evaluation model

### Rejected simple model

The simple model would be:

- first matching rule wins everything;
- stop evaluation after the first matching rule.

This is too limiting because one high-priority rule may only set a category while later matching rules could still add tags.

### Selected direction

The selected design direction is:

- evaluate active rules by `priority ASC`;
- each matching rule may propose outputs;
- the engine can continue evaluating later rules to fill outputs that have not been decided yet;
- scalar outputs are decided by the first matching rule that proposes that output;
- tag outputs can accumulate across matching rules without duplicates.

Scalar output:

- category;

Collection output:

- tags.

## Output resolution

### Category

Category is scalar.

The first active matching rule with `resultingCategory` decides the category suggestion.

Later matching rules that also propose a category do not replace the earlier suggestion.

If debug/explanation details are enabled, later category outputs should be recorded as skipped with:

```text
CATEGORY_ALREADY_SUGGESTED_BY_HIGHER_PRIORITY_RULE
```

If the transaction has no explicit category, the category can be auto-filled in modes that allow mutation.

If the evaluated transaction/draft already has an explicit category:

- same category as the rule suggestion: no conflict;
- different category: do not override automatically; return a conflict.

The evaluator itself never mutates the transaction.

### Tags

Tags are collection outputs.

Matching rules can suggest tags.

Suggested tags accumulate in priority order.

Duplicate tags are removed.

Explicit existing tags are preserved.

For v1 design, suggested tags that already exist on the transaction should still appear in evaluation details with:

```text
alreadyPresent = true
```

Future apply logic must not re-add duplicates.

If two matching rules suggest the same tag:

- the first matching rule is the source suggestion;
- later duplicate outputs may be skipped with `TAG_ALREADY_SUGGESTED`.

Auto-apply may add suggested tags without removing existing tags, depending on evaluation mode.

### Deferred outputs

Subscription and description mutations are not TransactionRule outputs in the current model.

Do not include these in `RuleEvaluationResult` v1:

- description replacement;
- financial subscription assignment;
- subscription output;
- description replacement output.

If they return later, they should be introduced as explicit rule-engine design work with clear conflict behavior and transaction validation.

## Suggestions vs mutation

The engine should be designed around evaluation results first, not direct mutation.

Intended conceptual API:

```text
TransactionRuleEvaluationService.evaluate(transactionDraft) -> RuleEvaluationResult
```

The evaluator must not mutate `FinancialTransaction`.

The evaluator only returns:

- matched rules;
- suggested outputs;
- conflicts;
- skipped outputs;
- optional condition-level details for future explanation/debug.

Applying suggestions should be a separate step.

Deferred conceptual API:

```text
applyEvaluation(transaction, evaluation, mode)
```

### `RuleEvaluationResult` shape

The internal backend result shape is implemented as `TransactionRuleEvaluationResult` plus supporting records. Phase 3A exposes a separate REST-safe preview response DTO rather than returning the internal records directly.

```text
RuleEvaluationResult
  evaluatedTransactionId: Long | null
  evaluatedAt: Instant | null
  mode: EvaluationMode | null
  matchedRules: List<RuleMatchResult>
  suggestedCategory: CategorySuggestion | null
  suggestedTags: List<TagSuggestion>
  conflicts: List<RuleOutputConflict>
  skippedOutputs: List<SkippedRuleOutput>
  hasSuggestions: derived boolean
  hasConflicts: derived boolean
```

`evaluatedTransactionId` is optional because preview may evaluate an unsaved draft/new transaction.

`evaluatedAt` remains deferred. It becomes more useful if an evaluation result is ever persisted or used for audit/debug later.

`mode` remains deferred for the internal result; Phase 2 hardcodes `FILL_EMPTY_ONLY` application on create, and Phase 3A preview does not apply a mode.

### `RuleMatchResult`

```text
RuleMatchResult
  ruleId: Long
  ruleName: String
  priority: Integer
  conditionLogic: ALL | ANY
  proposedOutputs: Set<CATEGORY | TAGS>
  matched: true
  conditionResults: deferred optional details
```

`matchedRules` should include active eligible rules whose conditions matched. Whether matched rules with no configured outputs are included remains an open decision.

### `CategorySuggestion`

```text
CategorySuggestion
  categoryId: Long
  categoryName: String
  sourceRuleId: Long
  sourceRuleName: String
  conflictsWithCurrentValue: boolean
  currentCategoryId: Long | null
  currentCategoryName: String | null
```

### `TagSuggestion`

```text
TagSuggestion
  tagId: Long
  tagName: String
  sourceRuleId: Long
  sourceRuleName: String
  alreadyPresent: boolean
  duplicateOfEarlierSuggestion: boolean
```

For v1 design, tags already present on the transaction appear in `suggestedTags` with `alreadyPresent=true`, rather than disappearing entirely. Apply logic still must not add duplicates.

### `RuleOutputConflict`

```text
RuleOutputConflict
  field: CATEGORY | TAGS
  currentValueId: Long | null
  currentValueLabel: String | null
  suggestedValueId: Long | null
  suggestedValueLabel: String | null
  sourceRuleId: Long
  sourceRuleName: String
  reason: String
```

`TAGS` conflicts are not expected in v1 fill-empty/additive behavior, but the field is included so the structure can grow without reshaping the result.

### `SkippedRuleOutput`

```text
SkippedRuleOutput
  field: CATEGORY | TAGS
  sourceRuleId: Long
  sourceRuleName: String
  reason: SkippedRuleOutputReason
```

Potential skip reasons:

- `RULE_INACTIVE`;
- `RULE_HAS_NO_CONDITIONS`;
- `RULE_DID_NOT_MATCH`;
- `CATEGORY_ALREADY_SUGGESTED_BY_HIGHER_PRIORITY_RULE`;
- `CATEGORY_CONFLICTS_WITH_EXPLICIT_VALUE`;
- `TAG_ALREADY_PRESENT`;
- `TAG_ALREADY_SUGGESTED`;
- `OUTPUT_INVALID_FOR_TRANSACTION`;
- `OUTPUT_NOT_CONFIGURED`.

Not all skip reasons need to be exposed in v1 UI. They are primarily useful for tests, logs, and future explain/debug behavior.

## Evaluation modes

### PREVIEW_ONLY

- evaluate rules;
- return suggestions and conflicts;
- do not mutate the transaction.

### FILL_EMPTY_ONLY

- implemented for `FinancialTransaction` create only;
- category applies only if empty and no conflict exists;
- tags are added without removing existing tags;
- `alreadyPresent=true` tag suggestions are not re-added;
- no override.

### OVERRIDE_WITH_CONFIRMATION

- future UI-driven apply mode;
- conflicts can be applied only if the user accepts.

### FORCE_OVERRIDE

- future explicit override mode;
- not v1.

`RuleEvaluationResult` should support these future modes, but implementation phase 1 only needs `PREVIEW_ONLY` evaluation semantics.

## Ownership for evaluation and apply

Admin has no special rule-evaluation behavior in v1. TransactionRule evaluation and FinancialTransaction rule application follow normal-user ownership rules.

For apply-on-create:

- resolve and validate the `FinancialAccount` first;
- confirm the account belongs to/is accessible by the current user under normal-user ownership rules;
- build `TransactionRuleEvaluationInput.userLogin` from that resolved transaction/account owner, which should normally be the authenticated user's login;
- evaluate only that owner's rules;
- do not evaluate another user's rules due to admin privileges;
- do not design special admin rule-evaluation flows.

## Create/update/reevaluate lifecycle

### Create

Automatic backend evaluation runs on `FinancialTransaction` create only.

The mutation uses `FILL_EMPTY_ONLY`.

Phase 2 remains unchanged: create through the central `FinancialTransactionService.save(...)` path may receive `FILL_EMPTY_ONLY` rule application. It is not currently restricted to `MANUAL` origin only.

The active manual create UI uses `MANUAL` `TransactionCandidate` autosave. Candidate-specific preview/apply commands provide rule suggestions, and posting the candidate creates the final `FinancialTransaction`. The old two-step FinancialTransaction draft preview UI is superseded as product UI, though the backend `POST /api/financial-transactions/rule-preview` endpoint remains available for direct/compatibility use. Direct API creates through `POST /api/financial-transactions` still use Phase 2 `FILL_EMPTY_ONLY` behavior.

### Update

Do not automatically reapply rules on every manual update in v1.

Changing description, amount, flow, account, transaction date, or posting date can affect rule matches, so automatic update reevaluation is risky.

Future UI should offer explicit "Reevaluate rules" behavior instead.

### Reevaluate one transaction

A future action from transaction detail/edit can reevaluate one transaction.

It should likely start as `PREVIEW_ONLY`.

The user can then accept suggestions.

### Bulk reevaluation

Bulk reevaluation is a future feature.

It should support account/date filters or selected transactions.

It should not be implemented until preview/apply behavior is mature.

## Explicit user input vs rule suggestions

Explicit user/source values win by default.

For v1 design:

- if a transaction draft already has category, that category is treated as explicit;
- if a transaction draft already has tags, those tags are treated as explicit existing tags;
- the evaluator can still return suggestions/conflicts;
- the evaluator does not override.

Manual create/update:

- user-selected category/tags are explicit.

Import/API:

- source-provided category/tags are also treated as explicit for v1;
- richer source policy is deferred.

If a rule conflicts with explicit input:

- record a conflict or suggestion;
- require confirmation before applying the override.

## Rule matching fields/operators

The engine must use the same semantics as backend `TransactionRuleCondition` validation.

Fields include:

- `DESCRIPTION`
- `AMOUNT`
- `FLOW`
- `EXTERNAL_REFERENCE`
- `ORIGIN`
- `TRANSACTION_DATE`
- `POSTING_DATE`
- `ACCOUNT`

Operators include:

- `EQUALS`
- `NOT_EQUALS`
- `CONTAINS`
- `NOT_CONTAINS`
- `STARTS_WITH`
- `ENDS_WITH`
- `REGEX`
- `GREATER_THAN`
- `GREATER_THAN_OR_EQUAL`
- `LESS_THAN`
- `LESS_THAN_OR_EQUAL`
- `BEFORE`
- `AFTER`
- `BETWEEN`
- `IN`
- `NOT_IN`

The `TransactionRuleCondition` validation matrix is the source of truth for:

- valid field/operator combinations;
- typed value parsing;
- text normalization;
- enum handling;
- date handling;
- amount handling;
- account-id handling;
- `IN` / `NOT_IN` token semantics.

## Implementation phases

### Phase 1 — pure evaluator ✅

- backend-only pure evaluator service implemented;
- no mutation;
- backend tests only;
- evaluate a transaction-like draft;
- return `RuleEvaluationResult`;
- support active rules ordered by priority;
- support `ALL` / `ANY` condition evaluation;
- support output suggestions;
- support conflict detection.

### Phase 2 — apply on create with fill-empty behavior ✅

- apply on `FinancialTransaction` create;
- use `FILL_EMPTY_ONLY`;
- resolve and validate the account first, then evaluate only the transaction/account owner's rules;
- no admin override;
- no cross-user rule evaluation;
- no silent override;
- no update reevaluation;
- no persisted evaluation result;
- no preview UI;
- no bulk reevaluation.

### Phase 3A — backend draft preview endpoint ✅

- `POST /api/financial-transactions/rule-preview`;
- accepts an unsaved draft with account, transaction fields, optional explicit category, and optional explicit tags;
- validates account/category/tags using current-user ownership rules;
- admin has no cross-user preview behavior;
- returns category/tag suggestions, conflicts, skipped outputs, matched rules, `hasSuggestions`, and `hasConflicts`;
- does not save a `FinancialTransaction`;
- does not mutate category/tags/rules;
- does not apply `FILL_EMPTY_ONLY`;
- does not persist the evaluation result;
- does not expose a UI.

### TC-2B.1 — manual TransactionCandidate autosave UI ✅

- `/financial-transaction/new` is the product manual-create route.
- Page load does not create an empty draft.
- The first meaningful user change creates a `MANUAL` `TransactionCandidate`.
- Meaningful first changes are account, transaction date, nonblank description, signed amount/amount entry, category, or tags.
- Posting date, external reference, and notes alone do not create the first candidate.
- After candidate creation, the UI replaces the URL with `/financial-transaction/drafts/{id}` so the user can resume by URL.
- Subsequent changes debounce autosave through `PATCH /api/transaction-candidates/{id}/manual-draft`.
- The UI displays amount and flow, but submits only `signedAmount`; backend derives final `amount` and `flow`.
- There is no explicit Save Draft button.
- Post flushes pending autosave, calls `POST /api/transaction-candidates/{id}/post`, and redirects to the posted `FinancialTransaction` detail.
- Cancel before candidate creation just navigates away; cancel after candidate creation calls the candidate cancel command.
- Candidate create/post does not call `POST /api/financial-transactions/rule-preview`, does not run frontend-side rules, and does not auto-apply TransactionRules. TC-2C candidate apply remains an explicit user action.

### TC-2C.1a — backend TransactionCandidate rule preview/apply foundation ✅

- `POST /api/transaction-candidates/{id}/rule-preview` evaluates active owner rules against the current persisted editable MANUAL candidate and returns transient suggestions, conflicts, skipped outputs, and matched rules. It does not mutate the candidate.
- `POST /api/transaction-candidates/{id}/apply-rules` re-evaluates current candidate state and applies category/tags with `FILL_EMPTY_ONLY`.
- Category fills only when the candidate has no category and the suggestion has no conflict.
- Tags are additive; existing/manual tags are preserved and duplicates are not added.
- Manual category/tags are not overwritten. If manual selections existed before apply, `classificationReviewStatus` remains `USER_SELECTED`.
- If suggestions exist for an unclassified candidate, apply sets `classificationReviewStatus=SUGGESTED`; if no applicable suggestions exist, it sets `NOT_APPLICABLE`.
- Manual category/tag PATCH sets `classificationReviewStatus=USER_SELECTED`.
- Rule-input PATCH after fresh classification sets `classificationReviewStatus=STALE`; notes-only changes do not because TransactionRules do not use notes.
- TC-2C.1c adds backend post gating so manual drafts cannot be posted while classification is `NOT_EVALUATED` or `STALE`. Backend post allows only `SUGGESTED`, `USER_SELECTED`, or `NOT_APPLICABLE` classification states when the candidate is otherwise ready.
- Candidate preview/apply does not call or reuse the public `POST /api/financial-transactions/rule-preview` endpoint.

### TC-2C.1b/1d — frontend TransactionCandidate rule suggestions UI ✅

- Manual candidate drafts show a compact Rule suggestions section after the candidate exists.
- After a successful autosave of rule-input fields, the UI automatically calls `POST /api/transaction-candidates/{id}/rule-preview`, renders suggested category/tags, matched rules, conflicts, and skipped outputs, and does not mutate the candidate form.
- Rule-input fields are account, transaction date, posting date, description, signed amount/amount/flow, and external reference. Notes-only and category/tag-only edits do not auto-refresh preview.
- Apply suggestions / Confirm no suggestions flushes pending/in-flight autosave, calls `POST /api/transaction-candidates/{id}/apply-rules`, and replaces local form state from the returned candidate.
- Preview is read-only; suggestions are never auto-applied.
- The UI displays classification review statuses: `NOT_EVALUATED`, `STALE`, `SUGGESTED`, `USER_SELECTED`, and `NOT_APPLICABLE`.
- Post is disabled with a visible message while status is `NOT_EVALUATED` or `STALE`; Post is allowed for `SUGGESTED`, `USER_SELECTED`, and `NOT_APPLICABLE` if the candidate is otherwise `READY_TO_POST`.
- Backend `POST /api/transaction-candidates/{id}/post` enforces the same classification gate and rejects direct API attempts to post `NOT_EVALUATED` or `STALE` manual candidates.
- Manual category/tag changes continue to autosave through the manual-draft PATCH endpoint and are represented as `USER_SELECTED` by the backend response.
- Candidate post still does not secretly preview/apply rules.
- Candidate flow still does not call the public `POST /api/financial-transactions/rule-preview` endpoint.

### Deferred after TC-2C.1b

- Existing-transaction preview / reevaluation UI remains deferred.
- Bulk reevaluation flow remains deferred.

### Phase 4 — reevaluate one transaction

- add explicit reevaluate action for one transaction;
- likely starts as `PREVIEW_ONLY`;
- user can accept suggestions.

### Phase 5 — bulk reevaluation

- add bulk reevaluation only after preview/apply behavior is mature;
- support filters or selected transactions;
- avoid silent destructive overrides.

## Deferred items

- applying rules on update;
- override confirmation UI;
- existing-transaction preview UI;
- transaction-level explanation UI;
- reevaluate one transaction;
- bulk reevaluation;
- rule match audit log;
- drag-and-drop rule priority UI;
- advanced source/override policies.

## Open decisions

- `RuleEvaluationResult` remains internal/backend-only; Phase 3A exposes separate REST preview DTOs.
- Whether condition-level details should be added later for explanation/debug UI.
- Whether skipped outputs should later be hidden/filtered in normal UI while remaining available from the backend preview response.
- Origin policy for future API/import/ingestion runtime: when those flows are implemented, decide whether they should use central create with rule application, bypass rule application, make rule application configurable, preview only, or apply only in specific modes. Current project decision: no premature origin-based restriction.

## Description normalization is separate

## CSV ingestion category/tag review

CSV ingestion uses the category/tag Transaction Rule evaluator through candidate-backed review commands.

TC-3C.2 migrates Pantalla 2 in the TransactionIngestion workflow UI to prepared `FILE_IMPORT` `TransactionCandidate`s:

- `POST /api/transaction-ingestions/{id}/candidates/prepare` creates/syncs candidates for `VALID` rows after Pantalla 1.
- The UI reloads `GET /api/transaction-ingestions/{id}/workflow` and uses each row's `candidate` summary as the source of truth for selected category/tags and `classificationReviewStatus`.
- `POST /api/transaction-ingestions/{id}/candidates/rule-preview` evaluates current candidate state read-only and returns transient suggestions, matched rules, conflicts, and skipped outputs keyed by candidate id.
- Preview does not mutate candidate category/tags and does not write to `rawData`.
- User edits in Pantalla 2 call candidate classification PATCH and are persisted on `TransactionCandidate`.
- Browser refresh reloads persisted category/tag selections from the workflow row candidate summaries.
- Before Confirm Import, the current UI reloads the workflow and validates that all `VALID` row candidates are ready/reviewed, then calls `/confirm` without legacy `records`/category/tag payload.
- TC-3D.1 changes Confirm Import itself to read persisted `FILE_IMPORT` candidates as the source of truth. TC-4B removes backend parsing/validation of the old confirm `records/categoryId/tagIds` body.
- Confirm Import does not run the evaluator and does not persist rule evaluation results or category/tag choices into `rawData`.
- UserPreference and `AUTO_APPLY` behavior remain deferred.

The older ingestion `POST /api/transaction-ingestions/{id}/classification-preview` endpoint was removed in TC-4B because the candidate-backed flow has no active dependency on it. New frontend/product code must use the candidate-backed prepare/rule-preview/apply/classification commands above.

The ingestion-scoped candidate classification commands for prepared `FILE_IMPORT` `TransactionCandidate`s are:

- `PATCH /api/transaction-ingestions/{ingestionId}/candidates/{candidateId}/classification` writes user-selected category/tags to the candidate and marks `classificationReviewStatus=USER_SELECTED`; the request must include at least one of `categoryId` or `tagIds`.
- `POST /api/transaction-ingestions/{ingestionId}/candidates/rule-preview` evaluates active owner TransactionRules against current candidate state with `TransactionOrigin.FILE_IMPORT` and returns transient suggestions/matches/conflicts/skips.
- `POST /api/transaction-ingestions/{ingestionId}/candidates/apply-rules` re-evaluates current candidate state and applies category/tags with `FILL_EMPTY_ONLY`.
- `POST /api/transaction-ingestions/{ingestionId}/candidates/{candidateId}/confirm-no-suggestions` marks `NOT_APPLICABLE` only when a fresh evaluation has no suggestions.
- These FILE candidate classification endpoints do not create `FinancialTransaction` rows, do not mutate `IngestionRecord.rawData`, and do not call `/api/financial-transactions/rule-preview`.

Candidate-backed Confirm Import uses the existing `POST /api/transaction-ingestions/{id}/confirm` path. For non-completed imports, every current `VALID` row must have exactly one reviewed `FILE_IMPORT` candidate with `status=READY_TO_POST`, `validationStatus=VALID`, and `classificationReviewStatus` of `SUGGESTED`, `USER_SELECTED`, or `NOT_APPLICABLE`. Confirm creates `FinancialTransaction` rows from candidate fields/category/tags, sets candidates to `POSTED`, links candidates and `IngestionRecord`s to the created transactions, preserves `rawData`, and completes the parent ingestion all-or-nothing. Completed retry remains idempotent and creates no duplicates.

`classificationReviewStatus` is the category/tag review gate used by manual post and FILE confirm. It is separate from `descriptionReviewStatus`, which records description normalization/review state. `NEEDS_REVIEW`, `API_IMPORT`, and candidate failure-state expansion are reserved/deferred until a later lifecycle slice formalizes or removes them.

Description normalization is not part of the category/tag Transaction Rule Engine.

- `TransactionRule` remains category/tags only.
- `resultingDescription` is not reintroduced into `TransactionRule`.
- `DescriptionNormalizationRule` is an ingestion-only rule type.
- It evaluates only original imported descriptions.
- It outputs only `resultingDescription`.
- First matching description normalization rule wins.
- It runs during FILE upload/record creation before row review.
- It does not run on manual FinancialTransaction create/update/PATCH.
- It does not run during Confirm Import.
- It does not invoke category/tag TransactionRule evaluation.

### Matcher helper boundary

`DescriptionNormalizationRuleEvaluationService` uses the shared `TextConditionMatcher` and `ConditionGroupEvaluator` helpers.
`TransactionRuleEvaluationService` intentionally keeps its existing matcher and ALL/ANY grouping logic for now to avoid accidental behavior changes in category/tag TransactionRules.

`TextConditionMatcher` is not yet a drop-in replacement for `TransactionRuleEvaluationService` because it uses `DescriptionNormalizationRuleOperator` rather than `RuleOperator`, does not cover `IN` / `NOT_IN`, returns `false` for invalid regex where TransactionRule evaluation currently throws for invalid stored regex, and uses different case-insensitive regex flags.

A future behavior-preserving refactor may introduce a shared lower-level text matcher or adapter once semantics are unified. `ConditionGroupEvaluator` may also be adopted later by `TransactionRuleEvaluationService`, but that is deferred because it is not needed for `DescriptionNormalizationRule` v1.
