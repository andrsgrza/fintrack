# FINTRACK Rule Engine Design

## Purpose

This document defines the future design contract for the FINTRACK Transaction Rule Engine.

Status: **Phase 1 backend evaluator implemented; apply/REST/UI remain deferred**.

Not implemented yet:

- automatic rule application on transaction create;
- automatic rule application on transaction update;
- REST preview endpoint;
- transaction reevaluation;
- rule preview UI;
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
  - internal `TransactionRuleEvaluationResult`;
  - no mutation;
  - no saving;
  - no REST endpoint;
  - no automatic apply-on-create/update.
- TransactionRule v1 outputs:
  - `resultingCategory`;
  - `resultingTags`.

### Designed but not implemented

- future apply modes.

### Deferred

- public REST preview endpoint;
- automatic apply-on-create;
- manual preview UI;
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
- TransactionRule detail owns embedded condition management.
- TransactionRule edit edits only general rule fields.
- TransactionRule create saves the inactive parent first, then redirects to detail to add conditions.
- active TransactionRule requires at least one condition.
- adding a condition does not auto-activate the rule.
- deleting the last condition deactivates the rule.

The pure evaluator is implemented. Automatic rule application/execution is **not implemented**.

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

### Proposed `RuleEvaluationResult` shape

This is not Java code yet. It is the conceptual shape implementation should follow.

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

`evaluatedAt` is optional for phase 1. It becomes more useful if the result is persisted, returned by a REST preview endpoint, or used for audit/debug later.

`mode` is optional for phase 1 pure preview. The result should still be compatible with future apply modes.

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

- future apply mode;
- category applies only if empty;
- tags are added without removing existing tags;
- no override.

### OVERRIDE_WITH_CONFIRMATION

- future UI-driven apply mode;
- conflicts can be applied only if the user accepts.

### FORCE_OVERRIDE

- future explicit override mode;
- not v1.

`RuleEvaluationResult` should support these future modes, but implementation phase 1 only needs `PREVIEW_ONLY` evaluation semantics.

## Ownership for evaluation and apply

Admin has no special rule-evaluation behavior in v1. TransactionRule evaluation and future FinancialTransaction rule application follow normal-user ownership rules.

For future apply-on-create:

- resolve and validate the `FinancialAccount` first;
- confirm the account belongs to/is accessible by the current user under normal-user ownership rules;
- build `TransactionRuleEvaluationInput.userLogin` from that resolved transaction/account owner, which should normally be the authenticated user's login;
- evaluate only that owner's rules;
- do not evaluate another user's rules due to admin privileges;
- do not design special admin rule-evaluation flows.

## Create/update/reevaluate lifecycle

### Create

Automatic backend evaluation may run on transaction create in a future phase.

The first mutation phase should apply only empty fields automatically.

Manual create UI may later show rule preview/conflicts before saving.

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

### Phase 2 — apply on create with fill-empty behavior

- apply on `FinancialTransaction` create;
- use `FILL_EMPTY_ONLY`;
- resolve and validate the account first, then evaluate only the transaction/account owner's rules;
- no admin override;
- no cross-user rule evaluation;
- no silent override;
- no update reevaluation;
- no bulk reevaluation.

### Phase 3 — preview UI

- add preview UI for manual transaction create/edit;
- show suggestions and conflicts before save/apply.

### Phase 4 — reevaluate one transaction

- add explicit reevaluate action for one transaction;
- likely starts as `PREVIEW_ONLY`;
- user can accept suggestions.

### Phase 5 — bulk reevaluation

- add bulk reevaluation only after preview/apply behavior is mature;
- support filters or selected transactions;
- avoid silent destructive overrides.

## Deferred items

- applying rules on create;
- applying rules on update;
- REST preview endpoint;
- preview UI;
- override confirmation UI;
- transaction-level explanation UI;
- reevaluate one transaction;
- bulk reevaluation;
- rule match audit log;
- drag-and-drop rule priority UI;
- advanced source/override policies.

## Open decisions

- Whether `RuleEvaluationResult` should later become a public REST DTO; today it is internal/backend-only.
- Whether condition-level details should be added later for explanation/debug UI.
- Whether skipped outputs should eventually be returned only in debug/explain mode if exposed over REST.
- Where preview UI will live.
- How to represent conflicts and skipped outputs consistently in REST responses if/when exposed.
