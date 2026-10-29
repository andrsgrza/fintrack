import React, { useMemo, useState } from 'react';
import { Button, Input, Label } from 'reactstrap';

import { SemanticAnalyticsCatalog, SemanticPredicateOperator, SemanticQueryDefinition } from './analytics-explorer.model';
import { copy, datasetById, derivedAlias, describeField, fieldType, label, predicateValueOptions } from './analytics-explorer-utils';

type Props = {
  catalog: SemanticAnalyticsCatalog;
  definition: SemanticQueryDefinition | null;
  locale: string;
  busy: boolean;
  onApply: (candidate: SemanticQueryDefinition) => Promise<void>;
  onClear: () => void;
};

type Choice = { id: string; title: string; description: string; meta?: string };

const clone = (definition: SemanticQueryDefinition): SemanticQueryDefinition =>
  JSON.parse(JSON.stringify(definition)) as SemanticQueryDefinition;

const matches = (choice: Choice, search: string) =>
  `${choice.title} ${choice.description} ${choice.meta ?? ''}`.toLocaleLowerCase().includes(search.toLocaleLowerCase());

const ChoiceList = ({
  labelText,
  search,
  onSearch,
  choices,
  onChoose,
  empty,
  disabled,
}: {
  labelText: string;
  search: string;
  onSearch: (value: string) => void;
  choices: Choice[];
  onChoose: (id: string) => void;
  empty: string;
  disabled: boolean;
}) => {
  const visible = choices.filter(choice => matches(choice, search));
  return (
    <div>
      <Label className="visually-hidden">{labelText}</Label>
      <Input value={search} disabled={disabled} onChange={event => onSearch(event.target.value)} placeholder={labelText} />
      <div className="row g-2 mt-1">
        {visible.map(choice => (
          <div className="col-12 col-md-6" key={choice.id}>
            <Button
              type="button"
              color="light"
              className="border text-start w-100 h-100 p-2"
              disabled={disabled}
              onClick={() => onChoose(choice.id)}
            >
              <span className="d-flex justify-content-between gap-2">
                <strong className="small">{choice.title}</strong>
                {choice.meta ? <span className="small text-muted text-nowrap">{choice.meta}</span> : null}
              </span>
              <span className="small text-muted d-block mt-1">{choice.description}</span>
            </Button>
          </div>
        ))}
      </div>
      {!visible.length ? <p className="small text-muted mt-2 mb-0">{empty}</p> : null}
    </div>
  );
};

const StepHeading = ({ number, title, help }: { number: number; title: string; help: string }) => (
  <div className="d-flex gap-2 align-items-start mb-3">
    <span
      className="rounded-circle bg-primary text-white fw-semibold text-center flex-shrink-0"
      style={{ width: '1.75rem', lineHeight: '1.75rem' }}
    >
      {number}
    </span>
    <div>
      <h3 className="h6 mb-1">{title}</h3>
      <p className="small text-muted mb-0">{help}</p>
    </div>
  </div>
);

/** Visual authoring creates exactly the same semantic AST as the SQL-like editor. */
export const AnalyticsVisualBuilder = ({ catalog, definition, locale, busy, onApply, onClear }: Props) => {
  const [calculationSearch, setCalculationSearch] = useState('');
  const [groupSearch, setGroupSearch] = useState('');
  const [conditionSearch, setConditionSearch] = useState('');
  const [predicateField, setPredicateField] = useState('');
  const [predicateOperator, setPredicateOperator] = useState<SemanticPredicateOperator>('EQUALS');
  const [predicateValues, setPredicateValues] = useState<string[]>([]);
  const [sortSearch, setSortSearch] = useState('');
  const dataset = datasetById(catalog, definition?.dataset);

  const apply = (mutate: (candidate: SemanticQueryDefinition) => void) => {
    if (!definition) return;
    const candidate = clone(definition);
    mutate(candidate);
    void onApply(candidate);
  };

  if (!definition || !dataset) {
    return (
      <section className="border rounded-3 p-4 bg-light" data-cy="analyticsVisualBuilderEmpty">
        <h2 className="h5">{copy(locale, 'Comienza con una fuente de información', 'Start with an information source')}</h2>
        <p className="mb-0 text-muted">
          {copy(
            locale,
            'Puedes elegir un ejemplo o seleccionar arriba lo que quieres analizar.',
            'Choose an example or select what you want to analyze above.',
          )}
        </p>
      </section>
    );
  }

  const calculationChoices: Choice[] = dataset.aggregateCapabilities.map(capability => ({
    id: `${capability.function}:${capability.field}`,
    title: `${label(locale, capability.function)} · ${label(locale, capability.field)}`,
    description: describeField(locale, capability.field),
    meta: fieldType(locale, capability.field),
  }));
  const groupChoices: Choice[] = dataset.groupings
    .filter(field => !definition.groupBy.includes(field))
    .map(field => ({ id: field, title: label(locale, field), description: describeField(locale, field), meta: fieldType(locale, field) }));
  const conditionChoices: Choice[] = dataset.predicateCapabilities.map(capability => ({
    id: capability.field,
    title: label(locale, capability.field),
    description: describeField(locale, capability.field),
    meta: fieldType(locale, capability.field),
  }));
  const capability = dataset.predicateCapabilities.find(item => item.field === predicateField);
  const knownValues = predicateValueOptions(locale, predicateField);
  const sortChoices: Choice[] = [
    ...definition.groupBy.map(field => ({
      id: `field:${field}`,
      title: label(locale, field),
      description: copy(locale, 'Ordenar por este grupo.', 'Order by this group.'),
      meta: copy(locale, 'Grupo', 'Group'),
    })),
    ...definition.projections.map(projection => ({
      id: `projection:${projection.alias}`,
      title: projection.alias,
      description: `${label(locale, projection.function)} · ${label(locale, projection.field)}`,
      meta: copy(locale, 'Cálculo', 'Calculation'),
    })),
  ];
  const currentSort = definition.orderBy[0];

  const addCalculation = (id: string) => {
    const [functionName, field] = id.split(':');
    apply(candidate => {
      candidate.projections.push({ alias: derivedAlias(functionName, field), function: functionName as 'COUNT' | 'SUM' | 'AVG', field });
    });
    setCalculationSearch('');
  };

  const addCondition = () => {
    const noValueOperator = predicateOperator === 'IS_UNCATEGORIZED' || predicateOperator === 'HAS_NONE';
    if (!predicateField || (!noValueOperator && !predicateValues.length)) return;
    apply(candidate => candidate.predicates.push({ field: predicateField, operator: predicateOperator, values: predicateValues }));
    setPredicateField('');
    setPredicateValues([]);
    setConditionSearch('');
  };

  return (
    <section data-cy="analyticsVisualBuilder" aria-label={copy(locale, 'Constructor visual de consulta', 'Visual query builder')}>
      <div className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-4">
        <div>
          <p className="text-uppercase small fw-semibold text-muted mb-1">{copy(locale, 'Constructor guiado', 'Guided builder')}</p>
          <h2 className="h5 mb-1">{label(locale, dataset.dataset)}</h2>
          <p className="text-muted mb-0">
            {copy(
              locale,
              'Cada cambio se valida con el mismo contrato que usa SQL-like.',
              'Every change is validated through the same contract used by SQL-like.',
            )}
          </p>
        </div>
        <Button color="link" type="button" disabled={busy} onClick={onClear}>
          {copy(locale, 'Empezar de nuevo', 'Start over')}
        </Button>
      </div>

      <div className="d-flex flex-column gap-3">
        <section className="border rounded-3 p-3">
          <StepHeading
            number={2}
            title={copy(locale, '¿Qué cálculos quieres ver?', 'What calculations do you want to see?')}
            help={copy(
              locale,
              'Puedes combinar hasta cuatro cálculos compatibles en una misma consulta.',
              'You can combine up to four compatible calculations in one query.',
            )}
          />
          <div className="row g-2">
            {definition.projections.map((projection, index) => (
              <div className="col-12 col-lg-6" key={`${projection.alias}-${index}`} data-cy={`analyticsProjection-${projection.alias}`}>
                <div className="border rounded-2 p-2 h-100">
                  <div className="d-flex justify-content-between gap-2 align-items-start">
                    <div>
                      <strong>
                        {label(locale, projection.function)} · {label(locale, projection.field)}
                      </strong>
                      <p className="small text-muted mb-0">{describeField(locale, projection.field)}</p>
                    </div>
                    <Button
                      type="button"
                      color="link"
                      size="sm"
                      disabled={busy || definition.projections.length === 1}
                      onClick={() => apply(candidate => candidate.projections.splice(index, 1))}
                    >
                      {copy(locale, 'Quitar', 'Remove')}
                    </Button>
                  </div>
                  <Label className="small mt-2 mb-1" for={`analytics-alias-${index}`}>
                    {copy(locale, 'Nombre del resultado', 'Result name')}
                  </Label>
                  <Input
                    id={`analytics-alias-${index}`}
                    defaultValue={projection.alias}
                    disabled={busy}
                    onBlur={event => {
                      if (event.target.value !== projection.alias)
                        apply(candidate => {
                          candidate.projections[index].alias = event.target.value;
                        });
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
          {definition.projections.length < 4 ? (
            <div className="mt-3">
              <ChoiceList
                labelText={copy(locale, 'Buscar un cálculo', 'Search calculations')}
                search={calculationSearch}
                onSearch={setCalculationSearch}
                choices={calculationChoices}
                onChoose={addCalculation}
                empty={copy(locale, 'No hay cálculos compatibles.', 'No compatible calculations.')}
                disabled={busy}
              />
            </div>
          ) : null}
        </section>

        <section className="border rounded-3 p-3">
          <StepHeading
            number={3}
            title={copy(locale, '¿Qué condiciones aplican?', 'Which conditions apply?')}
            help={copy(
              locale,
              'Las condiciones cambian qué movimientos se consideran. Las cuentas y monedas siguen siendo parte del alcance superior.',
              'Conditions change which records are considered. Accounts and currencies remain part of the scope above.',
            )}
          />
          {definition.predicates.length ? (
            <div className="d-flex flex-column gap-2 mb-3">
              {definition.predicates.map((predicate, index) => (
                <div
                  className="border rounded-2 p-2 d-flex justify-content-between gap-2 align-items-center"
                  key={`${predicate.field}-${index}`}
                  data-cy={`analyticsCondition-${predicate.field}`}
                >
                  <span className="small">
                    <strong>{label(locale, predicate.field)}</strong> · {label(locale, predicate.operator)} · {predicate.values.join(', ')}
                  </span>
                  <Button
                    color="link"
                    type="button"
                    size="sm"
                    disabled={busy}
                    onClick={() => apply(candidate => candidate.predicates.splice(index, 1))}
                  >
                    {copy(locale, 'Quitar', 'Remove')}
                  </Button>
                </div>
              ))}
            </div>
          ) : null}
          {!predicateField ? (
            <ChoiceList
              labelText={copy(locale, 'Buscar una condición', 'Search conditions')}
              search={conditionSearch}
              onSearch={setConditionSearch}
              choices={conditionChoices}
              onChoose={field => {
                setPredicateField(field);
                setPredicateOperator(dataset.predicateCapabilities.find(item => item.field === field)?.operators[0] ?? 'EQUALS');
                setPredicateValues([]);
              }}
              empty={copy(locale, 'No hay condiciones disponibles.', 'No conditions are available.')}
              disabled={busy}
            />
          ) : (
            <div className="border rounded-2 p-3 bg-light">
              <div className="d-flex justify-content-between gap-2 mb-2">
                <strong>{label(locale, predicateField)}</strong>
                <Button color="link" type="button" size="sm" onClick={() => setPredicateField('')} disabled={busy}>
                  {copy(locale, 'Cambiar', 'Change')}
                </Button>
              </div>
              <div className="d-flex flex-wrap gap-2 mb-3" aria-label={copy(locale, 'Operador de condición', 'Condition operator')}>
                {capability?.operators.map(operator => (
                  <Button
                    key={operator}
                    type="button"
                    size="sm"
                    color={predicateOperator === operator ? 'primary' : 'secondary'}
                    outline={predicateOperator !== operator}
                    disabled={busy}
                    onClick={() => {
                      setPredicateOperator(operator);
                      if (operator === 'IS_UNCATEGORIZED' || operator === 'HAS_NONE') setPredicateValues([]);
                      if (operator === 'EQUALS' && predicateValues.length > 1) setPredicateValues(predicateValues.slice(0, 1));
                    }}
                  >
                    {label(locale, operator)}
                  </Button>
                ))}
              </div>
              {predicateOperator === 'IS_UNCATEGORIZED' || predicateOperator === 'HAS_NONE' ? (
                <p className="small text-muted mb-3">
                  {copy(locale, 'Esta condición no necesita un valor.', 'This condition does not need a value.')}
                </p>
              ) : knownValues.length ? (
                <div className="d-flex flex-wrap gap-2 mb-3">
                  {knownValues.map(option => (
                    <Button
                      key={option.value}
                      type="button"
                      size="sm"
                      color={predicateValues.includes(option.value) ? 'primary' : 'secondary'}
                      outline={!predicateValues.includes(option.value)}
                      disabled={busy}
                      onClick={() =>
                        setPredicateValues(values =>
                          predicateOperator === 'EQUALS'
                            ? values.includes(option.value)
                              ? []
                              : [option.value]
                            : values.includes(option.value)
                              ? values.filter(value => value !== option.value)
                              : [...values, option.value],
                        )
                      }
                    >
                      {option.label}
                    </Button>
                  ))}
                </div>
              ) : (
                <Input
                  value={predicateValues.join(', ')}
                  disabled={busy}
                  onChange={event =>
                    setPredicateValues(
                      event.target.value
                        .split(',')
                        .map(value => value.trim())
                        .filter(Boolean),
                    )
                  }
                  placeholder={copy(locale, 'Escribe un valor', 'Enter a value')}
                />
              )}
              <Button
                color="primary"
                type="button"
                disabled={
                  busy || (!(predicateOperator === 'IS_UNCATEGORIZED' || predicateOperator === 'HAS_NONE') && !predicateValues.length)
                }
                onClick={addCondition}
              >
                {copy(locale, 'Aplicar condición', 'Apply condition')}
              </Button>
            </div>
          )}
        </section>

        <section className="border rounded-3 p-3">
          <StepHeading
            number={4}
            title={copy(locale, '¿Cómo quieres organizar el resultado?', 'How do you want to organize the result?')}
            help={copy(
              locale,
              'Agrupa para comparar y ordena por un grupo o por cualquiera de tus cálculos.',
              'Group to compare, then sort by a group or by any selected calculation.',
            )}
          />
          <div className="row g-3">
            <div className="col-12 col-xl-7">
              {definition.groupBy.length ? (
                <div className="d-flex flex-wrap gap-2 mb-3">
                  {definition.groupBy.map(field => (
                    <span className="badge text-bg-secondary p-2" key={field}>
                      {label(locale, field)}{' '}
                      <button
                        className="btn-close btn-close-white ms-2"
                        aria-label={copy(locale, `Quitar ${label(locale, field)}`, `Remove ${label(locale, field)}`)}
                        type="button"
                        disabled={busy}
                        onClick={() =>
                          apply(candidate => {
                            candidate.groupBy = candidate.groupBy.filter(item => item !== field);
                            candidate.orderBy = candidate.orderBy.filter(item => item.field !== field);
                          })
                        }
                      />
                    </span>
                  ))}
                </div>
              ) : null}
              <ChoiceList
                labelText={copy(locale, 'Buscar una forma de agrupar', 'Search ways to group')}
                search={groupSearch}
                onSearch={setGroupSearch}
                choices={groupChoices}
                onChoose={field => {
                  apply(candidate => candidate.groupBy.push(field));
                  setGroupSearch('');
                }}
                empty={copy(locale, 'No hay más agrupaciones compatibles.', 'No more compatible groupings.')}
                disabled={busy}
              />
            </div>
            <div className="col-12 col-xl-5">
              <Label>{copy(locale, 'Ordenar resultados', 'Sort results')}</Label>
              <ChoiceList
                labelText={copy(locale, 'Buscar grupo o cálculo', 'Search group or calculation')}
                search={sortSearch}
                onSearch={setSortSearch}
                choices={sortChoices}
                onChoose={id => {
                  const [kind, target] = id.split(':', 2);
                  apply(candidate => {
                    candidate.orderBy = [
                      kind === 'field'
                        ? { field: target, projectionAlias: null, direction: candidate.orderBy[0]?.direction ?? 'ASC' }
                        : { field: null, projectionAlias: target, direction: candidate.orderBy[0]?.direction ?? 'DESC' },
                    ];
                  });
                  setSortSearch('');
                }}
                empty={copy(locale, 'Primero agrega un grupo o cálculo.', 'Add a group or calculation first.')}
                disabled={busy}
              />
              {currentSort ? (
                <div className="d-flex align-items-center justify-content-between gap-2 mt-3 border rounded-2 p-2">
                  <span className="small">{currentSort.field ? label(locale, currentSort.field) : currentSort.projectionAlias}</span>
                  <div className="d-flex gap-1">
                    <Button
                      size="sm"
                      color="secondary"
                      outline={currentSort.direction !== 'ASC'}
                      disabled={busy}
                      onClick={() =>
                        apply(candidate => {
                          candidate.orderBy[0].direction = 'ASC';
                        })
                      }
                    >
                      {copy(locale, 'Menor a mayor', 'Low to high')}
                    </Button>
                    <Button
                      size="sm"
                      color="secondary"
                      outline={currentSort.direction !== 'DESC'}
                      disabled={busy}
                      onClick={() =>
                        apply(candidate => {
                          candidate.orderBy[0].direction = 'DESC';
                        })
                      }
                    >
                      {copy(locale, 'Mayor a menor', 'High to low')}
                    </Button>
                    <Button
                      size="sm"
                      color="link"
                      disabled={busy}
                      onClick={() =>
                        apply(candidate => {
                          candidate.orderBy = [];
                        })
                      }
                    >
                      {copy(locale, 'Quitar', 'Clear')}
                    </Button>
                  </div>
                </div>
              ) : null}
              <Label className="mt-3" for="analytics-row-limit">
                {copy(locale, 'Máximo de resultados', 'Maximum results')}
              </Label>
              <Input
                id="analytics-row-limit"
                type="number"
                min="1"
                max={catalog.maxLimit}
                value={definition.limit}
                disabled={busy}
                onChange={event =>
                  apply(candidate => {
                    candidate.limit = Number(event.target.value);
                  })
                }
              />
            </div>
          </div>
        </section>
      </div>
    </section>
  );
};
