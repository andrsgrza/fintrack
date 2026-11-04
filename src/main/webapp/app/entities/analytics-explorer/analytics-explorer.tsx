import axios from 'axios';
import React, { useEffect, useRef, useState } from 'react';
import { Button, ButtonGroup, Input, Label } from 'reactstrap';

import { useAppSelector } from 'app/config/store';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import {
  executeSemanticQuery,
  formatSemanticQuery,
  getExplorerAccounts,
  getExplorerCategories,
  getExplorerTags,
  getSemanticAnalyticsCatalog,
  parseSemanticQuery,
} from './analytics-explorer-api';
import { AnalyticsContextControls } from './analytics-context-controls';
import {
  AnalyticsDiagnostic,
  AnalyticsProblem,
  AnalyticsReference,
  ExplorerVisualization,
  SemanticAnalyticsCatalog,
  SemanticAnalyticsResult,
  SemanticQueryDefinition,
  VisualizationContext,
} from './analytics-explorer.model';
import { AnalyticsResultsPanel } from './analytics-results-panel';
import { AnalyticsVisualBuilder } from './analytics-visual-builder';
import {
  copy,
  datasetById,
  defaultVisualizationContext,
  initialDefinition,
  queryExamples,
  querySignature,
  suggestedAlias,
} from './analytics-explorer-utils';

type Account = { id: number; name: string; accountType?: string; currency?: string };
type Mode = 'VISUAL' | 'SQL';
type DrillState = { definition: SemanticQueryDefinition; label: string };

const diagnosticsFrom = (error: unknown): AnalyticsDiagnostic[] => {
  if (axios.isAxiosError<AnalyticsProblem>(error)) {
    const data = error.response?.data;
    if (data?.diagnostics?.length) return data.diagnostics;
    if (data?.detail) return [{ code: 'SEMANTIC_ANALYTICS_REQUEST_FAILED', message: data.detail }];
  }
  return [
    { code: 'SEMANTIC_ANALYTICS_REQUEST_FAILED', message: error instanceof Error ? error.message : 'Semantic query request failed.' },
  ];
};

const isCatalog = (candidate: SemanticAnalyticsCatalog): boolean =>
  Array.isArray(candidate.datasets) && candidate.datasets.length > 0 && !!candidate.semanticModel;

const clone = (definition: SemanticQueryDefinition): SemanticQueryDefinition =>
  JSON.parse(JSON.stringify(definition)) as SemanticQueryDefinition;

/** One Explorer, one canonical AST, one semantic executor. Visual and written authoring are alternate views only. */
const AnalyticsExplorer = () => {
  const locale = useAppSelector(state => state.locale.currentLocale) || 'en';
  const [catalog, setCatalog] = useState<SemanticAnalyticsCatalog | null>(null);
  const [definition, setDefinition] = useState<SemanticQueryDefinition | null>(null);
  const [context, setContext] = useState<VisualizationContext | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [categories, setCategories] = useState<AnalyticsReference[]>([]);
  const [tags, setTags] = useState<AnalyticsReference[]>([]);
  const [diagnostics, setDiagnostics] = useState<AnalyticsDiagnostic[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [resultLoading, setResultLoading] = useState(false);
  const [result, setResult] = useState<SemanticAnalyticsResult | null>(null);
  const [executedSignature, setExecutedSignature] = useState<string | null>(null);
  const [visualization, setVisualization] = useState<ExplorerVisualization>('TABLE');
  // All returned series are visible by default. A person may opt into Top N in the advanced builder, but it is never
  // confused with the semantic query LIMIT and never removes rows from the accessible result table.
  const [seriesLimit, setSeriesLimit] = useState<number | 'ALL'>('ALL');
  const [mode, setMode] = useState<Mode>('VISUAL');
  const [sqlText, setSqlText] = useState('');
  const [sqlValidated, setSqlValidated] = useState(true);
  const [scratch, setScratch] = useState(false);
  const [drillStack, setDrillStack] = useState<DrillState[]>([]);
  const sequence = useRef(0);

  useEffect(() => {
    let active = true;
    void Promise.all([getSemanticAnalyticsCatalog(), getExplorerAccounts()])
      .then(([nextCatalog, nextAccounts]) => {
        if (!active) return;
        if (!isCatalog(nextCatalog)) {
          setDiagnostics([
            {
              code: 'SEMANTIC_ANALYTICS_CATALOG_CONTRACT_MISMATCH',
              message: copy(
                locale,
                'El backend activo no expone el catálogo semántico requerido.',
                'The active backend does not expose the required semantic catalog.',
              ),
            },
          ]);
          return;
        }
        setCatalog(nextCatalog);
        setContext(defaultVisualizationContext(nextCatalog.schemaVersion, nextCatalog.supportedRelativePeriods[0]));
        setAccounts(
          nextAccounts
            .filter(account => account.id != null && account.name)
            .map(account => ({ id: account.id, name: account.name, accountType: account.accountType, currency: account.currency })),
        );
      })
      .catch(error => {
        if (active) setDiagnostics(diagnosticsFrom(error));
      })
      .finally(() => {
        if (active) setCatalogLoading(false);
      });
    void Promise.all([getExplorerCategories(), getExplorerTags()])
      .then(([nextCategories, nextTags]) => {
        if (!active) return;
        setCategories(nextCategories);
        setTags(nextTags);
      })
      .catch(() => {
        // Query execution still owner-validates category/tag references. Empty pickers are safer than cached cross-owner labels.
        if (active) {
          setCategories([]);
          setTags([]);
        }
      });
    return () => {
      active = false;
    };
  }, [locale]);

  const normalize = async (candidate: SemanticQueryDefinition): Promise<boolean> => {
    const current = ++sequence.current;
    setBusy(true);
    setDiagnostics([]);
    try {
      const text = await formatSemanticQuery(candidate);
      const parsed = await parseSemanticQuery(text);
      if (current !== sequence.current) return false;
      setDefinition(parsed.definition);
      setSqlText(parsed.canonicalText);
      setSqlValidated(true);
      return true;
    } catch (error) {
      if (current === sequence.current) setDiagnostics(diagnosticsFrom(error));
      return false;
    } finally {
      if (current === sequence.current) setBusy(false);
    }
  };

  const applyVisual = (candidate: SemanticQueryDefinition) => {
    setDefinition(candidate);
    setDiagnostics([]);
    setSqlValidated(true);
    setDrillStack([]);
  };

  const chooseDataset = (id: string, economicFlow?: 'IN' | 'OUT') => {
    if (!catalog) return;
    const dataset = datasetById(catalog, id);
    if (!dataset) return;
    if (
      definition &&
      !window.confirm(
        copy(
          locale,
          'Cambiar de información reiniciará los cálculos, condiciones y desgloses actuales. ¿Continuar?',
          'Changing the information source will reset the current calculations, conditions, and breakdowns. Continue?',
        ),
      )
    )
      return;
    const next = initialDefinition(catalog, dataset);
    if (economicFlow && id === 'TRANSACTION_LEDGER') {
      next.projections = [
        { alias: economicFlow === 'OUT' ? 'gastos' : 'ingresos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] },
      ];
      next.predicates = [
        { field: 'TRANSACTION_FLOW', operator: 'EQUALS', values: [economicFlow] },
        { field: 'TRANSFER_LINK_STATUS', operator: 'EQUALS', values: ['UNLINKED'] },
      ];
    } else {
      next.projections[0].alias = suggestedAlias(next.projections[0].function, next.projections[0].field);
    }
    setScratch(true);
    setMode('VISUAL');
    setDefinition(next);
    setSqlText('');
    setSqlValidated(true);
    setDiagnostics([]);
    setDrillStack([]);
  };

  const applySql = async (): Promise<boolean> => {
    const current = ++sequence.current;
    setBusy(true);
    setDiagnostics([]);
    try {
      const parsed = await parseSemanticQuery(sqlText);
      if (current !== sequence.current) return false;
      setDefinition(parsed.definition);
      setSqlText(parsed.canonicalText);
      setSqlValidated(true);
      setScratch(true);
      setDrillStack([]);
      return true;
    } catch (error) {
      if (current === sequence.current) {
        setDiagnostics(diagnosticsFrom(error));
        setSqlValidated(false);
      }
      return false;
    } finally {
      if (current === sequence.current) setBusy(false);
    }
  };

  const switchMode = async (next: Mode) => {
    if (next === mode || busy) return;
    if (next === 'SQL' && definition) {
      const current = ++sequence.current;
      setBusy(true);
      setDiagnostics([]);
      try {
        const text = await formatSemanticQuery(definition);
        if (current !== sequence.current) return;
        setSqlText(text);
        setSqlValidated(true);
      } catch (error) {
        if (current === sequence.current) setDiagnostics(diagnosticsFrom(error));
        return;
      } finally {
        if (current === sequence.current) setBusy(false);
      }
    }
    if (next === 'VISUAL' && sqlText.trim() && !sqlValidated && !(await applySql())) return;
    setMode(next);
  };

  const execute = async () => {
    if (!definition || !context || busy || resultLoading) return;
    if (mode === 'SQL' && !sqlValidated) {
      setDiagnostics([
        {
          code: 'INVALID_WRITTEN_DRAFT',
          message: copy(
            locale,
            'Corrige y aplica la consulta escrita antes de ejecutarla.',
            'Correct and apply the written query before running it.',
          ),
        },
      ]);
      return;
    }
    setResultLoading(true);
    setDiagnostics([]);
    try {
      const next = await executeSemanticQuery(definition, context);
      setResult(next);
      setExecutedSignature(querySignature(definition, context));
    } catch (error) {
      setDiagnostics(diagnosticsFrom(error));
    } finally {
      setResultLoading(false);
    }
  };

  const useExample = (example: SemanticQueryDefinition) => {
    setMode('VISUAL');
    setScratch(true);
    setDrillStack([]);
    void normalize(example);
  };

  const clear = () => {
    setDefinition(null);
    setSqlText('');
    setDiagnostics([]);
    setScratch(false);
    setDrillStack([]);
  };

  const drillDown = (categoryId: string, categoryLabel: string) => {
    if (!definition || definition.dataset !== 'TRANSACTION_LEDGER') return;
    const next = clone(definition);
    const groupedCategory = next.groupBy.findIndex(field =>
      ['CATEGORY_ID', 'CATEGORY_ROOT', 'CATEGORY_SUBCATEGORY', 'CATEGORY_BRANCH_CHILD'].includes(field),
    );
    if (groupedCategory < 0) return;
    next.predicates = next.predicates.filter(predicate => !(predicate.field === 'CATEGORY_ID' && predicate.operator === 'DESCENDANT_OF'));
    next.predicates.push({ field: 'CATEGORY_ID', operator: 'DESCENDANT_OF', values: [categoryId] });
    next.groupBy[groupedCategory] = 'CATEGORY_BRANCH_CHILD';
    setDrillStack(previous => [...previous, { definition, label: categoryLabel }]);
    setDefinition(next);
    setDiagnostics([]);
  };

  const drillBack = () => {
    const previous = drillStack[drillStack.length - 1];
    if (!previous) return;
    setDefinition(previous.definition);
    setDrillStack(stack => stack.slice(0, -1));
    setDiagnostics([]);
  };

  const stale = !!result && executedSignature !== querySignature(definition, context);
  return (
    <ProductPage wide className="analytics-explorer">
      <ProductPageHeader
        headingId="analytics-explorer-heading"
        title={copy(locale, 'Explorador de métricas', 'Metric explorer')}
        subtitle={copy(
          locale,
          'Construye una definición analítica visual o una Consulta escrita. La vista previa es temporal.',
          'Build a visual or written analytics definition. The preview is temporary.',
        )}
      />
      {catalogLoading ? (
        <div className="border rounded-2 p-4 text-center">{copy(locale, 'Cargando catálogo semántico…', 'Loading semantic catalog…')}</div>
      ) : null}
      {diagnostics.length ? <Diagnostics diagnostics={diagnostics} locale={locale} /> : null}
      {catalog && context ? (
        <div className="d-flex flex-column gap-4">
          <ProductSection
            title={<span className="visually-hidden">{copy(locale, 'Contexto de visualización', 'Visualization context')}</span>}
          >
            <AnalyticsContextControls
              catalog={catalog}
              context={context}
              accounts={accounts}
              locale={locale}
              disabled={busy || resultLoading}
              onChange={setContext}
            />
          </ProductSection>
          {!definition && !scratch ? (
            <ExampleGallery catalog={catalog} locale={locale} busy={busy} onExample={useExample} onScratch={() => setScratch(true)} />
          ) : null}
          {scratch || definition ? (
            <section className="d-flex flex-column gap-3" aria-label={copy(locale, 'Definición de consulta', 'Query definition')}>
              <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 px-1" data-cy="analyticsModeBar">
                <ButtonGroup aria-label={copy(locale, 'Modo de consulta', 'Query mode')}>
                  <Button
                    type="button"
                    size="sm"
                    color={mode === 'VISUAL' ? 'primary' : 'secondary'}
                    outline={mode !== 'VISUAL'}
                    disabled={busy}
                    onClick={() => void switchMode('VISUAL')}
                    data-cy="analyticsModeVisual"
                  >
                    {copy(locale, 'Visual', 'Visual')}
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    color={mode === 'SQL' ? 'primary' : 'secondary'}
                    outline={mode !== 'SQL'}
                    disabled={busy || !definition}
                    onClick={() => void switchMode('SQL')}
                    data-cy="analyticsModeSql"
                  >
                    {copy(locale, 'Consulta escrita', 'Written query')}
                  </Button>
                </ButtonGroup>
                <div className="d-flex align-items-center gap-2">
                  <span className="small text-muted d-none d-md-inline">
                    {copy(locale, 'Ambos modos representan la misma consulta.', 'Both modes represent the same query.')}
                  </span>
                  <Button
                    type="button"
                    size="sm"
                    color="primary"
                    disabled={!definition || !context || busy || resultLoading}
                    onClick={() => void execute()}
                    data-cy="analyticsExecute"
                  >
                    {resultLoading ? copy(locale, 'Ejecutando…', 'Running…') : copy(locale, 'Ejecutar consulta', 'Run query')}
                  </Button>
                </div>
              </div>
              {mode === 'VISUAL' ? (
                <AnalyticsVisualBuilder
                  catalog={catalog}
                  definition={definition}
                  locale={locale}
                  busy={busy}
                  categories={categories}
                  tags={tags}
                  visualization={visualization}
                  seriesLimit={seriesLimit}
                  onVisualizationChange={setVisualization}
                  onSeriesLimitChange={setSeriesLimit}
                  onApply={applyVisual}
                  onChooseInformation={chooseDataset}
                  onClear={clear}
                />
              ) : (
                <section className="card border shadow-sm">
                  <div className="card-body p-3 p-md-4">
                    <SqlEditor
                      locale={locale}
                      text={sqlText}
                      busy={busy}
                      onChange={text => {
                        setSqlText(text);
                        setSqlValidated(false);
                      }}
                      onApply={() => void applySql()}
                    />
                  </div>
                </section>
              )}
            </section>
          ) : null}
          {definition ? (
            <AnalyticsResultsPanel
              definition={definition}
              result={result}
              visualization={visualization}
              locale={locale}
              loading={resultLoading}
              stale={stale}
              seriesLimit={seriesLimit}
              drillTrail={drillStack.map(step => step.label)}
              onDrillDown={drillDown}
              onDrillBack={drillBack}
            />
          ) : null}
        </div>
      ) : null}
    </ProductPage>
  );
};

const ExampleGallery = ({
  catalog,
  locale,
  busy,
  onExample,
  onScratch,
}: {
  catalog: SemanticAnalyticsCatalog;
  locale: string;
  busy: boolean;
  onExample: (definition: SemanticQueryDefinition) => void;
  onScratch: () => void;
}) => (
  <section className="border rounded-3 p-4 bg-light" data-cy="analyticsExamples">
    <div className="d-flex flex-wrap justify-content-between gap-2 mb-3">
      <div>
        <p className="text-uppercase small fw-semibold text-muted mb-1">{copy(locale, 'Para empezar', 'Get started')}</p>
        <h2 className="h5 mb-1">
          {copy(locale, 'Elige un ejemplo o crea una consulta desde cero', 'Choose an example or create a query from scratch')}
        </h2>
        <p className="text-muted mb-0">
          {copy(
            locale,
            'Los ejemplos son atajos editables, no límites de capacidad.',
            'Examples are editable shortcuts, never capability limits.',
          )}
        </p>
      </div>
      <Button type="button" color="primary" disabled={busy} onClick={onScratch} data-cy="analyticsCreateScratch">
        {copy(locale, 'Crear desde cero', 'Create from scratch')}
      </Button>
    </div>
    <div className="row g-2">
      {queryExamples(catalog).map(example => (
        <div className="col-12 col-md-6 col-xl-4" key={example.id}>
          <Button
            type="button"
            color="light"
            className="border text-start w-100 h-100 p-3"
            disabled={busy}
            onClick={() => onExample(example.definition)}
            data-cy={`analyticsExample-${example.id}`}
          >
            <strong className="d-block small">{locale.startsWith('es') ? example.title.es : example.title.en}</strong>
            <span className="small text-muted d-block mt-1">
              {locale.startsWith('es') ? example.description.es : example.description.en}
            </span>
          </Button>
        </div>
      ))}
    </div>
  </section>
);

const SqlEditor = ({
  locale,
  text,
  busy,
  onChange,
  onApply,
}: {
  locale: string;
  text: string;
  busy: boolean;
  onChange: (text: string) => void;
  onApply: () => void;
}) => (
  <section data-cy="analyticsSqlEditor">
    <div className="d-flex justify-content-between gap-2 mb-2">
      <div>
        <p className="text-uppercase small fw-semibold text-muted mb-1">{copy(locale, 'Consulta escrita', 'Written query')}</p>
        <h2 className="h6 mb-1">{copy(locale, 'Escribe una consulta financiera gobernada', 'Write a governed financial query')}</h2>
        <p className="small text-muted mb-0">
          {copy(
            locale,
            'No es SQL de base de datos: el servidor valida el lenguaje financiero limitado.',
            'This is not database SQL: the server validates the constrained financial language.',
          )}
        </p>
      </div>
      <Button type="button" color="primary" size="sm" disabled={busy || !text.trim()} onClick={onApply} data-cy="analyticsApplySql">
        {copy(locale, 'Validar y aplicar', 'Validate and apply')}
      </Button>
    </div>
    <Label className="visually-hidden" for="analytics-sql-text">
      {copy(locale, 'Consulta escrita', 'Written query')}
    </Label>
    <Input
      id="analytics-sql-text"
      type="textarea"
      rows="12"
      value={text}
      spellCheck={false}
      placeholder="SELECT COUNT(TRANSACTION_ID) AS movimientos FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE GROUP BY ACCOUNT_INSTITUTION LIMIT 100"
      onChange={event => onChange(event.target.value)}
      data-cy="analyticsSqlText"
    />
    <p className="small text-muted mt-2 mb-0">
      {copy(
        locale,
        'Un borrador inválido se conserva aquí hasta que lo corrijas; no se ejecuta sin validarlo.',
        'An invalid draft stays here until corrected; it cannot run before validation.',
      )}
    </p>
  </section>
);

const Diagnostics = ({ diagnostics, locale }: { diagnostics: AnalyticsDiagnostic[]; locale: string }) => (
  <div className="alert alert-danger" role="alert" data-cy="analyticsDiagnostics">
    <strong>{copy(locale, 'Revisa la consulta', 'Review the query')}</strong>
    <ul className="mb-0 mt-2">
      {diagnostics.map((diagnostic, index) => (
        <li key={`${diagnostic.code}-${index}`}>
          {diagnostic.message}
          {diagnostic.line
            ? ` (${copy(locale, 'línea', 'line')} ${diagnostic.line}${diagnostic.column ? `, ${copy(locale, 'columna', 'column')} ${diagnostic.column}` : ''})`
            : ''}
        </li>
      ))}
    </ul>
  </div>
);

export default AnalyticsExplorer;
