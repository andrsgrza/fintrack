# BUDGET-UX-3 — Revisión estructurada y alcance financiero efectivo

## Propósito

La revisión antes de crear o programar una revisión de presupuesto presenta la
decisión financiera completa sin alterar la definición financiera del
presupuesto. El mismo resumen reutilizable se muestra también en Detail y Edit.

## Revisión antes de crear

La pantalla de revisión está organizada en cuatro bloques legibles:

1. objetivo: límite de gasto o meta de ingreso, importe, moneda, nombre y
   descripción;
2. calendario: frecuencia, modo de calendario, primer período, recurrencia y
   terminación;
3. alcance financiero: cuentas, categorías y etiquetas;
4. notificaciones: umbrales y aviso de cierre.

Los botones para volver a editar regresan al mismo formulario y no crean un
segundo flujo ni una segunda representación del presupuesto.

## Cobertura de categorías

La UI nunca calcula descendientes por su cuenta. `POST
/api/budgets/scope-preview` resuelve la cobertura mediante
`SemanticCategoryCoverageService`, que reutiliza el catálogo de referencias y
el recorrido `descendantsInclusive` de la ejecución analítica semántica.

El resultado separa:

- categorías seleccionadas explícitamente;
- categorías efectivamente incluidas, sin duplicados cuando se elige un padre y
  un hijo;
- referencias que ya no pertenecen al propietario o ya no existen;
- categorías incompatibles con el tipo INCOME/EXPENSE del presupuesto.

La jerarquía incluida es dinámica: una revisión histórica conserva los IDs que
seleccionó, mientras que el resumen comunica que la cobertura se determina con
la jerarquía vigente. Las categorías inactivas ya seleccionadas siguen siendo
visibles para evitar esconder el alcance histórico. Las referencias ajenas no
se revelan ni amplían el filtro.

Mientras la comprobación está pendiente, falla o encuentra un conflicto, Create
y Confirmar revisión no pueden persistir el alcance. Esto evita guardar una
descripción de cobertura que la ejecución no podría respetar.

## Límites de responsabilidad

Este endpoint es una proyección de lectura: no persiste periodos, revisiones,
consultas ni resultados financieros. La compatibilidad INCOME/EXPENSE se
centraliza en `BudgetCategoryCompatibility`, reutilizada por el compilador de
consulta del presupuesto; no se introduce otro cálculo de gasto o ingreso.

## Evidencia automatizada

- `BudgetResourceIT` verifica PostgreSQL real, aislamiento por propietario,
  padre+hijo sin duplicación, descendiente inactivo, alcance vacío y categoría
  incompatible.
- Las pruebas React de Create, Detail y Edit verifican la revisión estructurada,
  el detalle de categorías incluidas y el bloqueo mientras se resuelve el
  alcance.
- `budget-overview-real.cy.ts` recorre navegador → REST → Spring → PostgreSQL,
  confirma la respuesta real de alcance y toma capturas desktop y móvil de la
  revisión y del detalle.

## Aceptación ejecutada — 23 de noviembre de 2026

La aceptación de navegador usó exclusivamente PostgreSQL 17.4 descartable en
`127.0.0.1:15432` y Spring Boot temporal `prod,e2e` en
`127.0.0.1:18080`, con reloj fijo `2026-03-15T12:00:00Z`. Los puertos y datos
normales de desarrollo no participaron.

- Cypress real `budget-overview-real.cy.ts`: 8/8 aprobadas, sin pendientes ni
  omisiones. Comprueba el flujo navegador → REST → Spring → PostgreSQL, la
  expansión padre+hijo y las categorías efectivas, además de los flujos de
  calendario, alcance, revisiones y ciclo de vida ya existentes.
- Se inspeccionaron las capturas reales de Review y Detail, en escritorio y
  móvil. No presentan desborde horizontal; Review muestra el importe, el
  calendario, el alcance efectivo y las notificaciones antes de confirmar.
- `./mvnw -ntp --batch-mode -Dskip.installnodenpm -Dskip.npm verify`: PASS;
  Surefire 660 y Failsafe 1,379, ambos sin fallas, errores u omisiones.
- `npm run jest`: PASS; 70 suites y 768 pruebas.
- `npm run lint -- --no-fix`, build dev, build prod y paquete prod: PASS sin
  errores. Lint conserva cuatro advertencias históricas/de complejidad que no
  bloquean el build.

No se realizaron staging, commits ni cambios de configuración, puertos
normales, procesos de desarrollo o datos financieros de desarrollo.
