# BUDGET-5B-B — Edición y revisiones inmutables

Estado: implementado sobre la lectura de BUDGET-5B-A. Esta entrega añade edición de metadatos y programación de
revisiones. Las acciones de pausa, reanudación, finalización y borrado se añadieron posteriormente en
[BUDGET-5B-C](BUDGET-5B-C.md); notificaciones siguen fuera de alcance.

## Flujo de edición

La vista `/budgets/:budgetId` ofrece **Editar presupuesto** mientras el presupuesto no esté completado. La ruta
`/budgets/:budgetId/edit` carga, con autorización de dueño, la revisión que rige el período actual y las fronteras
de período desde Java. React no calcula ni infiere fronteras de calendario.

La persona puede cambiar por separado:

- nombre y descripción, mediante `PUT /api/budgets/{id}/metadata` con la versión optimista actual;
- importe objetivo, cuentas (`ALL` o `SELECTED`), categorías (`ALL` o `SELECTED`, con descendientes) y etiquetas
  (`NONE` o `SELECTED`, `ANY`/`ALL`), mediante una nueva `BudgetRevision` inmutable; y
- el instante de aplicación: período actual, siguiente período o uno futuro que devuelve
  `GET /api/budgets/{id}/periods`.

El formulario reutiliza exactamente los selectores owner-scoped de creación. Tipo, moneda, calendario y recurrencia
se presentan explícitamente como inmutables en esta experiencia. Un importe actual se aplica al período completo:
no prorratea ni cambia períodos anteriores. El cálculo de avance posterior sigue siendo exclusivamente
`BudgetEvaluationService` y el pipeline semántico de Analytics.

Antes de enviar una revisión, la interfaz muestra la fecha de inicio y el intervalo completo que devuelve el
servidor. Impide doble envío. Los comandos de revisión conservan un `clientRequestId` por intento recuperable: un
reintento de la misma petición reutiliza el recibo idempotente del backend. Los metadatos conservan su contrato
existente de versión optimista y la UI bloquea clics duplicados mientras se guardan.

## Revisión pendiente, reemplazo y cancelación

La página distingue la configuración vigente de cada programación futura activa, mostrando su fecha, objetivo y
alcance. Si se elige una frontera con programación existente, la confirmación explica que se trata de un reemplazo;
no se sobrescribe silenciosamente. La confirmación usa
`POST /api/budgets/{id}/revisions/{scheduleId}/replace` y conserva tanto la cancelación como la nueva programación
en el historial inmutable.

Cada programación futura también puede cancelarse desde una confirmación explícita mediante
`POST /api/budgets/{id}/revisions/{scheduleId}/cancel`. La cancelación no borra la revisión ni el registro de la
programación: resuelve la línea de tiempo a la configuración anterior. Conflictos de versión devuelven una acción
comprensible para recargar y revisar; no se intenta combinar cambios concurrentes en el navegador.

Los presupuestos completados no exponen mutaciones. Un presupuesto aún programado permite actualizar sólo nombre y
descripción: como el servidor todavía no tiene un período actual, no ofrece una acción de revisión ficticia. La
página muestra la primera revisión resuelta por `evaluation/at` exclusivamente para describirla. Los presupuestos
pausados siguen las reglas del servidor; las revisiones que éste acepte continúan usando sus fronteras autoritativas.

## Calendario

El backend ya puede validar ciertos cambios de calendario futuros: exige una frontera completa existente, un ancla
igual a esa frontera, conserva la recurrencia inicial y rechaza programaciones futuras incompatibles. BUDGET-5B-B no
expone esa operación. Una interfaz de calendario requeriría una decisión de producto sobre cómo presentar y resolver
las programaciones posteriores incompatibles, sin introducir migración de calendario en React. No se añadió un
mecanismo paralelo ni se usó completar presupuesto como atajo.

## Cobertura

- `BudgetCommandServiceTest` cubre programación actual/futura, inmutabilidad histórica, reemplazo, cancelación,
  conflicto de versión y repetición idempotente.
- `BudgetResourceIT` verifica PostgreSQL y REST para historial owner-scoped, límites exactos, actualización de
  metadatos y reemplazo de una programación futura sin modificar la revisión anterior.
- `budget-edit.spec.tsx` cubre carga del formulario desde la revisión efectiva, tipo/moneda inmutables, revisión
  actual, alcance reutilizado, confirmación, reemplazo/cancelación y conflicto optimista.
- `budget-overview-real.cy.ts` ejecuta el flujo de navegador real contra Spring y PostgreSQL aislados: modificación
  actual, recálculo visible, programación futura y cancelación persistida.
- La prueba de editor también verifica el estado programado sin período actual: conserva metadatos editables y no
  presenta un comando de revisión que el servidor rechazaría.

## Validación ejecutada

- `BudgetCommandServiceTest` (11), `BudgetEvaluationServiceIT` (7) y `BudgetResourceIT` (8) pasaron contra
  PostgreSQL Testcontainers: 26 pruebas, 0 errores y 0 fallos. La integración REST verificó actualización de
  metadatos y reemplazo de una revisión futura sin alterar el historial anterior.
- Las tres suites de Jest de presupuestos pasaron: 13 pruebas, incluidos editor, detalle y overview.
- Cypress ejecutó 7 escenarios reales contra Spring y PostgreSQL desechables: 7 pasaron, 0 fallaron, 0 omitidos.
  El recorrido del editor modificó el período actual, comprobó el recálculo visible, programó una revisión futura y
  la canceló verificando el historial REST. Se revisaron capturas de escritorio y móvil del editor.
- Los builds de frontend de desarrollo y producción, y el empaquetado Maven de producción, terminaron correctamente.
  El lint no tuvo errores; conserva tres advertencias de complejidad ya presentes fuera de este editor
  (`analytics-explorer`, `analytics-results-panel` y `budget-create`).
- `git diff --check` y `git diff --cached --check` no reportaron errores. No se hizo staging ni commit.
