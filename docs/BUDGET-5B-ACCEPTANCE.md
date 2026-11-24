# BUDGET-5B — Aceptación integrada automatizada

**Fecha:** 2026-11-16  
**Rama verificada:** `implement-budget`  
**Base verificada:** `5f2f516` antes de los cambios de esta aceptación  
**Resultado:** **PASS**

## Alcance y aislamiento

La aceptación cubrió BUDGET-5B-A, BUDGET-5B-B y BUDGET-5B-C: creación, calendario, alcance, evaluación financiera, revisiones, pausa, reanudación, finalización, edición y eliminación.

Las pruebas reales de navegador se ejecutaron contra Spring Boot temporal en el puerto `18085` y PostgreSQL temporal en `55439`. Los tests Java utilizaron sus propios contenedores PostgreSQL desechables. No se usaron ni modificaron puertos normales, procesos existentes, configuración ni datos financieros de desarrollo.

## Correcciones realizadas durante la aceptación

1. **Eliminación concurrente.** La última eliminación de `Budget` ahora incluye la versión esperada en el `DELETE` SQL. Si otro comando cambió el agregado después de la comprobación inicial, la operación devuelve conflicto y la transacción revierte la limpieza previa de dependencias. Esto cierra la ventana entre la lectura y el borrado final.
2. **Acciones de ciclo de vida.** Al refrescar el detalle tras cancelar una pausa, el encabezado ya no desmonta transitoriamente sus acciones. El menú cierra de forma explícita antes de abrir la confirmación, por lo que una acción consecutiva —incluida eliminar— no se pierde.
3. **Pruebas reales robustas.** Los fixtures de Cypress ahora toman el identificador de una transacción creada desde el encabezado `Location`, evalúan variables después de que Cypress las resuelve y distinguen correctamente el gasto del período pausado del nuevo período reanudado.

## Matriz de evidencia

| Área                              | Evidencia y resultado                                                                                                                                                                                                                                                                 |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Calendario, alcance y alta        | Cypress real crea presupuestos con alcance de cuenta, categoría raíz con descendientes y etiquetas; comprueba la previsualización Java y los tres modos de calendario. **PASS**                                                                                                       |
| Semántica financiera              | Cypress verifica gasto seleccionado al 120%, ingresos que excluyen gasto y piernas de transferencias internas, y recarga desde REST/PostgreSQL. Los tests de evaluación cubren SUM, progreso, restante, estados y disponibilidad. **PASS**                                            |
| Revisiones inmutables             | `BudgetEvaluationServiceIT` conserva enero con revisión/resultado históricos, aplica la revisión vigente a marzo, reevalúa al cambiar una transacción y programa abril sin prorratear. Cypress muestra revisión histórica, edición actual y programación/cancelación futura. **PASS** |
| Pausa, reanudación y finalización | Cypress con reloj fijo ejecutó: programar el 15-mar, comprobar pausa y finalización efectiva el 16-mar, y reanudar el 17-mar. El gasto del 16-mar no se arrastra al período nuevo del 17-mar; éste muestra $0.00. **PASS**                                                            |
| Estados y UX                      | Jest cubre creación, resumen, detalle, edición y acciones. Cypress cubre confirmaciones, mensajes, navegación por períodos y acción consecutiva después de cancelar una pausa. **PASS**                                                                                               |
| Seguridad y propiedad             | `BudgetResourceIT` niega a otro usuario toda lectura y mutación de Budget: evaluación, historial, revisión, pausa/cancelación, reanudación, finalización y eliminación. **PASS**                                                                                                      |
| Concurrencia e idempotencia       | Los tests de comando y REST cubren solicitudes repetidas, versión obsoleta y el `DELETE ... WHERE id AND version`; el recurso permanece cuando la versión ya avanzó. **PASS**                                                                                                         |
| Eliminación                       | Cypress elimina mediante la confirmación real, confirma que el Budget deja de existir y que la cuenta y la transacción continúan accesibles. **PASS**                                                                                                                                 |
| Diseño responsive                 | Capturas reales de detalle en 1280 px y 375 px inspeccionadas: sin desbordamiento horizontal; controles, importes, alcance e historial se conservan legibles. **PASS**                                                                                                                |

## Ejecuciones realizadas

| Comando                                                                                                                            | Resultado                                                                                                                                                                                      |
| ---------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `./mvnw -ntp --batch-mode -Dskip.installnodenpm -Dskip.npm verify`                                                                 | **PASS**. Surefire: 643 pruebas; Failsafe: 1,351 pruebas; 0 fallos, 0 errores, 0 omitidas en ambos reportes.                                                                                   |
| Focalizado Budget con PostgreSQL real                                                                                              | **PASS**. `BudgetCommandServiceTest`, `BudgetTimelineTest`, `BudgetEvaluationServiceIT` y `BudgetResourceIT`: 38 pruebas, 0 fallos/errores/omitidas.                                           |
| `npm run jest -- --runTestsByPath ...budget-create ...budget-overview ...budget-detail ...budget-edit ...budget-lifecycle-actions` | **PASS**. 5 suites, 29 pruebas, 0 fallos.                                                                                                                                                      |
| Cypress `budget-overview-real.cy.ts`                                                                                               | **PASS**. 8 pruebas navegador → REST → Spring → PostgreSQL; 0 fallos/omitidas.                                                                                                                 |
| Cypress `budget-lifecycle-real.cy.ts` por fases                                                                                    | **PASS**. 4 pruebas efectivas repartidas en cuatro relojes deterministas; 0 fallos. Las tres pruebas no correspondientes a cada fase aparecen como pendientes intencionales en esa invocación. |
| `npm run lint -- --no-fix`                                                                                                         | **PASS** sin errores. Persisten 3 advertencias de complejidad ya existentes: dos de Analytics Explorer y una de `budget-create.tsx`.                                                           |
| `npm run webapp:build:prod`                                                                                                        | **PASS**. Advertencias preexistentes de complejidad y tamaño de assets; sin errores.                                                                                                           |
| `./mvnw -ntp --batch-mode -DskipTests -Dskip.installnodenpm -Dskip.npm -Pprod package`                                             | **PASS**.                                                                                                                                                                                      |
| `git diff --check` y `git diff --cached --check`                                                                                   | **PASS**.                                                                                                                                                                                      |

## Capturas inspeccionadas

Las capturas se generaron en `target/cypress/screenshots/` durante Cypress:

- `budget-overview-real.cy.ts/budget-5b-a-detail-desktop.png`
- `budget-overview-real.cy.ts/budget-5b-a-detail-mobile.png`
- `budget-overview-real.cy.ts/budget-5b-b-revision-editor-desktop.png`
- `budget-overview-real.cy.ts/budget-5b-b-revision-editor-mobile.png`
- `budget-overview-real.cy.ts/budget-5b-c-pending-pause-desktop.png`
- `budget-overview-real.cy.ts/budget-5b-c-pending-pause-mobile.png`

## Limitaciones y trabajo diferido

No se detectó un bloqueador para BUDGET-5B. Esta aceptación no implementa notificaciones, carryover, prorrateo, cambios de calendario incompatibles ni otras capacidades fuera de BUDGET-5B. Las advertencias de complejidad y tamaño de bundle indicadas arriba no fueron introducidas por esta aceptación y no bloquearon los artefactos ni las pruebas.

No hubo staging, commits, stash, reset ni cambio de rama.
