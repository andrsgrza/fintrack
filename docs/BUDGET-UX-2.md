# BUDGET-UX-2 — Edición centralizada de presupuestos

## Modelo aprobado

La configuración se administra en `/budgets/:budgetId/edit`. No existe un comando
universal de «guardar todo»: cada preocupación conserva su propio contrato y su
propio control de concurrencia.

| Sección                  | Datos                                    | Acción                              | Contrato                                               |
| ------------------------ | ---------------------------------------- | ----------------------------------- | ------------------------------------------------------ |
| General                  | Nombre y descripción                     | Guardar nombre y descripción        | `PUT /api/budgets/{id}/metadata`                       |
| Configuración financiera | Objetivo y alcance de una nueva revisión | Revisar cambio → Confirmar revisión | comandos de `BudgetRevision` existentes                |
| Notificaciones           | Alertas porcentuales y resumen de cierre | Guardar notificaciones              | `GET`/`PUT /api/budgets/{id}/notification-preferences` |
| Revisiones programadas   | Revisiones futuras activas               | Cancelar revisión                   | comando de cancelación existente                       |

El calendario, tipo y moneda siguen siendo inmutables en este flujo. Las revisiones
financieras permanecen inmutables; editar datos generales o notificaciones no crea
una revisión ni cambia el alcance financiero.

## Detail de solo lectura

`/budgets/:budgetId` conserva el resultado, período seleccionado, configuración
financiera aplicable, alcance, calendario, historial y controles de ciclo de vida.
Notificaciones se presenta como resumen actual del presupuesto:

- Alertas de gasto o de ingresos, activadas/desactivadas y sus porcentajes exactos.
- Estado independiente del resumen de cierre de período.
- Una explicación explícita de que no es una instantánea de la revisión histórica
  del período consultado.
- `Configurar notificaciones` lleva a `#notifications` dentro del editor cuando el
  presupuesto todavía es modificable.

El detalle no ofrece interruptores, chips removibles ni acciones de guardar/cancelar
para notificaciones. Un presupuesto efectivamente finalizado permanece consultable,
sin enlace de edición de notificaciones.

## Navegación, borradores y concurrencia

El editor tiene controles compactos que desplazan a General, Configuración
financiera, Notificaciones y Revisiones programadas. El enlace desde Detail sí
conserva `#notifications`, para abrir el destino correcto. Los indicadores textuales
de «sin guardar» muestran qué sección cambió; no dependen sólo del color.

Cada sección mantiene su propio borrador. Guardar metadatos vuelve a cargar sólo su
línea base y no desmonta ni reemplaza un borrador de notificaciones o revisión.
Cancelar una sección revierte exclusivamente esa sección a su última respuesta
persistida. Si hay cambios locales, el enlace de regreso pregunta antes de salir y
la página instala la advertencia estándar del navegador para recargar/cerrar.

Los conflictos de versión continúan independientes. Una respuesta `409` de
metadatos, revisión o preferencias muestra el error autorizado, conserva el
borrador local y requiere revisión y una acción explícita posterior; nunca hay
resubmisión silenciosa.

Una corrección puntual de persistencia garantiza que una sustitución que modifica
sólo umbrales también avance la `@Version` de la preferencia. El instante de
actualización es estrictamente monótono incluso con el reloj fijo de aceptación;
así un segundo editor recibe `409` en lugar de sobrescribir silenciosamente la
configuración concurrente.

## Ciclo de vida, accesibilidad y móvil

Los presupuestos ACTIVE y PAUSED pueden editar configuraciones conforme a los
contratos existentes. Un FINISHED efectivo no puede editar: el editor muestra una
notificación resumida de solo lectura para conservar un enlace o marcador directo
sin abrir controles mutables. Una finalización futura no se trata como terminal
antes de su período efectivo.

Los controles de sección son botones nativos y accesibles por teclado. Las secciones
usan encabezados y etiquetas existentes; los mensajes de guardado y cambios no
guardados tienen roles de estado. El contenedor de navegación usa `flex-wrap`, por
lo que se adapta a pantallas estrechas sin ocultar acciones. Los selectores de
porcentajes conservan el wrapping y accesibilidad validados en BUDGET-UX-1.

## Alcance técnico y exclusiones

No se añadieron endpoints, migraciones ni contratos de dominio. Se reutilizan
`BudgetNotificationPreferencesEditor`, las APIs existentes de metadatos,
preferencias y `BudgetRevision`, y las reglas de propietario/ciclo de vida ya
autorizadas por el backend. La única corrección de backend fue el avance fiable de
versión de preferencias descrito arriba. No se modificaron Analytics, cálculo
financiero, calendarios, scheduler, notificaciones, datos de desarrollo ni
BUDGET-UX-3.

## Evidencia de pruebas

Pruebas de React añadidas/actualizadas:

- Resumen Detail habilitado/deshabilitado, EXPENSE/INCOME, porcentajes exactos,
  cierre de período, enlace al editor y ausencia de controles mutables.
- Cuatro secciones del editor, sin botón global.
- Guardados independientes, cancelación por sección y preservación de borradores.
- Conflictos de metadatos, preferencias y revisiones sin pérdida silenciosa.
- Estados PAUSED y FINISHED.
- Regresión del selector compacto de BUDGET-UX-1 y porcentajes de cuatro decimales.

La aceptación real de navegador se mantiene en los specs de preferencias existentes
y ahora recorre Detail (solo lectura) → Edit (configuración) contra las APIs reales.

## Aceptación ejecutada — 23 de noviembre de 2026

Se usó PostgreSQL 17.4 descartable en `127.0.0.1:15432` y una instancia Spring
Boot temporal `prod,e2e` en `127.0.0.1:18080`; ninguna instancia ni dato de
desarrollo en 5432/8080/9000/9060 participó. Cypress ejerció React → REST →
Spring → PostgreSQL sin interceptar ni sustituir respuestas de Budget.

- `budget-notification-preferences-real.cy.ts`: 3/3 aprobadas. Cubre creación,
  Detail de solo lectura, navegación a Edit, borradores independientes,
  guardado exclusivo de notificaciones, cancelación de General, persistencia,
  presupuesto terminado, porcentajes de cuatro decimales y un conflicto real
  `409` que conserva el borrador local.
- `budget-notification-preferences-lifecycle-real.cy.ts`: fases aisladas
  `schedule`, `boundary` y `ownership`: 3/3 aprobadas. Las seis pruebas que no
  corresponden a cada fase permanecieron pendientes deliberadamente y no se
  cuentan como aprobadas. Confirma finalización futura editable, PAUSED editable,
  FINISHED de solo lectura y aislamiento entre propietarios.
- Capturas inspeccionadas: Detail de escritorio, navegación de Edit en escritorio
  y móvil, editor de Notificaciones de escritorio y móvil, y Detail FINISHED. Se
  guardaron bajo
  `target/cypress/screenshots/budget-notification-preferences-real.cy.ts/`.

Gates completos ejecutados:

| Comando                                               | Resultado                                                          |
| ----------------------------------------------------- | ------------------------------------------------------------------ |
| `./mvnw -ntp --batch-mode verify`                     | PASS: Surefire 675, Failsafe 1,378; 0 fallas, errores u omisiones. |
| `npm run jest`                                        | PASS: 70 suites, 767 pruebas.                                      |
| `npm run lint -- --no-fix`                            | PASS: 0 errores; sólo 3 advertencias históricas de complejidad.    |
| `npm run webapp:build` y `npm run webapp:prod`        | PASS.                                                              |
| `./mvnw -ntp --batch-mode -DskipTests -Pprod package` | PASS.                                                              |
| `git diff --check` y `git diff --cached --check`      | PASS.                                                              |

La primera ejecución de Maven dentro del sandbox no pudo resolver el registro npm
(`ENOTFOUND`); la ejecución autorizada fuera del sandbox completó correctamente.
No se cambió configuración, puertos normales, datos financieros, staging ni Git.

## Trabajo diferido

BUDGET-UX-3 (revisión estructurada de creación y alcance efectivo de categorías)
permanece fuera de este cambio.
