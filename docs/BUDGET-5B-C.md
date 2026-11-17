# BUDGET-5B-C — Gestión de ciclo de vida y eliminación

Estado: implementado sobre BUDGET-5B-A y BUDGET-5B-B. Esta entrega completa las acciones de ciclo de vida de la
vista de detalle y el borrado permanente owner-scoped. No incorpora notificaciones, migración de calendario ni una
segunda máquina de estados en React.

## Fuente de verdad

La interfaz carga el historial inmutable desde `GET /api/budgets/{id}/history` y resuelve el estado operativo actual
con `GET /api/budgets/{id}/evaluation/current`. Si la persona está consultando un período histórico, las acciones
siguen basándose en ese estado actual del servidor, no en la fecha de referencia elegida. La siguiente frontera se
obtiene de `GET /api/budgets/{id}/periods`; React sólo presenta la fecha y no la calcula.

El encabezado conserva el estado actual (`ACTIVE`, `PAUSED`, `SCHEDULED` o `COMPLETED`) y, por separado, muestra una
acción pendiente con su fecha efectiva. Por ejemplo, una pausa futura no cambia la etiqueta de **Activo** hasta que
llega su frontera. Las evaluaciones de períodos pausados siguen siendo estados sin evaluación, no ceros financieros.

## Acciones disponibles

El menú contextual sólo muestra operaciones que el contrato existente de `BudgetCommandService` puede aceptar:

- **Pausar al siguiente período** usa `POST /api/budgets/{id}/pause`. El período actual, revisiones y calendario no
  cambian; la pausa y sus períodos cuentan dentro de una recurrencia finita.
- **Cancelar pausa pendiente** usa `POST /api/budgets/{id}/pause/{eventId}/cancel` y conserva los eventos y
  revisiones inmutables como historial. No existe una cancelación de reanudación porque el backend no la ofrece.
- **Reanudar en siguiente período** usa `POST /api/budgets/{id}/resume` sólo cuando el período actual ya está pausado.
- **Finalizar presupuesto** usa `POST /api/budgets/{id}/finish`; exige una confirmación que explica su carácter
  permanente, que el historial permanece accesible y que no habrá reanudación.

Todos los comandos de ciclo de vida reutilizan `expectedVersion`, `clientRequestId` y `timeZone` del contrato
existente. Un conflicto `409` solicita recargar y revisar; mientras se envía una operación el cuadro de confirmación
no admite un segundo envío.

## Eliminación permanente

`DELETE /api/budgets/{id}` recibe `BudgetDeleteCommand(expectedVersion)`. Tras comprobar propietario y versión,
el servicio elimina únicamente el agregado de Budget: recibos idempotentes, eventos de ciclo de vida, programaciones,
revisiones y enlaces de alcance. Las filas autorreferenciadas de cancelación se eliminan antes de su evento o
programación objetivo. El último borrado directo del agregado evita que Hibernate vuelva a aplicar el `cascade` sobre
revisiones ya eliminadas.

Nunca se eliminan ni modifican `FinancialAccount`, `FinancialTransaction`, `Category`, `Tag`, `InternalTransfer`,
definiciones de Analytics ni otros presupuestos. El diálogo nombra el presupuesto y advierte que el presupuesto y su
historial no se pueden recuperar. Tras `204`, la aplicación navega al resumen con una confirmación visible; una
respuesta de recurso inexistente concurrente (`404`, o el `400` owner-scoped ya usado por esta API) se trata como
eliminación ya completada y no deja el detalle obsoleto.

## Cobertura

- `BudgetCommandServiceTest` cubre la limpieza owner-scoped y conflicto de versión del borrado, además de las reglas
  existentes para pausa, cancelación, reanudación y finalización.
- `BudgetResourceIT` usa PostgreSQL desechable para verificar REST, programación/revisión/evento pendientes,
  aislamiento de dueño, conflicto optimista, limpieza de todas las filas Budget y preservación de cuenta,
  transacción, categoría y etiqueta.
- `budget-lifecycle-actions.spec.tsx` prueba visibilidad contextual, fechas pendientes, pausa, cancelación,
  reanudación, finalización, eliminación, conflicto y los payloads enviados.
- `budget-overview-real.cy.ts` añade un recorrido navegador → REST → Spring → PostgreSQL para pausa pendiente,
  cancelación y borrado confirmado con preservación de cuenta y transacción.
- `budget-lifecycle-real.cy.ts` usa tres arranques aislados del perfil `e2e` con un `Clock` fijo: programa pausa y
  finalización, comprueba sus fronteras reales, programa la reanudación y confirma que el período pausado histórico
  sigue sin evaluación financiera. `E2eFixedClockConfiguration` sólo se activa con el perfil `e2e` y la propiedad
  explícita `fintrack.e2e.fixed-clock`; no altera el reloj de desarrollo ni producción.

Los escenarios que requieren adelantar el reloj se ejecutan contra una aplicación y PostgreSQL desechables; no se
cambia el reloj de desarrollo ni se introducen esperas artificiales en Cypress.
