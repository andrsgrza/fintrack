# UI Composition Conventions

## Default

Generated CRUD pages are the default. Preserve them when they match the domain workflow.

## Extend, do not recreate

When a generated page is mostly correct, extend it with small embedded sections instead of creating a new page.

## Embedded child sections

A child entity may be embedded in a parent create/edit/detail page when:

- the child is a natural part of the parent concept;
- the child does not make sense without the parent;
- the user expects to manage both together.

When embedded, the child section must not show or edit the parent relationship field.

## Standalone child CRUD

Standalone CRUD pages may still exist for child entities. They are useful for admin/debug/direct maintenance, but they are not always the main product flow.

## Related lists

Large or historical relationships should not be edited from the parent edit page. They may appear as read-only sections in the parent detail page.

## Backend orchestration

Start with frontend orchestration only when the flow is simple and failure is acceptable.

Use a backend command endpoint when the operation must be atomic or when frontend orchestration would leak domain logic.

## Components

Embedded sections must be extracted into components.

Naming:

- `*form-section.tsx` for embedded editable child sections.
- `*view-section.tsx` for embedded read-only child sections.
- `*collection-editor.tsx` for embedded editable child collections.
- `*related-list.tsx` for read-only related lists.

## Implemented cases

### FinancialAccount + CreditAccountDetails

`CREDIT_CARD` FinancialAccount create/edit embeds CreditAccountDetails fields as a child form section, without exposing the `account` relationship selector. FinancialAccount detail embeds a read-only CreditAccountDetails view section.

This is frontend orchestration today: FinancialAccount is saved first, then CreditAccountDetails is created or updated for the saved account. Atomic backend command endpoint remains deferred.

Standalone CreditAccountDetails CRUD remains available for admin/debug/direct maintenance.

### TransactionRule + TransactionRuleCondition

TransactionRule detail/edit pages show a read-only related list of TransactionRuleCondition rows. The list is loaded from `GET /api/transaction-rules/{id}/conditions`, not by fetching all conditions in the frontend.

This is a parent-centered UX foundation, not a full embedded collection editor. Users can navigate to add/view/edit conditions through the existing standalone TransactionRuleCondition CRUD. The full inline rule builder and rule execution engine remain deferred.

TransactionRuleCondition create can be opened with `transactionRuleId` in the query string to preselect the parent. TransactionRuleCondition edit shows the parent as read-only because the backend treats `transactionRule` as immutable after create.
