import {
  ExplorerVisualization,
  SemanticAnalyticsCatalog,
  SemanticQueryDefinition,
} from 'app/entities/analytics-explorer/analytics-explorer.model';

export type DashboardText = { es: string; en: string };

/** The same logical widget contract future personal dashboards will persist, without storing financial results. */
export type FinancialVisualizationDefinition = {
  id: string;
  title: DashboardText;
  description: DashboardText;
  definition: SemanticQueryDefinition;
  visualization: ExplorerVisualization;
  /** Current credit-limit facts are not historical. The page makes a range explicit instead of changing context. */
  contextPolicy?: 'CURRENT_ONLY';
  span?: 'KPI' | 'HALF' | 'FULL';
};

export type RecommendedDashboardTemplate = {
  id: 'financial-overview' | 'movement-analysis' | 'accounts-and-position';
  version: 2;
  title: DashboardText;
  description: DashboardText;
  widgets: FinancialVisualizationDefinition[];
};

const text = (es: string, en: string): DashboardText => ({ es, en });
const predicates = {
  income: [
    { field: 'TRANSACTION_FLOW', operator: 'EQUALS' as const, values: ['IN'] },
    { field: 'TRANSFER_LINK_STATUS', operator: 'EQUALS' as const, values: ['UNLINKED'] },
  ],
  expense: [
    { field: 'TRANSACTION_FLOW', operator: 'EQUALS' as const, values: ['OUT'] },
    { field: 'TRANSFER_LINK_STATUS', operator: 'EQUALS' as const, values: ['UNLINKED'] },
  ],
};

const query = (
  catalog: SemanticAnalyticsCatalog,
  dataset: string,
  timeBinding: string,
  projections: SemanticQueryDefinition['projections'],
  groupBy: string[] = [],
  queryPredicates: SemanticQueryDefinition['predicates'] = [],
  orderBy: SemanticQueryDefinition['orderBy'] = [],
): SemanticQueryDefinition => ({
  schemaVersion: catalog.schemaVersion,
  semanticModel: catalog.semanticModel,
  dataset,
  timeBinding,
  projections,
  predicates: queryPredicates,
  groupBy,
  orderBy: orderBy.length ? orderBy : groupBy.map(field => ({ field, projectionAlias: null, direction: 'ASC' as const })),
  limit: catalog.maxLimit,
});

const economicKpi = (
  catalog: SemanticAnalyticsCatalog,
  id: string,
  title: DashboardText,
  flow: 'IN' | 'OUT',
): FinancialVisualizationDefinition => ({
  id,
  title,
  description: text(
    flow === 'IN'
      ? 'Entradas económicas; excluye piernas de transferencias internas vinculadas.'
      : 'Salidas económicas; excluye piernas de transferencias internas vinculadas.',
    flow === 'IN'
      ? 'Economic inflows; excludes linked internal-transfer legs.'
      : 'Economic outflows; excludes linked internal-transfer legs.',
  ),
  definition: query(
    catalog,
    'TRANSACTION_LEDGER',
    'TRANSACTION_DATE',
    [{ alias: flow === 'IN' ? 'ingresos' : 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
    [],
    flow === 'IN' ? predicates.income : predicates.expense,
  ),
  visualization: 'KPI',
  span: 'KPI',
});

/**
 * The sole official, code-defined recommendation registry. It is built from the live semantic catalog so every
 * definition uses the currently advertised schema version, model and bounded limit.
 */
export const recommendedDashboardTemplates = (catalog: SemanticAnalyticsCatalog): RecommendedDashboardTemplate[] => [
  {
    id: 'financial-overview',
    version: 2,
    title: text('Resumen financiero', 'Financial overview'),
    description: text('¿Cómo están mis finanzas en general?', 'How are my finances overall?'),
    widgets: [
      {
        id: 'registered-position',
        title: text('Posición financiera registrada', 'Registered financial position'),
        description: text(
          'Saldos registrados compatibles menos deuda registrada de tarjetas, a la fecha de corte y por moneda.',
          'Compatible recorded balances less recorded credit-card debt, at the cutoff and per currency.',
        ),
        definition: query(catalog, 'FINANCIAL_POSITION_SNAPSHOT', 'OBSERVATION_DATE', [
          { alias: 'posicion_financiera', function: 'SUM', field: 'FINANCIAL_POSITION', conditions: [] },
        ]),
        visualization: 'KPI',
        span: 'KPI',
      },
      economicKpi(catalog, 'economic-income', text('Ingresos económicos', 'Economic income'), 'IN'),
      economicKpi(catalog, 'economic-expenses', text('Gastos económicos', 'Economic expenses'), 'OUT'),
      {
        id: 'net-economic-flow',
        title: text('Flujo neto económico', 'Net economic flow'),
        description: text(
          'Ingresos económicos menos gastos económicos; las transferencias internas vinculadas se excluyen por el ejecutor.',
          'Economic income less expenses; linked internal transfers are excluded by the executor.',
        ),
        definition: query(catalog, 'TRANSACTION_LEDGER', 'TRANSACTION_DATE', [
          { alias: 'flujo_neto', function: 'NET', field: 'TRANSACTION_AMOUNT', conditions: [] },
        ]),
        visualization: 'KPI',
        span: 'KPI',
      },
      {
        id: 'economic-trend',
        title: text('Tendencia de ingresos, gastos y flujo', 'Income, expense, and flow trend'),
        description: text(
          'El período se desglosa por mes; cambia el rango del contexto para ajustar la ventana.',
          'The selected period is broken down by month; change the context range to adjust the window.',
        ),
        definition: query(
          catalog,
          'TRANSACTION_LEDGER',
          'TRANSACTION_DATE',
          [
            { alias: 'ingresos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: predicates.income },
            { alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: predicates.expense },
            { alias: 'flujo_neto', function: 'NET', field: 'TRANSACTION_AMOUNT', conditions: [] },
          ],
          ['TRANSACTION_MONTH'],
        ),
        visualization: 'LINE',
        span: 'FULL',
      },
      {
        id: 'spending-by-category',
        title: text('Gasto por categoría principal', 'Spending by root category'),
        description: text(
          'Distribución breve de gastos económicos por categoría principal.',
          'A concise distribution of economic expenses by root category.',
        ),
        definition: query(
          catalog,
          'TRANSACTION_LEDGER',
          'TRANSACTION_DATE',
          [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
          ['CATEGORY_ROOT'],
          predicates.expense,
          [{ field: null, projectionAlias: 'gastos', direction: 'DESC' }],
        ),
        visualization: 'DONUT',
        span: 'HALF',
      },
      {
        id: 'account-and-credit-summary',
        title: text('Resumen de cuentas y crédito', 'Account and credit summary'),
        description: text(
          'Deuda registrada por tarjeta; el detalle de cuentas y límites vive en Cuentas y patrimonio.',
          'Recorded debt by card; account and limit detail lives in Accounts and position.',
        ),
        definition: query(
          catalog,
          'CREDIT_ACCOUNT_SNAPSHOT',
          'OBSERVATION_DATE',
          [{ alias: 'deuda_tarjeta', function: 'SUM', field: 'CREDIT_DEBT', conditions: [] }],
          ['ACCOUNT_ID'],
        ),
        visualization: 'BAR',
        span: 'HALF',
      },
    ],
  },
  {
    id: 'movement-analysis',
    version: 2,
    title: text('Análisis de movimientos', 'Movement analysis'),
    description: text(
      '¿De dónde viene mi dinero, en qué lo gasto y cómo cambia?',
      'Where does money come from, where does it go, and how does it change?',
    ),
    widgets: [
      {
        id: 'income-expense-trend',
        title: text('Ingresos y gastos económicos', 'Economic income and expenses'),
        description: text(
          'Compara sólo movimientos económicos; no clasifica transferencias internas como ingresos o gastos.',
          'Compares only economic movements; internal transfers are not classified as income or expense.',
        ),
        definition: query(
          catalog,
          'TRANSACTION_LEDGER',
          'TRANSACTION_DATE',
          [
            { alias: 'ingresos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: predicates.income },
            { alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: predicates.expense },
          ],
          ['TRANSACTION_MONTH'],
        ),
        visualization: 'LINE',
        span: 'FULL',
      },
      {
        id: 'net-flow-trend',
        title: text('Flujo neto económico', 'Net economic flow'),
        description: text(
          'Diferencia gobernada entre ingresos y gastos económicos.',
          'Governed difference between economic income and expenses.',
        ),
        definition: query(
          catalog,
          'TRANSACTION_LEDGER',
          'TRANSACTION_DATE',
          [{ alias: 'flujo_neto', function: 'NET', field: 'TRANSACTION_AMOUNT', conditions: [] }],
          ['TRANSACTION_MONTH'],
        ),
        visualization: 'BAR',
        span: 'HALF',
      },
      {
        id: 'category-expense-trend',
        title: text('Gasto por categoría a lo largo del tiempo', 'Expense by category over time'),
        description: text(
          'Categorías principales con colores configurados y una leyenda interactiva.',
          'Root categories with configured colors and an interactive legend.',
        ),
        definition: query(
          catalog,
          'TRANSACTION_LEDGER',
          'TRANSACTION_DATE',
          [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
          ['TRANSACTION_MONTH', 'CATEGORY_ROOT'],
          predicates.expense,
        ),
        visualization: 'LINE',
        span: 'HALF',
      },
      {
        id: 'category-expense-distribution',
        title: text('Distribución de gasto por categoría', 'Expense distribution by category'),
        description: text(
          'Resumen por categoría principal. Las categorías son porciones excluyentes dentro de cada movimiento categorizado.',
          'Root-category summary. Categories are exclusive portions within each categorized transaction.',
        ),
        definition: query(
          catalog,
          'TRANSACTION_LEDGER',
          'TRANSACTION_DATE',
          [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
          ['CATEGORY_ROOT'],
          predicates.expense,
          [{ field: null, projectionAlias: 'gastos', direction: 'DESC' }],
        ),
        visualization: 'DONUT',
        span: 'HALF',
      },
      {
        id: 'institution-spending',
        title: text('Gasto por institución', 'Spending by institution'),
        description: text('Compara gastos económicos por institución financiera.', 'Compares economic expenses by financial institution.'),
        definition: query(
          catalog,
          'TRANSACTION_LEDGER',
          'TRANSACTION_DATE',
          [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
          ['TRANSACTION_MONTH', 'ACCOUNT_INSTITUTION'],
          predicates.expense,
        ),
        visualization: 'GROUPED_BAR',
        span: 'FULL',
      },
      {
        id: 'category-hierarchy',
        title: text('Explorar categorías', 'Explore categories'),
        description: text(
          'Selecciona una categoría en la tabla para abrir sus subcategorías sin duplicar asignaciones directas.',
          'Select a category in the table to open its children without duplicating direct assignments.',
        ),
        definition: query(
          catalog,
          'TRANSACTION_LEDGER',
          'TRANSACTION_DATE',
          [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
          ['CATEGORY_ROOT'],
          predicates.expense,
        ),
        visualization: 'TABLE',
        span: 'HALF',
      },
      {
        id: 'uncategorized-expenses',
        title: text('Gastos sin categoría', 'Uncategorized expenses'),
        description: text(
          'Muestra salidas económicas que aún no tienen categoría.',
          'Shows economic outflows that do not yet have a category.',
        ),
        definition: query(
          catalog,
          'TRANSACTION_LEDGER',
          'TRANSACTION_DATE',
          [{ alias: 'sin_categoria', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
          ['TRANSACTION_MONTH'],
          [...predicates.expense, { field: 'CATEGORY_ID', operator: 'IS_UNCATEGORIZED', values: [] }],
        ),
        visualization: 'BAR',
        span: 'HALF',
      },
      {
        id: 'tag-comparison',
        title: text('Comparación por etiquetas', 'Tag comparison'),
        description: text(
          'Las etiquetas pueden solaparse: sus totales no son porciones exclusivas de un mismo total.',
          'Tags may overlap: their totals are not exclusive portions of a single total.',
        ),
        definition: query(
          catalog,
          'TRANSACTION_LEDGER',
          'TRANSACTION_DATE',
          [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
          ['TRANSACTION_MONTH', 'TAG_ID'],
          predicates.expense,
        ),
        visualization: 'LINE',
        span: 'FULL',
      },
      {
        id: 'transfer-activity',
        title: text('Actividad de transferencias internas', 'Internal transfer activity'),
        description: text(
          'Cada transferencia validada se cuenta una vez; no son gastos ni ingresos económicos.',
          'Each validated transfer is counted once; they are not economic expenses or income.',
        ),
        definition: query(
          catalog,
          'INTERNAL_TRANSFER_EVENT',
          'TRANSFER_DATE',
          [
            { alias: 'transferencias', function: 'COUNT', field: 'TRANSFER_ID', conditions: [] },
            { alias: 'importe_transferido', function: 'SUM', field: 'TRANSFER_AMOUNT', conditions: [] },
          ],
          ['TRANSFER_MONTH'],
        ),
        visualization: 'GROUPED_BAR',
        span: 'FULL',
      },
    ],
  },
  {
    id: 'accounts-and-position',
    version: 2,
    title: text('Cuentas y patrimonio registrado', 'Accounts and registered position'),
    description: text(
      '¿Dónde está mi dinero, cuánto debo y cómo evolucionaron mis cuentas?',
      'Where is my money, what do I owe, and how have accounts changed?',
    ),
    widgets: [
      {
        id: 'position-by-account',
        title: text('Posición por cuenta', 'Position by account'),
        description: text(
          'Barras de saldo contable por cuenta y moneda, con línea base cero. Las inversiones son saldo registrado, no valor de mercado.',
          'Book-balance bars by account and currency with a zero baseline. Investments are recorded balances, not market value.',
        ),
        definition: query(
          catalog,
          'ACCOUNT_POSITION_SNAPSHOT',
          'OBSERVATION_DATE',
          [{ alias: 'saldo_registrado', function: 'SUM', field: 'POSITION_BALANCE', conditions: [] }],
          ['ACCOUNT_ID'],
        ),
        visualization: 'BAR',
        span: 'FULL',
      },
      {
        id: 'position-history',
        title: text('Evolución del saldo registrado', 'Recorded balance history'),
        description: text(
          'Saldos históricos por cuenta desde su fecha inicial; una ausencia previa no equivale a cero.',
          'Historical account balances since each opening date; an earlier absence does not mean zero.',
        ),
        definition: query(
          catalog,
          'ACCOUNT_POSITION_SNAPSHOT',
          'OBSERVATION_DATE',
          [{ alias: 'saldo_registrado', function: 'SUM', field: 'POSITION_BALANCE', conditions: [] }],
          ['OBSERVATION_MONTH', 'ACCOUNT_ID'],
        ),
        visualization: 'LINE',
        span: 'FULL',
      },
      {
        id: 'credit-debt-history',
        title: text('Deuda de tarjetas', 'Credit-card debt'),
        description: text(
          'Deuda registrada por tarjeta; se conserva aunque no exista límite de crédito.',
          'Recorded debt by card; it remains available even without a credit limit.',
        ),
        definition: query(
          catalog,
          'CREDIT_ACCOUNT_SNAPSHOT',
          'OBSERVATION_DATE',
          [{ alias: 'deuda_tarjeta', function: 'SUM', field: 'CREDIT_DEBT', conditions: [] }],
          ['OBSERVATION_MONTH', 'ACCOUNT_ID'],
        ),
        visualization: 'LINE',
        span: 'FULL',
      },
      {
        id: 'current-credit-limit',
        title: text('Límite de crédito actual', 'Current credit limit'),
        description: text(
          'Sólo existe para la fecha actual: FINTRACK no guarda historial de límites.',
          'Available only for today: FINTRACK does not keep a limit history.',
        ),
        definition: query(
          catalog,
          'CREDIT_ACCOUNT_SNAPSHOT',
          'OBSERVATION_DATE',
          [{ alias: 'limite_credito', function: 'SUM', field: 'CREDIT_LIMIT', conditions: [] }],
          ['ACCOUNT_ID'],
        ),
        visualization: 'KPI',
        contextPolicy: 'CURRENT_ONLY',
        span: 'KPI',
      },
      {
        id: 'current-available-credit',
        title: text('Crédito disponible actual', 'Current available credit'),
        description: text(
          'Límite actual menos deuda; puede ser negativo y nunca se recorta a cero.',
          'Current limit less debt; it may be negative and is never clamped to zero.',
        ),
        definition: query(
          catalog,
          'CREDIT_ACCOUNT_SNAPSHOT',
          'OBSERVATION_DATE',
          [{ alias: 'credito_disponible', function: 'SUM', field: 'CREDIT_AVAILABLE', conditions: [] }],
          ['ACCOUNT_ID'],
        ),
        visualization: 'KPI',
        contextPolicy: 'CURRENT_ONLY',
        span: 'KPI',
      },
      {
        id: 'current-credit-utilization',
        title: text('Utilización de crédito actual', 'Current credit utilization'),
        description: text(
          'Deuda actual sobre un límite positivo actual; no se limita artificialmente a 100%.',
          'Current debt over a positive current limit; it is never artificially capped at 100%.',
        ),
        definition: query(
          catalog,
          'CREDIT_ACCOUNT_SNAPSHOT',
          'OBSERVATION_DATE',
          [{ alias: 'utilizacion_credito', function: 'SUM', field: 'CREDIT_UTILIZATION_PERCENT', conditions: [] }],
          ['ACCOUNT_ID'],
        ),
        visualization: 'KPI',
        contextPolicy: 'CURRENT_ONLY',
        span: 'KPI',
      },
    ],
  },
];
