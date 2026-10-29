import React from 'react';
import { Button } from 'reactstrap';

import { SemanticAnalyticsCatalog, SemanticQueryDefinition } from './analytics-explorer.model';
import { copy, label, sourceDescription } from './analytics-explorer-utils';

type Props = {
  catalog: SemanticAnalyticsCatalog;
  definition: SemanticQueryDefinition | null;
  locale: string;
  disabled: boolean;
  onChooseDataset: (datasetId: string) => void;
};

/** Product-facing entry point for the governed source catalog. */
export const AnalyticsCatalogPanel = ({ catalog, definition, locale, disabled, onChooseDataset }: Props) => (
  <section aria-labelledby="analytics-source-heading" data-cy="analyticsDatasetCatalog">
    <div className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
      <div>
        <p className="text-uppercase small fw-semibold text-muted mb-1">{copy(locale, 'Paso 1', 'Step 1')}</p>
        <h2 id="analytics-source-heading" className="h5 mb-1">
          {copy(locale, '¿Qué información quieres analizar?', 'What information do you want to analyze?')}
        </h2>
        <p className="text-muted mb-0">
          {copy(
            locale,
            'Elige una fuente. Después decides los cálculos, condiciones y forma de presentar el resultado.',
            'Choose a source, then decide the calculations, conditions, and presentation.',
          )}
        </p>
      </div>
      {definition ? <span className="badge text-bg-primary">{copy(locale, 'Consulta en edición', 'Query in progress')}</span> : null}
    </div>
    <div className="row g-3">
      {catalog.datasets.map(dataset => {
        const selected = dataset.dataset === definition?.dataset;
        return (
          <div className="col-12 col-lg-6" key={dataset.dataset}>
            <article className={`border rounded-3 p-3 h-100 d-flex flex-column ${selected ? 'border-primary bg-light' : ''}`}>
              <h3 className="h6 mb-2">{label(locale, dataset.dataset)}</h3>
              <p className="text-muted mb-3">{sourceDescription(locale, dataset.dataset)}</p>
              <div className="mt-auto d-flex justify-content-between align-items-center gap-2">
                <small className="text-muted">
                  {copy(
                    locale,
                    `${dataset.aggregateCapabilities.length} cálculos disponibles`,
                    `${dataset.aggregateCapabilities.length} calculations available`,
                  )}
                </small>
                <Button
                  type="button"
                  size="sm"
                  color={selected ? 'secondary' : 'primary'}
                  outline={selected}
                  disabled={disabled || selected}
                  onClick={() => onChooseDataset(dataset.dataset)}
                  data-cy={`analyticsDataset-${dataset.dataset}`}
                >
                  {selected ? copy(locale, 'Seleccionada', 'Selected') : copy(locale, 'Elegir', 'Choose')}
                </Button>
              </div>
            </article>
          </div>
        );
      })}
    </div>
    {definition ? (
      <p className="small text-muted mt-3 mb-0">
        {copy(
          locale,
          'Cambiar la información reinicia los cálculos, condiciones y agrupaciones de esta consulta.',
          'Changing the information resets this query’s calculations, conditions, and groupings.',
        )}
      </p>
    ) : null}
  </section>
);
