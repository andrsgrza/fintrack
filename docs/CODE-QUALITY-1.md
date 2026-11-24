# CODE-QUALITY-1 — Frontend complexity refactor

## Purpose

This change removes the four existing ESLint `complexity` warnings by separating existing UI responsibilities. It does not alter FINTRACK financial calculations, API contracts, routes, persisted data, text, accessibility selectors, or product behavior.

## Refactoring map

| File                          | Extracted responsibility                                                                        | Preserved behavior                                                                                                                                                     |
| ----------------------------- | ----------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `analytics-explorer.tsx`      | Unsaved-widget cancellation and browser unload warning                                          | The same baseline comparison, confirmation copy, and navigation target remain in use.                                                                                  |
| `analytics-results-panel.tsx` | Heading, availability/warning display, empty state, facets, and compact detail-table disclosure | The same semantic rows, visualization compatibility fallback, warning codes, currency separation, chart inputs, table behavior, and `data-cy` selectors are preserved. |
| `budget-create.tsx`           | Calendar state and the calendar editor/preview section                                          | The same controls, calendar draft, validation, cancellation, preview request, recurrence rules, and create payload remain authoritative.                               |
| `budget-detail.tsx`           | Identity, period chooser, period summary/configuration, and history presentation                | The same data requests, period navigation, lifecycle actions, historical revision/scope display, and pagination remain in the parent lifecycle flow.                   |

## Behavioral boundaries

- No backend, persistence, endpoint, database, configuration, port, or financial-semantic change is included.
- `SemanticQueryDefinition`, `VisualizationContext`, execution results, saved visualization behavior, and result-table rows remain unchanged.
- Budget creation still sends the same `createBudget` payload, including recurrence, scope, and notification preferences.
- Budget detail still resolves the selected period from the URL and uses the same history/revision APIs.

## Validation record

| Check                 | Command / evidence                                                                                                               | Result                                                                                                |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| ESLint                | `npm run lint -- --no-fix`                                                                                                       | Pass: 0 errors, 0 warnings. The four `complexity` warnings are gone.                                  |
| Focused React         | `npm run jest -- analytics-explorer.spec.tsx budget-create.spec.tsx budget-detail.spec.tsx`                                      | Pass: 3 suites, 47 tests.                                                                             |
| Full React            | `npm run jest`                                                                                                                   | Pass: 70 suites, 768 tests.                                                                           |
| Development web build | `npm run webapp:build:dev`                                                                                                       | Pass. This non-server build was used so normal development ports remained untouched.                  |
| Production web build  | `npm run webapp:prod`                                                                                                            | Pass. Webpack retained its three existing bundle-size notices; they are not ESLint warnings.          |
| Maven verification    | `./mvnw -ntp --batch-mode -Dskip.npm=true verify`                                                                                | Pass: Surefire 660 tests and Failsafe 1,379 tests; all have 0 failures, 0 errors, and 0 skipped.      |
| Production package    | `./mvnw -ntp --batch-mode -DskipTests -Pprod package`                                                                            | Pass.                                                                                                 |
| Isolated Cypress      | Budget overview, Metric Explorer, and personal-dashboard widget journeys against temporary Spring `18082` and PostgreSQL `15434` | Pass: 12 tests, 0 failures, 0 pending, 0 skipped. The temporary processes were removed after the run. |

The Cypress evidence exercised actual browser → REST → Spring → PostgreSQL paths: budget creation/review, calendar modes, period navigation/history and revisions; Visual ↔ Consulta escrita AST parity and result presentation; and saved-widget execution, editing, reload and canonical grouping preservation. The existing selected dashboard-widget spec does not contain a separate cancellation assertion; it was not represented as one.

Representative rendered screenshots were reviewed at:

- `target/cypress/screenshots/entity/budget-overview-real.cy.ts/budget-ux-3-create-review-desktop.png`
- `target/cypress/screenshots/analytics/metric-explorer-real.cy.ts/metric-explorer-visual-written-parity-desktop.png`
- `target/cypress/screenshots/analytics/personal-dashboard-widgets-real.cy.ts/personal-dashboard-category-month-line-real-desktop.png`
