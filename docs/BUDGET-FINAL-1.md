# BUDGET-FINAL-1 — Monetary precision and progress accessibility

## Scope

This focused remediation closes three findings from BUDGET-FINAL-0 without changing Budget financial evaluation, Analytics V2, scheduling, notifications, or the Budget calendar model:

1. exact Budget-target precision and transport;
2. valid accessible progress ranges above 100%; and
3. the obsolete timezone note in BUDGET-6A.

## Approved monetary contract

A Budget target is a strictly positive, plain decimal value with at most two fractional digits, independent of its currency. It must fit PostgreSQL `DECIMAL(21,2)`: at most 19 integer digits and two fractional digits. Therefore `100`, `100.5`, `100.50`, and `9999999999999999999.99` are valid, while `0`, negative values, malformed text, scientific notation, `0.001`, `1.005`, `100.999`, `100.000`, and `10000000000000000000.00` are rejected. Invalid input is never rounded or truncated.

The existing `budget_revision.target_amount` column and JPA mapping already use `DECIMAL(21,2)` / precision 21 and scale 2, so this correction does not need a schema migration.

## Backend and API contract

`BudgetTargetAmountValidator` is the single Budget-target guard. Budget creation and revision scheduling both invoke it before persistence, so a rejected target cannot create or alter a Budget, revision, lifecycle state, or notification data. It checks positivity, supplied scale, integer-digit capacity, and total precision using `BigDecimal`; it does not strip zeros or apply a rounding mode.

Budget-target JSON uses an exact Budget-scoped decimal-string contract. Requests accept a plain decimal string (and retain compatibility with an exact JSON numeric literal), but Budget target and evaluated monetary values returned by Budget endpoints are strings. For example:

```json
{ "targetAmount": "100.50" }
```

This is intentionally not a global `BigDecimal` serialization change. It lets clients preserve a value such as `9999999999999999999.99` without JavaScript binary-number loss, while Java remains the authority that parses and validates the value.

## Frontend behavior

The create and revision forms retain target values as text from input through the API payload. They validate plain syntax, positivity, scale, and capacity before submitting; the server repeats the same protection. Amount formatting uses a decimal-safe, `BigInt`-based formatter for the integer portion, so large values render with grouping and never scientific notation. Existing historical revisions, detail views, overview cards, and notification snapshots consume the same string representation.

## Progress accessibility

The progress fill remains visually bounded to 0–100%, but the visible percentage and `aria-valuetext` preserve the authoritative actual value. The progressbar exposes a valid `aria-valuemin=0`, `aria-valuemax=100`, and bounded `aria-valuenow`; 120% or 250% is therefore not misrepresented as an invalid ARIA range. The correction applies to both expense limits and income goals. Unavailable evaluations do not render a numeric progressbar.

## Timezone documentation

The narrow BUDGET-6A note now points to the authoritative BUDGET-6C-B behavior: browser timezone detection is only a suggestion, persistence requires explicit user confirmation, an existing confirmed profile timezone is not overwritten, and immutable Budget-effective timezone assignments remain authoritative. Profile timezone changes take effect at the next applicable Budget period.

## Focused evidence

The targeted Java tests cover accepted and rejected scales, maximum precision, creation rollback, revision rollback, and notification regressions. `BudgetResourceIT` exercises the exact REST/PostgreSQL round trip, including preserved trailing zeros and rejected overflow values. React tests cover textual input, payloads, formatting, localized errors, and accessible progress at 0%, 50%, 100%, 120%, 250%, unavailable, expense, and income states. The final acceptance report records the exact commands and any environment-specific Cypress evidence.

## Compatibility and limitations

Budget endpoint consumers must treat Budget monetary response fields as decimal strings, not JavaScript numbers. Existing request clients that send an exact JSON number continue to be accepted, but new clients should send strings. The two-decimal target rule is a Budget product rule only; financial transaction precision and Analytics calculations are unchanged.

BUDGET-FINAL-2 remains responsible for the broader integrated cross-feature acceptance; it is deliberately outside this focused correction.
