# UI Composition Conventions

## Purpose

This document defines how we compose generated JHipster CRUD screens with custom embedded UI.

The goal is to keep generated CRUD pages useful while avoiding duplicated, inconsistent, or over-customized pages.

## Default rule

Generated CRUD pages are the default.

Preserve generated CRUD pages when they match the domain workflow.

Do not create custom pages just because the generated page looks basic. Extend only when the domain workflow requires it.

## Extend, do not recreate

When a generated page is mostly correct, extend it with small contextual components instead of creating a completely new page.

Prefer:

- small reusable form sections;
- read-only view sections;
- collection editors;
- related lists.

Avoid:

- duplicating full CRUD page logic;
- embedding complete generated CRUD pages inside other pages;
- creating parallel screens for the same workflow without a clear reason.

## Standalone CRUD vs embedded UI

Standalone CRUD pages and embedded UI serve different purposes.

### Standalone CRUD page

A standalone CRUD page is a full route/page for one entity.

Examples:

- `credit-account-details-update.tsx`
- `transaction-rule-condition-update.tsx`
- `transaction-rule-condition-detail.tsx`

Standalone CRUD remains useful for:

- admin/debug;
- direct maintenance;
- generated fallback;
- deep links;
- development/testing.

### Embedded UI

Embedded UI is not a full CRUD page placed inside another page.

Embedded UI is a contextual component designed for the parent workflow.

Examples:

- `credit-account-details-form-section.tsx`
- `credit-account-details-view-section.tsx`
- `transaction-rule-conditions-collection-editor.tsx`
- `transaction-rule-condition-form-section.tsx`

Embedded UI should hide or fix parent context instead of asking the user to select it again.

## Never embed complete CRUD pages

Do not embed complete generated CRUD pages inside parent pages.

Do not place a full child `*-update.tsx`, `*-detail.tsx`, or route-driven CRUD page inside a parent page.

Instead, extract the reusable part into a contextual component.

Examples:

- Do not embed `credit-account-details-update.tsx` inside `financial-account-update.tsx`.
- Do use `credit-account-details-form-section.tsx`.

- Do not embed `transaction-rule-condition-update.tsx` inside `transaction-rule-detail.tsx`.
- Do use `transaction-rule-condition-form-section.tsx` inside `transaction-rule-conditions-collection-editor.tsx`.

## Embedded child sections

A child entity may be embedded in a parent create/edit/detail page when:

- the child is a natural part of the parent concept;
- the child does not make sense without the parent;
- the user expects to manage both together;
- the parent provides necessary context;
- exposing the child as an independent choice would confuse the workflow.

When embedded, the child section must not show or edit the parent relationship field.

The parent is fixed by the containing page.

Examples:

- FinancialAccount embeds CreditAccountDetails without an account selector.
- TransactionRule embeds TransactionRuleConditions without a transactionRule selector.

## Standalone child CRUD

Standalone CRUD pages may still exist for child entities.

They are useful for:

- admin/debug;
- direct maintenance;
- generated fallback;
- deep links.

However, standalone child CRUD is not always the main product flow.

When a child is naturally managed from a parent page, the parent page may become the preferred product workflow.

## Related lists

Large, historical, or high-volume relationships should not usually be edited from the parent edit page.

They may appear as read-only sections in the parent detail page.

Use a related list when:

- the parent should show context;
- the child should not be edited inline;
- editing would create too much complexity;
- the child is historical or transactional.

Use a collection editor when:

- the child is naturally managed as part of the parent;
- inline add/edit/delete is expected;
- the child has no meaningful standalone workflow for normal users.

## Backend orchestration

Start with frontend orchestration only when:

- the flow is simple;
- partial failure is acceptable;
- the frontend is not leaking complex domain logic;
- backend invariants still protect correctness.

Use a backend command endpoint when:

- the operation must be atomic;
- multiple entities must be changed as one domain action;
- frontend orchestration would leak domain rules;
- partial failure would leave confusing or invalid state.

## Component naming

Embedded sections must be extracted into components.

Use these names:

- `*form-section.tsx`

  - embedded editable child section;
  - reusable fields for create/edit;
  - useful for 1:1 children or shared form slices.

- `*view-section.tsx`

  - embedded read-only child section;
  - useful for detail pages.

- `*collection-editor.tsx`

  - embedded editable child collection;
  - supports inline add/edit/delete for a 1:N child.

- `*related-list.tsx`
  - read-only related list;
  - useful for large/historical relationships.

## Embedded table display

Embedded tables should be user-facing, not raw database/model dumps.

When a child model has technical fields that only make sense together, prefer a readable summary column.

Avoid showing raw technical columns when they create noise.

Examples:

- TransactionRuleCondition has `value`, `secondValue`, and `caseSensitive`.
- In the embedded TransactionRule conditions table, these should be shown as one readable condition summary instead of three raw columns.

Preferred embedded display:

- `Amount between 20 and 500`
- `Description contains "UBER"`
- `Description contains "Uber" (case-sensitive)`
- `Flow is not IN`

Raw model fields may still exist in the backend and DTO. They do not need to appear as separate embedded UI columns.

## Implemented cases

## FinancialAccount + CreditAccountDetails

### Relationship type

`FinancialAccount` to `CreditAccountDetails` is a 1:1 child-style relationship for credit card accounts.

CreditAccountDetails is only meaningful for `CREDIT_CARD` accounts.

### UI pattern used

This uses the 1:1 embedded section pattern.

FinancialAccount create/edit embeds CreditAccountDetails fields when `accountType = CREDIT_CARD`.

FinancialAccount detail embeds a read-only CreditAccountDetails view section.

### Components used

- `credit-account-details-form-section.tsx`
- `credit-account-details-view-section.tsx`

### What we did not do

We did not embed the full generated CreditAccountDetails CRUD pages inside FinancialAccount.

We did not embed:

- `credit-account-details-update.tsx`
- `credit-account-details-detail.tsx`

### Parent relationship behavior

The embedded CreditAccountDetails section does not show or edit the `account` relationship selector.

The parent FinancialAccount is fixed by the containing FinancialAccount workflow.

### Orchestration

This is frontend orchestration today.

FinancialAccount is saved first, then CreditAccountDetails is created or updated for the saved account.

Atomic backend command endpoint remains deferred.

### Standalone CRUD

Standalone CreditAccountDetails CRUD remains available for admin/debug/direct maintenance.

## TransactionRule + TransactionRuleCondition

### Relationship type

`TransactionRule` to `TransactionRuleCondition` is a 1:N child collection.

A condition belongs to one rule. In normal product usage, conditions are managed in the context of their parent rule.

### UI pattern used

This uses the editable child collection pattern.

TransactionRule detail/view shows the embedded editable conditions collection editor.

TransactionRule edit is reserved for general TransactionRule fields and provides a "Manage conditions" link back to detail.

### Components used

- `transaction-rule-conditions-collection-editor.tsx`
- `transaction-rule-condition-form-section.tsx`

### What we did not do

We did not embed the full generated TransactionRuleCondition CRUD pages inside TransactionRule.

We did not embed:

- `transaction-rule-condition-update.tsx`
- `transaction-rule-condition-detail.tsx`

The embedded editor reuses the condition form section, not the standalone CRUD page.

### Parent relationship behavior

The embedded TransactionRuleCondition form does not show or edit the `transactionRule` parent selector.

The parent TransactionRule is fixed by the containing TransactionRule detail page.

Embedded create submits the current rule as the fixed parent.

Embedded edit PATCHes only editable condition fields and does not reparent.

### Condition loading

Both the detail editor and the edit-page Active safety check load conditions from:

`GET /api/transaction-rules/{id}/conditions`

The frontend does not fetch all TransactionRuleConditions and filter client-side for this workflow.

### Inline actions

The TransactionRule detail collection editor supports:

- inline add;
- inline edit;
- inline delete.

The embedded table does not show a View button because the condition is already visible in the parent context.

Standalone detail routes remain available for direct maintenance/debug.

### Embedded table display

The embedded TransactionRuleCondition table shows a normalized `Condition` summary plus actions. It does not expose raw technical child fields as separate embedded columns:

- no raw `value` column;
- no raw `secondValue` column;
- no raw `caseSensitive` true/false column;
- no `position` column.

Examples:

- `Description contains "Uber"`
- `Description contains "Uber" (case-sensitive)`
- `Amount between 20 and 500`
- `Flow is not IN`

The raw model fields still exist in DTO/backend persistence and are still edited through the smart form where appropriate.

### Position behavior

TransactionRuleCondition `position` is server-managed.

Embedded and standalone forms do not show or submit `position`.

New conditions are appended server-side with:

`max(position) + 1`

Deleting conditions does not reindex remaining positions.

Position controls stable display/evaluation order inside a rule.

Position does not affect `ALL` / `ANY` logical semantics.

`TransactionRule.priority` orders rules relative to each other.

`TransactionRuleCondition.position` orders conditions inside one rule.

Condition reorder UI/API remains deferred.

### Smart form behavior

The standalone TransactionRuleCondition form is a smart form, not a raw generated enum form.

It:

- filters operators by selected field;
- renders typed value inputs;
- shows `secondValue` only for `BETWEEN`;
- shows `caseSensitive` only for `DESCRIPTION` / `EXTERNAL_REFERENCE`;
- uses an accessible FinancialAccount selector for `ACCOUNT` `EQUALS` / `NOT_EQUALS`;
- still submits the selected account id as the string `value` expected by the backend;
- keeps `IN` / `NOT_IN` as comma-separated text in this slice.

The embedded TransactionRule detail editor reuses the same smart condition form section/helper behavior.

### Delete behavior

Deleting a condition uses the existing DELETE endpoint.

If the last condition is deleted, the backend deactivates the parent rule.

The TransactionRule detail page refreshes parent state after condition mutations.

### Deferred

Deferred for this workflow:

- row-positioned inline edit;
- condition reorder UI/API;
- rule execution engine;
- atomic backend command endpoint.
