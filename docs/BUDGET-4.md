# BUDGET-4 — Evaluación de períodos y progreso financiero

Estado: implementado en backend. Esta fase no incorpora pantallas, alertas, snapshots de resultados ni un segundo
motor financiero.

## Flujo canónico

`BudgetEvaluationService` es una lectura transaccional y owner-scoped. Para cada período:

1. carga una vez el `Budget` autorizado y sus alcances inmutables;
2. usa `BudgetTimeline` para resolver período, revisión efectiva y estado operacional;
3. omite los períodos `PAUSED`, `SCHEDULED` o posteriores a la finalización, sin ejecutar Analytics;
4. compila el período aplicable mediante `BudgetSemanticQueryCompiler`; y
5. ejecuta exclusivamente `SemanticAnalyticsExecutionService.executeForTrustedOwner`.

No consulta ni agrega `FinancialTransaction` por separado. Ingreso/gasto económico, exclusión de piernas vinculadas a
`InternalTransfer`, propietario, fecha de transacción, jerarquía de categorías, cuentas, etiquetas y moneda siguen
siendo las reglas del motor semántico canónico.

## Contrato de resultado

`BudgetEvaluationResult` incluye identidad del presupuesto y período, revisión efectiva y objetivo, además de:

- `operationalStatus`: `ACTIVE`, `PAUSED`, `SCHEDULED` o `COMPLETED`, derivado de BUDGET-2;
- `periodTiming`: `HISTORICAL`, `CURRENT` o `FUTURE`, relativo a la fecha local actual;
- `evaluationStatus`: separa una evaluación real de `NOT_YET_EVALUATED`, `NOT_EVALUATED`, configuración incompleta,
  indisponibilidad o fallo/contrato semántico;
- `semanticAvailability`: disponibilidad devuelta por Analytics cuando hubo ejecución;
- `actualAmount`, `progressPercentage` y `remainingAmount`, sólo cuando el resultado es aplicable y autoritativo; y
- `goalStatus`, separado del estado operacional: límites de gasto y metas de ingreso no se confunden.

`actualAmount=0` sólo aparece cuando Analytics devolvió una fila escalar aplicable con disponibilidad `ZERO`. Un
período pausado, futuro, no aplicable, `NO_DATA`, `UNAVAILABLE` o con configuración incompleta deja el importe en
`null`, con un `failureReason` tipado; nunca se presenta como cero.

La aritmética permitida en Budget compara el resultado semántico autoritativo contra el objetivo inmutable:

- `progressPercentage = actualAmount / targetAmount * 100`, con escala 2 y `HALF_UP`, sin límite artificial en 100%;
- `remainingAmount = targetAmount - actualAmount`, conservando su signo.

Un gasto histórico bajo el límite es `FINISHED_WITHIN_LIMIT`; uno actual es `WITHIN_LIMIT`. Para ingresos bajo meta,
el período abierto es `GOAL_PENDING` y uno histórico es `GOAL_NOT_MET`.

## Tiempo, historial y revisiones

Las rutas de evaluación exigen una zona IANA válida de la interacción autenticada. Esa zona sólo determina el
"hoy" local y el corte del período abierto; no identifica ni cambia de propietario y no reinterpreta fronteras de
calendario ya almacenadas como `LocalDate`.

El período actual se ejecuta desde su inicio hasta hoy inclusivo. Los períodos históricos usan todo su intervalo
`[startInclusive, endExclusive)`. Períodos futuros conservan sus fechas, revisión y objetivo planeados, pero no
producen avance especulativo. Una finalización no borra ni congela resultados anteriores: correcciones financieras
posteriores se reflejan al volver a consultar la historia.

El historial se genera en memoria de forma acotada (`firstPeriodIndex` + `size`, máximo 120) y no persiste
`BudgetPeriod` ni snapshots. Cada elemento conserva su índice y su propia revisión efectiva; una revisión posterior
no reescribe el objetivo o alcance de períodos anteriores.

No existe todavía una fuente persistida de zona horaria para trabajo no interactivo. Las futuras notificaciones deben
resolver una zona de usuario confiable antes de invocar esta lectura; BUDGET-4 no inventa un subsistema de
preferencias o localización.

## API de sólo lectura

Todas las rutas son `GET /api/budgets/{budgetId}/...` y sólo consultan presupuestos del usuario autenticado:

| Ruta                                                   | Uso                                                              |
| ------------------------------------------------------ | ---------------------------------------------------------------- |
| `/evaluation/current?timeZone=America/Mexico_City`     | resumen del período local actual                                 |
| `/evaluation/period/{periodIndex}?timeZone=...`        | período por índice                                               |
| `/evaluation/at?referenceDate=YYYY-MM-DD&timeZone=...` | período que contiene una fecha, conservando el corte real de hoy |
| `/evaluations?firstPeriodIndex=0&size=12&timeZone=...` | página de historia acotada                                       |

Índices/rangos/zona inválidos son `400`. Un presupuesto inexistente o de otro propietario se responde como `404`,
sin revelar su existencia. Errores de compilación o disponibilidad financiera son resultados tipados, no un cero ni
una mutación.

## Cobertura

- `BudgetEvaluationServiceTest`: gasto bajo/igual/sobre límite, ingreso pendiente/logrado/superado, porcentaje y
  diferencia firmada, cero conocido, períodos futuro/pausado, configuración indisponible y corte por zona IANA.
- `BudgetEvaluationServiceIT`: PostgreSQL desechable con gasto por categoría descendiente, moneda MXN frente a USD,
  exclusión de transferencia interna, corte ante transacción futura, corrección histórica, revisiones, pausa,
  finalización, aislamiento de dueño y resultados cero reales.
- La misma integración invoca las cuatro rutas REST, comprueba historial acotado, referencia inválida y que una
  lectura no altera los registros financieros.
