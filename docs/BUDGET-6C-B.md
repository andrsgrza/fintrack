# BUDGET-6C-B — Zona horaria financiera de presupuestos

## Contrato de producto

FINTRACK puede detectar la zona IANA del navegador como una **sugerencia** durante la
configuración inicial. La sugerencia no realiza ninguna escritura: la persona debe
confirmarla explícitamente o escribir/seleccionar otra zona IANA válida en Ajustes de
cuenta. La zona se guarda sólo después de que el servidor la valida.

Una zona ya guardada es autoritativa. Si `America/Mexico_City` está guardada y el
navegador después informa `America/New_York`, FINTRACK continúa usando la primera hasta
que la persona guarda un cambio explícito. La detección no se repite como un aviso de
configuración ni modifica el servidor al abrir, recargar, iniciar sesión o crear un
presupuesto.

Una cuenta sin zona confirmada sigue siendo utilizable. No se inventa una confirmación:
las lecturas de presupuestos sin una asignación histórica usan el _fallback_ explícito
preexistente (zona de perfil válida y, en último caso, UTC) para compatibilidad. Las
alertas opcionales siguen requiriendo una asignación duradera y no se habilitan por ese
fallback.

## Dos zonas con responsabilidades distintas

`User.timeZone` es la preferencia actual confirmada del usuario. Sirve como valor por
defecto al crear presupuestos nuevos y para planificar cambios futuros.

`BudgetTimeZoneAssignment` guarda la zona efectiva desde el inicio de un período de un
presupuesto. Esa asignación es la fuente de verdad para calcular y volver a abrir ese
período. Por ello una zona de perfil y la zona del período actual pueden diferir de
forma válida:

1. El período de mayo está asignado a `America/Mexico_City`.
2. El 15 de mayo se confirma `America/New_York` como nueva preferencia de perfil.
3. Mayo permanece en México; la siguiente frontera real de ese presupuesto obtiene Nueva
   York.
4. Al consultar mayo después, se conserva México aunque el perfil ya sea Nueva York.

No se recalculan límites mensuales, trimestrales, anclados o históricos a partir de una
preferencia actual ni de la zona del navegador. Las reglas de calendario existentes
siguen calculando fronteras, incluidos fin de mes, 29 de febrero y horario de verano.

## Efectividad y consistencia transaccional

`BudgetTimeZoneService.scheduleProfileTimeZoneChange` bloquea primero al propietario,
como las demás mutaciones de presupuesto. En la misma transacción valida la zona,
actualiza el perfil y crea o reemplaza solamente la asignación pendiente de la siguiente
frontera aplicable calculada por `BudgetTimeline`.

- El período abierto nunca se reescribe.
- Las asignaciones históricas no se borran ni se actualizan.
- Si A→B→C ocurre antes de la siguiente frontera, se sustituye la asignación pendiente
  B por C; no se duplican filas para la misma fecha efectiva.
- Reenviar la misma zona es idempotente: no crea asignaciones ni eventos.
- Presupuestos `ONCE`, finitos o terminados sin período futuro no bloquean el cambio de
  perfil ni reciben períodos ficticios. Los presupuestos pausados o futuros siguen las
  fronteras que realmente les correspondan.
- Si una operación requerida falla, la transacción no deja perfil y asignaciones en
  estados parciales.

El modelo ya tenía `user.time_zone` nullable y `BudgetTimeZoneAssignment`; BUDGET-6C-B
no necesita otra tabla ni una migración. `null` distingue correctamente una configuración
no confirmada de una elección explícita.

## API segura

La única escritura de perfil que conserva estas invariantes es:

| Método | Ruta                            | Resultado                                                                                           |
| ------ | ------------------------------- | --------------------------------------------------------------------------------------------------- |
| `GET`  | `/api/account/budget-time-zone` | Zona confirmada, indicador `configurationRequired` y cambios recién aplicados (vacío al leer).      |
| `PUT`  | `/api/account/budget-time-zone` | Valida una zona IANA, actualiza el perfil y devuelve las fechas efectivas por presupuesto afectado. |

La identidad procede de la sesión autenticada; no se acepta un propietario en la carga.
Las rutas requieren autenticación y el servidor rechaza identificadores vacíos, inválidos
o malformados. La API general de cuenta no recibe un campo de zona horaria y por tanto no
puede saltarse el servicio de asignaciones de Budget.

## Interfaz de Ajustes

La sección **Zona horaria financiera / Financial time zone** vive dentro de los Ajustes
de cuenta existentes. Muestra la zona guardada o la sugerencia inicial, permite escribir
o buscar identificadores IANA mediante un `datalist` construido con
`Intl.supportedValuesOf('timeZone')` cuando el navegador lo ofrece y conserva la
validación definitiva en Java. Incluye estado de carga, cancelar, errores del servidor,
confirmación y fechas efectivas reales calculadas por el backend. Todo el texto está en
español o inglés.

Cancelar revierte el borrador a la última zona autoritativa conocida. No hace una llamada
de escritura.

## Overview, detalle y notificaciones

Los parámetros históricos explícitos `referenceDate` continúan siendo fechas de
calendario elegidas por la persona. Cuando Overview se abre sin fecha, el backend obtiene
la fecha de cada tarjeta con su zona efectiva actual; no usa la fecha local del
navegador. Detail obtiene la frontera del período actual desde el backend antes de cargar
el resultado. Los parámetros de zona conservados por compatibilidad REST no son
autoritativos.

La evaluación resuelve primero el período solicitado y luego su
`BudgetTimeZoneAssignment`, antes de delegar al único ejecutor de Analytics V2. No hay
otro motor de períodos ni de cálculos financieros. Las notificaciones existentes y sus
instantáneas no se reescriben, las preferencias BUDGET-6C-A no cambian y un cambio de
zona no produce umbrales ni cierres artificiales. Un futuro productor de cierre
(BUDGET-6C-C) debe consumir la misma asignación efectiva por período.

## Evidencia focalizada inicial de BUDGET-6C-B

Ejecutado el 18 de noviembre de 2026 con PostgreSQL temporal de Testcontainers:

```text
./mvnw -ntp --batch-mode -Dskip.npm=true test-compile surefire:test \
  -Dtest=BudgetTimeZoneServiceTest,BudgetProfileTimeZoneServiceTest,\
BudgetEvaluationServiceTest,BudgetProfileTimeZoneResourceIT,BudgetOverviewServiceIT
```

Resultado: **20 pruebas, 0 fallos, 0 errores, 0 omitidas**.

Cobertura focalizada:

- sugerencia de navegador sin escritura y confirmación explícita;
- validación IANA, valor vacío/incorrecto, UTC, lectura autenticada y rechazo sin sesión;
- API general de cuenta sin vía de salto;
- idempotencia, A→B→C pendiente y preservación del período histórico;
- presupuesto terminal `ONCE` que no bloquea el perfil;
- resolución de evaluación que ignora una zona enviada por navegador;
- Overview sin fecha que resuelve la fecha actual en la zona efectiva del presupuesto;
- persistencia REST y estado real de PostgreSQL temporal.

Pruebas Jest focalizadas ejecutadas antes de la documentación:

```text
npm run jest -- budget-time-zone-settings.spec.tsx \
  budget-overview.spec.tsx budget-detail.spec.tsx budget-edit.spec.tsx
```

Resultado: **4 suites, 21 pruebas aprobadas**. Cubren detección sin guardado, prioridad
de la zona persistida, cancelar, confirmación y que Overview/Detail no calculen la fecha
financiera desde la zona del navegador.

Evidencia de navegador ejecutada en Chrome 157 contra Spring Boot temporal en
`localhost:18080`, reloj fijo `2026-03-15T18:00:00Z` y PostgreSQL 17 temporal en
`127.0.0.1:15432`:

```text
npm run e2e:cypress -- --config baseUrl=http://localhost:18080 \
  --spec src/test/javascript/cypress/e2e/account/budget-time-zone-real.cy.ts
```

Resultado: **1 prueba, 1 aprobada, 0 fallos, 0 pendientes, 0 omitidas**. No se
simularon respuestas de Budget ni de perfil: el navegador comprobó una sugerencia,
ausencia de `PUT` antes de confirmar, confirmación real y persistencia después de
recargar. El proceso Spring y el contenedor PostgreSQL fueron detenidos y eliminados al
terminar.

## Aceptación final — 18 de noviembre de 2026

La aceptación completa usó exclusivamente Spring Boot temporal con perfil `dev,e2e` en
`localhost:18080` y PostgreSQL 17 descartable en `127.0.0.1:15432`. No se usaron ni se
modificaron los puertos 8080/9000/9060, los procesos existentes ni la base financiera
de desarrollo. Cypress observó HTTP real; no simuló respuestas de perfil, Budget,
Analytics ni notificaciones.

### Browser → REST → PostgreSQL real

Chrome 157, Cypress 14.3.2 y Node 22.14.0 ejecutaron **11 pruebas: 11 aprobadas, 0
fallos, 0 pendientes y 0 omitidas**.

| Flujo                 | Evidencia real                                                                                                                                                                                                                                                                                                                                                                     |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| A                     | La regresión `budget-time-zone-real.cy.ts` pasó 1/1: sugerencia sin escritura, confirmación explícita y recarga.                                                                                                                                                                                                                                                                   |
| B                     | `America/Mexico_City` siguió siendo autoritativa cuando Chrome se cambió mediante CDP a `America/New_York`; no hubo `PUT` automático, ni antes ni después de recargar.                                                                                                                                                                                                             |
| C                     | El perfil pasó A (`America/Mexico_City`) → B (`America/Los_Angeles`) → C (`UTC`) antes de la misma frontera. Al reiniciar sólo la aplicación temporal en `2026-03-16T07:00:00Z`, el período nuevo usó C y el histórico conservó A, con importes reales de MX$70 y MX$50.                                                                                                           |
| D                     | Mensual, trimestral, diario pausado, terminado y `ONCE` recibieron sólo las asignaciones futuras aplicables. Pausado permaneció `PAUSED`/`NOT_EVALUATED`; terminado y `ONCE` no recibieron períodos ficticios. DST de Nueva York se comprobó antes del salto (`2026-03-08T06:30Z`), tras él (`07:30Z`) y en la siguiente frontera UTC (`2026-03-09T04:30Z`), sin un período extra. |
| E                     | Se verificaron aislamiento propietario/otro usuario (404 que oculta el recurso), IANA nula, vacía y desconocida (400), escritura sin sesión (401), y que un cambio válido no altera evaluación abierta, preferencias, `periodCloseEnabledSince`, ni la página de notificaciones y sus `readAt`/instantáneas.                                                                       |
| Regresión BUDGET-6C-A | `budget-notification-preferences-real.cy.ts` pasó 1/1 con preferencias reales y PostgreSQL temporal.                                                                                                                                                                                                                                                                               |

La especificación por fases está en
`src/test/javascript/cypress/e2e/account/budget-time-zone-acceptance-real.cy.ts`. Las
fases C y D requieren reiniciar sólo la aplicación temporal con un reloj fijo nuevo,
preservando la misma base descartable para demostrar evolución histórica:

```text
CYPRESS_BUDGET_TIME_ZONE_ACCEPTANCE_PHASE=<B|C_SETUP|C_ADVANCE|D_SCHEDULE|D_BOUNDARY|D_DST_PRE|D_DST_POST_JUMP|D_DST_NEXT_BOUNDARY|E> \
CYPRESS_BUDGET_TIME_ZONE_ACCEPTANCE_FIXTURE=<aislado> \
npm run e2e:cypress -- --config baseUrl=http://localhost:18080 \
  --spec src/test/javascript/cypress/e2e/account/budget-time-zone-acceptance-real.cy.ts
```

Durante la elaboración se corrigieron dos aislamientos de la prueba, no defectos de
producción: una fixture usaba alcance `ALL` y leía una transacción de otra fixture, y
la aserción de aislamiento se alineó al contrato existente 404 (no revela la existencia
del Budget). Cada fase final fue repetida y aprobada después de esas correcciones.

### Java: Surefire y Failsafe

La ejecución completa fue:

```text
./mvnw -ntp --batch-mode verify -DskipTests=false -DskipITs=false
```

Los XML actuales separan correctamente las fases configuradas en `pom.xml`:

- **Surefire:** 99 clases de reporte; **663 pruebas**, 0 fallos, 0 errores, 0 omitidas.
- **Failsafe:** 51 clases de reporte; **1,370 pruebas**, 0 fallos, 0 errores, 0 omitidas.
- **Total Java:** **2,033** pruebas sin doble conteo. Failsafe ejecuta `*IT*` y
  `*IntTest*` durante `integration-test`/`verify`; Surefire los excluye.

La cobertura PostgreSQL/Testcontainers relevante incluye
`BudgetProfileTimeZoneResourceIT` (5), `BudgetOverviewServiceIT` (1),
`BudgetResourceIT` (13) y `BudgetEvaluationServiceIT` (8). Surefire incluye
`BudgetTimeZoneServiceTest` (5) y `BudgetProfileTimeZoneServiceTest` (3).

### Calidad completa

- `npm run jest`: **68 suites, 728 pruebas, 0 fallos**; sólo advertencias preexistentes
  de `act()` de React/Popper.
- `npm run lint -- --no-fix`: éxito, sin errores. Quedan tres advertencias preexistentes
  de complejidad en `analytics-explorer.tsx`, `analytics-results-panel.tsx` y
  `budget-create.tsx`.
- `npm run webapp:build` y `npm run webapp:prod`: éxito; producción conserva esas tres
  advertencias y los avisos existentes de tamaño/precache de Webpack.
- `./mvnw -ntp --batch-mode -DskipTests -Pprod package`: éxito; generó
  `target/fintrack-0.0.1-SNAPSHOT.jar`.
- `git diff --check` y `git diff --cached --check`: sin errores.

## Límites deliberadamente diferidos

Esta aceptación no implementa el programador de cierre de períodos,
trabajos/reintentos de cierre ni notificaciones `PERIOD_CLOSED`; pertenecen a
BUDGET-6C-C/D. No son una limitación de la zona financiera aceptada aquí.
