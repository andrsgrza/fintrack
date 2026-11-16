# BUDGET-5A.1 — Modos de calendario y fechas de inicio flexibles

Estado: implementado. Esta corrección amplía únicamente la creación y la vista previa de calendarios de BUDGET-5A;
no introduce BUDGET-5B, notificaciones ni cambios al cálculo financiero de Budget.

## Un calendario canónico, tres formas de iniciarlo

La definición inmutable de cada `BudgetRevision` sigue siendo la fuente única de los períodos. Ahora tiene tres
alineaciones:

| Modo de la interfaz        | `BudgetAlignment` | Regla persistida                                                                                             |
| -------------------------- | ----------------- | ------------------------------------------------------------------------------------------------------------ |
| Desde una fecha específica | `ANCHORED`        | La fecha exacta es el ancla original.                                                                        |
| Calendario natural         | `NATURAL`         | La ancla es una frontera convencional válida.                                                                |
| Calendario personalizado   | `CUSTOM`          | La ancla establece el primer ciclo y, para mes/trimestre/año, `customDayOfMonth` conserva el día solicitado. |

`BudgetPeriodCalculator` Java sigue materializando todos los períodos `[inicio inclusivo, fin exclusivo)`. React sólo
convierte los controles de selección en el primer límite solicitado y muestra el resultado de
`POST /api/budgets/calendar-preview`; no calcula períodos financieros en el navegador.

### Fecha específica

La persona elige la fecha original. Un ciclo de cuatro días desde el 15 termina exclusivamente el 19; una semana
desde martes inicia siempre en martes; una recurrencia mensual/anual conserva su día original y usa el último día
disponible cuando el mes no lo tiene. Las fechas pasadas, actuales y futuras son válidas.

### Calendario natural

No hay selector libre que pueda producir una frontera inválida:

- semana: selector ISO, con inicio en lunes;
- mes: selector de mes/año, con inicio el día 1;
- trimestre: selector de trimestre y año, con inicio convencional de enero, abril, julio u octubre;
- año: selector de año, con inicio el 1 de enero.

No se ofrece este modo para intervalos de días. Si el intervalo cubre más de una unidad, el primer límite elegido
fija explícitamente la fase y el calculador avanza la cantidad solicitada de unidades completas.

### Calendario personalizado

- semana: se elige la semana ISO del primer ciclo y el día que la semana comienza; la fecha resultante persiste la
  fase, por lo que los ciclos posteriores comienzan en el mismo día de semana;
- mes y trimestre: se eligen el mes del primer ciclo y el día recurrente 1–31;
- año: se eligen el año del primer ciclo y el aniversario mes/día.

Para mes, trimestre y año, `custom_day_of_month` en `budget_revision` conserva el día pedido aun cuando el primer
inicio se haya ajustado. Así, una regla mensual día 31 que empieza el 28 de febrero comienza después el 31 de marzo;
no deriva permanentemente al 28. Un 29 de febrero anual usa el 28 en años no bisiestos y recupera el 29 en años
bisiestos. La interfaz explica estos ajustes sólo cuando pueden ocurrir y la vista previa muestra las fechas reales.

## Creación histórica y resumen

Una fecha de inicio anterior al día actual ya no se rechaza. El servidor conserva `createdAt` y `recordedAt` como el
instante real de creación: crear un presupuesto histórico no falsifica cuándo se registró ni modifica transacciones.
Los períodos históricos se siguen evaluando mediante Analytics canónico; esta fase no crea notificaciones retroactivas.

El resumen usa una única fecha común con el texto **«Consultar periodos correspondientes al día»** y una acción
**«Hoy»**. Cambiarla sólo elige el período mostrado para cada presupuesto; no cambia la definición, el calendario ni
los movimientos financieros.

## Persistencia y compatibilidad de revisiones

La migración forward-only `20261115130000_budget_custom_calendar_rule.xml` agrega la columna nullable
`budget_revision.custom_day_of_month`. Las revisiones `ANCHORED` y `NATURAL` la mantienen nula. Las revisiones
`CUSTOM` se validan antes de persistirse, y `BudgetTimeline` vuelve a construir la definición exacta de la revisión,
por lo que el historial y una recarga después de reiniciar conservan la regla original.

## Validación y cobertura

Los mensajes de validación conocidos del calendario se traducen al idioma de la interfaz; los selectores evitan las
fronteras naturales inválidas durante la interacción normal. La cobertura incluye fronteras ISO/naturales, semana
personalizada martes, recuperación de día 31 desde febrero, trimestre personalizado, aniversario bisiesto, inicio
histórico, persistencia y preview de la regla personalizada, y el recorrido real Chrome → REST → Spring → PostgreSQL
desechable para los cuatro flujos de creación aprobados.
