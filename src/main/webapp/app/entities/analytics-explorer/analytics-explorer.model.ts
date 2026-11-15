export type SemanticField = string;
export type SemanticPredicateOperator = 'EQUALS' | 'IN' | 'DESCENDANT_OF' | 'IS_UNCATEGORIZED' | 'HAS_ANY' | 'HAS_ALL' | 'HAS_NONE';

export type SemanticAggregateCapability = {
  function: 'COUNT' | 'SUM' | 'AVG' | 'NET';
  field: SemanticField;
  outputUnit: 'COUNT' | 'MONEY' | 'PERCENT';
  additivity: string;
};

export type SemanticPredicateCapability = {
  field: SemanticField;
  operators: SemanticPredicateOperator[];
};

export type SemanticFieldDefinition = {
  field: SemanticField;
  valueType: string;
  unit: 'COUNT' | 'MONEY' | 'PERCENT' | null;
  aggregateable: boolean;
  filterable: boolean;
  groupable: boolean;
  label?: { es: string; en: string };
  description?: { es: string; en: string };
};

export type SemanticDatasetDefinition = {
  dataset: string;
  grain: string;
  timeBinding: SemanticField;
  relationships: Array<{ name: string; target: string; cardinality: string; description: string }>;
  fields: SemanticFieldDefinition[];
  aggregateCapabilities: SemanticAggregateCapability[];
  predicateCapabilities: SemanticPredicateCapability[];
  groupings: SemanticField[];
  additivity: string;
  currencyPolicy: string;
  temporalSemantics: string;
};

/** Browser mirror of the only backend query AST. It never names tables, joins, owners or SQL. */
export type SemanticQueryDefinition = {
  schemaVersion: number;
  semanticModel: string;
  dataset: string;
  timeBinding: SemanticField;
  projections: Array<{ alias: string; function: 'COUNT' | 'SUM' | 'AVG' | 'NET'; field: SemanticField; conditions?: SemanticPredicate[] }>;
  predicates: SemanticPredicate[];
  groupBy: SemanticField[];
  orderBy: Array<{ field: SemanticField | null; projectionAlias: string | null; direction: 'ASC' | 'DESC' }>;
  limit: number;
};

export type SemanticPredicate = { field: SemanticField; operator: SemanticPredicateOperator; values: string[] };

export type VisualizationContext = {
  schemaVersion: number;
  time: { mode: 'RELATIVE' | 'ABSOLUTE_RANGE'; relativePeriod: string | null; from: string | null; through: string | null };
  accountIds: number[];
  currencyCodes: string[];
};

export type SemanticAnalyticsCatalog = {
  schemaVersion: number;
  semanticModel: string;
  catalogVersion: string;
  datasets: SemanticDatasetDefinition[];
  supportedRelativePeriods: string[];
  defaultLimit: number;
  maxLimit: number;
  authenticatedOwnerRequired: boolean;
};

export type AnalyticsDiagnostic = {
  code: string;
  field?: string | null;
  message: string;
  line?: number | null;
  column?: number | null;
};

export type AnalyticsProblem = { code?: string; diagnostics?: AnalyticsDiagnostic[]; detail?: string };

export type SemanticAnalyticsResult = {
  schemaVersion: number;
  semanticModel: string;
  effectiveContext: { from: string; through: string; accountIds: number[]; currencyCodes: string[] };
  executedAt: string;
  columns: Array<{ id: string; dimension: boolean; unit: 'COUNT' | 'MONEY' | 'PERCENT' | null; currencyFaceted: boolean }>;
  rows: Array<{
    dimensions: Array<{
      field: SemanticField;
      value: string | number | null;
      metadata?: {
        stableId: string;
        label: string;
        color: string | null;
        active: boolean | null;
        synthetic: boolean;
        path: string[];
      } | null;
    }>;
    currencyCode: string | null;
    values: Array<{
      alias: string;
      value: number | null;
      unit: 'COUNT' | 'MONEY' | 'PERCENT';
      availability: 'COMPLETE' | 'ZERO' | 'NO_DATA' | 'UNAVAILABLE' | 'NOT_APPLICABLE' | 'PARTIAL';
      /** True only for a valid empty calendar period completed by the semantic backend. */
      derived?: boolean;
    }>;
  }>;
  availability?: 'COMPLETE' | 'ZERO' | 'NO_DATA' | 'UNAVAILABLE' | 'NOT_APPLICABLE' | 'PARTIAL';
  warnings?: Array<{ code: string; message: string }>;
};

/** Presentation is local UI state. It never changes the semantic query definition. */
export type ExplorerVisualization = 'TABLE' | 'KPI' | 'LINE' | 'BAR' | 'GROUPED_BAR' | 'STACKED_BAR' | 'DONUT';

export type AnalyticsReference = {
  id: number;
  name: string;
  description?: string | null;
  color?: string | null;
  active?: boolean;
  parentId?: number | null;
  path?: string[];
  system?: boolean;
};
