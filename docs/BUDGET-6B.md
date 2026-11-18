# BUDGET-6B — Centro de notificaciones

## Alcance

BUDGET-6B consume las notificaciones durables producidas por BUDGET-6A. No recalcula progreso financiero, no altera el motor de umbrales y no modifica los snapshots históricos guardados en `budget_notification`.

## Contratos REST

Todos los endpoints requieren autenticación y resuelven al propietario desde la sesión, nunca desde un identificador enviado por el cliente.

| Método  | Ruta                                                 | Propósito                                                              |
| ------- | ---------------------------------------------------- | ---------------------------------------------------------------------- |
| `GET`   | `/api/notifications?page=0&size=20&unreadOnly=false` | Página acotada, ordenada por `eventAt DESC, id DESC`.                  |
| `GET`   | `/api/notifications/unread-count`                    | Contador eficiente de no leídas del usuario actual.                    |
| `PATCH` | `/api/notifications/{id}/read`                       | Marca una notificación propia como leída de manera idempotente.        |
| `POST`  | `/api/notifications/mark-all-read`                   | Marca sólo las que ya estaban materializadas al comenzar la operación. |

La operación masiva filtra con un corte de `materializedAt` tomado por el servidor. Una notificación materializada después de ese corte permanece no leída. Una id ajena se comporta como inexistente (`404`) para no revelar datos entre usuarios.

## Presentación y navegación

La barra autenticada muestra una campana global y una insignia sólo cuando existe al menos una notificación no leída. Abrir la vista previa no cambia `readAt`. La vista previa está limitada a seis registros y la página `/notifications` pagina en el servidor.

Abrir una notificación primero ejecuta el marcado remoto y, sólo si éste funciona, navega a:

`/budgets/{budgetId}?referenceDate={snapshot.periodStart}`

`BudgetDetail` ya usa `referenceDate` para seleccionar el período gobernado por el backend. La información mostrada en la alerta procede del snapshot inmutable; el detalle del presupuesto recalcula su evaluación autoritativa para el período seleccionado.

## Refresco

`NotificationCenterProvider` es la única fuente de polling. Carga el contador al autenticar, cada 30 segundos mientras el documento está visible y cuando el navegador vuelve a estar visible. También se refresca al abrir la vista previa, abrir el historial y tras acciones de lectura. Evita solicitudes superpuestas y desmonta el temporizador y listener al salir.

## Límites explícitos

No hay eliminación, archivado, preferencias de entrega, correo, push, SSE/WebSockets ni scheduler de cierre de período. BUDGET-6C conserva esos alcances.

## BUDGET-6B-FINAL — aceptación automatizada (2026-11-17)

La ejecución inicial bloqueada no reveló una incompatibilidad de Testcontainers: Docker Desktop, el contexto `desktop-linux` y el motor estaban disponibles. La causa fue que el sandbox de ejecución no exponía el socket Docker del host. Las pruebas aisladas se ejecutaron con acceso temporal autorizado al socket; no se modificó la configuración permanente de Docker, ni se tocó PostgreSQL o los procesos de desarrollo. El contenedor PostgreSQL temporal y la aplicación temporal se eliminaron al finalizar.

Se corrigió un defecto real expuesto por la regresión de Budget: las lecturas `history`, `effectiveRevision` y `periods` usaban la búsqueda de propietario con bloqueo pesimista de la ruta de mutación de notificaciones dentro de una transacción de sólo lectura. PostgreSQL rechazaba el `SELECT … FOR UPDATE`. Ahora las lecturas usan la misma validación de propietario sin bloqueo, mientras que las operaciones mutables conservan su bloqueo. La prueba `BudgetCommandServiceTest` cubre explícitamente esa separación.

### Evidencia ejecutada

| Validación                                                         | Resultado                                                                                                                                                         |
| ------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `BudgetNotificationResourceIT` con PostgreSQL Testcontainers       | 5/5; propiedad, paginación y orden estables, filtro/count no leído, idempotencia, corte de marcar-todo, acceso anónimo, id ajeno y limpieza al eliminar Budget.   |
| `BudgetCommandServiceTest` focalizado                              | 16/16; incluye la regresión de lectura sin bloqueo de mutación.                                                                                                   |
| Cypress real `budget-notifications-real.cy.ts`                     | 1/1; dos presupuestos, umbrales 50/80/100, transacción real, materialización, campana, snapshot, lectura, navegación por período, filtros, marcar-todo y recarga. |
| Cypress real `budget-overview-real.cy.ts`                          | 8/8; regresión de creación, revisión, período e historial contra Spring + PostgreSQL aislados.                                                                    |
| `./mvnw -ntp --batch-mode -Dskip.npm -Dskip.installnodenpm verify` | Surefire 648/648 y Failsafe 1,364/1,364; sin fallos, errores ni skips.                                                                                            |
| `npm run jest`                                                     | 67 suites, 721 pruebas; todas correctas.                                                                                                                          |
| `npm run lint -- --no-fix`                                         | 0 errores; permanecen tres advertencias de complejidad preexistentes.                                                                                             |
| `npm run webapp:build` y `npm run webapp:prod`                     | Correctos.                                                                                                                                                        |
| `./mvnw -ntp --batch-mode -DskipTests -Pprod package`              | Correcto. El primer intento quedó bloqueado por DNS del sandbox; el reintento autorizado resolvió el registro sin cambiar dependencias.                           |
| `git diff --check` y `git diff --cached --check`                   | Correctos.                                                                                                                                                        |

No quedan defectos P0, P1 o P2 identificados dentro del alcance de BUDGET-6B. Persisten solamente las advertencias no bloqueantes de complejidad y de tamaño de bundle ya existentes.
