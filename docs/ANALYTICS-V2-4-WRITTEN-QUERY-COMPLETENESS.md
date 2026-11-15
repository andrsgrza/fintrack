# Consulta escrita analítica: contrato canónico V2.4

Esta guía describe la superficie ejecutable actual de la **Consulta escrita**. No introduce otro lenguaje ni otro modelo: todo texto se transforma en un `SemanticQueryDefinition` normalizado y se ejecuta con el mismo catálogo, validador y pipeline que la API estructurada.

La definición responde **qué calcular**. El `VisualizationContext` responde **cuándo y sobre qué cuentas/monedas del dueño** ejecutarlo. Periodo, cuentas y monedas no se guardan ni se escriben dentro de la consulta.

## Forma de la consulta

```text
SELECT AGREGADO(CAMPO [FILTER WHERE condición [AND condición...]]) AS alias [, ...]
FROM DATASET
[TIME BINDING CAMPO]
[WHERE condición [AND condición...]]
[GROUP BY CAMPO [, ...]]
[ORDER BY alias|CAMPO ASC|DESC [, ...]]
[LIMIT entero]
```

El formatter siempre emite `TIME BINDING` y `LIMIT`; el parser usa el `timeBinding` registrado del dataset cuando se omite en texto. Las condiciones globales limitan todas las proyecciones. `FILTER WHERE` limita sólo ese cálculo, por lo que permite, por ejemplo, ingresos y gastos en la misma consulta.

Los literales se escriben entre comillas simples; una comilla interna se duplica (`'O''Brien'`). `=` se acepta como abreviatura de `EQUALS`, pero el formatter canónico emite `EQUALS`.

## Ejemplos ejecutables

### Movimientos contables (`TRANSACTION_LEDGER`)

Ingresos y gastos económicos —las dos piernas de una transferencia interna se excluyen explícitamente— por mes e institución:

```text
SELECT SUM(TRANSACTION_AMOUNT FILTER WHERE TRANSACTION_FLOW EQUALS 'IN' AND TRANSFER_LINK_STATUS EQUALS 'UNLINKED') AS income,
       SUM(TRANSACTION_AMOUNT FILTER WHERE TRANSACTION_FLOW EQUALS 'OUT' AND TRANSFER_LINK_STATUS EQUALS 'UNLINKED') AS expense
FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE
GROUP BY TRANSACTION_MONTH, ACCOUNT_INSTITUTION
ORDER BY TRANSACTION_MONTH ASC, income DESC
LIMIT 100
```

Gasto de una categoría y todos sus descendientes. `CATEGORY_BRANCH_CHILD` sólo es válido con la condición global `CATEGORY_ID DESCENDANT_OF`; así cada fila representa el hijo directo de la rama seleccionada:

```text
SELECT SUM(TRANSACTION_AMOUNT) AS branch_total
FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE
WHERE CATEGORY_ID DESCENDANT_OF '42'
GROUP BY TRANSACTION_MONTH, CATEGORY_BRANCH_CHILD
ORDER BY TRANSACTION_MONTH ASC
LIMIT 100
```

Las categorías permiten `EQUALS`, `IN`, `DESCENDANT_OF` e `IS_UNCATEGORIZED`. Las etiquetas permiten `HAS_ANY ('7', '8')`, `HAS_ALL ('7', '8')` y `HAS_NONE`; se pueden agrupar con `TAG_ID`. Una agrupación por etiquetas es intencionalmente solapada y el resultado incluye la advertencia de no aditividad correspondiente.

La superficie de este dataset admite `COUNT(TRANSACTION_ID)`, `SUM(TRANSACTION_AMOUNT)` y `AVG(TRANSACTION_AMOUNT)`; hasta cuatro cálculos, diez condiciones y dos desgloses. Los desgloses admitidos son día, semana ISO, mes, año, institución, cuenta, tipo de cuenta, categoría asignada, raíz, primera subcategoría, hijo de rama y etiqueta. Los importes se devuelven separados por moneda de cuenta: nunca se suman monedas distintas implícitamente.

### Posición de cuentas (`ACCOUNT_POSITION_SNAPSHOT`)

Saldo contable mensual por cuenta:

```text
SELECT SUM(POSITION_BALANCE) AS book_balance
FROM ACCOUNT_POSITION_SNAPSHOT TIME BINDING OBSERVATION_DATE
GROUP BY ACCOUNT_ID, OBSERVATION_MONTH
ORDER BY OBSERVATION_MONTH ASC
LIMIT 100
```

Es una serie puntual derivada de `initialBalance`, `initialBalanceDate` y movimientos publicados. Por ello debe agruparse por cuenta y mes; una observación anterior al inicio disponible se devuelve como `UNAVAILABLE`, no se inventa. Las inversiones representan saldo en libros y el resultado lo advierte cuando aplica.

### Tarjetas de crédito (`CREDIT_ACCOUNT_SNAPSHOT`)

Deuda histórica registrada:

```text
SELECT SUM(CREDIT_DEBT) AS debt
FROM CREDIT_ACCOUNT_SNAPSHOT TIME BINDING OBSERVATION_DATE
GROUP BY ACCOUNT_ID, OBSERVATION_MONTH
ORDER BY OBSERVATION_MONTH ASC
LIMIT 100
```

Límite, disponible y utilización actuales por tarjeta:

```text
SELECT SUM(CREDIT_LIMIT) AS credit_limit,
       SUM(CREDIT_AVAILABLE) AS available,
       SUM(CREDIT_UTILIZATION_PERCENT) AS utilization
FROM CREDIT_ACCOUNT_SNAPSHOT TIME BINDING OBSERVATION_DATE
GROUP BY ACCOUNT_ID
ORDER BY ACCOUNT_ID ASC
LIMIT 100
```

`CREDIT_DEBT` es histórico y requiere cuenta + mes. `CREDIT_LIMIT`, `CREDIT_AVAILABLE` y `CREDIT_UTILIZATION_PERCENT` son sólo actuales, pues FINTRACK no mantiene historial de cambios de límite; el contexto debe terminar hoy y sólo se agrupa por cuenta. Una cuenta no aplicable, sin límite o con límite cero conserva su estado financiero (`NOT_APPLICABLE`, `NO_DATA` o la advertencia de disponibilidad), sin fingir una serie histórica.

### Eventos de transferencias internas (`INTERNAL_TRANSFER_EVENT`)

Cada transferencia se cuenta una vez, no una vez por cada pierna:

```text
SELECT COUNT(TRANSFER_ID) AS transfer_count,
       SUM(TRANSFER_AMOUNT) AS transfer_volume
FROM INTERNAL_TRANSFER_EVENT TIME BINDING TRANSFER_DATE
WHERE SOURCE_ACCOUNT_TYPE EQUALS 'DEBIT' AND DESTINATION_ACCOUNT_TYPE EQUALS 'CASH'
GROUP BY TRANSFER_MONTH, DESTINATION_ACCOUNT_ID
ORDER BY TRANSFER_MONTH ASC
LIMIT 100
```

Se admiten desgloses temporales, cuenta/institución/tipo de origen y de destino. Una selección de cuentas en el contexto incluye el evento cuando cualquiera de los dos extremos seleccionados pertenece al dueño; el importe y conteo siguen siendo únicos. Los importes también se particionan por moneda.

## Matriz de capacidades verificadas

| Dataset                     | Cálculos y condiciones                                                                                                 | Desgloses                                                              | Estado                                                                 |
| --------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `TRANSACTION_LEDGER`        | `COUNT`, `SUM`, `AVG`; condiciones globales y por cálculo; flujo, vínculo de transferencia, tipo, categoría y etiqueta | Temporal, cuenta, institución, tipo, jerarquía de categoría y etiqueta | **Completamente soportado**                                            |
| `ACCOUNT_POSITION_SNAPSHOT` | `SUM(POSITION_BALANCE)` y condición de tipo de cuenta                                                                  | Cuenta + mes                                                           | **Completamente soportado dentro de su semántica histórica puntual**   |
| `CREDIT_ACCOUNT_SNAPSHOT`   | Deuda histórica; límite, disponible y utilización actuales; condición de tipo                                          | Cuenta + mes para deuda; sólo cuenta para métricas actuales            | **Completamente soportado con la restricción de historial de límites** |
| `INTERNAL_TRANSFER_EVENT`   | `COUNT(TRANSFER_ID)`, `SUM(TRANSFER_AMOUNT)`; tipo de cuenta origen/destino                                            | Temporal y extremos origen/destino                                     | **Completamente soportado**                                            |

El catálogo devuelve para cada dataset los campos tipados, cálculos, operaciones de condición, desgloses, unidades, política de moneda, relaciones y descripciones localizadas. La futura interfaz V2.5 debe consumir ese contrato; no debe inventar campos ni deducir capacidades fuera de él.

## Normalización, seguridad y resultados

La equivalencia garantizada es semántica:

```text
AST -> format -> parse -> AST normalizado
texto -> parse -> format -> parse -> AST normalizado equivalente
```

El parser sólo acepta el vocabulario del catálogo. Después de parsear, usa el mismo validador de definición que la ejecución JSON. Antes de ejecutar, el validador resuelve el contexto y las referencias del dueño: categorías, etiquetas y cuentas ajenas producen errores tipados y nunca se convierten en datos, etiquetas o metadatos de otro usuario.

Los resultados mantienen identidad y etiqueta de dimensión, metadatos de categoría/etiqueta, bucket temporal, alias y unidad de cada medida, moneda y estados `ZERO`, `NO_DATA`, `UNAVAILABLE`, `PARTIAL` o `NOT_APPLICABLE` cuando corresponden. Las advertencias financieras —etiquetas solapadas, punto histórico, disponibilidad de crédito y saldo de inversiones— viajan estructuradas; no se silencian ni se fusionan.

## Límites intencionales

No son parte del lenguaje: SQL/JPA arbitrario, tablas o joins físicos, fórmulas libres, comentarios, punto y coma, `OR`, subconsultas, expresiones no registradas, más de cuatro cálculos, más de dos desgloses, más de diez condiciones ni límites fuera del rango del catálogo. El lexer también limita el texto a 8,192 caracteres y 512 tokens.

Tampoco se relajan restricciones financieras para “hacer que funcione”: no hay histórico de límite/disponible/utilización de crédito, la posición exige cuenta y mes, y la rama de categorías exige su predicado descendiente. La interfaz visual V2.5 ya está implementada sobre este mismo contrato; su experiencia actual, agrupaciones ordenadas y paridad con Consulta escrita se documentan en [ANALYTICS-V2-5-EXPLORER-DASHBOARDS.md](ANALYTICS-V2-5-EXPLORER-DASHBOARDS.md).

## Evidencia de regresión V2.4

`SemanticAnalyticsDslServiceTest.formatsAndParsesEveryCapabilityTheCatalogCurrentlyDeclaresExecutable` recorre el catálogo y comprueba que cada capacidad declarada tenga una forma escrita canónica válida. `SemanticAnalyticsDslServiceTest.rejectsUnsafeAndUnsupportedTextBeforeItCanBecomeAnExecutableDefinition` cubre agregados desconocidos, joins, comentarios, punto y coma, `OR` y combinaciones de crédito/transferencia inválidas.

`SemanticAnalyticsExecutionServiceIT` cubre movimientos, jerarquía de categorías, etiquetas, semana ISO, partición de monedas y aislamiento de dueño. `SemanticSpecializedAnalyticsExecutionServiceIT.parses_formats_and_executes_credit_and_transfer_queries_through_the_single_semantic_pipeline` prueba texto -> AST -> ejecución para deuda histórica, crédito actual y eventos de transferencia, incluyendo la restricción histórica de utilización. `SemanticAnalyticsResourceIT` verifica que el catálogo HTTP publique los cuatro datasets gobernados.
