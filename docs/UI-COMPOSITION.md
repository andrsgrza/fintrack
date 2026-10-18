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

TransactionRule applies the same orientation principle without duplicating its editor: the detail explains Matching, Suggested results, and Evaluation behavior; configured create/edit contains the corresponding inputs plus an inline local conditions editor.

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

### Product overview

`/financial-account` is a product overview, not a generated CRUD table. It renders one concise, responsive card per accessible account with its name, translated type, currency, active/inactive state, optional last four digits, and a calculated balance summary. The existing account `color` is projected by the overview DTO only to render a restrained card accent; raw hexadecimal values are never displayed in read-only account surfaces.

- `DEBIT`, `CASH`, and `INVESTMENT` cards show **Current balance / Saldo actual**.
- `CREDIT_CARD` cards show **Current debt / Deuda actual** and, when `CreditAccountDetails` exists, credit limit, available credit, statement day, payment due day, and APR.
- A historical card with no `CreditAccountDetails` remains visible with its calculated debt and an incomplete-card warning; the overview never creates details as a side effect.

The overview does not display database ids, audit timestamps, ownership fields, raw colors, or generated relationship dumps. The account name opens its canonical detail, Edit is compact, and destructive Delete lives in a More-actions menu rather than a repeated View/Edit/Delete button group.

### Product detail and delete

`/financial-account/:id` is the canonical product detail view. Its header carries the account identity, translated type, currency, optional last four digits, compact status, and existing color accent, with Back, Edit, and More actions. It groups useful information into **Balance**, optional **Credit-card summary**, **General information**, and **Recent activity**. Debit/Cash/Investment accounts prioritize current balance, opening position, tracking date, inflow, and outflow. Credit cards prioritize current debt and available credit, with limit, opening position, tracking date, closing day, due day, and APR as secondary facts. It intentionally hides database ids, audit timestamps, owner data, raw colors, and raw Budget or TransactionIngestion relationship dumps.

An inactive account shows a clear status badge and a concise historical-only explanation: history and balances remain available, existing work may finish, and the account becomes selectable for new transactions, imports, and rules only after reactivation. Credit-card details stay inline; a missing detail record offers the parent Account edit route rather than a child CRUD route.

The account delete dialog is a product hard-delete flow, not the generated CRUD delete path. It loads a read-only deletion preview before enabling the destructive action, identifies the account without exposing its ID, lists only meaningful consequences, and makes clear that shared categories/tags remain. Typed blockers render as contextual product copy; the primary action stays disabled until none remain. A race-time `409 ACCOUNT_HARD_DELETE_BLOCKED` updates the same modal locally rather than creating a generic error toast. The dialog also provides **Deactivate instead**, which opens the existing account-edit route so the user can preserve the complete history.

Recent activity keeps its compact account-detail table, but it is also navigation: each transaction description is a keyboard-accessible product link to its canonical FinancialTransaction detail. **View all transactions** opens `/financial-transaction` with the current account's `accountId.equals` filter, page 1, and transaction-date descending sort. The FinancialTransaction list hydrates that URL into its open account filter, including the existing historical inactive-account `includeId` behavior; clearing the filter returns to the normal unfiltered list.

### Relationship type

`FinancialAccount` to `CreditAccountDetails` is a 1:1 child-style relationship for credit card accounts.

CreditAccountDetails is only meaningful for `CREDIT_CARD` accounts.

### UI pattern used

This uses the 1:1 embedded section pattern.

FinancialAccount create/edit embeds CreditAccountDetails fields when `accountType = CREDIT_CARD`.

The account form uses **Basic information**, **Identity**, **Configuration**, and conditional **Credit-card details** sections. Identity has the native color picker plus editable hex value; the redundant standalone preview is intentionally absent. The optional persisted `icon` remains a compatibility field but has no product form control until an icon system exists. Existing accounts show status as compact metadata; account type and currency remain visibly disabled because their existing immutability rules have not changed.

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

### Product write orchestration

FinancialAccount create/edit uses one atomic product command:

- `POST /api/financial-accounts/configured`;
- `PUT /api/financial-accounts/{id}/configured`.

For `CREDIT_CARD`, the command receives the parent account and its embedded credit-card details together. The backend scopes the parent to the current user, validates the child against that parent, and persists both in one transaction. A child validation or persistence failure rolls back the parent write; the product UI never writes `CreditAccountDetails` in a second request.

### Technical compatibility routes

`/credit-account-details` and its direct detail/create/edit/delete URLs remain routable only for compatibility and technical inspection. They are not listed in the normal entity menu. The list and detail render a **Technical / Técnico** notice and remain read-only; they link to the meaningful parent account rather than treating the child as an independent product record.

The direct `/new` and `/:id/edit` URLs render guidance only: product users must create or edit the parent `FinancialAccount`. The technical child UI offers no Create, Save, Edit, or Delete action, so there is no second browser write path around the configured parent commands. Backend generic endpoints remain technically compatible; this UI demotion does not remove or redirect them.

## TransactionRule + TransactionRuleCondition

### Relationship type

`TransactionRule` to `TransactionRuleCondition` is a 1:N child collection.

A condition belongs to one rule. In normal product usage, conditions are managed in the context of their parent rule.

### UI pattern used

This uses the configured parent + child collection command pattern for product create/edit.

TransactionRule create/edit manage rule metadata, outputs, active state, condition logic, and conditions together through the configured API:

- `POST /api/transaction-rules/configured`;
- `GET /api/transaction-rules/{id}/configured`;
- `PUT /api/transaction-rules/{id}/configured`.

Product create no longer creates an empty inactive draft. It keeps unsaved conditions in local frontend state and saves the complete configured rule in one command.

Product save is blocked, whether the rule is active or inactive, when the rule has:

- zero conditions;
- zero outputs (`resultingCategory` or `resultingTags`);
- an EXPENSE/INCOME resulting category without compatible FLOW semantics.

TransactionRule detail/view is a read-only product explanation backed by the configured aggregate API. It never creates, edits, or deletes child conditions; configured create/edit is the only product rule-writing flow. Standalone TransactionRuleCondition routes remain available for technical/debug direct maintenance.

TransactionRule list/detail use product-oriented summaries instead of generated field dumps.

The list shows:

- name;
- translated status;
- read-only order;
- translated condition logic;
- result summary;
- updated timestamp;
- actions.

Detail uses a product header (name, status, one-based evaluation position, Edit/Delete/Back actions) plus three read-only sections:

- Matching — complete typed condition list and accurate ALL/ANY explanation;
- Suggested results — category identity, tag pills, and visible historical inactive-output state;
- Evaluation behavior — priority position, multi-rule category/tag semantics, configuration-vs-execution boundary, and workflow applicability.

Detail is intentionally not a second condition editor. Edit contains the corresponding configured inputs.

TransactionRule create/edit additionally render the configured inline Conditions editor.

The configured inline editor:

- uses local state;
- does not call child `TransactionRuleCondition` endpoints directly;
- preserves condition ids on edit for persisted conditions;
- submits the full ordered conditions array through the configured parent command.

### Components used

- `transaction-rule-local-conditions-editor.tsx`
- `transaction-rule-condition-form-section.tsx`

### What we did not do

We did not embed the full generated TransactionRuleCondition CRUD pages inside TransactionRule.

We did not embed:

- `transaction-rule-condition-update.tsx`
- `transaction-rule-condition-detail.tsx`

The configured editor reuses the condition form section, not the standalone CRUD page.

### Parent relationship behavior

The embedded TransactionRuleCondition form does not show or edit the `transactionRule` parent selector.

In configured create/edit, the parent is the containing configured TransactionRule form. Conditions are local drafts until the parent configured command is saved.

The product detail has no child write operation. Technical/debug child routes retain their existing parent access and immutability rules.

### Condition loading

The product edit form loads parent + ordered conditions from:

`GET /api/transaction-rules/{id}/configured`

The product detail loads the complete parent plus ordered conditions from the same configured aggregate endpoint. It performs an additional read-only account catalog lookup only to replace account condition ids with names.

The frontend does not fetch all TransactionRuleConditions and filter client-side for this workflow.

### Inline actions

The configured create/edit local conditions editor supports:

- local inline add before a rule exists;
- local inline edit;
- local inline delete;
- locked auto-required FLOW condition display for EXPENSE/INCOME categories.

Standalone TransactionRuleCondition list/detail/create/edit routes remain available for deep links, debugging, and direct maintenance. TR-3 marks those standalone generated screens with a Technical/debug banner and the Entities menu marks TransactionRuleCondition as Technical. They are not the product workflow for creating or editing TransactionRules.

Generated TransactionRule and TransactionRuleCondition backend endpoints remain available temporarily:

- `POST /api/transaction-rules`
- `PUT /api/transaction-rules/{id}`
- `PATCH /api/transaction-rules/{id}`
- `POST /api/transaction-rule-conditions`
- `PUT /api/transaction-rule-conditions/{id}`
- `PATCH /api/transaction-rule-conditions/{id}`

These generated endpoints are technical/direct-maintenance surfaces. Configured endpoints remain the product source of truth for TransactionRule create/edit, and backend strict validation still protects generated paths while they exist.

### Read-only condition display

The product TransactionRule detail shows every configured condition as a normalized typed summary. It has no inline condition actions.

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

TransactionRule list is the only manual reorder surface. It is a complete Classification Rules manager, rendering rules in evaluation order (`priority ASC, id ASC`) as cards with readable condition summaries, a category identity and tag pills, status, and one-based evaluation position. Move up / Move down controls remain visible but disabled at the respective boundaries; a keyboard-accessible `@dnd-kit` handle also supports pointer, touch, and keyboard reorder. Both interactions swap/reposition the same complete current-user array, submit every ordered id to `PUT /api/transaction-rules/reorder`, then reload from the backend. A failed persistence restores the last confirmed order. The backend validates exact current-user membership and normalizes priorities to `0..n`; an administrator list may span owners, so it shows no reorder controls.

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

The configured TransactionRule create/edit editor uses the smart condition form section/helper behavior. The product detail reuses only its typed condition summary formatter.

### Category/FLOW guard in product create/edit

When a resulting category is selected:

- EXPENSE auto-adds a required `FLOW EQUALS OUT` condition when no FLOW condition exists;
- INCOME auto-adds a required `FLOW EQUALS IN` condition when no FLOW condition exists;
- the auto-required FLOW condition is locked while that category remains selected;
- BOTH/null category does not require FLOW and removes only the frontend auto-created FLOW condition;
- user-authored incompatible FLOW conditions are not silently mutated; save is blocked with a validation message;
- `ANY` condition logic is disabled/blocked for EXPENSE/INCOME outputs.

### Configured editor product composition

The configured TransactionRule editor uses the same product composition for create and edit: **Identity → Matching conditions → Results → Configuration summary → Status**. It is not a generated CRUD form and it does not expose rule priority or owner controls. The live summary describes the draft configuration only; it does not execute a rule or claim that a transaction matches.

The result category picker renders the selected category as a color-accented identity with its immediate hierarchy context and translated type. Its picker offers only active current-owner categories for new choices. `ProductTagSelector` renders colored result-tag pills, allows multiple active current-owner additions, and preserves a currently selected inactive historical tag with an explicit inactive label. A historical inactive category/tag may remain in an unrelated configured update, but cannot be selected anew and is not represented as a currently eligible automatic output.

The inline typed condition form restricts operators and controls by field. When a field or operator transition makes a value incompatible, it clears only that incompatible value and explains the reset; compatible text-field and date-field transitions retain the draft value. `FLOW` and `ORIGIN` are separate enum domains even though both render as selects, so switching between them clears the incompatible enum value.

Configured create/update is one atomic parent command. The UI prevents duplicate submissions, keeps the complete draft in place on an API error, and returns to the manager after a successful command. There is no aggregate version contract: concurrent successful configured updates retain the existing backend last-write-wins behavior.

### Delete behavior

Deleting a TransactionRule from its product detail deletes the rule and its conditions through the existing rule delete endpoint, reindexes the owner's future evaluation order, and returns to the manager. The confirmation explicitly says that posted transactions and previously reviewed candidates are not changed or automatically reevaluated. Technical condition deletion remains on the standalone technical route; deleting its last condition still deactivates the parent rule.

### Deferred

Deferred for this workflow:

- row-positioned inline edit;
- condition reorder UI/API;
- rule execution engine;
- override confirmation UI;
- atomic backend command endpoint.

FinancialTransaction manual create now owns the TransactionCandidate autosave UI and candidate-specific rule suggestion actions. Existing-transaction preview UI, override confirmation, and bulk reevaluation remain deferred and are documented in [RULE-ENGINE.md](RULE-ENGINE.md).

## FinancialTransaction manual create — TransactionCandidate autosave UX

Status: implemented through TC-2D.2 for manual create, draft resume, and draft recovery.

Manual create composition:

1. `/financial-transaction/new` renders a local unsaved manual draft form.
2. Page load does not create a backend candidate.
3. The first meaningful user change creates a `MANUAL` `TransactionCandidate` through `POST /api/transaction-candidates/manual`.
4. Meaningful first changes are account, transaction date, nonblank description, signed amount/amount entry, category, or tags.
5. Posting date, external reference, and notes alone do not create the first candidate.
6. After creation, the UI replaces the URL with `/financial-transaction/drafts/{id}`.
7. `/financial-transaction/drafts/{id}` accepts MANUAL candidates only. Non-MANUAL candidates and load failures show a safe error state with no editable form, autosave, Post, or manual Cancel action.
8. Subsequent edits debounce autosave through `PATCH /api/transaction-candidates/{id}/manual-draft`.
9. The UI displays amount plus flow, but submits only `signedAmount`; backend derives `amount` and `flow`.
10. The Rule suggestions section is available after a candidate exists. After successful autosave of rule-input fields, the UI automatically calls `POST /api/transaction-candidates/{id}/rule-preview` without mutating category/tags when account, transaction date, description, and a positive amount are complete. Until then it explains that those details are required rather than presenting stale suggestions as actionable. Apply suggestions / Confirm no suggestions flushes autosave and calls `POST /api/transaction-candidates/{id}/apply-rules`, then hydrates category/tags/status from the returned candidate.
11. Post flushes pending autosave, requires `classificationReviewStatus` to be `SUGGESTED`, `USER_SELECTED`, or `NOT_APPLICABLE`, calls `POST /api/transaction-candidates/{id}/post`, receives the final FinancialTransaction, removes the pre-post manual candidate server-side, and redirects to that transaction detail. A successfully posted new manual draft therefore no longer appears in draft recovery or at its draft URL.
12. Cancel before candidate creation navigates away; cancel after candidate creation calls the candidate cancel command.

The manual form uses the standard product page/section composition: **Account**, **Transaction**, **Classification**, **Optional details**, and **Actions**. New entry is titled “New transaction”; a persisted route is “Edit draft.” The header keeps autosave feedback compact (creating, saving, saved, or failed) and shows account/description context only after a candidate exists. Transaction date and posting date use a shared responsive grid. On desktop, its common header row aligns both inputs; **Use the same posting date** appears below the posting-date input. At narrower widths, each date label/input pair stacks naturally. In this manual-draft flow only, **Use the same posting date** is enabled by default: posting date stays visible but read-only and follows transaction date. Opting out preserves the current posting date and makes it independently editable; re-enabling immediately copies the current transaction date. A resumed draft infers the control from its persisted dates without overwriting a different posting date. The user enters a positive amount with account currency context and selects localized Expense/Income flow controls, while the candidate still receives only derived `signedAmount`.

Classification keeps flow-compatible category options and selected Category/Tag product presentation: category is a structured, color-accented hierarchy label. Tags use the shared `ProductTagSelector`: selected values are compact color-accented, removable pills; **Add tag** opens a searchable active-only picker; selected inactive historical tags remain visible with an Inactive marker and can be removed. The selector owns no API or candidate behavior: its parent supplies values and retains the existing autosave semantics. Rule suggestions prioritize suggested category/tags plus Apply suggestions or Confirm no suggestions; matched rules, conflicts, and skipped outputs are inside a closed “Rule details” disclosure. Optional external reference and notes live under a collapsed “More details” disclosure unless either has existing content. Publish remains the primary action with a concise readiness reason; persisted-draft cancel is secondary/destructive and asks for confirmation.

The manual-draft container owns loading, hydration, autosave, rule commands, post, cancel, and navigation. Its Account, Transaction, Classification, Optional details, and Actions sections are presentation-only components: they receive current values, display state, and callbacks, but do not call APIs or own candidate lifecycle state.

There is intentionally no explicit Save Draft button. Recoverability comes from autosave plus the `/financial-transaction/drafts/{id}` route.

TC-2D.2 adds the manual draft recovery page:

- `/financial-transaction/drafts` loads `GET /api/transaction-candidates/manual-drafts`.
- It shows current-user recoverable `MANUAL` draft summaries only.
- It includes only `DRAFT` and `READY_TO_POST` and excludes `CANCELLED`, `FAILED`, `FILE_IMPORT`, and `API_IMPORT`.
- There is no global `NEEDS_REVIEW` candidate status; review needs are represented by specific review/status fields. `API_IMPORT` is reserved/deferred and must not appear as an active manual draft recovery/product UI state until a later lifecycle slice formalizes it.
- It displays compact description/account/date/amount/status/classification/category/tags/updated-at metadata with fallback copy for incomplete drafts. Account references include the stored account type, currency, final four digits, and an inactive marker when applicable; category and tag chips retain their stored color and inactive context.
- Resume links to `/financial-transaction/drafts/{id}`.
- Resume is the primary row action. Cancel draft is deliberately destructive and lives in the row overflow menu; it calls `POST /api/transaction-candidates/{id}/cancel`, then removes/reloads the row.
- It does not post candidates from the list.
- The FinancialTransaction list links to this page through a secondary "Drafts" action; draft rows are not mixed into the posted FinancialTransaction list.
- Generic `TransactionCandidate` CRUD routes are not product UI.

## FinancialTransaction posted list — product presentation

`/financial-transaction` is a posted-transaction list, not a generated entity table. Each responsive card presents only the decision-making fields: description/detail link, transaction date, linked account, category path, tag chips, signed display amount, and flow. Income is shown as `+amount currency`; expense is shown as `−amount currency`. A compact server-backed live filter bar keeps description search visible and places transaction-date range, account, type, category, and tags under **Filters**. Description applies after 350 ms; each transaction-date bound applies after 300 ms; account, type, category, and tag additions/removals apply immediately. Every applied value is URL-backed, uses replace navigation, resets pagination to page 1, and preserves current sort/order. Pagination and sorting retain normal history navigation and preserve the applied filters. Enter flushes a pending text debounce; Clear cancels it before restoring empty URL filters. A small accessible updating indicator retains the prior list during a refresh. Its multi-tag filter uses the same shared `ProductTagSelector`, so URL-selected values are removable chips and **Add tag** searches active values locally without querying the transaction list. Selector catalogs show active choices for new filtering while retaining an already URL-selected inactive historical account/category/tag so historic transactions remain findable. Category hierarchy/color and tag color pills communicate filter identity; origin, ingestion, IDs, timestamps, subscriptions, and other technical relations remain off the product filters and primary list.

The header has a primary **New transaction** action and secondary **Drafts** and refresh actions. Each final row keeps compact edit visible and places Delete in an overflow menu. Opening either list or detail Delete shows an ordinary local confirmation and preserves the complete current list query on return. Final transactions are normally deletable; the modal handles request failures locally rather than emitting a generic toast.

## FinancialTransaction posted detail and edit — product composition

`/financial-transaction/{id}` is a compact posted-transaction detail rather than a generated label/value dump. Its header combines description, signed amount with exactly one localized flow badge, account context, and transaction date; account context never repeats the flow. Classification reuses the category hierarchy/color treatment and tag pill/color treatment used by the posted list and drafts. A metadata grid contains only useful dates, account, external reference when present, and a localized origin (`Manual`, `File import`, or `API`); it deliberately excludes IDs, timestamps, candidate/ingestion internals, and raw enum values. Notes render only when present. Edit is visible, Back is secondary, and Delete remains in the overflow menu.

`/financial-transaction/{id}/edit` remains the one-step posted `FinancialTransaction` PATCH flow. It is composed as Transaction, Classification, and More details sections with a single Cancel / Save changes footer. Account is read-only contextual product information, not a disabled selector; origin and other server-owned/technical fields are absent. It loads the product-safe selectable category/tag APIs (including existing inactive historical choices), filters category choices by flow, and uses the same `ProductTagSelector` rather than a native multi-select or duplicate tag presentation. Posted edit does not call rule preview or auto-reevaluate rules.

The candidate UI does not call `POST /api/financial-transactions/rule-preview`, does not run frontend-side TransactionRules, and does not apply rules during candidate post. It only calls candidate-specific preview/apply commands through `/api/transaction-candidates/{id}/rule-preview` and `/api/transaction-candidates/{id}/apply-rules`; preview is automatic/read-only after saved rule-input changes, while apply/confirm remains explicit.

Do not extend this as:

- silently saving preview suggestions;
- modal override confirmation;
- existing-transaction reevaluation;
- bulk reevaluation;
- persisted evaluation results;
- audit log;
- `MANUAL`-only backend apply behavior.

## Historical (retired) CSV Ingestion v1 — upload/review workflow

Status: **retired**. This section is retained only as historical design context. Its `FileIngestion`, `IngestionRecord`, `FILE` TransactionIngestion, routes, and command endpoints no longer exist. Transaction Batch is the sole active product workflow for CSV preparation, review, and publication.

CSV import upload/review is a `TransactionIngestion` domain workflow, not generated `FileIngestion` CRUD.

Composition rules:

- start from TransactionIngestion list/page with a contextual "New File Import" action;
- select the target FinancialAccount and upload the canonical CSV;
- call `POST /api/transaction-ingestions/file`;
- redirect to the persisted TransactionIngestion review page;
- show one persisted workflow review with file metadata, rows, row-review actions, category/tag classification, rule suggestions, and Confirm Import together;
- do not expose editable FileIngestion metadata fields as a product create flow;
- do not embed full FileIngestion CRUD inside TransactionIngestion;
- use contextual upload/review components;
- review rows are high-volume, so use related-list/table style rather than inline editable child collection;
- eligible `READY` reviews automatically prepare their valid-row candidates, then show category/tag controls and Confirm Import in the same review;
- completed reviews are read-only;
- later FinancialAccount shortcut should reuse the same flow with account preselected.

The canonical creation route is `/transaction-ingestion/new`. In create mode it is a parent-centered FILE ingestion workflow: it renders only Account, Ingestion Type, and a CSV file input for `FILE`; API ingestion shows a TBD placeholder and cannot be submitted. It hides lifecycle/system-owned fields such as status, source label, started/completed timestamps, counters, error message, and created timestamp. A successful FILE submit posts multipart `accountId` + `file` to `POST /api/transaction-ingestions/file`, creates the parent `TransactionIngestion`, `FileIngestion` metadata, and `IngestionRecord` review rows in one backend workflow, then redirects to `/transaction-ingestion/{id}`.

The standalone `/file-ingestion/new` route is not a product upload flow. TC-4D routes it to the shared technical write-unavailable state. File upload starts from `/transaction-ingestion/new`; the backend parent-scoped attach endpoint can remain for command compatibility/debug use, but the generated FileIngestion UI does not expose it as a user-facing create action.

The canonical workflow detail/review route is `/transaction-ingestion/{id}`. It is a recoverable TransactionIngestion workflow page, not FileIngestion CRUD. It loads workflow data through `GET /api/transaction-ingestions/{id}/workflow` and uses the shared `ProductPage` composition: a product header identifies the account, structured CSV filename, localized workflow status, and return-to-imports action; a compact summary displays only existing workflow counters appropriate to the current lifecycle; and technical file/process metadata stays available in a native collapsed **File details** disclosure. Review Tools separates creating rules, page-local automation, reevaluation, and application: reevaluation recomputes against current rules and may save eligible values only when the matching auto-apply setting is on; application explicitly persists the eligible current suggestions. The candidate review queue then shows exactly six desktop columns: **Date**, **Movement**, **Amount**, **Category**, **Tags**, and **Details**. Category and Tags keep their direct candidate-backed controls in the compact desktop row. The current persisted value is primary; a transient rule preview is secondary, says **Suggested**, and has a compact, accessible **Apply** action. An expanded explanation keeps the persisted review status separate from any current preview, so a preview does not masquerade as a saved state. **Details** expands secondary transaction data, description provenance/suggestions, classification explanation, Confirm no suggestions, and all row actions. General normalized-field editing remains Save/Cancel inside that expansion. A pending general edit disables unrelated mutation/refresh controls, and stays intact if a local filter hides its row until the user returns to it or cancels/saves. On narrow viewports the desktop table is not rendered: the same state and handlers render expandable movement cards, with classification controls and secondary actions inside the expanded card. `FileIngestion` remains metadata for the uploaded file. `IngestionRecord` rows are review rows. Valid rows use `VALID`, disabled rows use `DISABLED`, invalid rows use `REJECTED`, and a historically imported row whose final transaction was later deleted uses the translated `DELETED_AFTER_IMPORT` status; the table renders user-facing status strictly from `row.status`. A pre-confirm workflow row may include an optional lightweight prepared `TransactionCandidate` summary; category/tags are reviewed and persisted on `FILE_IMPORT` candidates, never in local-only row state or `rawData`. Local filters affect visibility only, never the full-ingestion scope of reevaluation, application, or confirm actions. After successful confirm, candidate absence is normal: the read model projects final category/tags and final transaction values from the row's linked `FinancialTransaction`, while description review metadata remains in `rawData.review`. API ingestion detail remains TBD.

The parent status shown on the review page uses readiness language before import: `READY` means ready to import, and `PARTIALLY_READY` means the workflow needs review or has no valid rows to import. `COMPLETED` means confirm import finished and the review is read-only, even if a historical row later reads **Transaction deleted after import**. `PARTIALLY_COMPLETED` is reserved and should not normally appear in CSV v1. Inline row edit is intentionally limited to normalized review fields: transaction date, posting date, description, signed amount, currency, external reference, and notes. Amount and flow stay read-only because they are derived server-side from signed amount. Edit controls render only for `VALID` and `REJECTED` rows while the parent is `READY` or `PARTIALLY_READY`. `DISABLED` rows render Enable only and must be enabled before editing. Imported/deleted-after-import/skipped/failed rows do not render edit controls.

When a FILE workflow is `READY` with valid rows missing candidates, its single review page automatically calls `POST /api/transaction-ingestions/{id}/candidates/prepare` once for the current missing-row set, reloads the workflow read model, validates that every `VALID` row has a usable candidate, then calls `POST /api/transaction-ingestions/{id}/candidates/rule-preview`. It does not prepare candidates for invalid, rejected, or disabled rows, and it avoids preparation on every render. The preview response is transient and keyed by candidate id; it does not mutate candidate category/tags. User category/tag edits call the ingestion-scoped candidate classification PATCH endpoint. Suggestions are applied where they are displayed: **Apply suggested category** in the Category cell sends `scope=CATEGORY`; **Apply suggested tags** in the Tags cell sends `scope=TAGS`; **Confirm no suggestions** remains in expanded classification details. The toolbar keeps separate, always-visible batch commands for descriptions, categories, and tags; category/tag batch commands omit `candidateIds` and send their respective scope. All explicit category/tag applications use `FILL_EMPTY_ONLY`, preserve manual selections, reload candidate summaries, and reconcile only the scope just applied, so a category application leaves a tag suggestion visible and vice versa. A returned `SKIPPED` row is an independently skipped batch result, not a successful application; the UI retains its preview and shows a warning to review affected rows. Category and tag options are loaded through the owner-scoped `/api/categories/selectable` and `/api/tags/selectable` APIs. The Tags cell uses the shared `ProductTagSelector`: persisted selections are compact color-accented removable pills, while **Add tag** opens an active-only searchable picker. Current inactive historical tags remain selected and visibly labelled Inactive but cannot be assigned anew. Transient suggested categories/tags remain visibly separate from persisted current values in their own Category/Tags controls until an existing Apply or auto-apply action persists them. Matched rules, conflicts, and skipped outputs are shown in expanded classification details. Browser refresh reloads persisted candidate category/tag selections through the workflow row `candidate` summary.

The same toolbar exposes four scoped reevaluation actions: **Reevaluate descriptions**, **Reevaluate categories**, **Reevaluate tags**, and **Reevaluate all**. Its separate Apply group always renders **Apply all suggested descriptions**, **Apply all suggested categories**, and **Apply all suggested tags**, disabling each when that domain has no pending change. A preview is not automatically pending work: an identical normalized description or category is informational only, and tag application is pending only when `FILL_EMPTY_ONLY` would add at least one new tag. A valid prepared row also exposes **Reevaluate this row**. Above it, four local V1 settings default to descriptions/categories/tags auto-apply off and protect manual changes on. They are intentionally not persisted: reloading the page restores defaults. Enabling a scope after candidates are ready runs that automatic scope once; a later global or row reevaluation applies only enabled scopes and otherwise remains preview-only. A transient description result visibly offers **Apply suggested description** in the description cell only when it differs from the current normalized description; that per-row command is explicit consent and applies unprotected. **Apply all suggested descriptions** batches only rows for which a current transient change would be applied under the manual-protection setting, preserving protected `USER_EDIT` rows and their suggestion. Description requests use `apply` and `protectManualChanges`; category/tag automatic requests use `automatic`, `protectManualChanges`, and `CATEGORY`/`TAGS`/`ALL` scope. Protected manual values stay selected while the fresh recommendation remains visible; unprotected automation can replace/remove manual values. Automatic category/tag reconciliation never writes `rawData`. If the independent description/classification parts of Reevaluate all do not both succeed, the UI retains the successful domain result and reports partial failure. Confirm Import remains separate.

The queue offers non-persisted local visibility filters: **All**, **With suggestions**, **With errors**, and **Disabled**. They derive from the already loaded workflow rows and transient description/classification previews; the visible count is therefore an in-memory result, not another backend classification. Filters only affect presentation. Reevaluate, Apply all, and Confirm Import retain their original ingestion-wide request semantics and never iterate just the visible rows. The queue memoizes its visible-row derivation and does not issue category/tag catalog requests per row; it remains an in-memory review surface, so it does not add pagination or virtualization for unusually large imports.

Confirm Import opens an accessible, responsive confirmation dialog before any mutation. Opening or cancelling it never calls the backend. The dialog identifies the account, filename, current workflow status, and only existing received/valid/invalid/disabled counts; it deliberately does not predict transactions to create. Its explanation distinguishes transient rule previews from persisted review decisions, says that disabled rows are excluded, and makes the backend the final validator. Its one guarded submit calls `POST /api/transaction-ingestions/{id}/confirm` with no legacy `records`/category/tag payload after reloading and validating persisted candidates. The backend reads persisted `FILE_IMPORT` candidates as the pre-post source of truth, creates `FinancialTransaction` rows from candidate fields/category/tags, links rows to those final transactions, keeps `DISABLED` rows skipped, removes candidate tag associations and candidates atomically, and does not persist category/tag choices into `rawData`. A successful response updates the same review route with server-confirmed result counts and a general **View transactions** link; it never invents an account-scoped transaction query. A failed/uncertain request rechecks the workflow: a now-`COMPLETED` result is shown as complete, otherwise the modal clearly asks the user not to retry until state is clear. `PARTIALLY_READY`, `FAILED`, and exceptional `PARTIALLY_COMPLETED` states use recovery guidance without fabricating Retry/Resume actions. Completed rows remain read-only and render final classification from `FinancialTransaction`, not from a candidate.

The old `POST /api/transaction-ingestions/{id}/classification-preview` endpoint was removed in TC-4B, and Confirm Import no longer parses the old `records/categoryId/tagIds` request body. They are not part of the active TransactionIngestion workflow UI composition; UI code must use the candidate-backed commands and post `/confirm` without per-row classification payload.

Generated ingestion CRUD inspection routes remain available for direct inspection and debugging, but they are not the product workflow. `FileIngestion`, `IngestionRecord`, and `ApiIngestion` stay in the Entities menu with a compact Technical badge. Their list/detail pages show technical/debug context banners where applicable. Product mutation actions that would compete with a workflow are hidden: TransactionIngestion list/workflow detail do not show Edit; FileIngestion list/detail show read-only metadata and hide Create/Edit/Delete; IngestionRecord list/detail show read-only row/audit data and hide Create/Edit/Delete; ApiIngestion list/detail show View/read-only metadata only and hide Create/Edit/Delete. Direct generated write routes (`/file-ingestion/new`, `/file-ingestion/:id/edit`, `/file-ingestion/:id/delete`, `/ingestion-record/new`, `/ingestion-record/:id/edit`, `/ingestion-record/:id/delete`, `/api-ingestion/new`, `/api-ingestion/:id/edit`, `/api-ingestion/:id/delete`, and `/transaction-ingestion/:id/edit`) do not render generated create/edit/delete forms; they show a safe unavailable technical state and link back to the corresponding list. Routes remain available for deep links and debugging; no redirects are used.

### Temporary generated ingestion write surfaces

The following generated backend write endpoints remain temporarily behavior-compatible, but the generated frontend write routes now show the technical write-unavailable state instead of mounting generated forms/modals:

- `/transaction-ingestion/:id/edit` with `PUT /api/transaction-ingestions/{id}` and `PATCH /api/transaction-ingestions/{id}`;
- generated `POST /api/transaction-ingestions`;
- generated FileIngestion write routes with `POST /api/file-ingestions`, `PUT /api/file-ingestions/{id}`, and `PATCH /api/file-ingestions/{id}`;
- generated IngestionRecord write routes with `POST /api/ingestion-records`, `PUT /api/ingestion-records/{id}`, and `PATCH /api/ingestion-records/{id}`;
- generated ApiIngestion write routes with `POST /api/api-ingestions`, `PUT /api/api-ingestions/{id}`, and `PATCH /api/api-ingestions/{id}`.

Canonical ingestion product writes should use the TransactionIngestion workflow command endpoints. Generic reducer thunks and endpoint tests may remain while backend compatibility remains. A later backend hardening slice may reject or remove these generated write paths after no active product/debug consumer exists.

## Rules landing and navigation

`/rules` is the user-facing Rules landing within the authenticated product navigation. It uses the standard wide `ProductPage` composition with one `ProductPageHeader` and two responsive `ProductSection` cards: Description Rules and Classification Rules. Each card is a normal keyboard-accessible link-style action to its preserved manager route (`/description-normalization-rule` and `/transaction-rule` respectively); it does not combine the rule engines or expose generated entity terminology.

The main entity menu exposes one **Rules / Reglas** entry. The separate product-facing Description Normalization Rule and Transaction Rule menu entries are removed, while the intentionally technical TransactionRuleCondition entry remains available with its Technical badge. The same authenticated entity menu is used for ordinary users and administrators, so this change introduces no new authorization path.

The Description Rules manager now provides both arrow controls and keyboard-accessible `@dnd-kit` drag-and-drop ordering. It operates against the complete authenticated-owner collection and restores the confirmed order after a failed persistence attempt. Transaction Rule manager ordering polish remains separate.

## DescriptionNormalizationRule UI composition

Description normalization rules follow the same parent-centered composition pattern as TransactionRule:

- list page is a wide product manager with ordered rule cards, active/inactive status, readable ALL/ANY condition summaries, result summaries, a back-to-Rules action, and owner-safe reorder controls;
- product create uses the configured parent + local conditions command and does not create an empty draft;
- standalone create and edit share one configured form: **Identity → Matching conditions → Results → Configuration summary → Status**, with local condition add/edit/remove and a live human-readable configuration summary;
- configured create and edit use `POST`, `GET`, and `PUT /api/description-normalization-rules[/id]/configured`; edit loads every ordered condition and saves the complete replacement atomically while priority stays server-managed;
- save failures preserve the local draft and show the backend validation message; progressive client validation prevents blank identity/result/condition values and a missing condition for either active or inactive rules after Save is attempted;
- detail is a read-only product view: Matching exposes every condition and grouping semantics, Result shows the replacement, and Behavior explains FILE-import scope, first-match-wins order, and non-retroactivity; it links only to configured edit/delete actions;
- condition forms have no field selector because every condition evaluates original imported description.

The detail no longer embeds child-condition CRUD. Those generated endpoints continue as technical compatibility surfaces, but product editing always uses the configured aggregate editor. Delete confirmation names the future-only and precedence effects rather than exposing a raw entity warning.

The product route is:

```text
/description-normalization-rule
```

Standalone generated/debug condition screens are not the product flow for managing conditions.

### Transaction Ingestion contextual rule editor

The unified FILE-ingestion review opens the existing full configured rule editors inside a modal rather than navigating to generated entity screens. The global **New Description Rule** and **New Classification Rule** actions start blank product forms. An eligible row's **Create rule** menu offers the same forms with editable review-only prefills.

- Normalization-rule row prefill: condition value = immutable original description; result = current normalized/manual description. If that original value is unavailable, the modal shows an honest unavailable-source state rather than substituting a current value.
- Transaction-rule row prefill: `DESCRIPTION CONTAINS` current persisted candidate description and `FLOW EQUALS` current candidate flow; persisted candidate category/tags are outputs. Account context is informational and never becomes an automatic condition; transient suggestions are never persisted as outputs. Inactive row outputs remain visible for reconciliation but cannot be saved as new rule outputs.
- The modal identifies the selected row, has an accessible labelled heading, uses the full engine-specific configured editor, and keeps catalog-dependent save actions disabled while their outputs load.
- Save only writes the formal rule. Save + reevaluate this row/all runs the existing scoped reevaluation command only after save succeeds. A row action targets its record/candidate; an all action targets eligible rows of the same ingestion. The UI labels classification reevaluation as suggestions unless the existing local auto-apply option is on. None of these actions confirms import or writes category/tags to `rawData`; a saved-rule/failed-reevaluation outcome is shown as partial success.

## TransactionRule generated technical/debug surfaces

## Transaction Batch workspace

`/transaction-batches` is the sole product entry for preparing CSV and manual movements together. It has a list of owned OPEN workspaces, a focused account-selection creation page, and one review workspace per batch. It intentionally does not embed the retired FILE-ingestion or transaction-ingestion screens. CSV rows are transient `FILE_IMPORT` TransactionCandidates inside that workspace; this source/origin label does not reintroduce the retired FileIngestion/IngestionRecord model.

The review workspace keeps add-manual and append-CSV actions separate from the review queue. Its desktop queue uses six compact review columns; narrow layouts become labelled cards without changing candidate controls or command scope. Categories are an editable, color-accented selector and tags reuse `ProductTagSelector`; neither gets a redundant read-only identity above it. Rule previews are secondary and only receive an Apply action when a saved value would actually change. Local filters only affect presentation of the fully loaded workspace.

Publishing uses a confirmation dialog, refuses locally known invalid rows, and routes a successful bounded receipt to the normal transaction list. If the browser loses the first publish response, reopening the batch attempts the bounded receipt read rather than creating a second publication. CSV append preserves its operation key after a failed request so retrying the same intent remains idempotent.

TR-3 clarifies the TransactionRule UI split:

- `/transaction-rule/new` is the product create route and uses `POST /api/transaction-rules/configured`.
- `/transaction-rule/:id` is the read-only product detail and uses `GET /api/transaction-rules/{id}/configured`.
- `/transaction-rule/:id/edit` is the product edit route and uses `GET/PUT /api/transaction-rules/{id}/configured`.
- Standalone TransactionRuleCondition routes remain available only for technical/debug/direct maintenance and show a Technical/debug banner.
- Product TransactionRule detail has no child condition editor; the standalone TransactionRuleCondition route remains the technical/debug surface.
- No UI text should recommend the old “create empty parent, then add conditions later” product flow.
- No ingestion category/tag review work was added as part of this TransactionRule branch.
- UserPreference remains deferred.

### Inactive account selection policy (ACC-UX-3A)

Manual transaction drafts, new file ingestions, and TransactionRule `ACCOUNT` condition forms use the shared selectable-account read model, not the generic account entity list. New choices are active and current-owner scoped. When editing an existing historical reference, its inactive account remains shown as `… · Inactive`; it is not a general inactive-account picker. Budget UI remains deferred to BUD-1 and FinancialSubscription is intentionally unchanged.

## CAT-UX-2 — Category product UI

`/category`, `/category/new`, `/category/:id`, and `/category/:id/edit` are product catalog screens. They show category name, optional description, translated type, hierarchy, active state, and color without exposing identifiers, ownership, timestamps, raw hex values, or relationship dumps. The page loads the current catalog once and derives readable paths such as `Expenses › Transport › Ride share` plus immediate children; it does not introduce a tree endpoint or one request per ancestor.

Create offers name, description, type, an optional type-compatible parent, color, and active state. Parent options use hierarchy labels. Edit preserves the current parent as read-only with an explanation because parent changes are not allowed after creation. A root category can attempt a type change; the backend remains authoritative when existing usage makes it unavailable. Inactive categories use an explicit status badge and historical-only explanation. Delete names the category, pre-blocks categories with children, and maps protected active-workflow references to product-safe wording.

## CAT-UX-3 — Tag product UI

`/tag`, `/tag/new`, `/tag/:id`, and `/tag/:id/edit` are product catalog screens. List rows combine the optional description with the tag name, render its configured color as a visual identity chip, and use translated active/inactive badges; they do not expose ids, ownership, timestamps, raw hex values, or relationship dumps. The list starts sorted by name, which is the stable product order for a flat catalog.

Tag create/edit exposes only name, description, color, and active state. Color is configured through a browser-native picker beside its editable `#RRGGBB` field; no additional preview box, alternate color format, or schema is introduced. Inactive tags stay visible and editable in this catalog, while the product explanation makes clear that they remain historical-only until reactivated. Delete names the tag, explains permanence and active-workflow blocking, and maps a protected candidate reference to product-safe copy rather than a raw backend error.

### CAT-UX-3.5 — Category and Tag visual composition

The catalog screens use a shared, deliberately compact composition: a title/header with primary creation or edit action, a readable sortable list of product rows, and an overflow menu for secondary View/Delete actions. The name is the row's primary navigation; Edit remains a compact visible action. This replaces the generated three-button CRUD action groups without removing any route or backend capability.

Detail pages use labelled product sections rather than a raw entity-property dump. Category details group description, classification, hierarchy, and immediate subcategories; Tag details group description. Their configured color is a subtle decorative accent on the header, not a standalone Appearance card or raw hex field. Forms keep a narrower reading width and divide inputs into Basic information, Classification where applicable, Color, and Status. Both use the same native color-picker + editable hex control without a redundant preview box, and active status uses a form switch with historical-only guidance.

The stored Category `icon` field is intentionally absent from normal product create/edit/list/detail presentation: there is no icon registry or product rendering contract yet, so exposing a free-text icon field would create a misleading setting. The field and its backend compatibility remain untouched. The composition is responsive: headers and controls stack on narrow screens while cards and form sections retain the same hierarchy.

### CAT-UX-3.5B — hierarchy, color, and compact status

The Category catalog is rendered from the current catalog payload in parent-before-child order. Category color is a card accent in the list and a subtle header accent in detail; raw `#RRGGBB` is edit-time configuration only.

Tags use their configured color as a readable name chip in the list and a subtle header accent in detail. Color never stands alone as meaning: category/tag names, type, hierarchy, and status remain visible text. Create/edit keeps the native picker and editable hex value together in one labelled color-control group without a separate preview.

Create/edit status is compact metadata directly below the title: translated active/inactive badge, accessible switch, and focusable/hoverable help icon. The historical-only explanation lives in that tooltip rather than a full-width Status card or fixed inactive warning. Detail pages retain the compact status badge and help affordance.

### CAT-UX-3.5G — final control and Tag-list polish

The shared status-help affordance uses a body-mounted Popper tooltip: it prefers the lower-right side of the help control, has an offset, stays inside the viewport, and flips to another available side when necessary. It opens on hover and keyboard focus, so it does not reserve layout space or cover its own control with a fixed-position hint.

The Color field remains one semantic fieldset and one aligned control row: a fixed-size native picker sits beside the editable `#RRGGBB` input, sharing the same effective height and wrapping only on small widths. Tag list cards use a compact primary row that keeps the tinted color-accented name chip and textual Active/Inactive badge together; the optional description is secondary content below, while Edit and the overflow menu stay on the right.

### CAT-UX-3.5C — Category hierarchy view modes

The Category list has two local, non-persisted views beside its existing sort control. **Nested** is the default: it groups each parent with descendants, indents by arbitrary current depth, and reserves a fixed disclosure column on every row. Parents render an accessible `aria-expanded` caret in that slot; leaves retain an empty, non-interactive placeholder so names remain aligned at the same depth. The caret starts open and hides every descendant when collapsed. It deliberately has no ASCII branch symbols or hierarchy-only color treatment.

**Flat** renders every category at the same visual level using the current sorted catalog order. A nested category keeps its name as the primary link and shows a secondary breadcrumb with the typographic `›` separator, for example `Expenses › Transport › Ride share`. Every ancestor in that breadcrumb links to its canonical Category detail; the current category remains plain text to avoid a redundant self-link. Ancestor links inherit surrounding text color, receive a subtle emphasis and hover/focus underline, and never compete with the row's primary name link. Category detail completes the inverse navigation: each immediate child name links to that child’s canonical detail route while its status badge remains a separate, non-navigating element. Nested mode sorts within sibling groups while retaining descendants below their parent; flat mode uses the full catalog order returned for the selected sort.

## Internal Transfers product page

`/internal-transfers` is the product surface for moving money between the user's own accounts. It uses responsive cards rather than the generated raw-ID table: each card makes its transfer date, source account and description, destination account and description, one shared amount, and optional transfer note scannable. Desktop presents the legs across an arrow; mobile stacks the same two leg cards without horizontal overflow. The list has account and date-range filters and server-side sorting, newest transaction date first.

The product routes are `/internal-transfers`, `/internal-transfers/new`, `/internal-transfers/:id`, and `/internal-transfers/:id/edit`; the old singular route redirects to the list. Create and edit are focused forms. Create has one positive amount, a shared transaction date, optional per-leg posting dates, and no flow selector or rule/DNR controls. Edit keeps the two accounts read-only: changing accounts means unlinking and recreating the transfer. Detail shows both persisted legs, their origin, classification where present, references/notes, and links to the two underlying transactions. Historical inactive accounts and a historical mismatch between leg dates remain readable with an explicit warning rather than being auto-repaired.

Unlink uses a destructive confirmation that states that both financial transactions are kept. The list and detail action menus expose product language only; raw `InternalTransfer` CRUD IDs and selectors are not a product navigation pattern. The underlying typed command/read API remains the source of truth: direct create/update/unlink do not evaluate normalization or transaction rules, and linking existing legs never overwrites their classifications or provenance.

## Internal transfers from manual transactions

The manual transaction draft keeps **Internal transfer** off by default. Enabling it exposes two mutually exclusive choices: create a derived counterpart or link one existing eligible transaction. The create form shows the counterparty account, posting date, description, reference, and notes; transaction date, currency, amount, and opposite flow are explicitly derived from the current draft. The link search is server-scoped and displays only a compact readable transaction summary, never raw identifiers. A restored selected counterpart that is no longer eligible remains visible with an unavailable explanation and blocks posting until the user chooses a valid alternative.

Posted transaction detail displays a compact transfer relation section with links to the aggregate and counterpart. A linked transaction's account, transaction date, amount, and flow are structurally read-only; description, posting date, category, tags, external reference, and notes remain editable. The same section offers edit-transfer and unlink actions. An unlinked final transaction instead offers a deliberate **Mark as internal transfer** affordance for creating a counterpart or linking an eligible existing transaction. These controls wrap existing transfer commands and do not duplicate a financial-transaction leg in the client.
