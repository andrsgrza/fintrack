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

## View/Edit layout parity

For primary admin/product CRUD screens that need custom UX, detail and edit should share the same conceptual layout.

The detail page shows values.

The edit page shows inputs in the same groups and approximate order.

This keeps navigation predictable: when a user clicks Edit from detail or list, they should feel they are editing the same object, not entering an unrelated generated form.

Use the same main groups and approximate order whenever possible.

Example section order:

- Identity
- Matching / configuration
- Result / output
- Status
- Metadata
- Child collections, when applicable

Detail-only metadata can remain read-only.

Edit-only controls can remain editable.

Child collection editors remain where the product workflow needs them.

Avoid:

- detail pages that read like long documents;
- detail pages with narrative headings that do not map to editable fields;
- edit pages whose grouping differs significantly from detail;
- edit pages that make the user re-orient after clicking Edit.

Prefer compact detail pages for admin/product entities.

Detail should be scannable.

Edit should mirror detail, replacing values with inputs.

If a detail section exists, the equivalent editable fields should appear in the same relative place on edit, unless the fields are read-only, server-managed, or only make sense in detail.

TransactionRule applies this pattern: detail and edit share Identity → Matching logic → Result → Status / Metadata ordering. Detail shows values and embedded conditions. Edit shows inputs plus a Manage conditions link.

## Edit form hydration

Edit forms must hydrate from the current entity when opened from either list or detail.

When visiting `/entity/:id/edit`, existing values must populate correctly after the entity loads.

This includes:

- scalar fields;
- booleans;
- selects;
- relationship selects;
- multi-select relationships;
- optional fields.

Do not accept an edit screen that renders empty inputs for an existing entity.

When using JHipster `ValidatedForm`, be careful with composition.

`ValidatedField` components should remain registered with the form.

In this project, the safest default is to keep `ValidatedField` components as direct children of the `ValidatedForm`, using headings and layout wrappers only where they do not break react-hook-form registration/default-value hydration.

If deeper form composition is required, pass form methods explicitly and add tests proving edit hydration works.

Required tests for customized edit pages:

- edit opened by route id loads existing values;
- edit opened from detail loads existing values;
- relationship selects hydrate correctly;
- multi-select relationships hydrate correctly;
- boolean fields hydrate correctly;
- create mode still starts with correct defaults.

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
- In the embedded TransactionRule conditions table, these are shown as one readable condition summary instead of three raw columns.

Preferred embedded display:

- `Amount between 20 and 500`
- `Description contains "UBER"`
- `Description contains "Uber" (case-sensitive)`
- `Flow is not Income`
- `Flujo no es Ingreso`

Raw model fields may still exist in the backend and DTO. They do not need to appear as separate embedded UI columns.

Enum values should be translated for user-facing summaries.

For example, TransactionRuleCondition may store `FLOW = IN` or `FLOW = OUT`, but summaries should display the same user-facing labels used by FinancialTransaction:

- EN: `Income` / `Expense`
- ES: `Ingreso` / `Gasto`

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

TransactionRule create saves only the parent rule. It does not embed a child collection editor because conditions require a persisted parent id.

The create flow is:

1. create an inactive TransactionRule parent;
2. redirect to TransactionRule detail for the saved rule;
3. add conditions from the embedded detail collection editor;
4. activate the rule from edit when at least one condition exists.

TransactionRule list/detail use product-oriented summaries instead of generated field dumps.

The list shows:

- name;
- translated status;
- read-only order;
- translated condition logic;
- result summary;
- updated timestamp;
- actions.

Detail uses compact sections aligned with edit:

- Identity;
- Matching logic;
- Result;
- Status / Metadata;
- embedded Conditions.

TransactionRule detail and edit follow View/Edit layout parity.

Both screens use the same conceptual grouping:

- Identity;
- Matching logic;
- Result;
- Status / Metadata.

Detail shows values in those groups.

Edit shows inputs in those groups.

TransactionRule detail additionally owns the embedded Conditions editor.

TransactionRule edit does not render the embedded Conditions editor; it only provides a Manage conditions link back to detail.

TransactionRule create also does not render the embedded Conditions editor, does not maintain client-side draft conditions, hides the Active toggle, and submits `active=false`.

### Components used

- `transaction-rule-conditions-collection-editor.tsx`
- `transaction-rule-condition-form-section.tsx`

### What we did not do

We did not embed the full generated TransactionRuleCondition CRUD pages inside TransactionRule.

We did not embed:

- `transaction-rule-condition-update.tsx`
- `transaction-rule-condition-detail.tsx`

The embedded editor reuses the condition form section, not the standalone CRUD page.

We also did not implement a create-with-conditions command endpoint or client-side draft child collection on TransactionRule create. Those remain deferred until there is a deliberate atomic parent+children command design.

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

The embedded TransactionRuleCondition table shows a normalized `Condition` summary plus actions.

It does not expose raw technical child fields as separate embedded columns:

- no raw `value` column;
- no raw `secondValue` column;
- no raw `caseSensitive` true/false column;
- no `position` column.

Examples:

- `Description contains "Uber"`
- `Description contains "Uber" (case-sensitive)`
- `Amount between 20 and 500`
- `Flow is not Income` / `Flujo no es Ingreso`

The raw model fields still exist in DTO/backend persistence and are still edited through the smart form where appropriate.

### Position behavior

TransactionRuleCondition `position` is server-managed.

Embedded and standalone forms do not show or submit `position`.

New conditions are appended server-side with:

`max(position) + 1`

Deleting conditions does not reindex remaining positions.

Position controls stable display/evaluation order inside a rule.

Position does not affect `ALL` / `ANY` logical semantics.

`TransactionRule.priority` orders rules relative to each other for the same owner/user.

TransactionRule priority/order is server-managed, unique/consecutive per user, reindexed on rule delete, stored 0-based, and displayed 1-based in the UI.

TransactionRule list is the only manual reorder surface. It always renders rules in evaluation order (`priority ASC, id ASC`) and provides only possible Move up / Move down controls per row: the first row does not render Move up, the last row does not render Move down, and a single-row list renders neither. Each move swaps adjacent rules in that priority-ordered array, sends the full current-user ordered id list to `PUT /api/transaction-rules/reorder`, then reloads from the backend. The backend validates exact current-user membership and normalizes priorities to `0..n`.

`TransactionRuleCondition.position` orders conditions inside one rule.

Unlike TransactionRule priority, TransactionRuleCondition position does not reindex on delete.

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

Adding a condition does not automatically activate the parent rule.

The TransactionRule detail page refreshes parent state after condition mutations.

### Deferred

Deferred for this workflow:

- create-with-conditions command endpoint;
- client-side draft child collection on parent create;
- row-positioned inline edit;
- TransactionRule drag-and-drop UI;
- condition reorder UI/API;
- rule execution engine;
- override confirmation UI;
- atomic backend command endpoint.

FinancialTransaction manual create owns the implemented Rule Engine workflow UI. Existing-transaction preview, override confirmation, and bulk reevaluation remain deferred and are documented in [RULE-ENGINE.md](RULE-ENGINE.md).

## FinancialTransaction manual create — two-step Rule Engine UX

Status: implemented for manual create only. Phase 3A provides the backend workflow endpoint, and Phase 3B uses it from the FinancialTransaction create form.

Phase 3B manual create composition:

1. Step 1 — Transaction details:
   - account;
   - description;
   - amount;
   - flow;
   - transaction date;
   - posting date;
   - external reference;
   - notes and other non-categorization fields as applicable.
2. Between steps:
   - call `POST /api/financial-transactions/rule-preview` with the unsaved draft;
   - do not save or mutate anything;
   - use the response as UI assistance only.
3. Step 2 — Categorization:
   - category;
   - tags;
   - prepopulate controls from suggested category/tags;
   - show conflicts/skipped outputs/matched rules where useful;
   - let the user accept, change, remove, or add category/tags before save.

Step 2 should own category/tags. Step 1 should not duplicate those controls.

Final Save still uses normal FinancialTransaction create. Backend Phase 2 `FILL_EMPTY_ONLY` remains a safety net: explicit category/tags sent by Step 2 are treated as user choices; if a direct API/UI create omits category/tags, backend create may still fill empty values.

Edit mode remains the existing one-step edit flow. It does not call rule preview and does not auto-reevaluate rules.

Do not extend this as:

- silently saving preview suggestions;
- modal override confirmation;
- existing-transaction reevaluation;
- bulk reevaluation;
- persisted evaluation results;
- audit log;
- `MANUAL`-only backend apply behavior.

## CSV Ingestion v1 — upload/review workflow

Status: implemented through I2C persisted upload/review/confirm workflow.

CSV import upload/review is a `TransactionIngestion` domain workflow, not generated `FileIngestion` CRUD.

Composition rules:

- start from TransactionIngestion list/page with a contextual "New File Import" action;
- select the target FinancialAccount and upload the canonical CSV;
- call `POST /api/transaction-ingestions/file`;
- redirect to the persisted TransactionIngestion review page;
- show persisted workflow summary, read-only file metadata, rows, review actions, and Confirm Import when ready;
- do not expose editable FileIngestion metadata fields as a product create flow;
- do not embed full FileIngestion CRUD inside TransactionIngestion;
- use contextual upload/review components;
- review rows are high-volume, so use related-list/table style rather than inline editable child collection;
- Confirm Import is shown only for `READY` reviews with at least one valid row;
- completed reviews are read-only;
- later FinancialAccount shortcut should reuse the same flow with account preselected.

The canonical creation route is `/transaction-ingestion/new`. In create mode it is a parent-centered FILE ingestion workflow: it renders only Account, Ingestion Type, and a CSV file input for `FILE`; API ingestion shows a TBD placeholder and cannot be submitted. It hides lifecycle/system-owned fields such as status, source label, started/completed timestamps, counters, error message, and created timestamp. A successful FILE submit posts multipart `accountId` + `file` to `POST /api/transaction-ingestions/file`, creates the parent `TransactionIngestion`, `FileIngestion` metadata, and `IngestionRecord` review rows in one backend workflow, then redirects to `/transaction-ingestion/{id}`.

The standalone `/file-ingestion/new` route is also treated as a TransactionIngestion workflow command, not metadata CRUD. It renders only an eligible pending FILE `TransactionIngestion` selector and a CSV file input, posts multipart `file` to `POST /api/transaction-ingestions/{id}/file-ingestion`, derives all `FileIngestion` metadata server-side, creates `IngestionRecord` rows, updates the parent readiness/counters, and redirects to `/transaction-ingestion/{id}`. Future embedded usage from a parent page should hide the parent selector because the parent context is already known.

The canonical workflow detail/review route is `/transaction-ingestion/{id}`. It is a recoverable TransactionIngestion workflow page, not FileIngestion CRUD. It loads workflow data through `GET /api/transaction-ingestions/{id}/workflow`, shows the parent summary, embeds read-only FileIngestion metadata for FILE ingestions, shows counts and rows, and supports row enable/disable plus normalized-row edit review actions. `FileIngestion` remains metadata for the uploaded file. `IngestionRecord` rows are review rows. Valid rows use `VALID`, disabled rows use `DISABLED`, invalid rows use `REJECTED`, and the table renders translated user-facing statuses strictly from `row.status`. API ingestion detail remains TBD.

The parent status shown on the review page uses readiness language before import: `READY` means ready to import, and `PARTIALLY_READY` means the workflow needs review or has no valid rows to import. `COMPLETED` means confirm import finished and the review is read-only. `PARTIALLY_COMPLETED` is reserved and should not normally appear in CSV v1. Inline row edit is intentionally limited to normalized review fields: transaction date, posting date, description, signed amount, currency, external reference, and notes. Amount and flow stay read-only because they are derived server-side from signed amount. Edit controls render only for `VALID` and `REJECTED` rows while the parent is `READY` or `PARTIALLY_READY`. `DISABLED` rows render Enable only and must be enabled before editing. Imported/skipped/failed rows do not render edit controls. Confirm Import calls the backend confirm endpoint, imports `VALID` rows, keeps `DISABLED` rows skipped, updates the review from the response, and does not invoke the Rule Engine.
