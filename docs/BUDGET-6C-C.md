# BUDGET-6C-C — Cierre automático durable de períodos de Budget

## Propósito y límites

Esta fase incorpora el productor automático de notificaciones de cierre de período.
No crea un segundo motor financiero: para calcular el importe cerrado reutiliza
`BudgetEvaluationService`, `BudgetSemanticQueryCompiler` y el ejecutor semántico de
Analytics V2. Tampoco rediseña el centro de notificaciones: la presentación completa
de `PERIOD_CLOSED`, sus enlaces y sus textos financieros quedan explícitamente para
BUDGET-6C-D.

El cierre no afirma reconstruir el libro mayor exactamente como estaba en la frontera
histórica. Si se captura tarde, usa el ledger autoritativo que existe **al capturar**.
La instantánea resultante no se recalcula después.

## Modelo durable

`budget_period_close_cursor` es una proyección reemplazable, una fila por Budget. Sólo
guarda el siguiente inicio de período candidato y el instante en que puede descubrirse.
Tiene una restricción única por `budget_id` y el índice
`(next_due_at, budget_id)`, por lo que el scheduler no escanea todos los Budgets cada
minuto.

`budget_period_close_work_item` es la evidencia durable de un cierre lógico. Su
identidad es inmutable: `(budget_id, period_start)`. Guarda fin exclusivo, zona IANA
efectiva, `logical_closed_at`, estado, intentos, siguiente intento, lease, diagnóstico,
instante de captura y la asociación única con el evento de outbox. Sus índices sirven
para trabajo vencido, leases caducados e historial por Budget. Las restricciones únicas
impiden tanto dos cierres lógicos como la asociación de dos work items con un outbox.

Estados y transiciones válidas:

```text
PENDING ──claim──> PROCESSING ──capture──> CAPTURED
  ^                         ├──retry──> RETRY_SCHEDULED ──claim──┘
  └──── lease expirada ─────┤
                            ├──cancel por ineligibilidad──> CANCELLED
                            └──bloqueo permanente─────────> BLOCKED
```

`CAPTURED`, `CANCELLED` y `BLOCKED` son terminales. Un work item sólo puede adjuntar
outbox cuando pasa de `PROCESSING` a `CAPTURED`.

## Scheduler, descubrimiento y multiusuario

`BudgetPeriodCloseScheduler` se ejecuta con Spring Scheduling cada `PT60S` de forma
configurable mediante `application.budget-period-close.fixed-delay`. Cada tick tiene dos
fases deliberadamente distintas:

1. `BudgetPeriodCloseCursorService` selecciona como máximo 50 cursores vencidos
   (configurable con `discovery-batch-size`) mediante `FOR UPDATE SKIP LOCKED`, crea
   durablemente un work item si corresponde y avanza el cursor en la misma transacción.
2. `BudgetPeriodCloseWorkClaimService` reclama como máximo 25 trabajos (configurable
   con `processing-batch-size`) con otro `SKIP LOCKED`, usando leases de cinco minutos.
   Cada captura se ejecuta en su propia transacción para que el fallo de un Budget no
   bloquee otros usuarios ni otros períodos.

Si la aplicación se apaga, el cursor y los work items permanecen. Al volver, los
cursores vencidos se descubren en lotes y los leases vencidos vuelven a ser reclamables.
La unicidad de identidad y la captura atómica, no el lease por sí solo, impiden duplicar
el evento lógico.

## Elegibilidad y último período activo

La preferencia existente aporta `periodCloseEnabled` y `periodCloseEnabledSince`.
Un período sólo puede capturarse si sigue habilitado, su frontera lógica es igual o
posterior a esa época de activación, el período era `ACTIVE` en `BudgetTimeline` y no
existe ya su work item. Activar durante un período abierto habilita su futura frontera;
activar después de que cerró no revive ese cierre. Desactivar elimina el cursor y el
procesador cancela cualquier trabajo todavía no capturado. Al reactivar, los trabajos
históricos cancelados conservan su identidad terminal y no resucitan.

La decisión se vuelve a comprobar dentro de la transacción de captura: descubrir no es
una autorización. Esto conserva la carrera correcta entre desactivar y capturar. El
estado se consulta para el período concreto, no desde el estado presente del Budget;
por eso el último período `ACTIVE` de `ONCE`, recurrencia finita o un `FINISH` sigue
siendo elegible aunque el Budget aparezca terminado cuando corre el worker.

## Calendario, zona horaria y evaluación financiera

El calendario viene exclusivamente de `BudgetTimeline`. La zona se resuelve por
`BudgetTimeZoneService.resolveZoneForPeriod`; el cierre lógico es
`period.endExclusive().atStartOfDay(zone).toInstant()`. La asignación IANA histórica
se guarda en el work item y se revalida antes de capturar. No se sustituye una zona
faltante por UTC para una instantánea inmutable.

`BudgetEvaluationService.evaluateClosedPeriodForNotification` es la estrecha ruta
interna para un período explícito. Selecciona la revisión efectiva de ese período y usa
la misma compilación semántica, alcance de cuentas/categorías/tags, separación de
moneda y exclusión de piernas de transferencias internas que la evaluación normal. Un
cero autoritativo es un importe válido; `UNAVAILABLE`, resultado parcial, sin datos o
fallos transitorios nunca se convierten en cero.

## Captura, outbox y contrato tipado

La captura obtiene el orden de bloqueo: **propietario → Budget → preferencia → work
item**. En una única transacción crea:

1. `BudgetPeriodCloseNotificationSnapshot` inmutable;
2. un `BudgetNotificationOutbox` `PERIOD_CLOSED`, con clave
   `PERIOD_CLOSED:<budgetId>:<periodStart>`;
3. el estado `CAPTURED`, la asociación al outbox y `capturedAt`.

La instantánea incluye identidad/nombre/tipo/moneda de Budget, inicio y fin exclusivo,
zona, revisión efectiva, objetivo, importe real, porcentaje, estado de evaluación,
`logicalClosedAt`, `capturedAt` y clave lógica. Si la transacción cae, ninguno de esos
efectos queda comprometido. La entrega posterior sigue siendo responsabilidad del
outbox existente e idempotente.

El outbox tiene ahora `notification_type`. La migración marca los eventos históricos
como `THRESHOLD_CROSSED`, por lo que sus JSON previos siguen válidos. `BudgetNotification`
preserva el tipo del outbox. El REST entrega el snapshot como JSON tipado por
`notificationType`, evitando deserializar un cierre como si fuera un umbral. La UI actual
usa una tarjeta mínima y segura para `PERIOD_CLOSED`; la experiencia detallada queda
para C-D.

## Reintentos, bloqueos y diagnóstico

Las excepciones desconocidas y resultados temporalmente no autoritativos se programan
en `RETRY_SCHEDULED` con backoff exponencial y tope de seis horas, sin máximo de
intentos que descarte silenciosamente un cierre. `BLOCKED` se reserva para una
inconsistencia permanente de período/zona o configuración no recuperable; mantiene un
diagnóstico y requiere conciliación administrativa/corrección de la configuración.
La deshabilitación, un cambio de época de activación o un período ya no activo son
`CANCELLED`, no `BLOCKED`.

Los registros durables (`state`, `attempt_count`, `next_attempt_at`, `lease_until`,
`last_error`, `logical_closed_at`, `captured_at`) son la fuente operativa inspectable.
Además, Micrometer registra de manera agregada cursores procesados, work items creados,
capturados, reintentados, bloqueados y cancelados, fallos inesperados, duración de cada
intento de captura y la edad del trabajo pendiente más antiguo. No se agregan nombres,
importes ni otros datos financieros sensibles a las métricas. No se crea una pantalla
administrativa ni un producto de jobs separado.

## Borrado y recuperación

El borrado permanente de un Budget elimina primero work items y cursor, antes de
preferencias, outbox y el Budget. No quedan filas huérfanas ni puede reaparecer trabajo
para un Budget eliminado. Pausar o finalizar, en contraste, sólo detiene el
descubrimiento futuro; conserva el historial y el último período activo elegible.

Un work item con lease abandonado vuelve a `PENDING` cuando expira. Tras cualquier
reclamo se revalidan propietario, Budget, preferencia, calendario, zona y elegibilidad,
de modo que un reinicio, dos instancias o una carrera de mutación no generan una
notificación incorrecta.

## Evidencia automatizada de esta entrega

Ejecuciones focalizadas realizadas el 18 de noviembre de 2026:

```text
./mvnw -ntp --batch-mode -Dskip.installnodenpm -Dskip.npm \
  -Dtest=BudgetPeriodCloseWorkItemTest,BudgetNotificationPreferenceServiceTest,BudgetCommandServiceTest,\
BudgetProfileTimeZoneServiceTest,BudgetNotificationServiceTest test
```

Resultado: **29 pruebas Surefire, 0 fallos, 0 errores, 0 omitidas**.

```text
./mvnw -ntp --batch-mode -Dskip.installnodenpm -Dskip.npm \
  -Dtest=BudgetNotificationEngineIT test
```

Resultado: **8 pruebas contra PostgreSQL 17 temporal de Testcontainers, 0 fallos,
0 errores, 0 omitidas**. Incluye cierre de un período de gasto real con MX$60 sobre
MX$100, cierre final `ONCE` con cero autoritativo, exclusión de duplicados/outbox
tipado y desactivación antes de la captura. La base de desarrollo no se usó.

La aceptación completa de Maven, Jest, lint, builds de producción y Cypress se reporta
por separado con sus resultados reales; no debe inferirse de esta evidencia focalizada.

## Cierre de aceptación — 18 de noviembre de 2026

Esta sección sustituye el estado de aceptación incompleto anotado arriba; no altera la
historia de la implementación.

### Diagnóstico del timeout ajeno de Transaction Batch

El fallo anterior de
`TransactionBatchCsvAppendServiceIT.rejectsStructuralCsvWithoutMutatingAndHandlesRepresentativeFortyAndFiveHundredRowAppends`
fue un `java.util.concurrent.TimeoutException` de **JUnit**, no un timeout de PostgreSQL,
lock, conexión, Testcontainers ni Maven. `src/test/resources/junit-platform.properties`
define 15 s como límite global. El XML del fallo registró 15.176 s; la repetición aislada
pasó en 14.527 s. La prueba conserva todas sus aserciones estructurales y sus apéndices
representativos de 40 y 500 filas, por lo que un límite global de 15 s no dejaba margen
realista para la carga que la propia prueba requiere.

Se añadió solamente `@Timeout(30)` a ese método. No se modificó el límite global, no se
saltó ninguna prueba ni se cambió código de producto de Transaction Batch. El `verify`
completo posterior pasó y el método terminó correctamente.

### Corrección descubierta durante la aceptación

La prueba real de ciclo de vida reveló que, al encontrar un período `PAUSED`, el cursor
seguía buscando períodos artificiales futuros. `BudgetPeriodCloseCursorService` ahora
trata `PAUSED`, al igual que `COMPLETED` y `SCHEDULED`, como terminal para ese episodio
de descubrimiento. Un comando posterior de reanudar o cambiar calendario vuelve a
sincronizar un cursor nuevo. Esto evita la exploración de hasta 10,000 períodos futuros
sin impedir que se capture el último período que seguía `ACTIVE` antes de la pausa.

`BudgetNotificationEngineIT.stops_discovery_after_the_last_active_period_when_the_budget_is_paused`
lo cubre con PostgreSQL real: captura exactamente el período activo final, no crea trabajo
futuro y elimina el cursor.

### Maven y regresión PostgreSQL

Comando ejecutado:

```text
./mvnw -ntp --batch-mode verify
```

Resultado: **PASS**.

| Motor    | Ejecutadas | Fallos | Errores | Omitidas |
| -------- | ---------: | -----: | ------: | -------: |
| Surefire |        680 |      0 |       0 |        0 |
| Failsafe |      1,374 |      0 |       0 |        0 |

Los 1,374 Failsafe incluyen 9 pruebas de `BudgetNotificationEngineIT`, contra
PostgreSQL 17 temporal de Testcontainers. Sus coberturas relevantes incluyen captura
atómica work-item/outbox, dos workers sin duplicado, expiración de lease y reintento,
rollback, fallos recuperables, `BLOCKED` sin bloquear períodos posteriores, época de
activación, evento capturado que sobrevive desactivación, zona y revisión históricas,
cero financiero frente a no disponible, compatibilidad de umbrales y limpieza por
borrado definitivo del Budget.

### Matriz Cypress real

Se usó Spring Boot real con perfil `prod,e2e`, un reloj fijo sólo para E2E, puerto
temporal 18080 y PostgreSQL 17 desechable en 127.0.0.1:15432. No hubo intercepts de
las APIs financieras o de notificaciones ni respuestas REST simuladas. Cada fase usa el
mismo spec con las demás pruebas deliberadamente `pending`:

| Flujo                           | Fase ejecutada                                | Resultado de la fase                       | Evidencia verificada                                                                                                                                                                                                                 |
| ------------------------------- | --------------------------------------------- | ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| A — cierre normal               | `setup` + `capture`                           | 1 passed, 0 failed, 5 pending en cada fase | Gasto real de MX$60, objetivo MX$100, scheduler, snapshot `PERIOD_CLOSED`, outbox, una sola clave lógica y materialización en `/notifications`.                                                                                      |
| B — recuperación                | `lifecycle-setup` + `lifecycle-capture`       | 1 passed, 0 failed, 5 pending en cada fase | Dos períodos diarios vencidos del mismo Budget (`2026-03-31`, `2026-04-01`), sin claves duplicadas.                                                                                                                                  |
| C — desactivación               | `cancellation-setup` + `cancellation-capture` | 1 passed, 0 failed, 5 pending en cada fase | Desactivar y reactivar no revive el cierre histórico; la carrera exacta «descubierto pero no capturado» queda además cubierta por la integración PostgreSQL `cancels_discovered_closing_when_preference_is_disabled_before_capture`. |
| D — evento ya capturado         | `capture` y `lifecycle-capture`               | 1 passed, 0 failed, 5 pending en cada fase | Desactivar una preferencia después de materializar no retira la notificación ni modifica su snapshot.                                                                                                                                |
| E — ciclo de vida y propietario | `lifecycle-capture`                           | 1 passed, 0 failed, 5 pending              | Último período activo de `ONCE` y `FINISH`, período plenamente pausado sin cierre futuro y usuario `admin` sin acceso a notificaciones del dueño.                                                                                    |

La recuperación esperó claves lógicas concretas, no sólo un contador de notificaciones:
el helper Cypress nuevo no considera la fase lista hasta que cada `PERIOD_CLOSED:<budget>:<period>`
esperado está materializado.

### Gates complementarios

| Comando                                               | Resultado                                                                                                                             |
| ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `npm run jest`                                        | **PASS** — 68 suites, 728 pruebas; hay advertencias de `act` existentes en la salida.                                                 |
| `npm run lint -- --no-fix`                            | **PASS** — 0 errores; 3 advertencias de complejidad preexistentes (`analytics-explorer`, `analytics-results-panel`, `budget-create`). |
| `npm run webapp:prod`                                 | **PASS** — advertencias existentes de tamaño de assets y las mismas 3 advertencias ESLint.                                            |
| `npm run webapp:build`                                | **PASS** — las mismas advertencias sin errores.                                                                                       |
| `./mvnw -ntp --batch-mode -DskipTests -Pprod package` | **PASS**.                                                                                                                             |
| `git diff --check` / `git diff --cached --check`      | **PASS** / **PASS**.                                                                                                                  |

### Entorno y limitaciones

Sólo se crearon y se detuvieron una app E2E temporal y un contenedor PostgreSQL temporal.
Los puertos y procesos normales 8080, 9000, 9060 y 5432 no se cambiaron ni se detuvieron;
no se leyó ni escribió la base financiera de desarrollo. No hubo `stage`, commit, stash,
reset ni cambio de rama.

No queda un P0/P1 de BUDGET-6C-C conocido. Las advertencias de complejidad, tamaño de
assets y convergencia de dependencias que el build informa son preexistentes y ajenas a
este milestone. La presentación financiera completa, navegación histórica y copy final
de `PERIOD_CLOSED` continúan correctamente diferidos a BUDGET-6C-D; el productor y la
compatibilidad segura del centro de notificaciones ya están listos para esa fase.

## Diferido a BUDGET-6C-D y extensión futura

BUDGET-6C-D debe dar a `PERIOD_CLOSED` presentación financiera completa, textos,
deep-links y experiencia histórica. Esta fase sólo garantiza que la campana, vista
previa e historial no fallen con un tipo nuevo.

Un futuro programador de reportes financieros puede reutilizar los patrones de cursor,
work item, lease y outbox, pero requerirá sus propios modelos y reglas de negocio. No
se creó una tabla genérica ni API de automatización especulativa para adelantar esa
función.
