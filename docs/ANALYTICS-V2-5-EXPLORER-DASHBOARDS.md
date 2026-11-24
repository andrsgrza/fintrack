# Analytics V2 — Metric Explorer and dashboards

**Current-state document — 2026-11-23.** This document records the implementation after the
semantic runtime described in [ANALYTICS-REDESIGN-4.md](ANALYTICS-REDESIGN-4.md). It supersedes
older “V2.5 pending” notes. The V2.1–V2.4 documents remain the design and regression history for
their respective slices.

## Changes reconciled

This document reconciles the implementation commits after the last prior Analytics document:

- `fa2ea53` — accurate empty-period semantics;
- `b4eb1e4` — understandable financial calculation shortcuts;
- `ef84344` — Explorer browser acceptance coverage;
- `df1fa5d` — recommended dashboards and shared result presentation;
- `3fa1bd9` — personal dashboard persistence, gallery, and template copy;
- `d8fe9c7` — Metric Explorer-to-widget save/edit workflow; and
- `58587eb` — ordered grouping UX, local temporal overrides, and widget-save compatibility;
- `67a2ab2` — complete personal-widget lifecycle and idempotent duplication;
- `38a9837` — one default-dashboard preference supporting both official and personal dashboards;
- `27fb7b2` — one visualization-compatibility contract at authoring, rendering, save, and persistence boundaries; and
- `9c1b5bf` — shared detailed and pivot result tables.

## One canonical analytical contract

Every analysis uses one `SemanticQueryDefinition`: a governed dataset, projections, predicates,
ordered `groupBy`, `orderBy`, and a bounded limit. It has no dashboard id, owner id, account list,
currency list, or date range.

`VisualizationContext` is independent. It supplies the period, selected accounts, and currencies
at execution time. A personal dashboard persists one default context; a widget persists its
canonical query definition and presentation settings. Neither object stores calculated financial
results. This separation applies equally to the visual builder, the written-query editor,
recommended dashboards, and personal widgets.

The two Explorer modes have semantic parity:

```text
Visual builder <-> SemanticQueryDefinition <-> Consulta escrita
```

The backend parses, formats, validates, and executes the same AST. It remains a controlled
financial language: arbitrary SQL/JPA, physical joins, formulas, and unsupported boolean logic
are not executable.

## Metric Explorer

The visual builder starts from understandable financial choices, without replacing the semantic
catalog with named-metric limits. For economic expenses and income it offers shortcuts such as
**Total gastado/ingresado**, **Número de gastos/ingresos**, and **Gasto/Ingreso promedio**. They
generate the existing `SUM(amount)`, `COUNT(transaction)`, and `AVG(amount)` projections. The
advanced editor still exposes every catalog-supported field and aggregation, including multiple
compatible projections.

The result availability policy is financial rather than cosmetic:

- a valid temporal bucket with no matching `SUM` or `COUNT` observations renders a recorded `0`;
- `AVG` without observations is `NO_DATA`, never a fabricated zero;
- historical balances and debt retain their authoritative availability (`UNAVAILABLE`, `PARTIAL`,
  or `NOT_APPLICABLE`) and are never zero-filled;
- linked internal-transfer legs remain excluded from economic income and expense; and
- monetary rows stay partitioned by currency. FINTRACK does not add MXN and USD without an
  explicit FX policy.

The charts keep true temporal spacing. A missing result is a gap, not a zero or a line segment
invented by the browser. A returned zero remains distinguishable from an absent period in the
chart, tooltip, and result table.

## Ordered grouping and charts

`groupBy` is ordered and its order is preserved end-to-end. In a two-dimensional chart, the first
grouping is the X-axis and the second grouping creates series. Therefore these are distinct but
both valid queries:

```text
ROOT_CATEGORY, TRANSACTION_MONTH  -> categories on X; months as series
TRANSACTION_MONTH, ROOT_CATEGORY  -> months on X; categories as series
```

The UI expresses this as **Agrupar por** and optional **Dividir por**, with contextual choices for
time and category hierarchy. When a grouping is replaced, only incompatible `ORDER BY` entries are
reconciled; remaining projection ordering and the requested group order are retained. The API
continues to reject duplicate/invalid order fields.

`LINE` is valid whenever an eligible temporal grouping is present, regardless of whether that
grouping is first or second. The Explorer eligibility check, chart renderer, save dialog, REST
command service, and persisted-widget validation all apply that same rule. Other financial and
unit/currency restrictions are still enforced; this is not a validation bypass.

For temporal dashboard widgets, a local **interval** control can run a compatible day/week/month/
year query in memory. It changes only the effective query for that render. It does not mutate the
saved AST, the dashboard `VisualizationContext`, or the persisted widget; reload restores the
widget's saved default interval.

## Recommended and personal dashboards

Three recommended dashboards are defined in code from the live semantic catalog:

1. **Resumen financiero** — registered position, economic income, expenses, net flow, and trends.
2. **Análisis de movimientos** — economic movement, categories/hierarchy, tags, institutions, and
   internal transfers.
3. **Cuentas y patrimonio registrado** — as-of account position, historical balances, credit debt,
   limits, available credit, and utilization.

Recommended dashboards are product templates, not user-owned database rows. Copying one creates a
fully independent `PersonalDashboard`; editing its widgets never changes the source template.

The personal-dashboard command API is owner-scoped and deliberately is not generated entity CRUD:

- `GET/POST /api/personal-dashboards`
- `POST /api/personal-dashboards/from-template`
- `GET/PUT/DELETE /api/personal-dashboards/{dashboardId}`, plus `PATCH /{dashboardId}/metadata` and `POST /{dashboardId}/duplicate`
- `GET/PUT /api/personal-dashboards/default`
- `POST /api/personal-dashboards/{dashboardId}/widgets`
- `PUT/DELETE /api/personal-dashboards/{dashboardId}/widgets/{widgetId}`, plus `POST /{widgetId}/duplicate`
- `PUT /api/personal-dashboards/{dashboardId}/layout`

The aggregate limits a user to 20 dashboards and a dashboard to 16 widgets. A root `@Version`
protects metadata, layout, and widget mutations together. Create/template-copy/add-widget commands
use owner-scoped idempotency receipts. A widget stores a validated canonical semantic definition,
visualization type/options, layout width, position, and visibility — never a duplicate executor or
financial data cache.

There is exactly one default-dashboard preference per owner. It can name either an owned personal
dashboard or one of the three stable official template identifiers. A new user falls back to
**Resumen financiero**; selecting an official template does not create a copy. Creating, copying,
or duplicating a dashboard does not silently replace the default. If the selected personal
dashboard is deleted, the preference safely falls back to the official financial overview.

Personal widgets are independent children of their dashboard. Their overflow menu supports
metadata editing, semantic-query editing through Metric Explorer, duplication, and confirmed
deletion; hide/show remains a layout operation. These commands preserve owner isolation, the
dashboard version, and the original widget's identity. Official widgets remain immutable.

The shared result adapter serves both Explorer and dashboards. Dashboards may collapse detail
tables for presentation, but preserve all server rows through **Ver datos** and keep the same
legend, accessibility, currency, availability, and financial-correctness behavior.

The shared table adapter offers **Detalle** and, for two ordered groupings, **Tabla dinámica**.
The first grouping remains the row dimension and the second the pivot columns; changing a table
mode is a presentation choice and never re-executes or rewrites the semantic query.

## One visualization compatibility contract

The frontend and backend derive visualization eligibility from the same canonical definition:

- **KPI** requires no grouping; **Línea** requires an eligible temporal grouping in either position;
- **Barras** requires a grouping and grouped bars require a comparison grouping or multiple calculations;
- stacked bars reject non-additive averages, overlapping tags, and historical point-in-time series;
- donuts require one additive result and one non-temporal, non-overlapping grouping, and reject
  percentages and negative returned values; and
- tables remain available for every valid result.

This is enforced when authoring, rendering, saving, updating, and loading widgets. It does not
rewrite the ordered query dimensions to satisfy a chart.

### Personal dashboard layout customization

Only a user-owned dashboard exposes **Personalizar dashboard**. Its normal viewer remains a
read-only financial view; the three recommended dashboards remain code-defined and do not expose
layout editing. Customization keeps a local complete layout draft containing only widget identity,
order, logical desktop width, and visibility. It never writes a query, a visualization context, a
calculation, or a financial result while the user rearranges the page.

The persisted desktop width is controlled rather than pixel-based: **Compacto** is four columns,
**Mediano** six, and **Ancho completo** twelve. Tablet and mobile adapt that preference to the
available responsive grid without changing the saved width. Charts cannot be saved as compact;
the command boundary enforces that minimum as well as the UI. Each visible card supports pointer
and touch drag-and-drop, keyboard drag, and **Mover arriba / Mover abajo** controls. All methods
produce the same deterministic complete order.

**Ocultar** removes a whole widget from the visible grid and from viewer execution, but not from
the dashboard aggregate. Its identity, query, visualization, and layout attributes remain intact
in **Widgets ocultos**, where it can be shown again. If all widgets are hidden, the viewer offers
an explicit recovery action. This is distinct from a chart legend, whose per-series visibility is
presentation-only and does not hide a widget.

**Equilibrado**, **Compacto**, and **Centrado en gráficas** are local size transformations. They
preserve widget order, visibility, titles, visualization types, default context, and canonical
definitions. They do not save automatically. There is no restore-to-original layout: after a
layout save, the resulting complete order, width, and visibility are the dashboard's current
layout. **Cancelar** simply discards the local draft and returns to that last saved layout.

The viewer submits a single `PUT /layout` only after **Guardar cambios**, with the aggregate
`expectedVersion` and every widget exactly once. **Cancelar** makes no write. An unsaved-change
indicator and native leave warning protect drafts. A failed, stale, invalid, or unauthorized save
leaves the local draft visible with an actionable error; it never silently overwrites another
session. The forward-only `20261105100000_remove_personal_dashboard_layout_baseline.xml` migration
removes retired baseline columns without changing the current layout of existing widgets.

## Safety and verification

The implementation retains the server-side ownership checks for account/category/tag references,
the controlled catalog, internal-transfer policy, result availability states, and currency
partitioning. It does not restore Analytics V1 readers, metric-only query contracts, legacy
bindings, or a second query engine.

Relevant regression coverage is located in:

- `SemanticAnalyticsExecutionServiceIT`, `SemanticSpecializedAnalyticsExecutionServiceIT`, and
  `SemanticAnalyticsValidatorTest` for semantic execution and financial rules;
- `PersonalDashboardResourceIT` and `PersonalDashboardPersistenceMigrationIT` for owner-scoped
  persistence, atomic layout behavior, template copies, widget validation, grouping
  order, and visualization compatibility;
- `analytics-explorer.spec.tsx` and `personal-dashboards.spec.tsx` for visual/written definitions
  and presentation state, plus `personal-dashboard-layout.spec.ts` for local layout transforms; and
- Cypress `analytics/personal-dashboard-widgets-real.cy.ts`,
  `analytics/personal-dashboards-real.cy.ts`,
  `analytics/personal-dashboard-layout-real.cy.ts`, and
  `analytics/recommended-dashboards-real.cy.ts`, plus
  `analytics/personal-widget-lifecycle-real.cy.ts`,
  `analytics/personal-dashboard-management-real.cy.ts`, and
  `analytics/official-dashboard-default-real.cy.ts` for real browser-to-REST coverage.

The focused widget regression covers both `ROOT_CATEGORY + MONTH + LINE` and `MONTH +
ROOT_CATEGORY + LINE`, save → reload → edit, and invalid non-temporal line queries that must still
be rejected.
