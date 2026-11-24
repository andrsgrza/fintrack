# BUDGET-FINAL-2 — aceptación integrada de Budget V1

**Estado final: PASS — evidencia integrada completada; listo para cierre de Budget V1.**

Fecha de ejecución: 18 de noviembre de 2026. Rama y punto de partida verificados: `implement-budget`,
`1e69b98cc61dd0a4922ea8960fd8a3977c5e3d70`. El árbol ya contenía cambios de Budget aceptados y sin
commit; se preservaron. No hubo `stage`, commit, stash, reset, switch de rama, cambio de configuración
ni acceso a la base financiera de desarrollo.

## Integridad y correcciones mínimas

El manifiesto de fuentes modificadas al inicio, sin este informe ni artefactos generados, tenía la huella
agregada `4be30648397427888fcd05ffe0107c7dff56125b089536fbef1c5830146afe8a`. La huella final de esas
fuentes, excluyendo este informe para evitar una autorreferencia, es
`6735543fc64c15fdae2bccd240a9cadbb56b050de07ef886ae2e11e74045ad9a`.

Durante la aceptación se confirmó y corrigió una presentación real: las cantidades decimales canónicas de
umbral, por ejemplo `50.0000`, aparecían en el navegador como `50.0000%`. `budget-utils.ts` ahora elimina
ceros superfluos sin convertir a `number`, sin redondear y manteniendo un mínimo de dos decimales de
presentación: `50.0000` se muestra como `50.00%`.

También se corrigieron expectativas Cypress que no correspondían al contrato actual:

- Fechas derivadas del reloj del navegador fueron sustituidas por las fechas del reloj E2E fijo.
- Los importes REST se comparan como los `string` exactos del contrato monetario.
- La expectativa de cierre usa el texto actual `Período cerrado`.
- Se eliminaron dos errores de lint de las pruebas, sin debilitar sus aserciones.

Un `500` reproducido al crear movimientos sobre una PostgreSQL temporal reutilizada resultó ser contaminación
de fixtures: la misma operación pasó sobre una PostgreSQL nueva. No se alteró producto para ocultarlo.

## Evidencia ejecutada antes de la aceptación completada

| Compuerta                                             | Resultado                                                                                                                                                                                                         |
| ----------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `./mvnw -ntp --batch-mode verify`                     | Esta primera agregación indicó 678 XML de Surefire y 1,378 de Failsafe. La reconciliación final demostró que 678 incluía XML `*IT` obsoletos en `target/surefire-reports`; no es el conteo unitario final válido. |
| `npm run jest`                                        | PASS: 69 suites, 751 pruebas. Hay advertencias `act(...)` heredadas de Popper, sin fallas.                                                                                                                        |
| `npm run lint -- --no-fix`                            | PASS con 0 errores. Persisten 3 advertencias de complejidad preexistentes: dos de Analytics y una de `budget-create.tsx`.                                                                                         |
| `npm run webapp:prod`                                 | PASS. Cuatro advertencias de tamaño de bundle/precache y las tres advertencias de lint anteriores.                                                                                                                |
| `./mvnw -ntp --batch-mode -DskipTests -Pprod package` | PASS. Mantiene advertencias conocidas de convergencia Maven y de cobertura JaCoCo anterior; no afecta el artefacto.                                                                                               |
| `git diff --check` y `git diff --cached --check`      | PASS. No hay archivos staged.                                                                                                                                                                                     |

## Navegador real, REST y PostgreSQL aislados

Todas estas ejecuciones usaron Spring temporal en `18083` y PostgreSQL Docker desechable (`15434`–`15437`),
nunca `8080`, `9000`, `9060` ni la base de desarrollo.

- Precisión monetaria: `budget-monetary-precision-real.cy.ts`, **3/3 PASS**. Capturas desktop y móvil en
  `target/cypress/screenshots/budget-monetary-precision-real.cy.ts/`.
- Preferencias de notificación: `budget-notification-preferences-real.cy.ts`, **1/1 PASS**.
- Centro de notificaciones, persistencia y estado leído: `budget-notifications-real.cy.ts`, **1/1 PASS**.
- Overview de presupuestos: los ocho escenarios del spec se ejecutaron individualmente y pasaron, incluidos
  alcance, calendario, ingreso/gasto, exclusión de transferencias internas, revisiones y ciclo de vida.
- Cierre de período productor: las fases `setup` y `capture` de
  `budget-period-close-real.cy.ts` pasaron en PostgreSQL nueva. Confirman movimiento real de MXN 60.00,
  límite 100.00, evento inmutable único y desactivación posterior sin retractarlo.
- Cierre de período y UX: las fases `setup`, `capture` y `presentation` de
  `budget-period-close-notification-real.cy.ts` pasaron. Comprueban gasto 60.00/100.00, ingreso
  150.00/200.00, cero de actividad, campana, historial, marcado como leído y navegación al período histórico.
  Las cuatro capturas inspeccionadas están en
  `target/cypress/screenshots/budget-period-close-notification-real.cy.ts/`.

La inspección visual de la campana confirmó que los tres cierres presentan importes, porcentaje, período y
fecha de registro. La lista es desplazable y conserva las notificaciones sin ocultar los datos financieros.

## Matriz de aceptación

| Área                                                                               | Evidencia                                                                          | Estado |
| ---------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- | ------ |
| Precisión y redondeo monetario                                                     | Jest, Cypress real y formato textual exacto                                        | PASS   |
| Ingresos, gastos, transferencias ligadas y evaluaciones                            | Maven/Failsafe, Cypress overview y fixtures independientes                         | PASS   |
| Revisión histórica, períodos, estados y calendario                                 | Maven/Failsafe y Cypress overview                                                  | PASS   |
| Preferencias, umbrales, outbox y lectura                                           | Java/PostgreSQL, Cypress real                                                      | PASS   |
| Cierre inmutable, cero válido y presentación                                       | Cypress con reloj cruzando la frontera y PostgreSQL real                           | PASS   |
| Aislamiento de propietario y entradas inválidas                                    | Failsafe y fase Cypress TZ-E real con dos usuarios                                 | PASS   |
| Zona horaria completa, DST y transiciones encadenadas                              | Fases Cypress TZ-B, TZ-C, TZ-D y TZ-E con reinicios de reloj y PostgreSQL temporal | PASS   |
| Recuperación de cierres perdidos, pausa/finish/once y cancelación antes de captura | Fases Cypress multietapa y `BudgetNotificationEngineIT` PostgreSQL                 | PASS   |

## Aceptación completada: fases reales que faltaban

### Matriz integrada

| Escenario                           | Spec existente                           | Evidencia nueva real                                                                | Resultado |
| ----------------------------------- | ---------------------------------------- | ----------------------------------------------------------------------------------- | --------- |
| TZ-1: A → B → C                     | `budget-time-zone-acceptance-real.cy.ts` | B, C_SETUP, C_ADVANCE, D_SCHEDULE y D_BOUNDARY                                      | PASS      |
| TZ-2: independencia del navegador   | el mismo spec                            | B con emulación Chrome `America/New_York` y perfil persistido `America/Mexico_City` | PASS      |
| TZ-3: DST New York                  | el mismo spec                            | D_DST_PRE, D_DST_POST_JUMP y D_DST_NEXT_BOUNDARY                                    | PASS      |
| Seguridad timezone/owner            | el mismo spec                            | E con `user`, `admin`, escrituras inválidas y solicitud sin token                   | PASS      |
| CLOSE-1: recuperación               | `budget-period-close-real.cy.ts`         | lifecycle-setup + lifecycle-capture tras reinicio y avance de reloj                 | PASS      |
| LIFE-1/3/4: PAUSED, FINISH y ONCE   | el mismo spec                            | lifecycle-capture contra scheduler, cursor y outbox reales                          | PASS      |
| LIFE-2: RESUME                      | `budget-lifecycle-real.cy.ts`            | schedule → boundary → resumed                                                       | PASS      |
| PREF-1: desactivar antes de captura | `budget-period-close-real.cy.ts`         | cancellation-setup + cancellation-capture                                           | PASS      |
| PREF-2: cierre ya capturado         | `budget-period-close-real.cy.ts`         | setup/capture histórico preservado y desactivación posterior                        | PASS      |
| Reintento, lease y atomicidad       | `BudgetNotificationEngineIT`             | Failsafe PostgreSQL de la compuerta final                                           | PASS      |

### Entorno y comandos reproducibles

Se usó Chrome headless real, React servido por Spring Boot temporal, autenticación real, APIs financieras reales y
PostgreSQL Docker desechable. Spring temporal siempre escuchó únicamente en `18083`; PostgreSQL temporal usó
`15438`–`15442`. Ninguna fase se conectó a `8080`, `9000`, `9060` ni a `5432` de desarrollo.

Las fases TZ usaron el mismo fixture `final2a-tz` sobre una base temporal persistida entre reinicios:

```text
npm run e2e:cypress -- --config baseUrl=http://127.0.0.1:18083,retries=0 \
  --env BUDGET_TIME_ZONE_ACCEPTANCE_PHASE=<B|C_SETUP|C_ADVANCE|D_SCHEDULE|D_BOUNDARY>,BUDGET_TIME_ZONE_ACCEPTANCE_FIXTURE=final2a-tz \
  --spec src/test/javascript/cypress/e2e/account/budget-time-zone-acceptance-real.cy.ts
```

Relojes: `2026-03-15T18:00:00Z` para B/C_SETUP/D_SCHEDULE; reinicio con
`2026-03-16T07:00:00Z` para C_ADVANCE/D_BOUNDARY. B pasó bajo una zona de navegador distinta sin efectuar ningún
`PUT` automático. C_SETUP confirmó A = `America/Mexico_City`, sustituyó B por C = `UTC` para el siguiente período;
C_ADVANCE confirmó el período 1 en UTC y el período histórico 0 todavía en A, incluido reload.

DST usó el fixture `final2a-dst`, perfil `America/New_York`, presupuesto diario anclado el 8-mar:

| Reloj fijo UTC         | Hora local NY | Aserción                                     |
| ---------------------- | ------------- | -------------------------------------------- |
| `2026-03-08T06:30:00Z` | 01:30 EST     | período 0: 8-mar → 9-mar                     |
| `2026-03-08T07:30:00Z` | 03:30 EDT     | el salto DST no inventa otro período         |
| `2026-03-09T04:30:00Z` | 00:30 EDT     | período 1: 9-mar → 10-mar y transición a UTC |

El límite correcto posterior al salto es `2026-03-09T04:00:00Z`, no `00:00:00Z`: medianoche local cambia de
UTC-05 a UTC-04. Una primera invocación diagnóstica a `2026-03-09T00:30:00Z` falló porque todavía era 20:30 local
del 8-mar; no fue defecto de producto ni se alteró la aserción. La repetición al límite correcto pasó.

TZ-E se ejecutó sobre PostgreSQL nueva (`15442`) con el reloj `2026-03-15T18:00:00Z`:

```text
... --env BUDGET_TIME_ZONE_ACCEPTANCE_PHASE=E,BUDGET_TIME_ZONE_ACCEPTANCE_FIXTURE=final2a-security ...
```

Pasó la confirmación explícita, `404` de recurso Budget ajeno, rechazo `400` de zonas inválidas y `401` sin token,
sin mutar evaluación, preferencias ni notificaciones del propietario.

Las fases CLOSE usaron PostgreSQL nueva en `15440`, con reloj `2026-03-31T18:00:00Z` para creación y
`2026-04-02T18:00:00Z` después del reinicio:

```text
... --env BUDGET_PERIOD_CLOSE_PHASE=<lifecycle-setup|cancellation-setup|lifecycle-capture|cancellation-capture>,BUDGET_PERIOD_CLOSE_LIFECYCLE_FIXTURE=final2a-lifecycle \
  --spec src/test/javascript/cypress/e2e/entity/budget-period-close-real.cy.ts
```

Las cuatro fases pasaron. El scheduler descubrió dos cierres perdidos del recurrente, materializó claves lógicas
únicas para 31-mar y 1-abr, conservó un único cierre de ONCE y el último período activo de FINISH, no produjo un
cierre para el período completamente PAUSED, y no inventó períodos futuros. La desactivación previa a captura hizo
inalcanzable el evento histórico incluso al reactivar; una desactivación posterior preservó el snapshot/outbox ya
capturado, sin duplicarlo. La repetición de lifecycle-capture confirmó idempotencia de la recuperación.

RESUME se probó en PostgreSQL nueva (`15441`) con los relojes 15, 16 y 17-mar a `18:00:00Z`:

```text
... --env BUDGET_LIFECYCLE_PHASE=<schedule|boundary|resumed>,BUDGET_LIFECYCLE_FIXTURE=final2a-resume \
  --spec src/test/javascript/cypress/e2e/entity/budget-lifecycle-real.cy.ts
```

Las tres fases pasaron. La pausa y finalización quedaron pendientes hasta la frontera; el 16-mar el presupuesto
pausado no era evaluable y el finalizado conservaba historial; la reanudación se hizo efectiva el 17-mar, abrió un
período activo nuevo con gasto `$0.00`, y no trasladó el gasto de `$25.00` del período pausado. Capturas reales:
`target/cypress/screenshots/budget-lifecycle-real.cy.ts/budget-5b-c-paused-desktop.png` y
`target/cypress/screenshots/budget-lifecycle-real.cy.ts/budget-5b-c-resumed-desktop.png`.

### Conteos Cypress nuevos

La evidencia aceptada de esta completación contiene **16 aserciones seleccionadas ejecutadas, 16 PASS, 0 FAIL**.
Cada ejecución de fase muestra los demás `it` condicionales como pending; éstos no se cuentan como aprobados. Se
registra por transparencia una invocación diagnóstica DST fallida por usar `00:30Z` antes de la frontera local; la
invocación corregida a `04:30Z` es la evidencia aceptada. No se usaron intercepts para falsear respuestas
financieras, ni esperas arbitrarias para carreras del scheduler.

## Matriz de seguridad REST

| Área                  | Contrato verificado                                                                                                                                    |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Budget y evaluaciones | `/api/budgets`, overview, evaluación, historial, revisión y lifecycle son owner-scoped; `BudgetResourceIT` y TZ-E rechazan lectura/mutación ajena.     |
| Alcance financiero    | cuentas, categorías y tags de un Budget se validan contra el propietario en los servicios/recursos de Budget y sus IT.                                 |
| Preferencias          | `GET/PUT /api/budgets/{id}/notification-preferences` son del propietario; su versión protege concurrencia.                                             |
| Zona horaria          | `PUT /api/account/budget-time-zone` modifica sólo el perfil autenticado; TZ-E cubre inválidos y ausencia de token.                                     |
| Notificaciones        | `GET /api/notifications` y su estado leído se filtran por owner; lifecycle-capture comprobó que `admin` no observa cierres de `user`.                  |
| Scheduler/outbox      | no expone un endpoint público de work items; cursor, work item, outbox y captura derivan del Budget owner y se cubren en `BudgetNotificationEngineIT`. |

## Reconciliación exacta de Maven

El conteo correcto no se obtiene sumando todo XML que permanezca en `target/`. Maven no borra XML de ejecuciones
anteriores cuando una clase `*IT` deja de ser responsabilidad de Surefire. Tras la compuerta final, Surefire emitió
**98 clases / 659 pruebas / 0 fallas / 0 errores / 0 omitidas**. Había cinco XML `*IT` obsoletos en
`target/surefire-reports` (19 pruebas) que llevaban la suma ingenua a 678. Failsafe emitió **51 clases / 1,378
pruebas / 0 fallas / 0 errores / 0 omitidas**.

El conteo histórico 686/1,377 no conserva los XML ni un manifiesto de fuentes de aquella ejecución, por lo que no
es posible atribuir honestamente sus ocho pruebas de diferencia a archivos concretos. No hubo cambio de configuración
de discovery: `pom.xml` sigue excluyendo `*IT` de Surefire e incluyendo `*IT`/`*IntTest` en Failsafe. La conclusión
reproducible es que la comparación 686 vs 678 no era válida: 678 ya estaba contaminado por XML obsoletos. No se
eliminó ni restauró cobertura para fabricar un número; el resultado final se reporta sólo con XML emitidos durante
la ejecución actual.

## Compuertas finales ejecutadas en esta completación

| Comando                                               | Resultado actual                                                                                                                                                           |
| ----------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `./mvnw -ntp --batch-mode verify`                     | PASS; Surefire 659, Failsafe 1,378, 0/0/0 fallas/errores/omitidas. Incluye PostgreSQL/Testcontainers, engine de notificación/outbox, calendario, revisiones y security IT. |
| `./mvnw -ntp --batch-mode test`                       | PASS; 659 Surefire emitidas, 0 fallas/errores/omitidas; usado para confirmar el conteo.                                                                                    |
| `npm run jest`                                        | PASS; 69 suites, 751 pruebas, 0 fallas.                                                                                                                                    |
| `npm run lint -- --no-fix`                            | PASS; 0 errores, 3 advertencias de complejidad históricas.                                                                                                                 |
| `npm run webapp:prod`                                 | PASS; mismas 3 advertencias y 4 advertencias históricas de tamaño/precache.                                                                                                |
| `./mvnw -ntp --batch-mode -DskipTests -Pprod package` | PASS; sólo advertencias conocidas de convergencia/JaCoCo.                                                                                                                  |
| `git diff --check` / `git diff --cached --check`      | PASS; no había staged files.                                                                                                                                               |

`verify` también ejecutó el build Webpack de desarrollo. Las advertencias de conexión SMTP vistas en pruebas son
casos controlados de pruebas de fallback y no errores de suite.

## Estado de fuente y trazabilidad

- Rama: `implement-budget`.
- HEAD: `1e69b98cc61dd0a4922ea8960fd8a3977c5e3d70`.
- Árbol: cambios Budget preexistentes preservados, sin staging ni commit durante esta aceptación.
- Manifiesto: 47 entradas de `git diff --name-only` + `git ls-files --others --exclude-standard`, ordenadas, con
  SHA-256 por archivo verificado durante esta aceptación. Se excluye únicamente `docs/BUDGET-FINAL-2.md` para
  evitar autorreferencia y se excluye `target/` por ser generado.
- Huella agregada SHA-256 de ese manifiesto: `6735543fc64c15fdae2bccd240a9cadbb56b050de07ef886ae2e11e74045ad9a`.

Para reproducir la huella:

```text
{ git diff --name-only; git ls-files --others --exclude-standard; } | rg -v '^docs/BUDGET-FINAL-2\\.md$' | sort -u \
  | while IFS= read -r file; do shasum -a 256 "$file"; done | shasum -a 256
```

## Defectos, limitaciones y decisión de readiness

El único defecto de producto corregido durante BUDGET-FINAL-2 sigue siendo el formato textual de umbrales
decimales descrito arriba; sus regresiones Jest, Cypress y los gates finales pasaron. No se descubrieron defectos
P0, P1 o P2 abiertos de Budget V1. La invocación DST con reloj equivocado fue una corrección de procedimiento de
prueba, no un cambio de código ni una debilidad de la aserción.

**Veredicto final: PASS — READY TO CLOSE BUDGETS V1.** La aceptación de producto no sustituye el posterior
baseline/commit de release: el árbol sigue deliberadamente dirty y éste no fue modificado en Git.

## Limpieza y preservación del entorno

Se crean y eliminan únicamente los procesos Spring temporales en `18083` y los contenedores Docker
`fintrack-budget-final2a-*` en puertos `15438`–`15442`. Los puertos y procesos normales (`8080`, `9000`, `9060`,
`5432`) no se modificaron ni se detuvieron; tampoco se tocó ningún registro financiero de desarrollo.
