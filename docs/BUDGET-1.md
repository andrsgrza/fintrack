# BUDGET-1 — Foundation de dominio y calendario

> **Complementado por [BUDGET-2](BUDGET-2.md).** Esta página documenta la fundación inicial; la programación de revisiones y el ciclo de vida vigentes están en BUDGET-2.

Estado: fundación de persistencia implementada. No incluye evaluación de gasto/ingreso, progreso, alertas ni interfaz de usuario.

## Contrato vigente

`Budget` es la identidad propiedad de un usuario: nombre, descripción opcional, `EXPENSE` o `INCOME`, moneda, versión, marcas de tiempo del servidor e idempotencia de creación. La zona horaria no se persiste en el presupuesto.

Cada creación registra exactamente una `BudgetRevision` número 1 inmutable y su programación inicial. La revisión contiene el importe objetivo positivo, definición de calendario, recurrencia, terminación y alcance. No existen DTO, mapper, filtros, endpoints de listado/edición/borrado ni UI genéricos de Budget.

El único comando actual es `POST /api/budgets`. Exige `clientRequestId`, zona IANA explícita y una fecha de ancla que no haya pasado en la fecha local de esa zona. Reintentar el mismo comando devuelve el mismo presupuesto; reutilizar la clave con contenido diferente devuelve `409 IDEMPOTENCY_CONFLICT`.

## Calendario determinista

`BudgetPeriodCalculator` es Java puro. Devuelve intervalos `[inicio inclusivo, fin exclusivo]` para `DAY`, `WEEK`, `MONTH`, `QUARTER` y `YEAR`.

- `ANCHORED` conserva el día de ancla sin deriva: una ancla 31 pasa por el último día de febrero pero vuelve al 31 cuando existe; una ancla del 29 de febrero vuelve al 29 en un año bisiesto.
- `NATURAL` sólo acepta fronteras reales: lunes ISO para semanas, día 1 para meses, inicio de trimestre para trimestres y 1 de enero para años. No hay día natural.
- Un presupuesto `ONCE` contiene un solo período completo. Las recurrencias pueden ser ilimitadas, terminar tras N períodos o en una frontera de período completa. No hay prorrateo ni períodos parciales.

## Alcance inmutable

Cada dimensión se guarda de forma explícita, por lo que `ALL` nunca significa una colección vacía:

| Dimensión  | Modos              | Regla                                                                                                              |
| ---------- | ------------------ | ------------------------------------------------------------------------------------------------------------------ |
| Cuentas    | `ALL`, `SELECTED`  | Las cuentas seleccionadas son del dueño y usan la moneda del presupuesto.                                          |
| Categorías | `ALL`, `SELECTED`  | Las categorías seleccionadas son del dueño y compatibles con `EXPENSE` o `INCOME`; `BOTH` es compatible con ambas. |
| Etiquetas  | `NONE`, `SELECTED` | Una selección usa coincidencia `ANY` o `ALL`; `NONE` no admite ids.                                                |

Al borrar una cuenta, categoría o etiqueta se quita sólo el vínculo de la revisión. El modo queda `SELECTED`; por tanto, una implementación futura puede informar configuración incompleta. Nunca se transforma en `ALL` ni se amplía el alcance de forma silenciosa.

## Persistencia y migración

El changelog `20261115100000_budget_foundation.xml` retira exclusivamente las tablas provisionales de Budget y crea `budget`, `budget_revision` y sus tres tablas de selección de revisión. Es forward-only; no toca transacciones, cuentas, categorías, etiquetas ni otros datos financieros.

## Límites deliberados

BUDGET-1 no ejecuta consultas Analytics, no calcula progreso ni gasto, no genera períodos en la base de datos y no expone una pantalla de presupuestos. Esas capacidades deben reutilizar el motor semántico de Analytics en slices posteriores, sin otro motor de cálculo financiero.

## Validación automatizada

- `BudgetPeriodCalculatorTest`: anclas 29/30/31, años bisiestos, fronteras naturales, terminaciones y períodos exclusivos.
- `BudgetCreationServiceTest`: creación atómica, alcance explícito, propiedad, zona horaria e idempotencia.
- `BudgetResourceIT`: PostgreSQL desechable + Liquibase, REST, selección propia/ajena, reintento y ausencia del CRUD antiguo.
