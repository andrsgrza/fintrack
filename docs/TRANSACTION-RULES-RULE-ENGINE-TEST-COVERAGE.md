# Transaction Rules and Rule Engine Test Coverage

This document summarizes the current automated test coverage for FINTRACK Transaction Rules, TransactionRuleCondition, the Rule Engine, and FinancialTransaction rule preview/apply behavior.

## Backend tests

## Rule Engine pure unit tests

File:

```text
src/test/java/com/fintrack/app/service/rules/TransactionRuleEvaluationServiceTest.java
```

This test class covers the pure evaluator without REST.

Covered behavior:

- inactive rules are ignored;
- rules without conditions are ignored defensively;
- rules without outputs are ignored defensively;
- rules evaluate by `priority ASC`, then `id ASC`;
- `ALL` requires all conditions;
- `ANY` requires at least one condition;
- `DESCRIPTION CONTAINS` matches;
- description matching is case-insensitive by default;
- case-sensitive description mismatch fails;
- `EXTERNAL_REFERENCE` null does not match, including `NOT_EQUALS` and `NOT_IN`;
- `AMOUNT GREATER_THAN` matches;
- `AMOUNT BETWEEN` matches;
- `FLOW EQUALS` matches;
- `ORIGIN IN` matches;
- `ACCOUNT EQUALS` matches;
- `TRANSACTION_DATE BEFORE` / `AFTER` / `BETWEEN` works;
- `POSTING_DATE` null does not match date operators;
- first matching category output wins;
- later category output is skipped;
- current category with same id creates no conflict;
- current category with different id creates conflict and does not override;
- tags accumulate across multiple matching rules;
- duplicate tag from a later matching rule is skipped;
- existing tag is returned with `alreadyPresent=true`;
- `alreadyPresent=true` tag is skipped as already present;
- `alreadyPresent=true` tag is not treated as a new tag to add.

## Rule Engine integration tests

File:

```text
src/test/java/com/fintrack/app/service/rules/TransactionRuleEvaluationServiceIT.java
```

This test class verifies that the evaluator works with persisted JPA entities.

Covered behavior:

- active persisted rules are loaded for the current user;
- rules from another user are not evaluated;
- persisted rule priority order is respected;
- persisted conditions are evaluated;
- `resultingCategory` is fetched;
- `resultingTags` are fetched;
- fetched outputs/conditions work without lazy loading failures.

## TransactionRule service tests

File:

```text
src/test/java/com/fintrack/app/service/TransactionRuleServiceTest.java
```

This test class covers parent rule domain behavior.

Covered behavior:

- save assigns the current user;
- save appends priority for the current user;
- users have independent priority sequences;
- update preserves existing owner;
- update allows the same priority as a no-op;
- update rejects changed priority;
- inaccessible rules cannot be updated;
- update rejects changed `updatedAt`;
- update rejects null `updatedAt`;
- `findOne` returns empty for another user's rule;
- admin lookup behavior is covered for rule CRUD lookup;
- delete returns false for inaccessible rule;
- delete removes accessible rule;
- delete reindexes remaining rules for the owner;
- reorder updates priorities in requested order;
- reorder rejects null ordered ids;
- reorder rejects empty ordered ids when user has rules;
- reorder rejects duplicate ids;
- reorder rejects missing existing rule;
- reorder rejects unknown or foreign rule id;
- eager relationship lookup is scoped to current user;
- partial update returns empty when rule is inaccessible;
- save rejects inaccessible category;
- update resolves category owned by rule owner.

## TransactionRuleCondition service tests

File:

```text
src/test/java/com/fintrack/app/service/TransactionRuleConditionServiceTest.java
```

This test class covers condition domain behavior.

Covered behavior:

- save resolves an accessible parent TransactionRule;
- save appends using max position;
- save ignores client-provided position;
- save fails when the TransactionRule is not accessible;
- invalid field/operator combinations are rejected;
- update fails when condition is not accessible;
- update rejects changing the parent TransactionRule;
- update preserves position when the same position is provided;
- update rejects changed position;
- PATCH preserves parent when `transactionRule` is absent;
- PATCH rejects null `transactionRule`;
- PATCH rejects a different `transactionRule.id`;
- PATCH preserves position when omitted;
- PATCH rejects changed position;
- PATCH rejects null position;
- delete handles parent condition lifecycle;
- delete returns false when condition is not accessible;
- findAll uses scoped query for regular user.

## TransactionRule REST integration tests

File:

```text
src/test/java/com/fintrack/app/web/rest/TransactionRuleResourceIT.java
```

This class covers the REST resource layer for TransactionRule.

Coverage includes:

- create;
- update;
- partial update;
- get/list/count;
- delete;
- validation errors;
- ownership/security behavior;
- relationship handling;
- generated criteria/filter behavior;
- domain-rule behavior exposed through REST.

## TransactionRuleCondition REST integration tests

File:

```text
src/test/java/com/fintrack/app/web/rest/TransactionRuleConditionResourceIT.java
```

This class covers the REST resource layer for TransactionRuleCondition.

Coverage includes:

- create;
- update;
- partial update;
- get/list;
- delete;
- required field validations;
- ownership/security behavior;
- field/operator validation behavior;
- position behavior;
- generated criteria/filter behavior;
- parent relationship behavior.

## FinancialTransaction REST tests for rule apply-on-create

File:

```text
src/test/java/com/fintrack/app/web/rest/FinancialTransactionResourceIT.java
```

The apply-on-create block covers rule application when creating a FinancialTransaction.

Covered behavior:

- create without category applies suggested category;
- create with explicit category does not override it;
- create without tags adds suggested tags;
- create with explicit tags preserves them and adds new suggested tags;
- explicit tag equal to suggested tag is not duplicated;
- explicit different category remains unchanged while suggested new tags are still added;
- no matching rules leave category and tags unchanged;
- inactive rule is not applied;
- rule from another user is not applied;
- admin create against another user's account is rejected and does not apply rules;
- update does not apply rules;
- PATCH does not apply rules;
- category and multiple tags are applied only once;
- explicit category equal to suggested category remains unchanged and tags still apply.

## FinancialTransaction REST tests for rule preview

File:

```text
src/test/java/com/fintrack/app/web/rest/FinancialTransactionResourceIT.java
```

The preview block covers:

```text
POST /api/financial-transactions/rule-preview
```

Covered behavior:

- preview without category returns suggested category;
- preview does not save a transaction;
- preview with explicit different category returns conflict;
- preview with same explicit category does not produce conflict;
- preview without tags returns suggested tags;
- preview with explicit tag marks that tag as `alreadyPresent`;
- preview with explicit tag plus new suggested tag returns both states;
- preview with duplicate tag suggestions reports skipped output;
- preview with no matching rules returns empty result;
- preview ignores inactive rules;
- preview ignores rules from another user;
- preview rejects foreign account;
- admin preview for another user's account is rejected;
- preview rejects foreign category;
- preview rejects foreign tag;
- preview validates required fields;
- preview does not apply `FILL_EMPTY_ONLY`;
- preview does not persist transactions.

## Frontend tests

## TransactionRule reducer tests

File:

```text
src/main/webapp/app/entities/transaction-rule/transaction-rule-reducer.spec.ts
```

Covered behavior:

- initial state;
- loading state;
- updating state;
- reset state;
- error state;
- fetch list;
- fetch one;
- create;
- update;
- partial update;
- delete;
- reset action.

## TransactionRule UX tests

File:

```text
src/main/webapp/app/entities/transaction-rule/transaction-rule-ux.spec.tsx
```

Covered behavior:

- product-oriented list columns render;
- row actions are kept;
- rules render in priority ascending order even if raw entities are unsorted;
- only possible move controls render;
- single-row list renders no reorder buttons;
- moving up sends full swapped priority order and reloads;
- moving down sends full swapped priority order and reloads;
- reorder uses priority ascending array even when raw entities are reversed;
- reorder failure shows an error;
- create title renders;
- timestamps are hidden in create;
- active is hidden in create;
- embedded conditions editor is not rendered in create mode;
- create submits new rules as inactive drafts;
- successful create redirects to the new rule detail page;
- edit title renders;
- active field renders in edit;
- manage conditions link renders in edit;
- existing values hydrate in edit;
- edit submits without priority;
- resulting category, tags, and active hydrate in edit;
- edit form does not render empty values before entity load;
- background condition count is loaded in edit;
- active is disabled when there are no conditions;
- active is enabled when at least one condition exists;
- embedded add form on detail posts condition with fixed rule id;
- detail sections align with edit layout;
- embedded edit patches editable condition fields only;
- condition delete refreshes list after confirmation;
- condition delete failure does not break detail page;
- condition load failure does not break edit/detail;
- related condition summaries display in normalized form;
- empty related conditions state displays.

## TransactionRuleCondition reducer tests

File:

```text
src/main/webapp/app/entities/transaction-rule-condition/transaction-rule-condition-reducer.spec.ts
```

Covered behavior:

- initial state;
- loading state;
- updating state;
- reset state;
- error state;
- fetch list;
- fetch one;
- create;
- update;
- partial update;
- delete;
- reset action.

## TransactionRuleCondition helper tests

File:

```text
src/main/webapp/app/entities/transaction-rule-condition/transaction-rule-condition-form-helpers.spec.ts
```

Covered behavior:

- text operators for description and external reference;
- numeric operators for amount;
- date operators for transaction/posting date;
- enum operators for flow/origin;
- account operators;
- support detection for second value;
- support detection for case sensitivity;
- value input kind by field/operator.

## TransactionRuleCondition UX tests

File:

```text
src/main/webapp/app/entities/transaction-rule-condition/transaction-rule-condition-ux.spec.tsx
```

Covered behavior:

- dynamic create title;
- parent preselected from query parameter;
- dynamic edit title;
- parent disabled in edit mode;
- second value only appears for `BETWEEN`;
- case sensitive only appears for textual fields;
- operators filter for `DESCRIPTION`;
- operators filter for `AMOUNT`;
- operators filter for `FLOW`;
- enum select renders for `FLOW EQUALS`;
- operators filter for `TRANSACTION_DATE`;
- date input renders for date fields;
- operators filter for `ACCOUNT`;
- account selector renders for account equality;
- enum select renders for `ORIGIN`;
- `IN` and `NOT_IN` render as text input with helper text;
- changing field resets incompatible operator and clears values;
- changing away from `BETWEEN` hides and clears second value;
- account selector submits value as string;
- `caseSensitive` is submitted as false when hidden;
- standalone edit payload omits position.

## TransactionRuleCondition display tests

File:

```text
src/main/webapp/app/entities/transaction-rule-condition/transaction-rule-condition-display.spec.ts
```

This file covers display/summary formatting for TransactionRuleCondition values.

## FinancialTransaction reducer tests

File:

```text
src/main/webapp/app/entities/financial-transaction/financial-transaction-reducer.spec.ts
```

Covered behavior:

- initial state;
- loading state;
- updating state;
- reset state;
- error state;
- fetch list;
- fetch one;
- create;
- update;
- partial update;
- delete;
- reset action.

## FinancialTransaction UX tests for rule preview/manual create

File:

```text
src/main/webapp/app/entities/financial-transaction/financial-transaction-ux.spec.tsx
```

Covered current manual-create behavior:

- `/financial-transaction/new` renders a manual candidate form and does not create a candidate on page load;
- non-meaningful initial changes do not create the first candidate;
- a meaningful first change creates a `MANUAL` TransactionCandidate and navigates to `/financial-transaction/drafts/{id}`;
- rapid edits do not create multiple candidates;
- autosave debounces PATCH requests and sends `signedAmount` instead of client-controlled `amount`/`flow`;
- save/create failures are visible;
- pending autosave is flushed before post;
- incomplete candidates cannot post;
- ready candidates post through the candidate post command and redirect to FinancialTransaction detail;
- cancel before/after candidate creation follows command behavior;
- resume by draft URL hydrates candidate state;
- cancelled drafts are read-only and posted drafts redirect;
- candidate create/post does not call `/api/financial-transactions/rule-preview`;
- posted FinancialTransaction edit hides technical fields and keeps account immutable;
- posted edit submit uses partial update without immutable/server-owned fields;
- detail shows clean fields with amount/currency and hides technical metadata.

Deferred after TC-2B.1:

- candidate-specific rule preview/apply;
- category/tag suggestion confirmation for manual candidates;
- existing-transaction reevaluation.

## Suggested focused test commands

Backend focused suite:

```bash
JAVA_HOME=/Users/andresgarzaarmendariz/.sdkman/candidates/java/17.0.19-tem ./mvnw \
  -Dskip.installnodenpm \
  -Dskip.npm \
  -Dskip.webpack \
  -Dtest=TransactionRuleEvaluationServiceTest,TransactionRuleEvaluationServiceIT,TransactionRuleResourceIT,TransactionRuleServiceTest,TransactionRuleConditionResourceIT,TransactionRuleConditionServiceTest,FinancialTransactionResourceIT \
  test
```

Frontend focused suite:

```bash
npm run jest -- transaction-rule
npm run jest -- transaction-rule-condition
npm run jest -- financial-transaction
```

## Note about ingestion stacktrace

The referenced stacktrace is not from Transaction Rules or the Rule Engine.

It points to an ingestion enum/runtime mismatch:

```text
No enum constant com.fintrack.app.domain.enumeration.IngestionRecordStatus.IMPORTED
```

That indicates the running backend code and database data/migration state may be out of sync for `IngestionRecordStatus`. It should be handled separately from TransactionRule/Rule Engine test coverage.
