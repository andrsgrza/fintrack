# BUDGET-5A — Resumen, creación y vista previa de calendario

Estado: implementado y corregido por [BUDGET-5A.1](BUDGET-5A-1.md). Esta fase incorpora el primer flujo de producto de Presupuestos; no incorpora detalle o
edición, historial navegable, pausas/finalización, notificaciones, prorrateo ni una librería de visualizaciones.

## Resumen owner-scoped

`GET /api/budgets/overview` requiere `referenceDate` y `timeZone` IANA del navegador, y admite filtros opcionales
de `type` y `status`. Lee sólo los presupuestos del usuario autenticado y devuelve una tarjeta independiente por
presupuesto. No produce un total global ni mezcla monedas.

Cada tarjeta separa dos conceptos que no deben confundirse:

- **estado operacional** (`SCHEDULED`, `ACTIVE`, `PAUSED`, `COMPLETED`), derivado de la línea de tiempo inmutable;
- **estado financiero/meta**, derivado de `BudgetEvaluationResult` cuando el período en la fecha de referencia se
  puede evaluar.

Un presupuesto que aún no inicia o cuya vida finita ya terminó no recibe un período inventado ni una evaluación. Un
período con resultado semántico `ZERO` sí muestra `$0.00`; `NO_DATA`, `UNAVAILABLE`, `PARTIAL`, configuración
incompleta y fallos conservan su mensaje/estado tipado y no se convierten en cero.

`BudgetOverviewService` carga el agregado autorizado y sus revisiones/eventos por lotes y reutiliza
`BudgetEvaluationService.evaluateAuthorizedBudgetAt`. Por tanto los importes provienen exclusivamente de la
ejecución semántica de BUDGET-3/BUDGET-4: no hay consulta directa a `FinancialTransaction`, fórmula alternativa ni
duplicación de la exclusión de transferencias internas.

Una fecha histórica anterior a una finalización posterior sigue evaluando su período histórico aplicable; una fecha
futura o fuera de la vida programada no ejecuta Analytics.

## Creación y calendario

`POST /api/budgets/calendar-preview` recibe sólo un borrador de calendario y devuelve hasta seis períodos calculados
por el mismo `BudgetPeriodCalculator` Java que valida y crea el presupuesto. No persiste entidades ni reimplementa
aritmética de fechas en React. Rechaza una zona no IANA, pero acepta fechas de ancla pasadas: una creación histórica
conserva el instante real de creación y evalúa sus períodos mediante el mismo contrato financiero.

El formulario `/budgets/new` inicia de forma deliberadamente incompleta:

- gastos económicos, fecha local de hoy, alineación desde la fecha elegida y recurrencia indefinida;
- sin moneda, importe, unidad ni intervalo implícitos;
- todas las cuentas/categorías compatibles y sin filtro de etiquetas.

La persona selecciona moneda antes de limitar cuentas. Puede elegir raíces de categoría (sus descendientes se
incluyen por el compilador semántico) y etiquetas con semántica `ANY` o `ALL`. La creación final usa el comando
idempotente existente `POST /api/budgets`; el mismo `clientRequestId` se conserva ante un error, el formulario no
borra el borrador y un doble clic no inicia dos peticiones.

La zona horaria se toma de `Intl.DateTimeFormat().resolvedOptions().timeZone` y se envía para preview, creación y
resumen. BUDGET-5A no la persiste como preferencia de usuario; trabajo futuro no interactivo debe resolver una zona
confiable antes de evaluar o notificar.

BUDGET-5A.1 define los tres modos de calendario y el campo inmutable que conserva una regla personalizada de fin de
mes. Esta página mantiene el alcance de la primera UI; el contrato de calendarios vigente está en
[`BUDGET-5A-1.md`](BUDGET-5A-1.md).

## UI y límites de fase

La ruta `/budgets` muestra el resumen, fecha de referencia y filtros. Desde BUDGET-5B-A, cada tarjeta abre la
consulta de detalle en `/budgets/:budgetId`; sus períodos, configuración histórica e historial están documentados en
[`BUDGET-5B-A.md`](BUDGET-5B-A.md). Las tarjetas no exponen acciones de pausa, finalización, edición o cambios de
calendario.

Se reutilizan los selectores owner-scoped de cuentas, categorías y `ProductTagSelector`; no se crean catálogos o
almacenamientos paralelos.

## Cobertura

- `BudgetCalendarPreviewServiceTest`: intervalos exactos, inicio histórico, mes anclado/personalizado al día 31,
  calendario natural, recurrencia finita y borde de fecha entre `America/Mexico_City` y `Asia/Tokyo`.
- `BudgetResourceIT`: preview REST válido/inválido sin crear un presupuesto.
- `BudgetOverviewServiceIT`: PostgreSQL desechable, actual económico canónico, período futuro, finalizado,
  filtros y aislamiento de dueño.
- `budget-create.spec.tsx` y `budget-overview.spec.tsx`: defaults incompletos, modos específico/natural/personalizado,
  preview antes de crear, idempotencia ante doble clic/reintento, preservación de borrador, filtros y cero financiero
  válido.
- `budget-overview-real.cy.ts`: Chrome real → REST → Spring → PostgreSQL desechable, selectores reutilizados,
  preview Java, creación, tarjeta y capturas de escritorio/móvil sin desbordamiento horizontal.

La corrección B4 sobre una evaluación histórica previa a una finalización y el rechazo de períodos futuros siguen
cubiertos por `BudgetEvaluationServiceIT`.
