# BUDGET-6C-D — Experiencia de notificaciones de cierre de período

## Alcance

Esta fase completa el consumo de la notificación `PERIOD_CLOSED` producida por
BUDGET-6C-C. No cambia el scheduler, el cursor, los work items, el outbox ni el
motor financiero de Budget/Analytics. La campana y el historial leen el mismo REST
owner-scoped existente; no existe una segunda fuente de notificaciones, polling
adicional, canal de entrega ni cálculo financiero en React.

## Presentación inmutable

`notification-content.tsx` es el único renderer compartido entre vista previa de la
campana e historial. Distingue mediante `notificationType`:

- `THRESHOLD_CROSSED`: conserva el copy, los umbrales múltiples y la instantánea
  histórica existente.
- `PERIOD_CLOSED`: consume `BudgetPeriodCloseNotificationSnapshot` de BUDGET-6C-C.

Antes de presentar o navegar, `notification-api.ts` valida en tiempo de ejecución la
forma de JSON, fechas locales, instantes, zona IANA, moneda, tipo de Budget y números
finitos. Una fila antigua/corrupta se presenta con un fallback honesto y nunca se
convierte en un importe inventado. El backend también devuelve `snapshot: null` para
una fila cuyo JSON almacenado no pueda leerse, con un warning operacional sin incluir
el contenido financiero. Por tanto, una fila malformada no impide leer las otras filas
del propietario.

`PERIOD_CLOSED` muestra nombre de Budget, período, moneda, importe real capturado,
objetivo, avance y tipo de evento. Los valores provienen sólo de la instantánea:

- Gasto: “Registraste … de un límite de …” y “% del límite”.
- Ingreso: “Registraste … de una meta de …” y “% de la meta”.
- Cero con `EVALUATED`: se presenta explícitamente como “No se registraron gastos” o
  “No se registraron ingresos”, junto con `$0`, objetivo y `0%`.
- Un resultado no evaluado o inválido no se presenta como cero.

Los porcentajes no se recortan a 100%, por lo que un cierre por encima del límite/meta
conserva su porcentaje real.

## Fechas y zona histórica

El rango guardado continúa siendo inicio inclusivo/fin exclusivo. Se presenta como un
rango humano inclusivo operando sobre las fechas de calendario persistidas, no sobre la
zona actual del navegador. `logicalClosedAt` se formatea en la zona IANA guardada y se
presenta como “Período finalizado el …”. Si el día local de `capturedAt` difiere,
también aparece “Resultado registrado el …”; esto evita afirmar que el ledger se
reconstruyó exactamente al instante lógico de cierre.

Cambiar después una transacción, revisión o zona actual puede cambiar la evaluación
actual del detalle de Budget, pero jamás modifica el contenido de la notificación.

## Campana, historial y navegación

La campana mantiene el refresh existente: carga autenticada inicial, cada 30 segundos
cuando la pestaña es visible, regreso a visibilidad, apertura de la campana/historial y
cambios de lectura. Abrir la campana no marca nada como leído. Activar una fila:

1. llama al `PATCH /api/notifications/{id}/read` owner-scoped;
2. actualiza el contador sólo después de la respuesta exitosa;
3. extrae `periodStart` sólo de una instantánea validada;
4. navega a `/budgets/{budgetId}?referenceDate={periodStart}`.

Budget Detail resuelve el período y su zona desde backend, no reconstruye el calendario
en React. Si falla la lectura o la instantánea no tiene período válido, el registro se
mantiene visible, se informa el error y no se realiza una navegación engañosa.

El historial conserva filtros Todas/No leídas, paginación de servidor, orden estable
por `eventAt,id`, marcar todo como leído, estados de carga/vacío/error y compatibilidad
con historiales mezclados de umbrales y cierres. El layout usa etiquetas semánticas,
envoltura de nombre/importe y los límites responsivos ya existentes de la campana. En
móvil, la acción «Abrir presupuesto» se coloca debajo del contenido para no comprimir
nombres, importes ni fechas históricas. No hay dependencias UI nuevas.

## Pruebas añadidas

- `notification-content.spec.tsx`: umbrales, cierre EXPENSE, cierre INCOME, cero
  autoritativo, porcentaje mayor de 100, captura tardía, preview compacto y fallback
  de contenido incompleto.
- `notification-history.spec.tsx`: navegación sólo después de marcar leído para un
  cierre y preservación de la fila ante error de lectura.
- `notification-bell.spec.tsx`: preview de cierre sin marcar leído al abrir y deep-link
  histórico después de la respuesta del servidor.
- `BudgetNotificationServiceTest`: JSON histórico corrupto no invalida toda la página.
- `BudgetNotificationResourceIT`: colección real PostgreSQL con umbral y cierre,
  persistencia del snapshot después de lectura y contrato de período/importe/objetivo.
- `budget-period-close-notification-real.cy.ts`: fixture financiera real de gasto,
  ingreso y cero; captura mediante scheduler/outbox, campana, historial, screenshots,
  deep-link histórico y corrección financiera posterior que no reescribe la
  instantánea.

## Evidencia de ejecución

Las pruebas focalizadas, builds completos y Cypress real se registran en el informe de
aceptación de esta fase con sus conteos reales. Las capturas Cypress se escriben en
`target/cypress/screenshots/` y se inspeccionan antes de declarar aceptación visual.

## Diferido explícitamente

No se implementan reportes programados, PDF, correo, push, SSE/WebSockets, borrado o
archivo de notificaciones, nuevos canales configurables, otro motor financiero ni un
rediseño global de la interfaz.
