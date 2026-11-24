# BUDGET-6C-A — Notification preferences

## Product behavior

Budget delivery preferences are optional and mutable. They are deliberately separate from immutable
`BudgetRevision` records, so changing a delivery choice never changes a budget's amount, scope, calendar,
financial result, or Analytics query.

- Percentage alerts start **disabled**. The creation screen suggests 50%, 80%, and 100%, but suggestions are not
  selected, stored, or evaluated until the user explicitly enables alerts and saves.
- Expense budgets use **Alertas de gasto**; income budgets use **Alertas de ingresos**. Thresholds are percentages
  of the limit or target respectively.
- **Resumen al cerrar el período** is independent from percentage alerts. Saving either control never generates a
  threshold claim, outbox item, notification, or historical financial recalculation.
- A finished budget is readable but its preferences cannot be changed. Paused budgets remain editable.

## Creation and detail contracts

`POST /api/budgets` accepts the optional `notificationPreferences` object:

```json
{
  "percentageAlertsEnabled": true,
  "thresholdPercentages": [50, 80, 100],
  "periodCloseEnabled": true
}
```

Omitting it creates no preference row and leaves both delivery capabilities disabled. If present, it is validated
and written in the same transaction as the Budget, its initial revision, schedule, and timezone assignment. The
normalized values are part of the creation fingerprint, so retrying the same request is idempotent while reusing a
key with different preferences remains a conflict.

The existing owner-scoped endpoints remain the detail contract:

- `GET /api/budgets/{id}/notification-preferences`
- `PUT /api/budgets/{id}/notification-preferences`

`PUT` is a full replacement and requires the returned `expectedVersion` for an already configured preference. A
409 conflict is not overwritten by the browser: the client reloads the authoritative version and leaves its draft
for an explicit review and retry. GET remains available after completion; PUT is rejected for an effectively
completed budget by the server as well as rendered read-only by React.

## Immediate period-close semantics

`budget_notification_preference.period_close_enabled_since` is the server-side UTC instant at which the **current
continuous enabled episode** began.

- OFF → ON stores the current clock instant immediately. A period which closes after that instant is eligible for a
  future producer.
- ON → ON and threshold-only edits retain the instant.
- ON → OFF clears it immediately. A closing whose boundary has passed but has not yet been captured is therefore
  cancelled.
- A later OFF → ON creates a new instant. A boundary before the new instant cannot be revived.

The future BUDGET-6C-C closing producer must serialize with the existing notification mutation lock order
`owner → preference`, check that closing is currently enabled, require
`periodCloseEnabledSince <= logicalClosingBoundary`, and write its immutable snapshot/outbox record in the same
transaction. The durable outbox insertion is the capture boundary: after it commits, disabling a preference never
deletes or mutates that record.

A per-period assignment history is unnecessary for the approved immediate policy: the one active epoch captures
both activation and deactivation/re-enablement behavior without projecting a period start. It survives restarts and
unrelated edits because it is persisted independently of `updatedAt`.

## Migration and validation

The forward-only Liquibase migration adds the nullable activation epoch. Existing enabled development rows receive
the migration execution timestamp. Their historical activation instant is unknowable, so this safe bootstrap
prevents retroactive closing events while allowing future boundaries. Disabled rows stay null.

The server remains the authority for validation: up to 20 unique positive thresholds, at most four fractional
digits, and at most 15 digits of precision. The React editor mirrors these limits for helpful feedback but never
coerces a value or substitutes for server validation.

## UX

Creation includes a compact optional-notification section and review summary. Detail loads the authoritative
preference, offers independent controls, custom add/edit/remove threshold actions, save/cancel, loading/error
states, and English/Spanish text. Finished budgets show their saved values with a clear read-only explanation.

## Deferred work and limitations

This slice only persists eligibility. It does not implement a closing scheduler, backlog recovery, `PERIOD_CLOSED`
outbox/notification presentation, timezone-profile UI, email, push, or external delivery. Those belong to
BUDGET-6C-B/C/D. Existing BUDGET-6A threshold capture and BUDGET-6B notification history remain unchanged.

## Acceptance evidence

Focused Java and React coverage accompanies this change for creation defaults/configuration, normalized thresholds,
activation transitions, persistence payloads, save/cancel, and versioned updates. The real PostgreSQL REST
regression also verifies that replacing an unchanged threshold set does not violate the unique threshold constraint.

The following evidence was executed in the `implement-budget` checkout:

- `./mvnw -ntp --batch-mode verify`: succeeded. The reports written by that run contain 646 Surefire tests and
  1,365 Failsafe tests, with zero errors, failures, or skips in each report set.
- `npm run jest -- --silent`: succeeded, 67 suites and 725 tests passed.
- Focused real PostgreSQL `BudgetResourceIT`: succeeded, 13 tests passed. It used Testcontainers rather than a
  development database.
- Focused Java preference/creation tests: succeeded, 12 tests passed.
- The real Cypress creation/edit/activation flow was repeated in Chrome against a disposable PostgreSQL database and
  a Spring Boot process on port 18080: 1/1 passed, with no mocked Budget or preference API responses.
- `npm run lint -- --no-fix`, the development web build, `npm run webapp:prod`, and
  `./mvnw -ntp --batch-mode -DskipTests -Pprod package` succeeded. ESLint has no errors; the only warnings are the
  three pre-existing complexity warnings in Analytics Explorer and the pre-existing Budget creation component.

### Completed browser acceptance

The missing real browser flows were executed on 2026-11-18 using Chrome 157, Cypress 14.3.2, Node 22.14.0, a
disposable PostgreSQL 17 container on port 15432, and a Spring Boot process on port 18080 with the `e2e` profile.
The fixed server clock was restarted from 2026-03-15 to 2026-03-16 between phases; neither the normal development
ports nor its database were used.

- **Scheduled future finish:** 1/1 passed. A `FINISH` command effective on the following daily period left the
  Budget editable before that boundary, including a real preference PUT.
- **Effective paused/finished states:** 1/1 passed. Once the clock crossed the boundary, the paused Budget saved,
  reloaded, and retained changed preferences. The finished Budget rendered the saved values read-only, exposed no
  Save action, and rejected a direct authenticated PUT with the existing `400 INVALID_PAYLOAD` contract without
  changing persisted values.
- **Cross-user ownership:** 1/1 passed. A second authenticated user received the established owner-hidden
  `400 INVALID_PAYLOAD` response on GET and PUT, could not view the owner UI, and left the owner preference and
  activation epoch unchanged.

The prior `SIGABRT` was an environment failure, not an application or browser assertion. Inside the restricted
sandbox Cypress could not update
`~/Library/Caches/Cypress/14.3.2/binary_state.json` (`EPERM`), and sandbox networking could not reach the
host-isolated application. `npx cypress verify` from the supported host environment verified the same installed
binary successfully, and every targeted Chrome run completed normally. No dependency, lockfile, browser setting,
or product configuration was changed.

### Test-count reconciliation

The BUDGET-6B document records a historical 648 Surefire / 1,364 Failsafe run. The later BUDGET-6C-A `verify`
reports contain 646 Surefire / 1,365 Failsafe tests. This is not a test-discovery or source-removal regression:
`pom.xml` has no Surefire/Failsafe change, and comparison with `7884bcd` finds no removed Java test method and four
added methods (three unit tests and one PostgreSQL integration test). The committed BUDGET-6B documentation does
not retain its original XML reports, so the old two-test Surefire difference cannot be attributed more precisely;
it is evidence from a non-identical historical execution/report set, not missing BUDGET-6C-A coverage.
