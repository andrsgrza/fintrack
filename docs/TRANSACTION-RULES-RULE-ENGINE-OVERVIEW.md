# Transaction Rules and Rule Engine Overview

This document explains how FINTRACK Transaction Rules and the Transaction Rule Engine are currently organized across backend and frontend.

## Short version

There are two related but separate pieces:

1. **TransactionRule CRUD/configuration**

   - Defines rules, outputs, priority, active state, and ownership.
   - Conditions are managed as child records through `TransactionRuleCondition`.

2. **Rule Engine**
   - Evaluates active rules against a `FinancialTransaction` draft.
   - Suggests category and tags.
   - Applies suggestions only on `FinancialTransaction` create using `FILL_EMPTY_ONLY`.
   - Provides a preview endpoint for the manual FinancialTransaction create UI.

## Backend: TransactionRule REST resource

Main REST resource:

```text
src/main/java/com/fintrack/app/web/rest/TransactionRuleResource.java
```

Base path:

```text
/api/transaction-rules
```

Main endpoints:

```text
POST   /api/transaction-rules
PUT    /api/transaction-rules/{id}
PATCH  /api/transaction-rules/{id}
GET    /api/transaction-rules
GET    /api/transaction-rules/count
GET    /api/transaction-rules/{id}
GET    /api/transaction-rules/{id}/conditions
PUT    /api/transaction-rules/reorder
DELETE /api/transaction-rules/{id}
```

Main service:

```text
src/main/java/com/fintrack/app/service/TransactionRuleService.java
```

The REST resource delegates to:

```java
transactionRuleService.save(...)
transactionRuleService.update(...)
transactionRuleService.partialUpdate(...)
transactionRuleService.findOne(...)
transactionRuleService.delete(...)
transactionRuleService.reorder(...)
transactionRuleConditionService.findByTransactionRuleId(...)
transactionRuleQueryService.findByCriteria(...)
transactionRuleQueryService.countByCriteria(...)
```

## Backend: TransactionRule domain behavior

`TransactionRuleService` owns the parent rule domain behavior:

- Rule ownership is user-scoped.
- `name` is trimmed.
- `name` is required after trim.
- `name` is unique per owner, case-insensitive and trim-insensitive.
- `description` is trimmed.
- blank `description` becomes `null`.
- `createdAt` and `updatedAt` are server-owned.
- `priority` is server-managed.
- new rules are appended last.
- create starts as an inactive draft.
- `active=true` requires at least one condition.
- a rule must have at least one output.

Current v1 outputs:

```text
resultingCategory
resultingTags
```

Removed/deferred outputs:

```text
resultingDescription
resultingFinancialSubscription
```

## Backend: TransactionRule priority and reorder

Priority defines evaluation order.

Internally, priority is server-managed and zero-based. The UI displays it as one-based order:

```text
priority 0 -> #1
priority 1 -> #2
priority 2 -> #3
```

Manual reorder is exposed through:

```text
PUT /api/transaction-rules/reorder
```

Payload shape:

```json
{
  "orderedIds": [1, 2, 3]
}
```

The service validates that the ordered IDs belong to the current user and represent the complete set of that user's rules.

## Backend: TransactionRuleCondition REST resource

Main REST resource:

```text
src/main/java/com/fintrack/app/web/rest/TransactionRuleConditionResource.java
```

Base path:

```text
/api/transaction-rule-conditions
```

Main endpoints:

```text
POST   /api/transaction-rule-conditions
PUT    /api/transaction-rule-conditions/{id}
PATCH  /api/transaction-rule-conditions/{id}
GET    /api/transaction-rule-conditions
GET    /api/transaction-rule-conditions/{id}
DELETE /api/transaction-rule-conditions/{id}
```

Main service:

```text
src/main/java/com/fintrack/app/service/TransactionRuleConditionService.java
```

For the parent-centered UI, conditions are also loaded through:

```text
GET /api/transaction-rules/{id}/conditions
```

That endpoint delegates to:

```java
transactionRuleConditionService.findByTransactionRuleId(id)
```

## Backend: TransactionRuleCondition domain behavior

`TransactionRuleConditionService` manages child condition behavior:

- Conditions belong to a `TransactionRule`.
- Ownership is resolved through the parent rule.
- `position` is server-managed.
- Conditions are loaded/evaluated by `position ASC`, then `id ASC`.
- `secondValue` is only meaningful for `BETWEEN`.
- `caseSensitive` only applies to text fields.
- `ACCOUNT` conditions store the account id as string.
- `IN` and `NOT_IN` values remain comma-separated text.

Representative repository:

```text
src/main/java/com/fintrack/app/repository/TransactionRuleConditionRepository.java
```

Important repository methods include:

```java
findByTransactionRuleIdOrderByPositionAscIdAsc(...)
findMaxPositionByTransactionRuleId(...)
deleteByTransactionRuleId(...)
findPotentialDuplicates(...)
```

## Backend: Rule Engine package

The Rule Engine lives separately from CRUD services:

```text
src/main/java/com/fintrack/app/service/rules/
```

Main classes:

```text
TransactionRuleEvaluationService
TransactionRuleEvaluationInput
TransactionRuleEvaluationResult
RuleMatchResult
CategorySuggestion
TagSuggestion
RuleOutputConflict
SkippedRuleOutput
RuleOutputField
RuleOutputConflictReason
RuleOutputSkipReason
```

Main evaluator:

```text
src/main/java/com/fintrack/app/service/rules/TransactionRuleEvaluationService.java
```

Main method:

```java
evaluate(TransactionRuleEvaluationInput input)
```

The evaluator does not save anything. It only returns suggestions, conflicts, skipped outputs, and matched-rule metadata.

## Backend: Rule Engine repository loading

The evaluator loads active rules through:

```java
transactionRuleRepository.findActiveRulesForEvaluationByUserLoginOrderByPriorityAscIdAsc(input.userLogin())
```

Repository:

```text
src/main/java/com/fintrack/app/repository/TransactionRuleRepository.java
```

The evaluation query is user-scoped and ordered by:

```text
priority ASC, id ASC
```

The evaluator also defensively sorts rules by priority and id.

## Backend: Rule Engine semantics

The evaluator:

- loads active rules only;
- evaluates rules for the provided `userLogin`;
- ignores inactive rules;
- ignores rules without conditions;
- ignores rules without outputs;
- evaluates rules by `priority ASC`, then `id ASC`;
- evaluates conditions by `position ASC`, then `id ASC`;
- supports `ALL` and `ANY` condition logic;
- treats null/blank actual values as non-matching, including for negative operators;
- uses first matching category output as the category suggestion;
- accumulates tag suggestions across multiple matching rules;
- skips duplicate tag suggestions;
- marks already-present tags as `alreadyPresent`.

## Backend: TransactionRuleEvaluationInput

The evaluator input represents the transaction draft being evaluated.

Important fields include:

```text
userLogin
description
amount
flow
externalReference
origin
transactionDate
postingDate
accountId
currentCategoryId
currentCategoryName
currentTagIds
currentTagNames
```

`currentCategoryId` and `currentTagIds` represent explicit/current transaction values so the engine can avoid overriding or duplicating them.

## Backend: FinancialTransaction create integration

Rule application happens from:

```text
src/main/java/com/fintrack/app/service/FinancialTransactionService.java
```

The REST entry point is:

```text
POST /api/financial-transactions
```

Resource:

```text
src/main/java/com/fintrack/app/web/rest/FinancialTransactionResource.java
```

Flow:

```text
FinancialTransactionResource.createFinancialTransaction(...)
  -> financialTransactionService.save(dto)
    -> resolve and validate account ownership
    -> resolve explicit category/tags/subscription/ingestion
    -> normalize and validate transaction fields
    -> applyRulesOnCreate(...)
      -> transactionRuleEvaluationService.evaluate(input)
      -> applyRuleEvaluationOnCreate(...)
    -> save FinancialTransaction
```

Rules are applied only on create.

They are not applied on:

```text
PUT   /api/financial-transactions/{id}
PATCH /api/financial-transactions/{id}
```

## Backend: FILL_EMPTY_ONLY apply semantics

The create integration uses `FILL_EMPTY_ONLY`.

Category behavior:

- If the transaction has no explicit category and the evaluator suggests a category, apply it.
- If the transaction already has an explicit category, do not override it.
- If there is a category conflict, do not apply the suggestion.
- Conflicts do not fail the create request.

Tag behavior:

- Preserve explicit tags.
- Add suggested tags only if they are new.
- Do not duplicate tags.
- Do not re-add already-present tags.
- Duplicate or already-present suggestions are skipped.

The evaluation result is not persisted.

The transaction response does not expose the evaluation result.

## Backend: FinancialTransaction rule preview

Preview endpoint:

```text
POST /api/financial-transactions/rule-preview
```

Resource method:

```java
FinancialTransactionResource.previewRules(...)
```

Service method:

```java
financialTransactionService.previewRules(request)
```

Preview flow:

```text
FinancialTransactionResource.previewRules(...)
  -> financialTransactionService.previewRules(request)
    -> resolve account/category/tags/subscription/ingestion
    -> build unsaved FinancialTransaction draft
    -> normalize and validate draft
    -> transactionRuleEvaluationService.evaluate(input)
    -> map result to FinancialTransactionRulePreviewResponseDTO
```

Preview does not save anything.

Preview DTOs:

```text
FinancialTransactionRulePreviewRequestDTO
FinancialTransactionRulePreviewResponseDTO
```

## Frontend: TransactionRule routes

Main route files live under:

```text
src/main/webapp/app/entities/transaction-rule/
```

Important files:

```text
transaction-rule.tsx
transaction-rule-update.tsx
transaction-rule-detail.tsx
transaction-rule-delete-dialog.tsx
```

Main routes:

```text
/transaction-rule
/transaction-rule/new
/transaction-rule/:id
/transaction-rule/:id/edit
/transaction-rule/:id/delete
```

## Frontend: TransactionRule list

File:

```text
src/main/webapp/app/entities/transaction-rule/transaction-rule.tsx
```

The list:

- loads rules sorted by `priority ASC`, then `id ASC`;
- defensively sorts client-side;
- displays order as `#1`, `#2`, `#3`;
- shows rule name, status, logic, outputs, updated timestamp, and actions;
- keeps View/Edit/Delete actions;
- shows Move up / Move down controls only when possible;
- sends reorder requests to:

```text
api/transaction-rules/reorder
```

## Frontend: TransactionRule create/edit

File:

```text
src/main/webapp/app/entities/transaction-rule/transaction-rule-update.tsx
```

Create behavior:

- creates an inactive draft;
- priority is not editable;
- conditions are not created inline;
- after successful create, user is sent to the rule detail page to add conditions.

Edit behavior:

- edits rule metadata and outputs;
- uses PATCH semantics;
- active checkbox appears only in edit mode;
- activation requires loaded conditions and at least one condition;
- priority remains read-only/server-managed;
- includes a link/button to manage conditions from the detail page.

## Frontend: TransactionRule detail and embedded conditions

File:

```text
src/main/webapp/app/entities/transaction-rule/transaction-rule-detail.tsx
```

The detail page shows the parent rule and embeds the conditions editor.

Embedded editor:

```text
src/main/webapp/app/entities/transaction-rule/components/transaction-rule-conditions-collection-editor.tsx
```

From the rule detail page, users can:

- add conditions;
- edit conditions;
- delete conditions.

The editor loads conditions from:

```text
GET api/transaction-rules/{id}/conditions
```

And writes through:

```text
POST   api/transaction-rule-conditions
PUT    api/transaction-rule-conditions/{id}
PATCH  api/transaction-rule-conditions/{id}
DELETE api/transaction-rule-conditions/{id}
```

## Frontend: Smart condition form

Smart form component:

```text
src/main/webapp/app/entities/transaction-rule-condition/components/transaction-rule-condition-form-section.tsx
```

It handles:

- operator filtering by selected field;
- typed value inputs by field/operator;
- `secondValue` only for `BETWEEN`;
- `caseSensitive` only for `DESCRIPTION` and `EXTERNAL_REFERENCE`;
- account selector for `ACCOUNT EQUALS` / `ACCOUNT NOT_EQUALS`;
- account id submitted as string;
- comma-separated values for `IN` / `NOT_IN`.

## Frontend: FinancialTransaction rule preview flow

Main file:

```text
src/main/webapp/app/entities/financial-transaction/financial-transaction-manual-draft.tsx
```

Current product manual create uses TC-2B.1 TransactionCandidate autosave:

```text
/financial-transaction/new
  -> first meaningful change creates MANUAL TransactionCandidate
  -> /financial-transaction/drafts/{id}
  -> autosave draft
  -> post candidate
  -> FinancialTransaction detail
```

The older FinancialTransaction two-step rule-preview UI is superseded as the product create route. `POST /api/financial-transactions/rule-preview` remains a backend preview endpoint, but candidate create/post does not call it. TC-2C candidate-specific rule preview/apply is implemented through `POST /api/transaction-candidates/{id}/rule-preview` and `POST /api/transaction-candidates/{id}/apply-rules`; applying suggestions is explicit and posting a candidate does not auto-run rules.

The backend preview endpoint remains:

```text
api/financial-transactions/rule-preview
```

Current candidate preview/apply behavior:

- evaluates the persisted `MANUAL` candidate through `POST /api/transaction-candidates/{id}/rule-preview`;
- displays matching rules/suggestions/conflicts without mutating the candidate;
- applies suggestions only when the user explicitly chooses apply/confirm through candidate commands;
- uses `FILL_EMPTY_ONLY`: category fills only if empty/no conflict, tags are additive, and manual selections are preserved;
- requires reviewed classification before candidate post.

Final manual posting calls:

```text
POST /api/transaction-candidates/{id}/post
```

Candidate post creates the final `FinancialTransaction` and does not auto-run rule preview/apply.

## Current implementation status

Implemented:

- TransactionRule CRUD/domain rules.
- TransactionRuleCondition CRUD/domain rules.
- Server-managed priority.
- Manual reorder.
- Pure backend evaluator.
- Apply-on-create for FinancialTransaction.
- Backend rule preview endpoint.
- Manual TransactionCandidate autosave UI.
- Candidate-specific manual rule preview/apply UI.
- Candidate-backed CSV ingestion classification in the unified workflow review.

Not implemented:

- Rule application on FinancialTransaction update.
- Rule application on FinancialTransaction PATCH.
- Bulk reevaluation.
- Manual reevaluate-one-transaction endpoint.
- Persisted rule evaluation result.
- Matched-rule audit log.
- Rule engine execution during CSV confirm/import.
- Description output.
- FinancialSubscription output.
- Override confirmation flow.
- Rule preview UI outside manual FinancialTransaction create.
