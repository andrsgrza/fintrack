import React, { useEffect, useMemo, useState } from 'react';
import { Button, ButtonGroup, Input, Label } from 'reactstrap';

import {
  AnalyticsReference,
  ExplorerVisualization,
  SemanticAnalyticsCatalog,
  SemanticPredicate,
  SemanticPredicateOperator,
  SemanticQueryDefinition,
} from './analytics-explorer.model';
import { visualizationEligibility } from './analytics-results-panel';
import { copy, datasetById, describeField, label, predicateValueOptions, suggestedAlias } from './analytics-explorer-utils';

type Props = {
  catalog: SemanticAnalyticsCatalog;
  definition: SemanticQueryDefinition | null;
  locale: string;
  busy: boolean;
  categories: AnalyticsReference[];
  tags: AnalyticsReference[];
  visualization: ExplorerVisualization;
  seriesLimit: number | 'ALL';
  onVisualizationChange: (visualization: ExplorerVisualization) => void;
  onSeriesLimitChange: (limit: number | 'ALL') => void;
  onApply: (candidate: SemanticQueryDefinition) => void;
  onChooseInformation: (datasetId: string, economicFlow?: 'IN' | 'OUT') => void;
  onClear: () => void;
};

type ConditionScope = string;
type InformationChoice = {
  id: string;
  dataset: string;
  economicFlow?: 'IN' | 'OUT';
  title: { es: string; en: string };
  description: { es: string; en: string };
};

type EconomicCalculation = {
  id: 'TOTAL' | 'COUNT' | 'AVERAGE';
  function: 'COUNT' | 'SUM' | 'AVG';
  field: string;
  alias: { expense: string; income: string };
  title: { expense: { es: string; en: string }; income: { es: string; en: string } };
};

const informationChoices: InformationChoice[] = [
  {
    id: 'ECONOMIC_EXPENSE',
    dataset: 'TRANSACTION_LEDGER',
    economicFlow: 'OUT',
    title: { es: 'Gastos económicos', en: 'Economic expenses' },
    description: {
      es: 'Salidas económicas; excluye transferencias internas vinculadas.',
      en: 'Economic outflows; excludes linked internal transfers.',
    },
  },
  {
    id: 'ECONOMIC_INCOME',
    dataset: 'TRANSACTION_LEDGER',
    economicFlow: 'IN',
    title: { es: 'Ingresos económicos', en: 'Economic income' },
    description: {
      es: 'Entradas económicas; excluye transferencias internas vinculadas.',
      en: 'Economic inflows; excludes linked internal transfers.',
    },
  },
  {
    id: 'TRANSACTION_LEDGER',
    dataset: 'TRANSACTION_LEDGER',
    title: { es: 'Movimientos financieros', en: 'Financial transactions' },
    description: { es: 'Actividad registrada en tus cuentas.', en: 'Recorded activity in your accounts.' },
  },
  {
    id: 'ACCOUNT_POSITION_SNAPSHOT',
    dataset: 'ACCOUNT_POSITION_SNAPSHOT',
    title: { es: 'Posiciones y saldo histórico', en: 'Historical balances and positions' },
    description: { es: 'Saldos contables observados por cuenta.', en: 'Book balances observed by account.' },
  },
  {
    id: 'CREDIT_ACCOUNT_SNAPSHOT',
    dataset: 'CREDIT_ACCOUNT_SNAPSHOT',
    title: { es: 'Crédito', en: 'Credit' },
    description: { es: 'Deuda y disponibilidad de tarjetas.', en: 'Card debt and availability.' },
  },
  {
    id: 'INTERNAL_TRANSFER_EVENT',
    dataset: 'INTERNAL_TRANSFER_EVENT',
    title: { es: 'Transferencias internas', en: 'Internal transfers' },
    description: { es: 'Cada transferencia se cuenta una sola vez.', en: 'Each transfer is counted exactly once.' },
  },
];

const economicCalculations: EconomicCalculation[] = [
  {
    id: 'TOTAL',
    function: 'SUM',
    field: 'TRANSACTION_AMOUNT',
    alias: { expense: 'gastos', income: 'ingresos' },
    title: { expense: { es: 'Total gastado', en: 'Total spent' }, income: { es: 'Total ingresado', en: 'Total received' } },
  },
  {
    id: 'COUNT',
    function: 'COUNT',
    field: 'TRANSACTION_ID',
    alias: { expense: 'numero_gastos', income: 'numero_ingresos' },
    title: {
      expense: { es: 'Número de gastos', en: 'Number of expenses' },
      income: { es: 'Número de ingresos', en: 'Number of income entries' },
    },
  },
  {
    id: 'AVERAGE',
    function: 'AVG',
    field: 'TRANSACTION_AMOUNT',
    alias: { expense: 'gasto_promedio', income: 'ingreso_promedio' },
    title: { expense: { es: 'Gasto promedio', en: 'Average expense' }, income: { es: 'Ingreso promedio', en: 'Average income' } },
  },
];

const clone = (definition: SemanticQueryDefinition): SemanticQueryDefinition =>
  JSON.parse(JSON.stringify(definition)) as SemanticQueryDefinition;
const noValueOperator = (operator: SemanticPredicateOperator) => operator === 'IS_UNCATEGORIZED' || operator === 'HAS_NONE';
const valueIsSingle = (operator: SemanticPredicateOperator) => operator === 'EQUALS' || operator === 'DESCENDANT_OF';
const optionText = (locale: string, choice: InformationChoice) => (locale.startsWith('es') ? choice.title.es : choice.title.en);
const optionDescription = (locale: string, choice: InformationChoice) =>
  locale.startsWith('es') ? choice.description.es : choice.description.en;
const conditionsFor = (definition: SemanticQueryDefinition, scope: ConditionScope): SemanticPredicate[] =>
  scope === 'GLOBAL' ? definition.predicates : (definition.projections.find(projection => projection.alias === scope)?.conditions ?? []);
const hasPredicate = (definition: SemanticQueryDefinition, field: string, value: string) =>
  definition.predicates.some(predicate => predicate.field === field && predicate.values.includes(value));
const economicFlow = (predicates: SemanticPredicate[]): 'OUT' | 'IN' | null => {
  const flow = predicates.find(predicate => predicate.field === 'TRANSACTION_FLOW' && predicate.operator === 'EQUALS')?.values[0];
  const unlinked = predicates.some(
    predicate => predicate.field === 'TRANSFER_LINK_STATUS' && predicate.operator === 'EQUALS' && predicate.values.includes('UNLINKED'),
  );
  return unlinked && (flow === 'OUT' || flow === 'IN') ? flow : null;
};
const economicFlowForProjection = (definition: SemanticQueryDefinition, projection: SemanticQueryDefinition['projections'][number]) =>
  economicFlow(definition.predicates) ?? economicFlow(projection.conditions ?? []);
const economicCalculationFor = (projection: SemanticQueryDefinition['projections'][number]) =>
  economicCalculations.find(item => item.function === projection.function && item.field === projection.field);
const economicCalculationText = (locale: string, flow: 'OUT' | 'IN', calculation: EconomicCalculation) => {
  const text = calculation.title[flow === 'OUT' ? 'expense' : 'income'];
  return locale.startsWith('es') ? text.es : text.en;
};
const uniqueAlias = (definition: SemanticQueryDefinition, preferred: string) => {
  const aliases = new Set(definition.projections.map(projection => projection.alias));
  if (!aliases.has(preferred)) return preferred;
  let index = 2;
  while (aliases.has(`${preferred}_${index}`)) index += 1;
  return `${preferred}_${index}`;
};
const currentInformation = (definition: SemanticQueryDefinition | null) => {
  if (!definition) return '';
  if (definition.dataset !== 'TRANSACTION_LEDGER') return definition.dataset;
  if (hasPredicate(definition, 'TRANSACTION_FLOW', 'OUT') && hasPredicate(definition, 'TRANSFER_LINK_STATUS', 'UNLINKED'))
    return 'ECONOMIC_EXPENSE';
  if (hasPredicate(definition, 'TRANSACTION_FLOW', 'IN') && hasPredicate(definition, 'TRANSFER_LINK_STATUS', 'UNLINKED'))
    return 'ECONOMIC_INCOME';
  return 'TRANSACTION_LEDGER';
};
const projectionName = (
  locale: string,
  definition: SemanticQueryDefinition,
  projection: SemanticQueryDefinition['projections'][number],
) => {
  const flow = economicFlowForProjection(definition, projection);
  const economicCalculation = economicCalculationFor(projection);
  if (flow && economicCalculation) return economicCalculationText(locale, flow, economicCalculation);
  return projection.alias === suggestedAlias(projection.function, projection.field)
    ? `${label(locale, projection.function)} ${label(locale, projection.field).toLocaleLowerCase()}`
    : projection.alias;
};

const StepHeading = ({ number, title, help, children }: { number: number; title: string; help: string; children?: React.ReactNode }) => (
  <header className="d-flex justify-content-between align-items-start gap-3 p-3 border-bottom bg-light">
    <div className="d-flex gap-2 align-items-start">
      <span
        className="rounded-circle bg-success text-white fw-semibold text-center flex-shrink-0"
        style={{ width: '1.65rem', lineHeight: '1.65rem' }}
      >
        {number}
      </span>
      <div>
        <h2 className="h6 mb-1">{title}</h2>
        <p className="small text-muted mb-0">{help}</p>
      </div>
    </div>
    {children}
  </header>
);

const InformationSelector = ({
  definition,
  locale,
  busy,
  onChoose,
}: {
  definition: SemanticQueryDefinition | null;
  locale: string;
  busy: boolean;
  onChoose: (choice: InformationChoice) => void;
}) => {
  const available = informationChoices;
  const selected = currentInformation(definition);
  return (
    <div>
      <Label className="small text-uppercase fw-semibold text-muted mb-1" for="analytics-information-source">
        {copy(locale, 'Información', 'Information')}
      </Label>
      <Input
        id="analytics-information-source"
        type="select"
        value={selected}
        disabled={busy}
        data-cy="analyticsInformationSource"
        onChange={event => {
          const choice = available.find(candidate => candidate.id === event.target.value);
          if (choice) onChoose(choice);
        }}
      >
        {!definition ? <option value="">{copy(locale, 'Elige qué información analizar', 'Choose information to analyze')}</option> : null}
        {available.map(choice => (
          <option key={choice.id} value={choice.id}>
            {optionText(locale, choice)}
          </option>
        ))}
      </Input>
      {selected ? (
        <p className="small text-muted mt-1 mb-0">
          {optionDescription(locale, available.find(choice => choice.id === selected) ?? available[0])}
        </p>
      ) : null}
    </div>
  );
};

const ProjectionRow = ({
  catalog,
  definition,
  projectionIndex,
  locale,
  busy,
  onApply,
}: {
  catalog: SemanticAnalyticsCatalog;
  definition: SemanticQueryDefinition;
  projectionIndex: number;
  locale: string;
  busy: boolean;
  onApply: (mutate: (candidate: SemanticQueryDefinition) => void) => void;
}) => {
  const dataset = datasetById(catalog, definition.dataset);
  const projection = definition.projections[projectionIndex];
  if (!dataset || !projection) return null;
  const isEconomicShortcut = Boolean(economicFlowForProjection(definition, projection) && economicCalculationFor(projection));
  const fields = [...new Set(dataset.aggregateCapabilities.map(capability => capability.field))];
  const functions = dataset.aggregateCapabilities
    .filter(capability => capability.field === projection.field)
    .map(capability => capability.function);
  const changeField = (field: string) => {
    const nextFunction = dataset.aggregateCapabilities.find(capability => capability.field === field)?.function;
    if (!nextFunction) return;
    onApply(candidate => {
      const current = candidate.projections[projectionIndex];
      const rename = current.alias === suggestedAlias(current.function, current.field);
      candidate.projections[projectionIndex] = {
        ...current,
        field,
        function: nextFunction,
        alias: rename ? suggestedAlias(nextFunction, field) : current.alias,
      };
    });
  };
  const changeFunction = (functionName: 'COUNT' | 'SUM' | 'AVG') =>
    onApply(candidate => {
      const current = candidate.projections[projectionIndex];
      const rename = current.alias === suggestedAlias(current.function, current.field);
      candidate.projections[projectionIndex] = {
        ...current,
        function: functionName,
        alias: rename ? suggestedAlias(functionName, current.field) : current.alias,
      };
    });
  return (
    <div className="border rounded-2 p-2 bg-light" data-cy={`analyticsProjection-${projection.alias}`}>
      {isEconomicShortcut ? <div className="small fw-semibold mb-2">{projectionName(locale, definition, projection)}</div> : null}
      <div className="row g-2 align-items-end">
        <div className="col-12 col-md-5">
          <Label className="small text-uppercase fw-semibold text-muted mb-1" for={`analytics-projection-field-${projectionIndex}`}>
            {copy(locale, 'Información', 'Information')}
          </Label>
          <Input
            id={`analytics-projection-field-${projectionIndex}`}
            type="select"
            value={projection.field}
            disabled={busy}
            onChange={event => changeField(event.target.value)}
          >
            {fields.map(field => (
              <option key={field} value={field}>
                {label(locale, field)}
              </option>
            ))}
          </Input>
        </div>
        <div className="col-12 col-md-3">
          <Label className="small text-uppercase fw-semibold text-muted mb-1" for={`analytics-projection-function-${projectionIndex}`}>
            {copy(locale, 'Cálculo', 'Calculation')}
          </Label>
          <Input
            id={`analytics-projection-function-${projectionIndex}`}
            type="select"
            value={projection.function}
            disabled={busy}
            onChange={event => changeFunction(event.target.value as 'COUNT' | 'SUM' | 'AVG')}
          >
            {functions.map(functionName => (
              <option key={functionName} value={functionName}>
                {label(locale, functionName)}
              </option>
            ))}
          </Input>
        </div>
        <div className="col-12 col-md-3">
          <Label className="small text-uppercase fw-semibold text-muted mb-1" for={`analytics-projection-alias-${projectionIndex}`}>
            {copy(locale, 'Nombre en resultados', 'Result name')}
          </Label>
          <Input
            id={`analytics-projection-alias-${projectionIndex}`}
            value={projection.alias}
            disabled={busy}
            onChange={event =>
              onApply(candidate => {
                candidate.projections[projectionIndex].alias = event.target.value;
              })
            }
          />
        </div>
        <div className="col-12 col-md-1">
          <Button
            type="button"
            color="secondary"
            outline
            size="sm"
            className="w-100"
            disabled={busy || definition.projections.length === 1}
            onClick={() => onApply(candidate => candidate.projections.splice(projectionIndex, 1))}
            aria-label={copy(
              locale,
              `Quitar ${projectionName(locale, definition, projection)}`,
              `Remove ${projectionName(locale, definition, projection)}`,
            )}
          >
            {copy(locale, 'Quitar', 'Remove')}
          </Button>
        </div>
      </div>
    </div>
  );
};

const ProjectionSummaryRow = ({
  definition,
  projectionIndex,
  locale,
  busy,
  onRemove,
}: {
  definition: SemanticQueryDefinition;
  projectionIndex: number;
  locale: string;
  busy: boolean;
  onRemove: () => void;
}) => {
  const projection = definition.projections[projectionIndex];
  if (!projection) return null;
  return (
    <div
      className="d-flex align-items-center justify-content-between gap-2 border rounded-2 px-3 py-2 bg-light"
      data-cy={`analyticsProjection-${projection.alias}`}
    >
      <div>
        <div className="fw-semibold">{projectionName(locale, definition, projection)}</div>
        <div className="small text-muted">{copy(locale, 'Cálculo configurado', 'Configured calculation')}</div>
      </div>
      <Button
        type="button"
        color="secondary"
        outline
        size="sm"
        disabled={busy || definition.projections.length === 1}
        onClick={onRemove}
        aria-label={copy(
          locale,
          `Quitar ${projectionName(locale, definition, projection)}`,
          `Remove ${projectionName(locale, definition, projection)}`,
        )}
      >
        {copy(locale, 'Quitar', 'Remove')}
      </Button>
    </div>
  );
};

const EconomicShortcutPicker = ({
  flow,
  selected,
  locale,
  busy,
  onChoose,
  onCustomize,
}: {
  flow: 'OUT' | 'IN';
  selected: EconomicCalculation | undefined;
  locale: string;
  busy: boolean;
  onChoose: (calculation: EconomicCalculation) => void;
  onCustomize: () => void;
}) => (
  <div className="border rounded-2 p-2 bg-light" data-cy="analyticsEconomicCalculationShortcuts">
    <div className="small fw-semibold mb-2">{copy(locale, 'Elige un cálculo', 'Choose a calculation')}</div>
    <div className="d-flex flex-wrap align-items-center gap-2">
      <ButtonGroup size="sm" aria-label={copy(locale, 'Cálculos económicos', 'Economic calculations')}>
        {economicCalculations.map(calculation => (
          <Button
            key={calculation.id}
            type="button"
            color={selected?.id === calculation.id ? 'primary' : 'secondary'}
            outline={selected?.id !== calculation.id}
            disabled={busy}
            onClick={() => onChoose(calculation)}
          >
            {economicCalculationText(locale, flow, calculation)}
          </Button>
        ))}
      </ButtonGroup>
      <Button type="button" color="link" size="sm" disabled={busy} onClick={onCustomize}>
        {copy(locale, 'Personalizar cálculo…', 'Customize calculation…')}
      </Button>
    </div>
  </div>
);

const CalculationSection = ({
  catalog,
  definition,
  locale,
  busy,
  onApply,
  onChooseInformation,
  onClear,
}: {
  catalog: SemanticAnalyticsCatalog;
  definition: SemanticQueryDefinition;
  locale: string;
  busy: boolean;
  onApply: (mutate: (candidate: SemanticQueryDefinition) => void) => void;
  onChooseInformation: (choice: InformationChoice) => void;
  onClear: () => void;
}) => {
  const [adding, setAdding] = useState(false);
  const [advanced, setAdvanced] = useState(false);
  const [search, setSearch] = useState('');
  const dataset = datasetById(catalog, definition.dataset);
  const information = currentInformation(definition);
  const economic = information === 'ECONOMIC_EXPENSE' ? 'OUT' : information === 'ECONOMIC_INCOME' ? 'IN' : null;
  useEffect(() => {
    setAdvanced(false);
    setAdding(false);
  }, [information]);
  if (!dataset) return null;
  const choices = dataset.aggregateCapabilities.filter(capability => {
    const text =
      `${label(locale, capability.function)} ${label(locale, capability.field)} ${describeField(locale, capability.field)}`.toLocaleLowerCase();
    return text.includes(search.toLocaleLowerCase());
  });
  const add = (functionName: 'COUNT' | 'SUM' | 'AVG', field: string) => {
    onApply(candidate => {
      const base = suggestedAlias(functionName, field);
      candidate.projections.push({ alias: uniqueAlias(candidate, base), function: functionName, field, conditions: [] });
    });
    setAdding(false);
    setSearch('');
  };
  const chooseEconomicCalculation = (calculation: EconomicCalculation) => {
    if (!economic) return;
    onApply(candidate => {
      const current = candidate.projections[0];
      candidate.projections[0] = {
        alias: calculation.alias[economic === 'OUT' ? 'expense' : 'income'],
        function: calculation.function,
        field: calculation.field,
        conditions: current.conditions ?? [],
      };
    });
  };
  const addEconomicCalculation = (calculation: EconomicCalculation) => {
    if (!economic) return;
    onApply(candidate => {
      const preferred = calculation.alias[economic === 'OUT' ? 'expense' : 'income'];
      candidate.projections.push({
        alias: uniqueAlias(candidate, preferred),
        function: calculation.function,
        field: calculation.field,
        conditions: [],
      });
    });
    setAdding(false);
  };
  const remove = (projectionIndex: number) => onApply(candidate => candidate.projections.splice(projectionIndex, 1));
  return (
    <section className="border-bottom" data-cy="analyticsCalculationSection">
      <StepHeading
        number={1}
        title={copy(locale, '¿Qué quieres calcular?', 'What do you want to calculate?')}
        help={copy(
          locale,
          'Añade uno o varios cálculos que se puedan leer juntos.',
          'Add one or more calculations that can be read together.',
        )}
      >
        <Button type="button" color="link" size="sm" disabled={busy} onClick={onClear}>
          {copy(locale, 'Nueva consulta', 'New query')}
        </Button>
      </StepHeading>
      <div className="p-3">
        <div className="row g-2 mb-3">
          <div className="col-12 col-lg-7">
            <InformationSelector definition={definition} locale={locale} busy={busy} onChoose={onChooseInformation} />
          </div>
        </div>
        {economic && !advanced ? (
          <div className="d-flex flex-column gap-2">
            <EconomicShortcutPicker
              flow={economic}
              selected={economicCalculationFor(definition.projections[0])}
              locale={locale}
              busy={busy}
              onChoose={chooseEconomicCalculation}
              onCustomize={() => setAdvanced(true)}
            />
            {definition.projections.map((_, index) => (
              <ProjectionSummaryRow
                key={`${definition.projections[index].alias}-${index}`}
                definition={definition}
                projectionIndex={index}
                locale={locale}
                busy={busy}
                onRemove={() => remove(index)}
              />
            ))}
          </div>
        ) : (
          <div className="d-flex flex-column gap-2">
            {economic ? (
              <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
                <p className="small text-muted mb-0">{copy(locale, 'Editor avanzado de cálculos', 'Advanced calculation editor')}</p>
                <Button type="button" color="link" size="sm" disabled={busy} onClick={() => setAdvanced(false)}>
                  {copy(locale, 'Usar opciones simples', 'Use simple options')}
                </Button>
              </div>
            ) : null}
            {definition.projections.map((_, index) => (
              <ProjectionRow
                key={`${definition.projections[index].alias}-${index}`}
                catalog={catalog}
                definition={definition}
                projectionIndex={index}
                locale={locale}
                busy={busy}
                onApply={onApply}
              />
            ))}
          </div>
        )}
        {economic ? (
          <p className="small text-muted mt-2 mb-0">
            {copy(
              locale,
              'Los gastos e ingresos económicos excluyen las piernas vinculadas a transferencias internas.',
              'Economic income and expenses exclude linked internal-transfer legs.',
            )}
          </p>
        ) : null}
        {definition.projections.length < 4 ? (
          <div className="mt-2">
            {!adding ? (
              <Button type="button" color="secondary" outline size="sm" disabled={busy} onClick={() => setAdding(true)}>
                {copy(locale, '+ Añadir cálculo', '+ Add calculation')}
              </Button>
            ) : economic && !advanced ? (
              <div className="border rounded-2 p-2 bg-white" data-cy="analyticsCalculationPicker">
                <div className="d-flex justify-content-between gap-2 align-items-center mb-2">
                  <span className="small fw-semibold">{copy(locale, 'Añadir cálculo', 'Add calculation')}</span>
                  <Button type="button" color="link" size="sm" disabled={busy} onClick={() => setAdding(false)}>
                    {copy(locale, 'Cerrar', 'Close')}
                  </Button>
                </div>
                <div className="d-flex flex-wrap align-items-center gap-2">
                  {economicCalculations.map(calculation => (
                    <Button
                      key={calculation.id}
                      type="button"
                      size="sm"
                      color="secondary"
                      outline
                      disabled={busy}
                      onClick={() => addEconomicCalculation(calculation)}
                    >
                      {economicCalculationText(locale, economic, calculation)}
                    </Button>
                  ))}
                  <Button
                    type="button"
                    color="link"
                    size="sm"
                    disabled={busy}
                    onClick={() => {
                      setAdding(false);
                      setAdvanced(true);
                    }}
                  >
                    {copy(locale, 'Personalizar cálculo…', 'Customize calculation…')}
                  </Button>
                </div>
              </div>
            ) : (
              <div className="border rounded-2 p-2 bg-white" data-cy="analyticsCalculationPicker">
                <div className="d-flex justify-content-between gap-2 align-items-center mb-2">
                  <Label className="small fw-semibold mb-0" for="analytics-calculation-search">
                    {copy(locale, 'Añadir cálculo', 'Add calculation')}
                  </Label>
                  <Button type="button" color="link" size="sm" disabled={busy} onClick={() => setAdding(false)}>
                    {copy(locale, 'Cerrar', 'Close')}
                  </Button>
                </div>
                <Input
                  id="analytics-calculation-search"
                  type="search"
                  bsSize="sm"
                  value={search}
                  disabled={busy}
                  onChange={event => setSearch(event.target.value)}
                  placeholder={copy(locale, 'Buscar cálculo', 'Search calculation')}
                />
                <div className="d-flex flex-wrap gap-2 mt-2">
                  {choices.map(capability => (
                    <Button
                      key={`${capability.function}:${capability.field}`}
                      type="button"
                      size="sm"
                      color="secondary"
                      outline
                      disabled={busy}
                      onClick={() => add(capability.function, capability.field)}
                    >
                      {label(locale, capability.function)} · {label(locale, capability.field)}
                    </Button>
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : null}
      </div>
    </section>
  );
};

const ReferenceValues = ({
  references,
  values,
  operator,
  locale,
  disabled,
  onChange,
}: {
  references: AnalyticsReference[];
  values: string[];
  operator: SemanticPredicateOperator;
  locale: string;
  disabled: boolean;
  onChange: (values: string[]) => void;
}) => {
  const [search, setSearch] = useState('');
  const visible = references.filter(reference =>
    `${reference.name} ${reference.description ?? ''}`.toLocaleLowerCase().includes(search.toLocaleLowerCase()),
  );
  const select = (value: string) => {
    if (valueIsSingle(operator)) return onChange(values.includes(value) ? [] : [value]);
    return onChange(values.includes(value) ? values.filter(candidate => candidate !== value) : [...values, value]);
  };
  return (
    <div>
      <Input
        type="search"
        bsSize="sm"
        value={search}
        disabled={disabled}
        onChange={event => setSearch(event.target.value)}
        placeholder={copy(locale, 'Buscar…', 'Search…')}
      />
      <div className="d-flex flex-wrap gap-1 mt-2" style={{ maxHeight: '9rem', overflowY: 'auto' }}>
        {visible.map(reference => {
          const selected = values.includes(String(reference.id));
          return (
            <Button
              key={reference.id}
              type="button"
              size="sm"
              color={selected ? 'primary' : 'secondary'}
              outline={!selected}
              disabled={disabled}
              onClick={() => select(String(reference.id))}
            >
              {reference.color ? (
                <span aria-hidden="true" className="me-1" style={{ color: reference.color }}>
                  ●
                </span>
              ) : null}
              {reference.name}
              {reference.path && reference.path.length > 1 ? ` · ${reference.path.join(' › ')}` : ''}
            </Button>
          );
        })}
      </div>
    </div>
  );
};

const ConditionEditor = ({
  definition,
  catalog,
  locale,
  busy,
  categories,
  tags,
  onAdd,
}: {
  definition: SemanticQueryDefinition;
  catalog: SemanticAnalyticsCatalog;
  locale: string;
  busy: boolean;
  categories: AnalyticsReference[];
  tags: AnalyticsReference[];
  onAdd: (scope: ConditionScope, predicate: SemanticPredicate) => void;
}) => {
  const [search, setSearch] = useState('');
  const [field, setField] = useState('');
  const [operator, setOperator] = useState<SemanticPredicateOperator>('EQUALS');
  const [values, setValues] = useState<string[]>([]);
  const [scope, setScope] = useState<ConditionScope>('GLOBAL');
  const dataset = datasetById(catalog, definition.dataset);
  if (!dataset) return null;
  const filtered = dataset.predicateCapabilities.filter(capability =>
    `${label(locale, capability.field)} ${describeField(locale, capability.field)}`
      .toLocaleLowerCase()
      .includes(search.toLocaleLowerCase()),
  );
  const capability = dataset.predicateCapabilities.find(item => item.field === field);
  const references = field === 'CATEGORY_ID' ? categories : field === 'TAG_ID' ? tags : [];
  const fixedValues = predicateValueOptions(locale, field);
  const chooseField = (next: string) => {
    const nextCapability = dataset.predicateCapabilities.find(item => item.field === next);
    setField(next);
    setOperator(nextCapability?.operators[0] ?? 'EQUALS');
    setValues([]);
  };
  const add = () => {
    if (!field || (!noValueOperator(operator) && !values.length)) return;
    onAdd(scope, { field, operator, values });
    setField('');
    setValues([]);
    setSearch('');
  };
  return (
    <div className="border rounded-2 p-2 bg-light mt-2" data-cy="analyticsConditionEditor">
      {!field ? (
        <div className="row g-2 align-items-end">
          <div className="col-12 col-md-5">
            <Label className="small text-uppercase fw-semibold text-muted mb-1" for="analytics-condition-search">
              {copy(locale, 'Campo', 'Field')}
            </Label>
            <Input
              id="analytics-condition-search"
              type="search"
              bsSize="sm"
              value={search}
              disabled={busy}
              onChange={event => setSearch(event.target.value)}
              placeholder={copy(locale, 'Buscar campo', 'Search field')}
            />
          </div>
          <div className="col-12 col-md-7">
            <Label className="visually-hidden" for="analytics-condition-field">
              {copy(locale, 'Campo disponible', 'Available field')}
            </Label>
            <Input
              id="analytics-condition-field"
              type="select"
              bsSize="sm"
              value=""
              disabled={busy}
              onChange={event => chooseField(event.target.value)}
            >
              <option value="">{copy(locale, 'Elige un campo', 'Choose a field')}</option>
              {filtered.map(item => (
                <option key={item.field} value={item.field}>
                  {label(locale, item.field)}
                </option>
              ))}
            </Input>
          </div>
        </div>
      ) : (
        <div className="row g-2 align-items-end">
          <div className="col-12 col-md-3">
            <Label className="small text-uppercase fw-semibold text-muted mb-1" for="analytics-condition-scope">
              {copy(locale, 'Aplicar a', 'Apply to')}
            </Label>
            <Input
              id="analytics-condition-scope"
              type="select"
              bsSize="sm"
              value={scope}
              disabled={busy}
              onChange={event => setScope(event.target.value)}
            >
              <option value="GLOBAL">{copy(locale, 'Toda la consulta', 'Whole query')}</option>
              {definition.projections.map(projection => (
                <option key={projection.alias} value={projection.alias}>
                  {projectionName(locale, definition, projection)}
                </option>
              ))}
            </Input>
          </div>
          <div className="col-12 col-md-3">
            <Label className="small text-uppercase fw-semibold text-muted mb-1" for="analytics-condition-operator">
              {copy(locale, 'Operador', 'Operator')}
            </Label>
            <Input
              id="analytics-condition-operator"
              type="select"
              bsSize="sm"
              value={operator}
              disabled={busy}
              onChange={event => {
                const next = event.target.value as SemanticPredicateOperator;
                setOperator(next);
                setValues(noValueOperator(next) ? [] : valueIsSingle(next) ? values.slice(0, 1) : values);
              }}
            >
              {capability?.operators.map(item => (
                <option key={item} value={item}>
                  {label(locale, item)}
                </option>
              ))}
            </Input>
          </div>
          <div className="col-12 col-md-5">
            <Label className="small text-uppercase fw-semibold text-muted mb-1">{copy(locale, 'Valor', 'Value')}</Label>
            {noValueOperator(operator) ? (
              <p className="small text-muted mb-1">{copy(locale, 'No necesita valor.', 'No value is needed.')}</p>
            ) : null}
            {!noValueOperator(operator) && references.length ? (
              <ReferenceValues
                references={references}
                values={values}
                operator={operator}
                locale={locale}
                disabled={busy}
                onChange={setValues}
              />
            ) : null}
            {!noValueOperator(operator) && !references.length && fixedValues.length ? (
              <div className="d-flex flex-wrap gap-1">
                {fixedValues.map(item => {
                  const selected = values.includes(item.value);
                  return (
                    <Button
                      key={item.value}
                      type="button"
                      size="sm"
                      color={selected ? 'primary' : 'secondary'}
                      outline={!selected}
                      disabled={busy}
                      onClick={() =>
                        setValues(
                          valueIsSingle(operator)
                            ? selected
                              ? []
                              : [item.value]
                            : selected
                              ? values.filter(value => value !== item.value)
                              : [...values, item.value],
                        )
                      }
                    >
                      {item.label}
                    </Button>
                  );
                })}
              </div>
            ) : null}
            {!noValueOperator(operator) && !references.length && !fixedValues.length ? (
              <Input
                bsSize="sm"
                value={values.join(', ')}
                disabled={busy}
                onChange={event =>
                  setValues(
                    event.target.value
                      .split(',')
                      .map(value => value.trim())
                      .filter(Boolean),
                  )
                }
                placeholder={copy(locale, 'Escribe un valor', 'Enter a value')}
              />
            ) : null}
          </div>
          <div className="col-12 col-md-1 d-flex gap-1">
            <Button type="button" color="primary" size="sm" disabled={busy || (!noValueOperator(operator) && !values.length)} onClick={add}>
              {copy(locale, 'Añadir', 'Add')}
            </Button>
            <Button type="button" color="secondary" outline size="sm" disabled={busy} onClick={() => setField('')}>
              {copy(locale, 'Cambiar', 'Change')}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
};

const predicateSummary = (locale: string, predicate: SemanticPredicate, references: AnalyticsReference[]) => {
  const names = new Map(references.map(reference => [String(reference.id), reference.name]));
  const values = noValueOperator(predicate.operator)
    ? ''
    : predicate.values.map(value => names.get(value) ?? label(locale, value)).join(', ');
  return `${label(locale, predicate.field)} · ${label(locale, predicate.operator)}${values ? ` · ${values}` : ''}`;
};

const ConditionsSection = ({
  catalog,
  definition,
  locale,
  busy,
  categories,
  tags,
  onApply,
}: {
  catalog: SemanticAnalyticsCatalog;
  definition: SemanticQueryDefinition;
  locale: string;
  busy: boolean;
  categories: AnalyticsReference[];
  tags: AnalyticsReference[];
  onApply: (mutate: (candidate: SemanticQueryDefinition) => void) => void;
}) => {
  const [open, setOpen] = useState(false);
  const [adding, setAdding] = useState(false);
  const allConditions =
    definition.predicates.length + definition.projections.reduce((total, projection) => total + (projection.conditions?.length ?? 0), 0);
  const references = [...categories, ...tags];
  const remove = (scope: ConditionScope, index: number) =>
    onApply(candidate => {
      if (scope === 'GLOBAL') candidate.predicates.splice(index, 1);
      else {
        const projection = candidate.projections.find(item => item.alias === scope);
        if (projection) projection.conditions = (projection.conditions ?? []).filter((_, candidateIndex) => candidateIndex !== index);
      }
    });
  const add = (scope: ConditionScope, predicate: SemanticPredicate) =>
    onApply(candidate => {
      if (scope === 'GLOBAL') candidate.predicates.push(predicate);
      else {
        const projection = candidate.projections.find(item => item.alias === scope);
        if (projection) projection.conditions = [...(projection.conditions ?? []), predicate];
      }
    });
  return (
    <section className="border-bottom" data-cy="analyticsConditionsSection">
      <StepHeading
        number={2}
        title={copy(locale, 'Condiciones', 'Conditions')}
        help={copy(locale, 'Limita los movimientos que quieres incluir.', 'Limit the movements you want to include.')}
      >
        <Button
          type="button"
          color="link"
          size="sm"
          disabled={busy}
          aria-expanded={open}
          onClick={() => setOpen(value => !value)}
          data-cy="analyticsConditionsToggle"
        >
          {open
            ? copy(locale, 'Ocultar condiciones', 'Hide conditions')
            : allConditions
              ? copy(locale, `${allConditions} activas`, `${allConditions} active`)
              : copy(locale, 'Mostrar condiciones', 'Show conditions')}
        </Button>
      </StepHeading>
      {open ? (
        <div className="p-3">
          {['GLOBAL', ...definition.projections.map(projection => projection.alias)].map(scope => {
            const predicates = conditionsFor(definition, scope);
            if (!predicates.length) return null;
            const scopedProjection = definition.projections.find(item => item.alias === scope);
            const scopeLabel =
              scope === 'GLOBAL'
                ? copy(locale, 'Toda la consulta', 'Whole query')
                : scopedProjection
                  ? projectionName(locale, definition, scopedProjection)
                  : scope;
            return (
              <div className="mb-2" key={scope}>
                <p className="small fw-semibold mb-1">{scopeLabel}</p>
                {predicates.map((predicate, index) => (
                  <div
                    className="border rounded-2 bg-light p-2 d-flex justify-content-between gap-2 align-items-center mb-1"
                    key={`${scope}-${predicate.field}-${index}`}
                  >
                    <span className="small">{predicateSummary(locale, predicate, references)}</span>
                    <Button type="button" color="link" size="sm" disabled={busy} onClick={() => remove(scope, index)}>
                      {copy(locale, 'Quitar', 'Remove')}
                    </Button>
                  </div>
                ))}
              </div>
            );
          })}
          {adding ? (
            <ConditionEditor
              definition={definition}
              catalog={catalog}
              locale={locale}
              busy={busy}
              categories={categories}
              tags={tags}
              onAdd={(scope, predicate) => {
                add(scope, predicate);
                setAdding(false);
              }}
            />
          ) : (
            <Button type="button" color="secondary" outline size="sm" disabled={busy} onClick={() => setAdding(true)}>
              {copy(locale, '+ Añadir condición', '+ Add condition')}
            </Button>
          )}
          <p className="small text-muted mt-2 mb-0">
            {copy(
              locale,
              'Las condiciones se suman al periodo, las cuentas y las monedas elegidas arriba.',
              'Conditions are added to the period, accounts, and currencies selected above.',
            )}
          </p>
        </div>
      ) : null}
    </section>
  );
};

const BreakdownSection = ({
  catalog,
  definition,
  locale,
  busy,
  seriesLimit,
  onSeriesLimitChange,
  onApply,
}: {
  catalog: SemanticAnalyticsCatalog;
  definition: SemanticQueryDefinition;
  locale: string;
  busy: boolean;
  seriesLimit: number | 'ALL';
  onSeriesLimitChange: (limit: number | 'ALL') => void;
  onApply: (mutate: (candidate: SemanticQueryDefinition) => void) => void;
}) => {
  const [advanced, setAdvanced] = useState(false);
  const dataset = datasetById(catalog, definition.dataset);
  if (!dataset) return null;
  const primary = definition.groupBy[0] ?? '';
  const comparison = definition.groupBy[1] ?? '';
  const setBreakdown = (position: 0 | 1, field: string) =>
    onApply(candidate => {
      const groupBy = [...candidate.groupBy];
      if (field) groupBy[position] = field;
      else groupBy.splice(position, 1);
      candidate.groupBy = groupBy.filter((value, index) => value && groupBy.indexOf(value) === index).slice(0, 2);
      candidate.orderBy = candidate.orderBy.filter(order => !order.field || candidate.groupBy.includes(order.field));
    });
  return (
    <section className="border-bottom" data-cy="analyticsBreakdownSection">
      <StepHeading
        number={3}
        title={copy(locale, 'Desglose', 'Breakdown')}
        help={copy(locale, 'Organiza el resultado para compararlo de una forma útil.', 'Organize the result to compare it usefully.')}
      />
      <div className="p-3">
        <div className="row g-2">
          <div className="col-12 col-md-6">
            <Label className="small text-uppercase fw-semibold text-muted mb-1" for="analytics-primary-breakdown">
              {copy(locale, '¿Cómo quieres organizar los resultados?', 'How do you want to organize results?')}
            </Label>
            <Input
              id="analytics-primary-breakdown"
              type="select"
              value={primary}
              disabled={busy}
              onChange={event => setBreakdown(0, event.target.value)}
              data-cy="analyticsPrimaryBreakdown"
            >
              <option value="">{copy(locale, 'Sin desglose', 'No breakdown')}</option>
              {dataset.groupings.map(field => (
                <option key={field} value={field}>
                  {label(locale, field)}
                </option>
              ))}
            </Input>
          </div>
          <div className="col-12 col-md-6">
            <Label className="small text-uppercase fw-semibold text-muted mb-1" for="analytics-comparison-breakdown">
              {copy(locale, '¿Quieres comparar grupos dentro de cada uno?', 'Compare groups within each one?')}
            </Label>
            <Input
              id="analytics-comparison-breakdown"
              type="select"
              value={comparison}
              disabled={busy || !primary}
              onChange={event => setBreakdown(1, event.target.value)}
              data-cy="analyticsComparisonBreakdown"
            >
              <option value="">{copy(locale, 'Sin comparación adicional', 'No additional comparison')}</option>
              {dataset.groupings
                .filter(field => field !== primary)
                .map(field => (
                  <option key={field} value={field}>
                    {label(locale, field)}
                  </option>
                ))}
            </Input>
          </div>
        </div>
        <div className="mt-2">
          <Button
            type="button"
            color="link"
            size="sm"
            className="px-0"
            disabled={busy}
            aria-expanded={advanced}
            onClick={() => setAdvanced(value => !value)}
          >
            {advanced
              ? copy(locale, 'Ocultar opciones avanzadas', 'Hide advanced options')
              : copy(locale, 'Opciones avanzadas', 'Advanced options')}
          </Button>
          {advanced ? (
            <div className="row g-2 mt-1">
              <div className="col-12 col-md-6">
                <Label className="small mb-1" for="analytics-row-limit">
                  {copy(locale, 'Máximo de filas de la consulta', 'Maximum query rows')}
                </Label>
                <Input
                  id="analytics-row-limit"
                  type="number"
                  min="1"
                  max={catalog.maxLimit}
                  value={definition.limit}
                  disabled={busy}
                  onChange={event =>
                    onApply(candidate => {
                      candidate.limit = Math.max(1, Number(event.target.value) || 1);
                    })
                  }
                />
              </div>
              <div className="col-12 col-md-6">
                <Label className="small mb-1" for="analytics-series-limit">
                  {copy(locale, 'Series que quieres mostrar', 'Series to show')}
                </Label>
                <Input
                  id="analytics-series-limit"
                  type="select"
                  value={seriesLimit}
                  disabled={busy}
                  onChange={event => onSeriesLimitChange(event.target.value === 'ALL' ? 'ALL' : Number(event.target.value))}
                >
                  <option value="5">{copy(locale, 'Principales 5', 'Top 5')}</option>
                  <option value="10">{copy(locale, 'Principales 10', 'Top 10')}</option>
                  <option value="ALL">{copy(locale, 'Todas las disponibles', 'All available')}</option>
                </Input>
              </div>
            </div>
          ) : null}
        </div>
      </div>
    </section>
  );
};

const VisualizationSection = ({
  definition,
  visualization,
  locale,
  busy,
  onVisualizationChange,
}: {
  definition: SemanticQueryDefinition;
  visualization: ExplorerVisualization;
  locale: string;
  busy: boolean;
  onVisualizationChange: (visualization: ExplorerVisualization) => void;
}) => {
  const options = visualizationEligibility(definition, null, locale);
  const simple = ['KPI', 'LINE', 'BAR', 'DONUT', 'TABLE'] as ExplorerVisualization[];
  const barActive = ['BAR', 'GROUPED_BAR', 'STACKED_BAR'].includes(visualization);
  const optionFor = (option: ExplorerVisualization) => options.find(item => item.option === option);
  return (
    <section data-cy="analyticsVisualizationSection">
      <StepHeading
        number={4}
        title={copy(locale, 'Visualización', 'Visualization')}
        help={copy(locale, 'Elige cómo presentar el resultado.', 'Choose how to present the result.')}
      />
      <div className="p-3">
        <ButtonGroup aria-label={copy(locale, 'Presentación', 'Presentation')} className="flex-wrap">
          {simple.map(option => {
            const effectiveOption = option === 'BAR' ? 'BAR' : option;
            const capability = optionFor(effectiveOption);
            const selected = option === 'BAR' ? barActive : visualization === option;
            return (
              <Button
                key={option}
                type="button"
                size="sm"
                color={selected ? 'primary' : 'secondary'}
                outline={!selected}
                disabled={busy || !capability?.enabled}
                title={!capability?.enabled ? capability?.reason : undefined}
                onClick={() => onVisualizationChange(effectiveOption)}
              >
                {option === 'KPI'
                  ? copy(locale, 'KPI', 'KPI')
                  : option === 'LINE'
                    ? copy(locale, 'Línea', 'Line')
                    : option === 'BAR'
                      ? copy(locale, 'Barras', 'Bars')
                      : option === 'DONUT'
                        ? copy(locale, 'Dona', 'Donut')
                        : copy(locale, 'Tabla', 'Table')}
              </Button>
            );
          })}
        </ButtonGroup>
        {barActive ? (
          <div className="d-flex flex-wrap align-items-center gap-2 mt-2">
            <Label className="small mb-0" for="analytics-bar-style">
              {copy(locale, 'Estilo de barras', 'Bar style')}
            </Label>
            <Input
              id="analytics-bar-style"
              type="select"
              bsSize="sm"
              style={{ width: 'auto' }}
              value={visualization}
              disabled={busy}
              onChange={event => onVisualizationChange(event.target.value as ExplorerVisualization)}
            >
              {(['BAR', 'GROUPED_BAR', 'STACKED_BAR'] as ExplorerVisualization[]).map(option => {
                const capability = optionFor(option);
                return (
                  <option key={option} value={option} disabled={!capability?.enabled}>
                    {option === 'BAR'
                      ? copy(locale, 'Barras simples', 'Simple bars')
                      : option === 'GROUPED_BAR'
                        ? copy(locale, 'Barras agrupadas', 'Grouped bars')
                        : copy(locale, 'Barras apiladas', 'Stacked bars')}
                  </option>
                );
              })}
            </Input>
          </div>
        ) : null}
        {options
          .filter(option => !option.enabled)
          .map(option => (
            <p className="small text-muted mt-2 mb-0" key={option.option} hidden>
              {option.reason}
            </p>
          ))}
        <p className="small text-muted mt-2 mb-0">
          {copy(
            locale,
            'La presentación cambia cómo ves los resultados, no los datos de la consulta.',
            'Presentation changes how you see results, not query data.',
          )}
        </p>
      </div>
    </section>
  );
};

/** Visual authoring is a compact facade over the same canonical AST used by Consulta escrita. */
export const AnalyticsVisualBuilder = ({
  catalog,
  definition,
  locale,
  busy,
  categories,
  tags,
  visualization,
  seriesLimit,
  onVisualizationChange,
  onSeriesLimitChange,
  onApply,
  onChooseInformation,
  onClear,
}: Props) => {
  const apply = (mutate: (candidate: SemanticQueryDefinition) => void) => {
    if (!definition) return;
    const candidate = clone(definition);
    mutate(candidate);
    onApply(candidate);
  };
  const chooseInformation = (choice: InformationChoice) => onChooseInformation(choice.dataset, choice.economicFlow);

  if (!definition) {
    return (
      <section
        className="card border shadow-sm"
        data-cy="analyticsVisualBuilder"
        aria-label={copy(locale, 'Constructor visual de consulta', 'Visual query builder')}
      >
        <StepHeading
          number={1}
          title={copy(locale, '¿Qué quieres calcular?', 'What do you want to calculate?')}
          help={copy(locale, 'Elige la información con la que quieres empezar.', 'Choose the information you want to start with.')}
        />
        <div className="p-3">
          <div className="col-12 col-lg-7 px-0">
            <InformationSelector definition={null} locale={locale} busy={busy} onChoose={chooseInformation} />
          </div>
        </div>
      </section>
    );
  }

  return (
    <section
      className="card border shadow-sm"
      data-cy="analyticsVisualBuilder"
      aria-label={copy(locale, 'Constructor visual de consulta', 'Visual query builder')}
    >
      <CalculationSection
        catalog={catalog}
        definition={definition}
        locale={locale}
        busy={busy}
        onApply={apply}
        onChooseInformation={chooseInformation}
        onClear={onClear}
      />
      <ConditionsSection
        catalog={catalog}
        definition={definition}
        locale={locale}
        busy={busy}
        categories={categories}
        tags={tags}
        onApply={apply}
      />
      <BreakdownSection
        catalog={catalog}
        definition={definition}
        locale={locale}
        busy={busy}
        seriesLimit={seriesLimit}
        onSeriesLimitChange={onSeriesLimitChange}
        onApply={apply}
      />
      <VisualizationSection
        definition={definition}
        visualization={visualization}
        locale={locale}
        busy={busy}
        onVisualizationChange={onVisualizationChange}
      />
    </section>
  );
};
