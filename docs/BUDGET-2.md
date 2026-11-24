# BUDGET-2 — Revisiones programadas y ciclo de vida

Estado: implementado en backend. La compilación de una revisión/período al motor semántico compartido se documenta en
[BUDGET-3](BUDGET-3.md); todavía no calcula avance, alertas ni expone una UI de presupuestos.

## Historial inmutable

`BudgetRevision` sigue siendo un snapshot completo de configuración. No contiene una fecha efectiva. Esa autoridad vive solamente en `BudgetRevisionSchedule`:

- `SCHEDULED` asocia una revisión a un inicio de período completo.
- `CANCELLED` referencia la programación que anula; no borra ni modifica la revisión o la programación original.
- Para un inicio solicitado, gana la programación activa con la fecha efectiva más reciente; si comparten fecha, gana la secuencia registrada más reciente. Por tanto, el orden de captura no altera el historial anterior.

La migración `20261115110000_budget_revision_scheduling.xml` crea la programación, eventos de ciclo de vida y recibos idempotentes. También registra una programación inicial para cada revisión 1 BUDGET-1 existente. Sólo agrega tablas Budget y no modifica datos financieros.

## Cambios de configuración

Los comandos son owner-scoped, exigen `expectedVersion` y un `clientRequestId` para operaciones que agregan historial. Reintentar el mismo comando devuelve el resultado original; reutilizar la clave con otro contenido devuelve conflicto.

- Nombre y descripción son metadatos mutables.
- Tipo y moneda siguen siendo inmutables.
- Importe y alcance se pueden aplicar al período actual completo o a una frontera futura completa.
- El cambio actual queda registrado con la misma frontera inicial y sustituye la configuración efectiva de todo ese período sin borrar las programaciones futuras.
- Reemplazar o cancelar un cambio futuro agrega operaciones de historial y conserva los demás cambios futuros.
- Un cambio de calendario requiere una frontera válida tanto del calendario anterior como del nuevo. El nuevo ancla debe ser la fecha seleccionada; no se crean períodos parciales ni se desplazan fechas.
- Para evitar extender o reinterpretar períodos ya programados, BUDGET-2 conserva la recurrencia y terminación originales. Un cambio de calendario exige resolver primero las programaciones posteriores incompatibles.

## Ciclo de vida derivado

`BudgetLifecycleEvent` es append-only: `PAUSE_REQUESTED`, `PAUSE_CANCELLED`, `RESUME_REQUESTED` y `FINISH_REQUESTED`. El presupuesto no guarda una columna mutable de estado.

- Pausar, reanudar o terminar manualmente se hace efectivo en la próxima frontera completa.
- Una pausa no altera ancla, límites de recurrencia ni fechas de cambios futuros. Los períodos pausados devuelven `PAUSED` y `evaluationEligible=false`; no implican un importe financiero de cero.
- La cancelación de pausa sólo se permite antes de hacerse efectiva y conserva ambos eventos.
- La terminación automática se deriva de `ONCE`, `AFTER_N_PERIODS` o `END_DATE`; los períodos pausados cuentan para `N`.
- Una terminación efectiva es irreversible. No se producen evaluaciones financieras en BUDGET-2.
- Una pausa, reanudación o terminación pendiente no puede combinarse con otra transición conflictiva; debe resolverse antes de programar la siguiente.

## API de comandos y lectura

Además de `POST /api/budgets`, las rutas son específicas al producto, no CRUD genérico:

- `PUT /api/budgets/{id}/metadata`
- `POST /api/budgets/{id}/revisions`
- `POST /api/budgets/{id}/revisions/{scheduleId}/replace`
- `POST /api/budgets/{id}/revisions/{scheduleId}/cancel`
- `POST /api/budgets/{id}/pause`, `/pause/{eventId}/cancel`, `/resume`, `/finish`
- `GET /api/budgets/{id}/effective-revision`, `/history`, `/periods`

`/periods` devuelve calendario y estado operacional, no progreso o movimientos. Todo acceso se resuelve primero contra el propietario autenticado.

## Pruebas

- `BudgetTimelineTest`: orden efectivo frente a orden de captura, cancelación, pausas/reanudación, terminación y detección de períodos parciales.
- `BudgetCommandServiceTest`: cambio actual/futuro, reemplazo, cancelación, versión, idempotencia, pausa/reanudación y terminación.
- `BudgetResourceIT`: PostgreSQL desechable con Liquibase y reloj fijo; creación, programación, reintento, cancelación, resolución, historial, owner scope, conflictos de versión y rollback de transiciones inválidas.
