# FINTRACK Rule Engine Design

## Purpose

This document defines the future design contract for the FINTRACK Transaction Rule Engine.

Status: **design only**.

Not implemented yet:

- rule execution service;
- automatic rule application on transaction create;
- automatic rule application on transaction update;
- transaction reevaluation;
- rule preview UI;
- bulk reclassification;
- rule execution audit log.

The engine should evaluate user-defined `TransactionRule`s against `FinancialTransaction`-like input and produce suggested or applied outputs for:

- category assignment;
- tag assignment.

Subscription and description assignment are deliberately not outputs in the current TransactionRule model. They can be reconsidered in a later rule-engine slice.

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

The rule execution engine is **not implemented**.

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

The first matching rule with `resultingCategory` can suggest a category.

If the transaction has no explicit category, the category can be auto-filled in modes that allow mutation.

If the transaction already has a different explicit category, the engine must not silently override it. It should produce a conflict or suggestion.

### Tags

Matching rules can suggest tags.

Suggested tags accumulate in priority order.

Duplicate tags are removed.

Explicit existing tags are preserved.

Auto-apply may add suggested tags without removing existing tags, depending on evaluation mode and the final source/override policy.

### Deferred outputs

Subscription and description mutations are not TransactionRule outputs in the current model. If they return later, they should be introduced as explicit rule-engine design work with clear conflict behavior and transaction validation.

## Suggestions vs mutation

The engine should be designed around evaluation results first, not direct mutation.

Preferred conceptual API:

```text
evaluate(transactionDraft) -> RuleEvaluationResult
```

`RuleEvaluationResult` should be able to contain:

- matched rules;
- condition match details, if useful later;
- suggested outputs;
- conflicts;
- skipped outputs with reasons.

Applying suggestions should be a separate step.

Preferred conceptual API:

```text
applyEvaluation(transaction, evaluation, mode)
```

## Evaluation modes

### PREVIEW_ONLY

- evaluate rules;
- return suggestions and conflicts;
- do not mutate the transaction.

### FILL_EMPTY_ONLY

- apply suggestions only to empty or unset fields;
- do not override explicit user values.

### OVERRIDE_WITH_CONFIRMATION

- allow the user to accept conflicting suggestions explicitly.

### FORCE_OVERRIDE

- future system/admin/bulk mode;
- not part of v1.

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

Explicit user input wins by default.

Rules should not silently override values the user explicitly chose.

If a rule conflicts with explicit input:

- record a conflict or suggestion;
- require confirmation before applying the override.

For imports/API, fields may be source-provided rather than user-confirmed.

The first implementation should still avoid destructive overrides unless a field is empty.

Richer source/override policy remains deferred.

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

## Proposed implementation phases

### Phase 1 — pure evaluator

- implement pure evaluator service;
- no mutation;
- no UI;
- tests only;
- evaluate a transaction-like draft;
- return `RuleEvaluationResult`;
- support active rules ordered by priority;
- support `ALL` / `ANY` condition evaluation;
- support output suggestions;
- support conflict detection.

### Phase 2 — apply on create with fill-empty behavior

- apply on `FinancialTransaction` create;
- use `FILL_EMPTY_ONLY`;
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

- rule execution service implementation;
- applying rules on create;
- applying rules on update;
- preview UI;
- override confirmation UI;
- transaction-level explanation UI;
- reevaluate one transaction;
- bulk reevaluation;
- rule match audit log;
- drag-and-drop rule priority UI;
- advanced source/override policies.

## Open decisions

- Whether subscription or description outputs should return in a future rule-engine slice.
- Whether API/import source values count as explicit values.
- Whether tag suggestions should auto-apply when existing tags are present.
- Whether multiple matching rules can contribute outputs in v1 or only in the pure evaluator phase.
- Final shape of `RuleEvaluationResult` DTO.
- Where preview UI will live.
- Whether condition match details are needed in v1 or only for later explanation UI.
- How to represent conflicts and skipped outputs consistently in REST responses.
