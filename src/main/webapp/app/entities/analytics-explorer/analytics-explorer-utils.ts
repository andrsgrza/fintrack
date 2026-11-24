import {
  ExplorerVisualization,
  SemanticAnalyticsCatalog,
  SemanticAnalyticsResult,
  SemanticDatasetDefinition,
  SemanticQueryDefinition,
  VisualizationContext,
} from './analytics-explorer.model';

type Translation = { es: string; en: string };

const translations: Record<string, Translation> = {
  TRANSACTION_LEDGER: { es: 'Movimientos', en: 'Transactions' },
  FINANCIAL_POSITION_SNAPSHOT: { es: 'Posición financiera registrada', en: 'Registered financial position' },
  ACCOUNT_POSITION_SNAPSHOT: { es: 'Posiciones de cuentas', en: 'Account positions' },
  CREDIT_ACCOUNT_SNAPSHOT: { es: 'Tarjetas de crédito', en: 'Credit cards' },
  INTERNAL_TRANSFER_EVENT: { es: 'Transferencias internas', en: 'Internal transfers' },
  TRANSACTION_ID: { es: 'Movimiento', en: 'Transaction' },
  TRANSACTION_AMOUNT: { es: 'Importe', en: 'Amount' },
  TRANSACTION_FLOW: { es: 'Flujo', en: 'Flow' },
  TRANSACTION_DATE: { es: 'Fecha de movimiento', en: 'Transaction date' },
  TRANSACTION_DAY: { es: 'Día', en: 'Day' },
  TRANSACTION_WEEK: { es: 'Semana', en: 'Week' },
  TRANSACTION_MONTH: { es: 'Mes', en: 'Month' },
  TRANSACTION_YEAR: { es: 'Año', en: 'Year' },
  TRANSFER_LINK_STATUS: { es: 'Relación con transferencia interna', en: 'Internal transfer relationship' },
  ACCOUNT_INSTITUTION: { es: 'Institución financiera', en: 'Financial institution' },
  ACCOUNT_ID: { es: 'Cuenta', en: 'Account' },
  ACCOUNT_TYPE: { es: 'Tipo de cuenta', en: 'Account type' },
  CATEGORY_ID: { es: 'Categoría asignada', en: 'Assigned category' },
  CATEGORY_ROOT: { es: 'Categoría principal', en: 'Root category' },
  CATEGORY_SUBCATEGORY: { es: 'Subcategoría', en: 'Subcategory' },
  CATEGORY_BRANCH_CHILD: { es: 'Hijos de la categoría', en: 'Category children' },
  TAG_ID: { es: 'Etiqueta', en: 'Tag' },
  OBSERVATION_DATE: { es: 'Fecha de observación', en: 'Observation date' },
  OBSERVATION_MONTH: { es: 'Mes de observación', en: 'Observation month' },
  POSITION_BALANCE: { es: 'Saldo registrado', en: 'Recorded balance' },
  FINANCIAL_POSITION: { es: 'Posición financiera registrada', en: 'Registered financial position' },
  CREDIT_DEBT: { es: 'Deuda de tarjeta', en: 'Credit-card debt' },
  CREDIT_LIMIT: { es: 'Límite de crédito actual', en: 'Current credit limit' },
  CREDIT_AVAILABLE: { es: 'Crédito disponible actual', en: 'Current available credit' },
  CREDIT_UTILIZATION_PERCENT: { es: 'Utilización de crédito actual', en: 'Current credit utilization' },
  TRANSFER_ID: { es: 'Transferencia interna', en: 'Internal transfer' },
  TRANSFER_AMOUNT: { es: 'Importe transferido', en: 'Transferred amount' },
  TRANSFER_DATE: { es: 'Fecha de transferencia', en: 'Transfer date' },
  TRANSFER_DAY: { es: 'Día', en: 'Day' },
  TRANSFER_WEEK: { es: 'Semana', en: 'Week' },
  TRANSFER_MONTH: { es: 'Mes', en: 'Month' },
  TRANSFER_YEAR: { es: 'Año', en: 'Year' },
  SOURCE_ACCOUNT_ID: { es: 'Cuenta de origen', en: 'Source account' },
  SOURCE_ACCOUNT_INSTITUTION: { es: 'Institución de origen', en: 'Source institution' },
  SOURCE_ACCOUNT_TYPE: { es: 'Tipo de cuenta de origen', en: 'Source account type' },
  DESTINATION_ACCOUNT_ID: { es: 'Cuenta de destino', en: 'Destination account' },
  DESTINATION_ACCOUNT_INSTITUTION: { es: 'Institución de destino', en: 'Destination institution' },
  DESTINATION_ACCOUNT_TYPE: { es: 'Tipo de cuenta de destino', en: 'Destination account type' },
  COUNT: { es: 'Contar', en: 'Count' },
  SUM: { es: 'Total', en: 'Total' },
  NET: { es: 'Flujo neto económico', en: 'Net economic flow' },
  AVG: { es: 'Promedio', en: 'Average' },
  EQUALS: { es: 'Es igual a', en: 'Equals' },
  IN: { es: 'Es uno de', en: 'Is one of' },
  DESCENDANT_OF: { es: 'Está dentro de', en: 'Is within' },
  DESCENDANT_OF_ANY: { es: 'Está dentro de cualquiera de', en: 'Is within any of' },
  IS_UNCATEGORIZED: { es: 'No tiene categoría', en: 'Has no category' },
  HAS_ANY: { es: 'Tiene alguna de', en: 'Has any of' },
  HAS_ALL: { es: 'Tiene todas', en: 'Has all' },
  HAS_NONE: { es: 'No tiene etiquetas', en: 'Has no tags' },
  COMPLETE: { es: 'Completo', en: 'Complete' },
  ZERO: { es: 'Cero', en: 'Zero' },
  NO_DATA: { es: 'Sin datos', en: 'No data' },
  UNAVAILABLE: { es: 'No disponible', en: 'Unavailable' },
  NOT_APPLICABLE: { es: 'No aplica', en: 'Not applicable' },
  PARTIAL: { es: 'Parcial', en: 'Partial' },
  DEBIT: { es: 'Débito', en: 'Debit' },
  CASH: { es: 'Efectivo', en: 'Cash' },
  INVESTMENT: { es: 'Inversión', en: 'Investment' },
  CREDIT_CARD: { es: 'Tarjeta de crédito', en: 'Credit card' },
  CREDIT: { es: 'Tarjeta de crédito', en: 'Credit card' },
  OUT: { es: 'Salida', en: 'Outflow' },
  LINKED: { es: 'Vinculado a transferencia interna', en: 'Linked to an internal transfer' },
  UNLINKED: { es: 'No vinculado a transferencia interna', en: 'Not linked to an internal transfer' },
};

export const copy = (locale: string, es: string, en: string) => (locale.startsWith('es') ? es : en);
export const label = (locale: string, id: string) => translations[id]?.[locale.startsWith('es') ? 'es' : 'en'] ?? id;
export const datasetById = (catalog: SemanticAnalyticsCatalog, id: string | null | undefined) =>
  catalog.datasets.find(dataset => dataset.dataset === id);
export const isTemporalField = (field: string) => /_(DAY|WEEK|MONTH|YEAR)$/.test(field) || field === 'OBSERVATION_MONTH';
export const isCategoryField = (field: string) =>
  ['CATEGORY_ID', 'CATEGORY_ROOT', 'CATEGORY_SUBCATEGORY', 'CATEGORY_BRANCH_CHILD'].includes(field);

const presentationLocale = (locale: string) => (locale.startsWith('es') ? 'es-MX' : 'en-US');

const isoWeekStart = (year: number, week: number) => {
  const fourthOfJanuary = new Date(Date.UTC(year, 0, 4));
  const mondayOffset = (fourthOfJanuary.getUTCDay() + 6) % 7;
  const start = new Date(fourthOfJanuary);
  start.setUTCDate(fourthOfJanuary.getUTCDate() - mondayOffset + (week - 1) * 7);
  return start;
};

const isoDate = (date: Date) => date.toISOString().slice(0, 10);
const dateFromIso = (value: string) => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const parsed = new Date(`${value}T00:00:00.000Z`);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
};
const startOfIsoWeek = (date: Date) => {
  const start = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  start.setUTCDate(start.getUTCDate() - ((start.getUTCDay() + 6) % 7));
  return start;
};
const isoWeekBucket = (monday: Date) => {
  const thursday = new Date(monday);
  thursday.setUTCDate(thursday.getUTCDate() + 3);
  const weekYear = thursday.getUTCFullYear();
  const firstMonday = isoWeekStart(weekYear, 1);
  const week = Math.floor((monday.getTime() - firstMonday.getTime()) / (7 * 24 * 60 * 60 * 1000)) + 1;
  return `${weekYear}-W${String(week).padStart(2, '0')}`;
};

/** A canonical identity is required so generated empty buckets and returned ISO-week buckets share one chart point. */
export const temporalBucketIdentity = (field: string, value: string | number | null) => {
  const raw = String(value ?? '—').replace(/^ISO\s+/, '');
  return `${field}:${raw}`;
};

/**
 * Lists every calendar bucket that overlaps the executed context. These are presentation-only null points:
 * they preserve the real distance between returned observations without manufacturing a financial result.
 */
export const temporalBucketsInContext = (field: string, from: string, through: string) => {
  if (!isTemporalField(field)) return [];
  const start = dateFromIso(from);
  const end = dateFromIso(through);
  if (!start || !end || start > end) return [];

  const buckets: Array<{ value: string; sortKey: number }> = [];
  if (field.endsWith('_DAY')) {
    for (let cursor = new Date(start); cursor <= end; cursor.setUTCDate(cursor.getUTCDate() + 1)) {
      buckets.push({ value: isoDate(cursor), sortKey: cursor.getTime() });
    }
    return buckets;
  }
  if (field.endsWith('_WEEK')) {
    for (let cursor = startOfIsoWeek(start), last = startOfIsoWeek(end); cursor <= last; cursor.setUTCDate(cursor.getUTCDate() + 7)) {
      buckets.push({ value: isoWeekBucket(cursor), sortKey: cursor.getTime() });
    }
    return buckets;
  }
  if (field.endsWith('_MONTH') || field === 'OBSERVATION_MONTH') {
    for (
      let cursor = new Date(Date.UTC(start.getUTCFullYear(), start.getUTCMonth(), 1)),
        last = new Date(Date.UTC(end.getUTCFullYear(), end.getUTCMonth(), 1));
      cursor <= last;
      cursor.setUTCMonth(cursor.getUTCMonth() + 1)
    ) {
      buckets.push({ value: `${cursor.getUTCFullYear()}-${String(cursor.getUTCMonth() + 1).padStart(2, '0')}`, sortKey: cursor.getTime() });
    }
    return buckets;
  }
  for (let year = start.getUTCFullYear(); year <= end.getUTCFullYear(); year += 1) {
    buckets.push({ value: String(year), sortKey: Date.UTC(year, 0, 1) });
  }
  return buckets;
};

/** Turns a semantic temporal identity into a date the person can read. The raw identity stays in the result/AST. */
export const formatTemporalBucket = (field: string, value: string | number | null, locale: string) => {
  if (value === null || value === undefined) return '—';
  const raw = String(value);
  const language = presentationLocale(locale);
  const week = raw.match(/^(?:ISO\s+)?(\d{4})-W(\d{1,2})$/);
  if (week && isTemporalField(field)) {
    const start = isoWeekStart(Number(week[1]), Number(week[2]));
    const end = new Date(start);
    end.setUTCDate(start.getUTCDate() + 6);
    const startFormatter = new Intl.DateTimeFormat(language, { day: 'numeric', month: 'short', timeZone: 'UTC' });
    const endFormatter = new Intl.DateTimeFormat(language, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
    return `${startFormatter.format(start)} – ${endFormatter.format(end)}`;
  }
  if (/^\d{4}-\d{2}$/.test(raw) && isTemporalField(field)) {
    const [year, month] = raw.split('-').map(Number);
    return new Intl.DateTimeFormat(language, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
      new Date(Date.UTC(year, month - 1, 1)),
    );
  }
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw) && isTemporalField(field)) {
    return new Intl.DateTimeFormat(language, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(
      new Date(`${raw}T00:00:00.000Z`),
    );
  }
  return raw;
};

/** Sorts temporal buckets without changing their source identity. */
export const temporalSortKey = (field: string, value: string | number | null) => {
  if (!isTemporalField(field) || value === null || value === undefined) return null;
  const raw = String(value);
  const week = raw.match(/^(?:ISO\s+)?(\d{4})-W(\d{1,2})$/);
  if (week) return isoWeekStart(Number(week[1]), Number(week[2])).getTime();
  if (/^\d{4}-\d{2}$/.test(raw)) return Date.parse(`${raw}-01T00:00:00.000Z`);
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) return Date.parse(`${raw}T00:00:00.000Z`);
  if (/^\d{4}$/.test(raw)) return Date.UTC(Number(raw), 0, 1);
  return null;
};

const aliasFor = (functionName: string, field: string) => `${functionName.toLowerCase()}_${field.toLowerCase()}`;

export const initialDefinition = (catalog: SemanticAnalyticsCatalog, dataset: SemanticDatasetDefinition): SemanticQueryDefinition => {
  const first = dataset.aggregateCapabilities[0];
  const groupBy =
    dataset.dataset === 'ACCOUNT_POSITION_SNAPSHOT'
      ? ['ACCOUNT_ID', 'OBSERVATION_MONTH']
      : dataset.dataset === 'CREDIT_ACCOUNT_SNAPSHOT'
        ? ['ACCOUNT_ID']
        : [];
  return {
    schemaVersion: catalog.schemaVersion,
    semanticModel: catalog.semanticModel,
    dataset: dataset.dataset,
    timeBinding: dataset.timeBinding,
    projections: [{ alias: aliasFor(first.function, first.field), function: first.function, field: first.field, conditions: [] }],
    predicates: [],
    groupBy,
    orderBy: [],
    limit: catalog.defaultLimit,
  };
};

export const defaultVisualizationContext = (schemaVersion: number, relativePeriod: string | undefined): VisualizationContext => ({
  schemaVersion,
  time: { mode: 'RELATIVE', relativePeriod: relativePeriod ?? 'THIS_MONTH', from: null, through: null },
  accountIds: [],
  currencyCodes: [],
});

export type QueryExample = {
  id: string;
  title: Translation;
  description: Translation;
  definition: SemanticQueryDefinition;
};

const baseDefinition = (catalog: SemanticAnalyticsCatalog, dataset: SemanticDatasetDefinition) => ({
  schemaVersion: catalog.schemaVersion,
  semanticModel: catalog.semanticModel,
  dataset: dataset.dataset,
  timeBinding: dataset.timeBinding,
  limit: catalog.defaultLimit,
});

const hasAggregate = (dataset: SemanticDatasetDefinition | undefined, functionName: string, field: string) =>
  !!dataset?.aggregateCapabilities.some(item => item.function === functionName && item.field === field);

/** Examples are canonical, editable definitions. The catalog remains the capability authority. */
export const queryExamples = (catalog: SemanticAnalyticsCatalog): QueryExample[] => {
  const ledger = datasetById(catalog, 'TRANSACTION_LEDGER');
  const positions = datasetById(catalog, 'ACCOUNT_POSITION_SNAPSHOT');
  if (!ledger) return [];
  const base = baseDefinition(catalog, ledger);
  const economicExpense = [
    { field: 'TRANSACTION_FLOW', operator: 'EQUALS' as const, values: ['OUT'] },
    { field: 'TRANSFER_LINK_STATUS', operator: 'EQUALS' as const, values: ['UNLINKED'] },
  ];
  const economicIncome = [
    { field: 'TRANSACTION_FLOW', operator: 'EQUALS' as const, values: ['IN'] },
    { field: 'TRANSFER_LINK_STATUS', operator: 'EQUALS' as const, values: ['UNLINKED'] },
  ];
  const examples: QueryExample[] = [
    {
      id: 'expenses-by-category',
      title: { es: 'Gastos por categoría', en: 'Expenses by category' },
      description: {
        es: 'Gasto económico: excluye transferencias internas vinculadas.',
        en: 'Economic expense: excludes linked internal transfers.',
      },
      definition: {
        ...base,
        projections: [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
        predicates: economicExpense,
        groupBy: ['CATEGORY_ID'],
        orderBy: [{ field: null, projectionAlias: 'gastos', direction: 'DESC' }],
      },
    },
    {
      id: 'expenses-over-time',
      title: { es: 'Gastos a lo largo del tiempo', en: 'Expenses over time' },
      description: { es: 'Evolución mensual del gasto económico.', en: 'Monthly economic expense trend.' },
      definition: {
        ...base,
        projections: [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
        predicates: economicExpense,
        groupBy: ['TRANSACTION_MONTH'],
        orderBy: [{ field: 'TRANSACTION_MONTH', projectionAlias: null, direction: 'ASC' }],
      },
    },
    {
      id: 'income-and-expenses-monthly',
      title: { es: 'Ingresos y gastos mensuales', en: 'Monthly income and expenses' },
      description: { es: 'Dos cálculos económicos comparables por mes.', en: 'Two comparable economic calculations by month.' },
      definition: {
        ...base,
        projections: [
          { alias: 'ingresos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: economicIncome },
          { alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: economicExpense },
        ],
        predicates: [],
        groupBy: ['TRANSACTION_MONTH'],
        orderBy: [{ field: 'TRANSACTION_MONTH', projectionAlias: null, direction: 'ASC' }],
      },
    },
    {
      id: 'transactions-by-institution',
      title: { es: 'Movimientos por institución', en: 'Transactions by institution' },
      description: { es: 'Cuenta los movimientos por institución financiera.', en: 'Counts transactions by financial institution.' },
      definition: {
        ...base,
        projections: [{ alias: 'movimientos', function: 'COUNT', field: 'TRANSACTION_ID', conditions: [] }],
        predicates: [],
        groupBy: ['ACCOUNT_INSTITUTION'],
        orderBy: [{ field: null, projectionAlias: 'movimientos', direction: 'DESC' }],
      },
    },
    {
      id: 'expenses-by-tag',
      title: { es: 'Gastos por etiqueta', en: 'Expenses by tag' },
      description: {
        es: 'Las etiquetas pueden solaparse; sus totales no se suman entre sí.',
        en: 'Tags can overlap; their totals do not add together.',
      },
      definition: {
        ...base,
        projections: [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
        predicates: economicExpense,
        groupBy: ['TAG_ID'],
        orderBy: [{ field: null, projectionAlias: 'gastos', direction: 'DESC' }],
      },
    },
  ];
  if (positions && hasAggregate(positions, 'SUM', 'POSITION_BALANCE')) {
    examples.push({
      id: 'balances-by-account',
      title: { es: 'Saldos por cuenta', en: 'Balances by account' },
      description: {
        es: 'Saldos contables registrados por cuenta y mes; no es valor de mercado.',
        en: 'Recorded book balances by account and month; not market value.',
      },
      definition: {
        ...baseDefinition(catalog, positions),
        projections: [{ alias: 'saldo_registrado', function: 'SUM', field: 'POSITION_BALANCE', conditions: [] }],
        predicates: [],
        groupBy: ['OBSERVATION_MONTH', 'ACCOUNT_ID'],
        orderBy: [{ field: 'OBSERVATION_MONTH', projectionAlias: null, direction: 'ASC' }],
      },
    });
  }
  return examples;
};

export const querySignature = (definition: SemanticQueryDefinition | null, context: VisualizationContext | null) =>
  JSON.stringify({ definition, context });

export const rowLabel = (row: SemanticAnalyticsResult['rows'][number], locale: string) =>
  row.dimensions.length
    ? row.dimensions
        .map(
          dimension =>
            `${label(locale, dimension.field)}: ${
              isTemporalField(dimension.field)
                ? formatTemporalBucket(dimension.field, dimension.value, locale)
                : (dimension.metadata?.label ?? dimension.value ?? '—')
            }`,
        )
        .join(' · ')
    : copy(locale, 'Total', 'Total');

export const derivedAlias = aliasFor;

/** Readable defaults keep implementation field names out of the normal visual workflow. */
export const suggestedAlias = (functionName: string, field: string) => {
  const defaults: Record<string, string> = {
    'COUNT:TRANSACTION_ID': 'movimientos',
    'SUM:TRANSACTION_AMOUNT': 'total',
    'NET:TRANSACTION_AMOUNT': 'flujo_neto',
    'AVG:TRANSACTION_AMOUNT': 'promedio',
    'SUM:POSITION_BALANCE': 'saldo_registrado',
    'AVG:POSITION_BALANCE': 'saldo_promedio',
    'SUM:CREDIT_DEBT': 'deuda_tarjeta',
    'SUM:CREDIT_LIMIT': 'limite_credito',
    'SUM:CREDIT_AVAILABLE': 'credito_disponible',
    'AVG:CREDIT_UTILIZATION_PERCENT': 'utilizacion_credito',
    'COUNT:TRANSFER_ID': 'transferencias',
    'SUM:TRANSFER_AMOUNT': 'total_transferido',
  };
  return defaults[`${functionName}:${field}`] ?? aliasFor(functionName, field);
};

type FieldHelp = { es: string; en: string; typeEs: string; typeEn: string };

const fieldHelp: Record<string, FieldHelp> = {
  TRANSACTION_ID: { es: 'Cuenta movimientos financieros.', en: 'Counts financial transactions.', typeEs: 'Conteo', typeEn: 'Count' },
  TRANSACTION_AMOUNT: { es: 'Importe registrado del movimiento.', en: 'Recorded transaction amount.', typeEs: 'Importe', typeEn: 'Amount' },
  TRANSACTION_FLOW: { es: 'Distingue entradas y salidas.', en: 'Distinguishes inflows and outflows.', typeEs: 'Opción', typeEn: 'Option' },
  TRANSFER_LINK_STATUS: {
    es: 'Distingue las piernas vinculadas a transferencias internas.',
    en: 'Distinguishes legs linked to internal transfers.',
    typeEs: 'Opción',
    typeEn: 'Option',
  },
  CATEGORY_ID: {
    es: 'Categoría asignada al movimiento.',
    en: 'Category assigned to the transaction.',
    typeEs: 'Categoría',
    typeEn: 'Category',
  },
  CATEGORY_ROOT: {
    es: 'Categoría principal de la asignación.',
    en: 'Root category of the assignment.',
    typeEs: 'Categoría',
    typeEn: 'Category',
  },
  CATEGORY_SUBCATEGORY: { es: 'Primera subcategoría.', en: 'First subcategory.', typeEs: 'Categoría', typeEn: 'Category' },
  CATEGORY_BRANCH_CHILD: {
    es: 'Hijo inmediato de una categoría elegida; conserva asignaciones directas.',
    en: 'Immediate child of a selected category; retains direct assignments.',
    typeEs: 'Categoría',
    typeEn: 'Category',
  },
  TAG_ID: {
    es: 'Un movimiento con varias etiquetas puede aparecer en varios grupos.',
    en: 'A transaction with several tags can appear in several groups.',
    typeEs: 'Etiqueta',
    typeEn: 'Tag',
  },
  POSITION_BALANCE: {
    es: 'Saldo contable a una fecha; no es valor de mercado.',
    en: 'Book balance at a date; not market value.',
    typeEs: 'Saldo',
    typeEn: 'Balance',
  },
  CREDIT_DEBT: { es: 'Deuda registrada de una tarjeta.', en: 'Recorded credit-card debt.', typeEs: 'Importe', typeEn: 'Amount' },
  CREDIT_LIMIT: {
    es: 'Límite registrado hoy; sin historial.',
    en: 'Limit recorded today; no history.',
    typeEs: 'Importe',
    typeEn: 'Amount',
  },
  CREDIT_AVAILABLE: {
    es: 'Límite actual menos deuda actual; puede ser negativo.',
    en: 'Current limit minus current debt; can be negative.',
    typeEs: 'Importe',
    typeEn: 'Amount',
  },
  CREDIT_UTILIZATION_PERCENT: {
    es: 'Deuda actual sobre límite positivo actual; sin tope artificial.',
    en: 'Current debt over current positive limit; never capped.',
    typeEs: 'Porcentaje',
    typeEn: 'Percent',
  },
  TRANSFER_AMOUNT: {
    es: 'Un evento de transferencia, no dos piernas ni un gasto.',
    en: 'One transfer event, not two legs or an expense.',
    typeEs: 'Importe',
    typeEn: 'Amount',
  },
};

export const describeField = (locale: string, field: string) =>
  fieldHelp[field]?.[locale.startsWith('es') ? 'es' : 'en'] ?? label(locale, field);
export const fieldType = (locale: string, field: string) => fieldHelp[field]?.[locale.startsWith('es') ? 'typeEs' : 'typeEn'] ?? '';

export const sourceDescription = (locale: string, dataset: string) => {
  const descriptions: Record<string, Translation> = {
    TRANSACTION_LEDGER: {
      es: 'Movimientos, categorías, etiquetas y actividad económica.',
      en: 'Transactions, categories, tags, and economic activity.',
    },
    FINANCIAL_POSITION_SNAPSHOT: {
      es: 'Saldos registrados compatibles menos deuda registrada de tarjetas, a una misma fecha y por moneda.',
      en: 'Compatible recorded balances less recorded credit-card debt, at one date and per currency.',
    },
    ACCOUNT_POSITION_SNAPSHOT: {
      es: 'Saldos contables históricos por cuenta y mes.',
      en: 'Historical book balances by account and month.',
    },
    CREDIT_ACCOUNT_SNAPSHOT: {
      es: 'Deuda histórica y medidas actuales de tarjetas de crédito.',
      en: 'Historical debt and current credit-card measures.',
    },
    INTERNAL_TRANSFER_EVENT: { es: 'Cada transferencia interna se cuenta una sola vez.', en: 'Every internal transfer is counted once.' },
  };
  return descriptions[dataset]?.[locale.startsWith('es') ? 'es' : 'en'] ?? label(locale, dataset);
};

export const relativePeriodLabel = (locale: string, period: string) =>
  ({
    THIS_MONTH: copy(locale, 'Este mes', 'This month'),
    LAST_MONTH: copy(locale, 'Mes anterior', 'Last month'),
    LAST_3_MONTHS: copy(locale, 'Últimos 3 meses', 'Last 3 months'),
    THIS_YEAR: copy(locale, 'Este año', 'This year'),
    LAST_YEAR: copy(locale, 'Año anterior', 'Last year'),
  })[period] ?? period;

export const predicateValueOptions = (locale: string, field: string): Array<{ value: string; label: string }> => {
  const raw =
    field === 'TRANSACTION_FLOW'
      ? [
          ['OUT', copy(locale, 'Salida', 'Outflow')],
          ['IN', copy(locale, 'Entrada', 'Inflow')],
        ]
      : field === 'TRANSFER_LINK_STATUS'
        ? [
            ['UNLINKED', copy(locale, 'No vinculada a transferencia interna', 'Not linked to an internal transfer')],
            ['LINKED', copy(locale, 'Vinculada a transferencia interna', 'Linked to an internal transfer')],
          ]
        : ['ACCOUNT_TYPE', 'SOURCE_ACCOUNT_TYPE', 'DESTINATION_ACCOUNT_TYPE'].includes(field)
          ? ['DEBIT', 'CASH', 'INVESTMENT', 'CREDIT_CARD'].map(value => [value, label(locale, value)])
          : [];
  return raw.map(([value, optionLabel]) => ({ value, label: optionLabel }));
};

export const visualizationTitle = (locale: string, visualization: ExplorerVisualization) =>
  ({
    KPI: copy(locale, 'Indicadores', 'Indicators'),
    LINE: copy(locale, 'Líneas', 'Line chart'),
    BAR: copy(locale, 'Barras', 'Bars'),
    GROUPED_BAR: copy(locale, 'Barras agrupadas', 'Grouped bars'),
    STACKED_BAR: copy(locale, 'Barras apiladas', 'Stacked bars'),
    DONUT: copy(locale, 'Dona', 'Donut'),
    TABLE: copy(locale, 'Tabla', 'Table'),
  })[visualization];
