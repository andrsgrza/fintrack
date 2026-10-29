import {
  ExplorerVisualization,
  SemanticAnalyticsCatalog,
  SemanticAnalyticsResult,
  SemanticDatasetDefinition,
  SemanticQueryDefinition,
  VisualizationContext,
} from './analytics-explorer.model';

const spanish: Record<string, string> = {
  TRANSACTION_LEDGER: 'Movimientos financieros',
  ACCOUNT_POSITION_SNAPSHOT: 'Saldos históricos',
  TRANSACTION_ID: 'Transacción',
  TRANSACTION_AMOUNT: 'Importe',
  TRANSACTION_FLOW: 'Flujo',
  TRANSACTION_DATE: 'Fecha de transacción',
  TRANSFER_LINK_STATUS: 'Estado de transferencia interna',
  ACCOUNT_INSTITUTION: 'Institución financiera',
  CATEGORY_ID: 'Categoría',
  CATEGORY_ROOT: 'Categoría principal',
  CATEGORY_SUBCATEGORY: 'Subcategoría',
  CATEGORY_BRANCH_CHILD: 'Subcategoría de la rama',
  TAG_ID: 'Etiqueta',
  ACCOUNT_TYPE: 'Tipo de cuenta',
  ACCOUNT_ID: 'Cuenta',
  OBSERVATION_DATE: 'Fecha de observación',
  OBSERVATION_MONTH: 'Mes de observación',
  POSITION_BALANCE: 'Saldo registrado',
  COUNT: 'Conteo',
  SUM: 'Suma',
  AVG: 'Promedio',
  EQUALS: 'Es igual a',
  IN: 'Es uno de',
  COMPLETE: 'Completo',
  ZERO: 'Cero',
  UNAVAILABLE: 'No disponible',
  NO_DATA: 'Sin datos',
  NOT_APPLICABLE: 'No aplica',
  PARTIAL: 'Parcial',
  DEBIT: 'Débito',
  CASH: 'Efectivo',
  INVESTMENT: 'Inversión',
  CREDIT: 'Tarjeta de crédito',
};

const english: Record<string, string> = {
  TRANSACTION_LEDGER: 'Financial activity',
  ACCOUNT_POSITION_SNAPSHOT: 'Historical balances',
  TRANSACTION_ID: 'Transaction',
  TRANSACTION_AMOUNT: 'Amount',
  TRANSACTION_FLOW: 'Flow',
  TRANSACTION_DATE: 'Transaction date',
  TRANSFER_LINK_STATUS: 'Internal transfer status',
  ACCOUNT_INSTITUTION: 'Financial institution',
  CATEGORY_ID: 'Category',
  CATEGORY_ROOT: 'Main category',
  CATEGORY_SUBCATEGORY: 'Subcategory',
  CATEGORY_BRANCH_CHILD: 'Branch subcategory',
  TAG_ID: 'Tag',
  ACCOUNT_TYPE: 'Account type',
  ACCOUNT_ID: 'Account',
  OBSERVATION_DATE: 'Observation date',
  OBSERVATION_MONTH: 'Observation month',
  POSITION_BALANCE: 'Recorded balance',
  COUNT: 'Count',
  SUM: 'Sum',
  AVG: 'Average',
  EQUALS: 'Equals',
  IN: 'Is one of',
  COMPLETE: 'Complete',
  ZERO: 'Zero',
  UNAVAILABLE: 'Unavailable',
  NO_DATA: 'No data',
  NOT_APPLICABLE: 'Not applicable',
  PARTIAL: 'Partial',
  DEBIT: 'Debit',
  CASH: 'Cash',
  INVESTMENT: 'Investment',
  CREDIT: 'Credit card',
};

export const copy = (locale: string, es: string, en: string) => (locale.startsWith('es') ? es : en);
export const label = (locale: string, id: string) => (locale.startsWith('es') ? spanish[id] : english[id]) ?? id;
export const datasetById = (catalog: SemanticAnalyticsCatalog, id: string | null | undefined) =>
  catalog.datasets.find(dataset => dataset.dataset === id);

const aliasFor = (functionName: string, field: string) => `${functionName.toLowerCase()}_${field.toLowerCase()}`;

export const initialDefinition = (catalog: SemanticAnalyticsCatalog, dataset: SemanticDatasetDefinition): SemanticQueryDefinition => {
  const first = dataset.aggregateCapabilities[0];
  return {
    schemaVersion: catalog.schemaVersion,
    semanticModel: catalog.semanticModel,
    dataset: dataset.dataset,
    timeBinding: dataset.timeBinding,
    projections: [{ alias: aliasFor(first.function, first.field), function: first.function, field: first.field }],
    predicates: [],
    groupBy: [],
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
  title: { es: string; en: string };
  description: { es: string; en: string };
  definition: SemanticQueryDefinition;
};

/** Optional starting points. They are ordinary semantic definitions, never an alternative capability registry. */
export const queryExamples = (catalog: SemanticAnalyticsCatalog): QueryExample[] => {
  const ledger = catalog.datasets.find(dataset => dataset.dataset === 'TRANSACTION_LEDGER');
  if (!ledger) return [];
  const base = {
    schemaVersion: catalog.schemaVersion,
    semanticModel: catalog.semanticModel,
    dataset: ledger.dataset,
    timeBinding: ledger.timeBinding,
    limit: catalog.defaultLimit,
  };
  return [
    {
      id: 'transactions-by-institution',
      title: { es: 'Movimientos por institución', en: 'Transactions by institution' },
      description: { es: 'Cuenta movimientos y los compara por institución financiera.', en: 'Counts activity by financial institution.' },
      definition: {
        ...base,
        projections: [{ alias: 'transaction_count', function: 'COUNT', field: 'TRANSACTION_ID' }],
        predicates: [],
        groupBy: ['ACCOUNT_INSTITUTION'],
        orderBy: [{ field: 'ACCOUNT_INSTITUTION', projectionAlias: null, direction: 'ASC' }],
      },
    },
    {
      id: 'economic-expense-by-category',
      title: { es: 'Gasto económico promedio por categoría', en: 'Average economic expense by category' },
      description: {
        es: 'Excluye transferencias internas vinculadas para medir sólo gasto económico.',
        en: 'Excludes linked internal transfers to measure economic expense only.',
      },
      definition: {
        ...base,
        projections: [{ alias: 'average_expense', function: 'AVG', field: 'TRANSACTION_AMOUNT' }],
        predicates: [
          { field: 'TRANSACTION_FLOW', operator: 'EQUALS', values: ['OUT'] },
          { field: 'TRANSFER_LINK_STATUS', operator: 'EQUALS', values: ['UNLINKED'] },
        ],
        groupBy: ['CATEGORY_ID'],
        orderBy: [{ field: null, projectionAlias: 'average_expense', direction: 'DESC' }],
      },
    },
    {
      id: 'count-and-average-expense',
      title: { es: 'Cantidad y promedio de gastos', en: 'Expense count and average' },
      description: {
        es: 'Combina dos cálculos compatibles en una misma consulta.',
        en: 'Combines two compatible calculations in one query.',
      },
      definition: {
        ...base,
        projections: [
          { alias: 'expense_count', function: 'COUNT', field: 'TRANSACTION_ID' },
          { alias: 'average_expense', function: 'AVG', field: 'TRANSACTION_AMOUNT' },
        ],
        predicates: [
          { field: 'TRANSACTION_FLOW', operator: 'EQUALS', values: ['OUT'] },
          { field: 'TRANSFER_LINK_STATUS', operator: 'EQUALS', values: ['UNLINKED'] },
        ],
        groupBy: ['CATEGORY_ID'],
        orderBy: [{ field: null, projectionAlias: 'average_expense', direction: 'DESC' }],
      },
    },
  ];
};

export const querySignature = (definition: SemanticQueryDefinition | null, context: VisualizationContext | null) =>
  JSON.stringify({ definition, context });

export const rowLabel = (row: SemanticAnalyticsResult['rows'][number], locale: string) =>
  row.dimensions.length
    ? row.dimensions
        .map(dimension => `${label(locale, dimension.field)}: ${dimension.metadata?.label ?? dimension.value ?? '—'}`)
        .join(' · ')
    : copy(locale, 'Total', 'Total');

export const visualizationOptions = (result: SemanticAnalyticsResult | null): ExplorerVisualization[] => {
  if (!result?.rows.length) return ['TABLE'];
  const groupCount = result.rows[0]?.dimensions.length ?? 0;
  if (!groupCount) return ['KPI', 'TABLE'];
  const time = result.rows[0]?.dimensions.some(dimension => dimension.field === 'OBSERVATION_MONTH');
  return time ? ['LINE', 'TABLE', 'BAR'] : ['BAR', 'TABLE', 'DONUT'];
};

export const derivedAlias = aliasFor;

type FieldHelp = { es: string; en: string; typeEs: string; typeEn: string };

const fieldHelp: Record<string, FieldHelp> = {
  TRANSACTION_ID: {
    es: 'Cuenta cuántos movimientos hay.',
    en: 'Counts financial movements.',
    typeEs: 'Identificador',
    typeEn: 'Identifier',
  },
  TRANSACTION_AMOUNT: {
    es: 'Usa el importe registrado de cada movimiento.',
    en: 'Uses the recorded amount of each movement.',
    typeEs: 'Importe',
    typeEn: 'Amount',
  },
  TRANSACTION_FLOW: {
    es: 'Distingue entradas y salidas.',
    en: 'Distinguishes money in and money out.',
    typeEs: 'Opción',
    typeEn: 'Option',
  },
  TRANSFER_LINK_STATUS: {
    es: 'Permite incluir o excluir las dos piernas de transferencias internas.',
    en: 'Lets you include or exclude both legs of internal transfers.',
    typeEs: 'Opción',
    typeEn: 'Option',
  },
  ACCOUNT_INSTITUTION: {
    es: 'Agrupa por la institución de cada cuenta.',
    en: 'Groups by each account’s institution.',
    typeEs: 'Texto',
    typeEn: 'Text',
  },
  CATEGORY_ID: {
    es: 'Agrupa por la categoría asignada al movimiento.',
    en: 'Groups by the category assigned to a movement.',
    typeEs: 'Categoría',
    typeEn: 'Category',
  },
  CATEGORY_ROOT: {
    es: 'Agrupa cada categoría bajo su categoría principal.',
    en: 'Groups each category under its main category.',
    typeEs: 'Categoría',
    typeEn: 'Category',
  },
  CATEGORY_SUBCATEGORY: {
    es: 'Agrupa por la primera subcategoría debajo de la categoría principal.',
    en: 'Groups by the first subcategory below the main category.',
    typeEs: 'Categoría',
    typeEn: 'Category',
  },
  CATEGORY_BRANCH_CHILD: {
    es: 'Agrupa por la subcategoría inmediata de una categoría seleccionada.',
    en: 'Groups by the immediate subcategory of a selected category.',
    typeEs: 'Categoría',
    typeEn: 'Category',
  },
  TAG_ID: {
    es: 'Agrupa movimientos por etiqueta; un movimiento con varias etiquetas puede aparecer en varios grupos.',
    en: 'Groups activity by tag; a movement with several tags can appear in several groups.',
    typeEs: 'Etiqueta',
    typeEn: 'Tag',
  },
  ACCOUNT_TYPE: {
    es: 'Distingue débito, efectivo, inversión y otros tipos.',
    en: 'Distinguishes debit, cash, investment, and other account types.',
    typeEs: 'Opción',
    typeEn: 'Option',
  },
  ACCOUNT_ID: {
    es: 'Distingue cada cuenta en el saldo histórico.',
    en: 'Distinguishes each account in historical balances.',
    typeEs: 'Cuenta',
    typeEn: 'Account',
  },
  OBSERVATION_MONTH: { es: 'Agrupa saldos por mes.', en: 'Groups balances by month.', typeEs: 'Mes', typeEn: 'Month' },
  POSITION_BALANCE: {
    es: 'Usa el saldo contable calculado a una fecha.',
    en: 'Uses the authoritative recorded balance on a date.',
    typeEs: 'Saldo',
    typeEn: 'Balance',
  },
};

export const describeField = (locale: string, field: string) =>
  fieldHelp[field]?.[locale.startsWith('es') ? 'es' : 'en'] ?? label(locale, field);
export const fieldType = (locale: string, field: string) => fieldHelp[field]?.[locale.startsWith('es') ? 'typeEs' : 'typeEn'] ?? '';

export const sourceDescription = (locale: string, dataset: string) =>
  dataset === 'TRANSACTION_LEDGER'
    ? copy(
        locale,
        'Analiza tus movimientos: cantidad, importe promedio o total.',
        'Analyze your activity: count, average amount, or total amount.',
      )
    : copy(locale, 'Consulta saldos históricos por cuenta y mes.', 'Review historical balances by account and month.');

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
          ['OUT', copy(locale, 'Salida / gasto', 'Money out / expense')],
          ['IN', copy(locale, 'Entrada / ingreso', 'Money in / income')],
        ]
      : field === 'TRANSFER_LINK_STATUS'
        ? [
            ['UNLINKED', copy(locale, 'Excluir transferencias internas', 'Exclude internal transfers')],
            ['LINKED', copy(locale, 'Sólo transferencias internas', 'Only internal transfers')],
          ]
        : field === 'ACCOUNT_TYPE'
          ? ['DEBIT', 'CASH', 'INVESTMENT', 'CREDIT'].map(value => [value, label(locale, value)])
          : [];
  return raw.map(([value, optionLabel]) => ({ value, label: optionLabel }));
};
