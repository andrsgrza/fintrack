# BUDGET-UX-1 — Preferencias de notificación de presupuestos

## Alcance

Este ajuste mejora únicamente el editor compartido de preferencias de notificación que se usa al crear un presupuesto y en **Notificaciones** dentro del detalle. No cambia revisiones, períodos, evaluación financiera, calendario, cálculo de progreso, eventos históricos ni la pantalla de detalle.

## Comportamiento de alertas de porcentaje

- Cuando **Alertas de gasto/ingresos** está desactivado, se muestran el título, la explicación y el interruptor. El selector de porcentajes no se monta ni participa en el orden de tabulación.
- Al activarlo, aparece **Avísame cuando alcance:** con las sugerencias 50 %, 80 % y 100 %, porcentajes personalizados y el contador de valores configurados.
- Las sugerencias son botones con estado accesible `aria-pressed`: seleccionarlas agrega el porcentaje; seleccionarlas otra vez lo quita.
- **+ Añadir porcentaje** revela el control de texto para un porcentaje personalizado. El foco llega al campo al abrirlo y vuelve al botón al añadir o cancelar.
- Se permiten porcentajes positivos con hasta cuatro decimales, sin límite funcional de 100 %, hasta un máximo de 20 valores. No se admiten formatos científicos, negativos, cero, más de cuatro decimales ni equivalentes numéricos duplicados como `50` y `50.0`.
- Desactivar las alertas no borra los porcentajes ya configurados. Al guardarse ese estado, el servidor conserva los valores pero no evalúa cruces; al reactivarlo, vuelven a estar disponibles.
- **Resumen al cerrar el período** es independiente y siempre permanece visible.

## Contrato decimal

`thresholdPercentages` cruza el contrato HTTP como `string[]` de decimales simples. El frontend no convierte estos valores mediante `Number`, `parseFloat` ni ordenamientos numéricos de JavaScript. Mantiene el texto decimal exacto recibido o escrito y compara una forma canónica sólo para validación, duplicados y orden.

El backend serializa los umbrales como cadenas decimales y deserializa cadenas sin pérdida. Sigue aceptando literales JSON numéricos de clientes heredados sin pasar por `double`; la UI actual sólo envía cadenas. La validación de servicio continúa siendo la autoridad para positividad, escala máxima de cuatro, precisión máxima y máximo de veinte umbrales.

## Estados de detalle

- Presupuesto activo o pausado: edición y guardado con control de versión optimista; Cancelar restituye la respuesta autoritativa del servidor.
- Conflicto: se conserva el borrador local y se actualiza el token de versión para un reintento explícito.
- Presupuesto terminado: las preferencias permanecen visibles en modo de sólo lectura; no hay controles editables.
- Los snapshots y notificaciones históricos no se reinterpretan ni se modifican por este cambio de presentación.

## Validación ejecutada

- Jest: 70 suites y 761 pruebas aprobadas, incluida la cobertura del selector condicional, preservación al alternar, sugerencias, `87.1250`, duplicados equivalentes, formatos inválidos, máximo de veinte, modo de sólo lectura y representación textual exacta.
- REST con PostgreSQL aislado por Testcontainers: `BudgetResourceIT`, 15 pruebas aprobadas. Verifica que el endpoint devuelve cadenas y acepta `"87.1250"` sin convertirlo a binario.
- Cypress con Spring Boot y PostgreSQL aislados: 2 escenarios aprobados para preferencias de notificación. Las capturas revisadas cubren creación/escritorio, creación/móvil y un presupuesto terminado de sólo lectura.
- Lint y compilación de producción del frontend aprobados; el lint conserva tres advertencias de complejidad ya existentes fuera de este ajuste.

## Alcance diferido y limitaciones

- BUDGET-UX-2 conserva la decisión pendiente de centralizar la edición; este ajuste no mueve el editor desde creación y detalle.
- BUDGET-UX-3 conserva la revisión estructurada de creación y la presentación de cobertura de categorías.
- Las pruebas de ciclos de cierre y de transiciones de ciclo de vida requieren sus dos fases de reloj aislado. Permanecen cubiertas por sus suites dedicadas; no se modificaron el programador, los snapshots ni el motor de notificaciones en este cambio de interfaz.

## Aceptación final BUDGET-UX-1 — 20 de noviembre de 2026

### Entorno aislado y preservación

La aceptación usó una aplicación Spring Boot temporal en `127.0.0.1:18080` y un PostgreSQL 17.4 temporal en `127.0.0.1:15432`, con perfiles `prod,e2e`, autenticación real y relojes deterministas. Cypress ejerció React → REST → Spring → PostgreSQL; no interceptó ni simuló las APIs financieras, de preferencias o notificaciones. Los servicios temporales se detuvieron al terminar. No se usaron ni modificaron los puertos ni los datos de desarrollo: `8080`, `5432`, `9000` y `9060` permanecieron fuera de este flujo.

### Matriz Cypress real

| Flujo                                                          | Especificación y fase                                                                                                                      | Reloj del backend                                                     | Resultado real                                 |
| -------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------- | ---------------------------------------------- |
| A — creación, persistencia y presentación                      | `budget-notification-preferences-real.cy.ts`                                                                                               | `2026-11-19T18:00:00Z`                                                | 2 aprobadas, 0 fallidas, 0 pendientes          |
| B — cruces financieros de umbral                               | `budget-notifications-real.cy.ts`                                                                                                          | `2026-03-15T18:00:00Z`                                                | 2 aprobadas, 0 fallidas, 0 pendientes          |
| D/E — programación, ciclo de vida y aislamiento entre usuarios | `budget-notification-preferences-lifecycle-real.cy.ts`: `schedule`, `ownership`, `boundary`                                                | `2026-03-15T18:00:00Z`; para `boundary`, `2026-03-16T18:00:00Z`       | 3 aprobadas; 6 pendientes de fase, 0 fallidas  |
| C — cierre de período, recuperación y cancelación              | `budget-period-close-real.cy.ts`: `setup`, `lifecycle-setup`, `cancellation-setup`, `capture`, `lifecycle-capture`, `cancellation-capture` | preparación: `2026-03-31T18:00:00Z`; capturas: `2026-04-02T18:00:00Z` | 6 aprobadas; 30 pendientes de fase, 0 fallidas |

Las 36 pruebas pendientes de esta matriz son ramas deliberadamente no seleccionadas por cada invocación multifase; no se contabilizan como aprobadas. En total se ejecutaron **13 pruebas Cypress reales aprobadas, 0 fallidas y 36 pendientes de fase**.

Las invocaciones usaron el mecanismo de fase ya presente, con el `baseUrl` temporal y el `fixture` compartido correspondientes, por ejemplo:

```bash
npm run e2e:cypress -- --config baseUrl=http://localhost:18080 \
  --spec src/test/javascript/cypress/e2e/entity/budget-notification-preferences-real.cy.ts

CYPRESS_BUDGET_NOTIFICATION_ACCEPTANCE_PHASE=boundary \
  CYPRESS_BUDGET_NOTIFICATION_ACCEPTANCE_FIXTURE=budget-ux1-acceptance-v2 \
  npm run e2e:cypress -- --config baseUrl=http://localhost:18080 \
  --spec src/test/javascript/cypress/e2e/entity/budget-notification-preferences-lifecycle-real.cy.ts
```

#### Evidencia funcional

- **A:** confirma el estado inicial desactivado, desmontaje del selector, sugerencias 50/80/100, porcentaje personalizado, persistencia tras recargar y preservación de valores al alternar las alertas. También confirma el resumen de cierre independiente y la vista de solo lectura de un presupuesto terminado.
- **B:** crea transacciones financieras reales con ámbito de cuenta y categoría. Se comprobaron capturas durables, materialización de outbox, historial/notificación y deduplicación. En la regresión adicional, un cruce de 50 % mientras las alertas estaban desactivadas no se reclama retroactivamente; tras reactivarlas, un nuevo movimiento materializa sólo el cruce posterior de `80.1250`, con importe inmutable de `90`.
- **C:** alternar solamente los umbrales no modifica `periodCloseEnabledSince`. El productor y la outbox reales materializan `PERIOD_CLOSED` después de la frontera y conservan su snapshot inmutable. La recuperación no duplica claves lógicas y una reactivación posterior no revive una captura histórica cancelada.
- **D:** un presupuesto `PAUSED` permite editar y recargar preferencias; uno `FINISHED` conserva su resumen legible, no presenta controles y rechaza el `PUT` directo con `400 INVALID_PAYLOAD` sin modificar el estado persistido.
- **E:** los intentos de otro usuario de leer o escribir preferencias ajenas siguen rechazados conforme a la convención de ocultamiento de propietario; el propietario vuelve a observar umbrales, estado y época de cierre sin cambios.
- **F:** el navegador y las pruebas REST/PostgreSQL confirman equivalencia numérica de `50`, `50.0` y `50.0000`, conservación textual de `80.1250`/`87.1250`, orden decimal exacto, rechazo de duplicados/escala inválida y lectura segura de snapshots históricos mezclados de `THRESHOLD_CROSSED` y `PERIOD_CLOSED`.

Las capturas inspeccionadas se generaron en:

- `target/cypress/screenshots/budget-notification-preferences-real.cy.ts/budget-notification-preferences-desktop.png`
- `target/cypress/screenshots/budget-notification-preferences-real.cy.ts/budget-notification-preferences-mobile.png`
- `target/cypress/screenshots/budget-notification-preferences-real.cy.ts/budget-notification-preferences-finished.png`

Muestran respectivamente el selector de escritorio, su disposición móvil y el resumen terminado de solo lectura. Las tres verifican que el selector de porcentajes y el resumen de cierre no compiten visualmente.

### Regresiones y gates

| Comando                                               | Resultado                                                                                                           |
| ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `./mvnw -ntp --batch-mode verify`                     | **PASS**. Ejecución nueva sin filtros: Surefire 674/674 y Failsafe 1,378/1,378; 0 fallidas, 0 errores y 0 omitidas. |
| `npm run jest`                                        | **PASS**: 70 suites, 761 pruebas.                                                                                   |
| `npm run lint -- --no-fix`                            | **PASS**: 0 errores; conserva las 3 advertencias de complejidad preexistentes.                                      |
| `npm run webapp:build`                                | **PASS**.                                                                                                           |
| `npm run webapp:prod`                                 | **PASS**. Conserva sólo advertencias conocidas de tamaño de activos y complejidad.                                  |
| `./mvnw -ntp --batch-mode -DskipTests -Pprod package` | **PASS**.                                                                                                           |
| `git diff --check` y `git diff --cached --check`      | **PASS**: sin errores de espacios ni contenido staged.                                                              |

La ejecución directa de `failsafe:integration-test failsafe:verify` no es un sustituto válido del ciclo Maven del proyecto: invocada fuera del ciclo que resuelve la propiedad del agente Mockito, deja literal `-javaagent:${org.mockito:mockito-core:jar}` y termina antes de ejecutar tests. No afecta al `verify` requerido anterior, que sí resolvió el agente y generó los 1,378 reportes Failsafe aprobados.

### Ajustes de aceptación, no de producto

- La aserción Cypress se alineó con el selector aprobado: 80 % es una sugerencia con `aria-pressed`, no un chip personalizado.
- Las pruebas de cierre filtran el `logicalEventKey` sólo después de verificar `notificationType === PERIOD_CLOSED`; así los snapshots históricos de umbral, que legítimamente no tienen ese campo, siguen siendo legibles.
- Se ajustaron las capturas al área efectivamente renderizada del editor y resumen, no a la cabecera de página.
- Se añadió la regresión real de desactivar → mutar → reactivar para fijar la semántica de no reclamar cruces retroactivos.

No se modificó código de producción durante esta aceptación; los ajustes son de pruebas, capturas y evidencia.

### Estado final

**BUDGET-UX-1 — PASS, pendiente de revisión de producto.** No hay defectos P0, P1 ni P2 abiertos en este alcance. El siguiente trabajo puede iniciar BUDGET-UX-2 sólo tras la aprobación de producto; esta aceptación no lo implementa.
