# BUDGET-3 — Compilación semántica canónica

Estado: implementado en backend. Esta fase prepara la evaluación financiera de un período; no calcula avance,
porcentajes, alertas, resultados persistidos ni expone UI de presupuestos.

## Un solo motor financiero

`BudgetSemanticQueryCompiler` convierte un `BudgetRevision` efectivo y un `BudgetPeriod` acotado en:

- un `SemanticQueryDefinition` canónico;
- un `VisualizationContext` separado; y
- un `SemanticAnalyticsTrustedOwnerContext` de servidor.

No lee `FinancialTransactionRepository`, no emite SQL propio y no guarda una consulta o resultado paralelo en
`BudgetRevision`. La ejecución futura pasa por `SemanticAnalyticsExecutionService`, el mismo validador y el mismo
ejecutor de ledger que utiliza Metric Explorer.

Los presupuestos `EXPENSE` compilan `OUT` + `UNLINKED`; los `INCOME`, `IN` + `UNLINKED`. Por ello las dos piernas de
una `InternalTransfer` vinculada no se tratan como ingreso o gasto económico.

## Contexto y fechas

El AST no contiene propietario, fechas, cuentas ni moneda. El compilador coloca en el contexto:

- período con inicio inclusivo y fin exclusivo traducido a `ABSOLUTE_RANGE` inclusivo;
- para un período abierto, límite en la fecha local del propietario obtenida del `Clock` inyectado y de una zona IANA
  ya resuelta;
- cuentas seleccionadas, o selección vacía para `ALL` dinámico; y
- exactamente la moneda del presupuesto.

Un período que comienza después de la fecha local del propietario no se ejecuta ni se convierte en cero. Devuelve el
resultado tipado `INVALID_EFFECTIVE_PERIOD`.

## Alcances compilados

- **Cuentas ALL:** el contexto deja la selección vacía; Analytics resuelve en ejecución todas las cuentas actuales
  del propietario con la moneda requerida. Cuentas inactivas siguen siendo elegibles para sus movimientos históricos.
- **Cuentas SELECTED:** sólo los IDs almacenados, con owner y moneda verificados. Una selección vacía ya no se
  interpreta como ALL: devuelve `INCOMPLETE_CONFIGURATION`.
- **Categorías ALL:** no agrega condición, por lo que conserva transacciones sin categoría.
- **Categorías SELECTED:** emite `CATEGORY_ID DESCENDANT_OF_ANY (...)`. Cada raíz y todos sus descendientes se unen
  con semántica OR; al materializar una sola condición `IN`, una raíz padre y su hija no duplican importes.
- **Etiquetas:** `HAS_ANY` o `HAS_ALL`, siempre por `EXISTS`/conteo correlacionado y sin multiplicar totales.

Entre familias de alcance las condiciones se combinan con AND. Referencias faltantes, ajenas o incompatibles no
ensanchan la consulta: producen un error de compilación tipado.

## Extensiones compartidas de Analytics

`DESCENDANT_OF_ANY` se incorporó al catálogo, validador, parser/formateador SQL-like, ejecutor y contrato React. Es
un operador general del modelo semántico, no un filtro SQL exclusivo de Budget.

Para una consulta escalar monetaria de ledger sin movimientos coincidentes, Analytics ahora emite una fila derivada
por moneda de cuenta aplicable: `SUM` y `COUNT` son cero conocido; `AVG` queda `NO_DATA`. Esto no altera saldos
históricos, deuda, crédito, resultados no aplicables ni errores de ejecución.

## Seguridad interna

La ejecución de Budget usa `SemanticAnalyticsTrustedOwnerContext.forBudget(...)`, que sólo acepta un `Budget`
persistido y su propietario. No existe una ruta REST ni un payload que acepte `ownerId`; el validador vuelve a
resolver cuentas, categorías y etiquetas en el ámbito de ese propietario. Así un administrador que gestione un
presupuesto ajeno no puede ejecutar accidentalmente contra sus propias cuentas.

## Pruebas

- `BudgetSemanticQueryCompilerTest`: fecha local con reloj fijo y rechazo de períodos futuros.
- `BudgetSemanticQueryCompilerIT`: PostgreSQL desechable; compilación, categorías padre/hija solapadas, tags `ALL`,
  transferencias internas, aislamiento de propietario, igualdad con ejecución Analytics y configuración incompleta.
- `SemanticAnalyticsExecutionServiceIT`: unión de descendientes y cero conocido escalar / `AVG NO_DATA`.
- `SemanticAnalyticsDslServiceTest`: formato y parseo de cada capacidad publicada, incluido
  `DESCENDANT_OF_ANY`.

`BudgetEvaluationService` de BUDGET-4 consume este contrato para calcular progreso, sin reemplazarlo. Conserva las
distinciones entre cero conocido, configuración incompleta, `NO_DATA`, `UNAVAILABLE`, `NOT_APPLICABLE` y `PARTIAL`;
su contrato de estados y rutas está en [`BUDGET-4.md`](BUDGET-4.md).
