# BUDGET-5B-A — Detalle e historial de períodos

Estado: implementado. Esta primera parte de BUDGET-5B añade una lectura de detalle; la edición de metadatos y
revisiones inmutables se documenta en [BUDGET-5B-B](BUDGET-5B-B.md). Esta página no incorpora pausa, reanudación,
finalización, borrado ni notificaciones.

## Ruta y navegación

`/budgets/:budgetId?referenceDate=YYYY-MM-DD` es una vista owner-scoped de un presupuesto. El resumen abre esa ruta
con la misma fecha de referencia que la tarjeta. La vista permite elegir una fecha, abrir cualquiera de los períodos
ya cargados y avanzar o retroceder entre períodos programados.

Las fronteras, índices, estados y revisión efectiva nunca se calculan en React. La página obtiene el presupuesto y el
período de `GET /api/budgets/overview`, consulta el resultado de período con la página acotada
`GET /api/budgets/{id}/evaluations`, y carga la configuración aplicable desde
`GET /api/budgets/{id}/effective-revision` usando el inicio del período devuelto por el servidor.

Un resultado `COMPLETED` posterior a una terminación no se ofrece como destino de navegación ni se muestra como una
fila de historial. Una fecha anterior al inicio o posterior a la vida programada muestra explícitamente que no hay
período programado; no se inventan importe, objetivo ni período.

## Resultado y configuración histórica

El encabezado muestra tipo, moneda, estado operacional, nombre y descripción. Para el período seleccionado presenta:

- fechas de inicio y fin exclusivas interpretadas para la persona;
- objetivo inmutable de la revisión aplicable;
- ingreso o gasto económico evaluado, avance y restante/excedido sólo si la evaluación es aplicable;
- estado financiero explícito para futuro, pausa, configuración incompleta, indisponibilidad y fallo semántico; y
- calendario y alcance de la revisión efectiva: cuentas, categorías (con descendientes) y etiquetas `NONE`, `ANY` o
  `ALL`.

Un cero se muestra sólo cuando el backend canónico devolvió un importe aplicable. Los estados sin evaluación se
presentan con `—` y su explicación, nunca como `$0.00`.

Los nombres de cuentas, categorías y etiquetas se obtienen mediante los selectores owner-scoped existentes, incluidos
valores históricos todavía accesibles. Si una referencia histórica ya no está disponible se indica como tal; la UI no
infiere ni sustituye el alcance original.

Las correcciones posteriores a movimientos financieros pueden cambiar el resultado de una lectura histórica, como
define BUDGET-4, pero no modifican la revisión, objetivo ni alcance mostrados para ese período.

## Historial acotado

La tabla usa `firstPeriodIndex` y `size` (diez filas visibles y una fila adicional sólo para decidir paginación). No
descarga una vida completa de presupuestos recurrentes. Cada fila incluye período, objetivo, importe evaluado cuando
existe, porcentaje y estado, y abre ese período en la misma vista. El orden estable es ascendente por índice de
período.

## Cobertura

- `BudgetEvaluationServiceIT` comprueba con PostgreSQL que una página de períodos conserva la revisión, objetivo y
  alcance de enero aunque una revisión posterior cambie febrero, y que otro usuario recibe `404`.
- `budget-detail.spec.tsx` cubre carga de detalle, navegación derivada del backend, paginación, configuración
  histórica, resultado no disponible y fecha fuera del calendario.
- `budget-overview-real.cy.ts` continúa el flujo real de creación a detalle, verifica alcance e historial y conserva
  capturas de escritorio y móvil en el entorno aislado.
