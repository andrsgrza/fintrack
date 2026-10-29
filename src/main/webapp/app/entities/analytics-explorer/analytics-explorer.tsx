import axios from 'axios';
import React, { useEffect, useRef, useState } from 'react';
import { Button, ButtonGroup, Input, Label } from 'reactstrap';

import { useAppSelector } from 'app/config/store';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import {
  executeSemanticQuery,
  formatSemanticQuery,
  getExplorerAccounts,
  getSemanticAnalyticsCatalog,
  parseSemanticQuery,
} from './analytics-explorer-api';
import { AnalyticsCatalogPanel } from './analytics-catalog-panel';
import { AnalyticsContextControls } from './analytics-context-controls';
import {
  AnalyticsDiagnostic,
  AnalyticsProblem,
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
} from './analytics-explorer-utils';

type Account = { id: number; name: string; accountType?: string; currency?: string };
type Mode = 'VISUAL' | 'SQL';

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

/** One explorer, one AST, and one backend executor regardless of which authoring mode the user chooses. */
const AnalyticsExplorer = () => {
  const locale = useAppSelector(state => state.locale.currentLocale) || 'en';
  const [catalog, setCatalog] = useState<SemanticAnalyticsCatalog | null>(null);
  const [definition, setDefinition] = useState<SemanticQueryDefinition | null>(null);
  const [context, setContext] = useState<VisualizationContext | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [diagnostics, setDiagnostics] = useState<AnalyticsDiagnostic[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [resultLoading, setResultLoading] = useState(false);
  const [result, setResult] = useState<SemanticAnalyticsResult | null>(null);
  const [executedSignature, setExecutedSignature] = useState<string | null>(null);
  const [visualization, setVisualization] = useState<ExplorerVisualization>('TABLE');
  const [mode, setMode] = useState<Mode>('VISUAL');
  const [sqlText, setSqlText] = useState('');
  const [sqlValidated, setSqlValidated] = useState(true);
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
    return () => {
      active = false;
    };
  }, [locale]);

  const normalize = async (candidate: SemanticQueryDefinition) => {
    const current = ++sequence.current;
    setBusy(true);
    setDiagnostics([]);
    try {
      const text = await formatSemanticQuery(candidate);
      const parsed = await parseSemanticQuery(text);
      if (current !== sequence.current) return;
      setDefinition(parsed.definition);
      setSqlText(parsed.canonicalText);
      setSqlValidated(true);
    } catch (error) {
      if (current === sequence.current) setDiagnostics(diagnosticsFrom(error));
    } finally {
      if (current === sequence.current) setBusy(false);
    }
  };

  const chooseDataset = (id: string) => {
    if (!catalog) return;
    const dataset = datasetById(catalog, id);
    if (!dataset) return;
    if (
      definition &&
      !window.confirm(
        copy(
          locale,
          'Cambiar de dataset reemplazará agregaciones, grupos y filtros actuales. ¿Continuar?',
          'Changing datasets will replace current aggregations, groups, and filters. Continue?',
        ),
      )
    )
      return;
    void normalize(initialDefinition(catalog, dataset));
  };

  const applySql = async () => {
    const current = ++sequence.current;
    setBusy(true);
    setDiagnostics([]);
    try {
      const parsed = await parseSemanticQuery(sqlText);
      if (current !== sequence.current) return false;
      setDefinition(parsed.definition);
      setSqlText(parsed.canonicalText);
      setSqlValidated(true);
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
          code: 'INVALID_SQL_DRAFT',
          message: copy(
            locale,
            'Corrige y aplica el texto SQL-like antes de ejecutar.',
            'Correct and apply the SQL-like text before executing.',
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
    void normalize(example);
  };

  const stale = !!result && executedSignature !== querySignature(definition, context);
  return (
    <ProductPage wide className="analytics-explorer">
      <ProductPageHeader
        headingId="analytics-explorer-heading"
        title={copy(locale, 'Explorador analítico', 'Analytics explorer')}
        subtitle={copy(
          locale,
          'Construye una consulta semántica visual o SQL-like. Las vistas previas son temporales.',
          'Build a visual or SQL-like semantic query. Previews are temporary.',
        )}
        actions={
          <>
            <ButtonGroup aria-label={copy(locale, 'Modo de edición', 'Editing mode')}>
              <Button
                type="button"
                size="sm"
                color={mode === 'VISUAL' ? 'primary' : 'secondary'}
                outline={mode !== 'VISUAL'}
                disabled={busy}
                onClick={() => void switchMode('VISUAL')}
                data-cy="analyticsModeVisual"
              >
                {copy(locale, 'Guía visual', 'Visual guide')}
              </Button>
              <Button
                type="button"
                size="sm"
                color={mode === 'SQL' ? 'primary' : 'secondary'}
                outline={mode !== 'SQL'}
                disabled={busy}
                onClick={() => void switchMode('SQL')}
                data-cy="analyticsModeSql"
              >
                {copy(locale, 'SQL-like avanzado', 'Advanced SQL-like')}
              </Button>
            </ButtonGroup>
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
          </>
        }
      />
      {catalogLoading ? (
        <div className="border rounded-2 p-4 text-center">{copy(locale, 'Cargando catálogo semántico…', 'Loading semantic catalog…')}</div>
      ) : null}
      {diagnostics.length ? <Diagnostics diagnostics={diagnostics} locale={locale} /> : null}
      {catalog && context ? (
        <div className="d-flex flex-column gap-4">
          <ProductSection title={copy(locale, 'Alcance de ejecución', 'Execution scope')}>
            <AnalyticsContextControls
              catalog={catalog}
              context={context}
              accounts={accounts}
              locale={locale}
              disabled={busy || resultLoading}
              onChange={setContext}
            />
          </ProductSection>
          <ProductSection title={copy(locale, 'Construir consulta', 'Build query')}>
            {mode === 'VISUAL' ? (
              <div className="d-flex flex-column gap-4">
                {!definition ? (
                  <section className="border rounded-3 p-3 bg-light" data-cy="analyticsExamples">
                    <h2 className="h5 mb-1">
                      {copy(locale, 'Empieza rápido o crea una consulta desde cero', 'Start quickly or create a query from scratch')}
                    </h2>
                    <p className="text-muted mb-3">
                      {copy(
                        locale,
                        'Los ejemplos son atajos editables: no limitan los cálculos que puedes construir.',
                        'Examples are editable shortcuts: they never limit the calculations you can build.',
                      )}
                    </p>
                    <div className="row g-2">
                      {queryExamples(catalog).map(example => (
                        <div className="col-12 col-lg-4" key={example.id}>
                          <Button
                            type="button"
                            color="light"
                            className="border text-start w-100 h-100 p-3"
                            disabled={busy}
                            onClick={() => useExample(example.definition)}
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
                ) : null}
                <AnalyticsCatalogPanel
                  catalog={catalog}
                  definition={definition}
                  locale={locale}
                  disabled={busy}
                  onChooseDataset={chooseDataset}
                />
                <AnalyticsVisualBuilder
                  catalog={catalog}
                  definition={definition}
                  locale={locale}
                  busy={busy}
                  onApply={normalize}
                  onClear={() => {
                    setDefinition(null);
                    setSqlText('');
                    setDiagnostics([]);
                  }}
                />
              </div>
            ) : (
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
            )}
          </ProductSection>
          <ProductSection title={copy(locale, 'Paso 5 · Presentar resultados', 'Step 5 · Present results')}>
            <AnalyticsResultsPanel
              result={result}
              visualization={visualization}
              locale={locale}
              loading={resultLoading}
              stale={stale}
              onVisualizationChange={setVisualization}
            />
          </ProductSection>
        </div>
      ) : null}
    </ProductPage>
  );
};

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
        <h3 className="h6 mb-1">{copy(locale, 'Consulta semántica SQL-like', 'SQL-like semantic query')}</h3>
        <p className="small text-muted mb-0">
          {copy(
            locale,
            'No es SQL de base de datos; el servidor analiza y valida el lenguaje limitado.',
            'This is not database SQL; the server parses and validates this constrained language.',
          )}
        </p>
      </div>
      <Button type="button" color="primary" size="sm" disabled={busy || !text.trim()} onClick={onApply} data-cy="analyticsApplySql">
        {copy(locale, 'Validar y aplicar', 'Validate and apply')}
      </Button>
    </div>
    <Label className="visually-hidden" for="analytics-sql-text">
      SQL-like
    </Label>
    <Input
      id="analytics-sql-text"
      type="textarea"
      rows="12"
      value={text}
      spellCheck={false}
      placeholder="SELECT COUNT(TRANSACTION_ID) AS transactions FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE GROUP BY ACCOUNT_INSTITUTION LIMIT 100"
      onChange={event => onChange(event.target.value)}
      data-cy="analyticsSqlText"
    />
    <p className="small text-muted mt-2 mb-0">
      {copy(
        locale,
        'Puedes editar un borrador inválido; no se ejecuta hasta validarlo y presionar Ejecutar consulta.',
        'You may edit an invalid draft; it does not execute until validated and Run query is pressed.',
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
