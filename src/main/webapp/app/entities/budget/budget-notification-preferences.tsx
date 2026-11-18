import React, { useState } from 'react';
import { Button, Input, Label } from 'reactstrap';

import { BudgetNotificationPreferencesPayload, BudgetType } from './budget-api';
import { copy } from './budget-utils';

export type BudgetNotificationPreferencesDraft = Pick<
  BudgetNotificationPreferencesPayload,
  'percentageAlertsEnabled' | 'thresholdPercentages' | 'periodCloseEnabled'
>;

export const emptyNotificationPreferences = (): BudgetNotificationPreferencesDraft => ({
  percentageAlertsEnabled: false,
  thresholdPercentages: [],
  periodCloseEnabled: false,
});

const suggestions = [50, 80, 100];

const normalizeCustomThreshold = (value: string): number | null => {
  const trimmed = value.trim();
  if (!/^\d+(?:\.\d{1,4})?$/.test(trimmed)) return null;
  const numeric = Number(trimmed);
  if (!Number.isFinite(numeric) || numeric <= 0 || trimmed.replace('.', '').replace(/^0+/, '').length > 15) return null;
  return numeric;
};

export const validateNotificationPreferences = (value: BudgetNotificationPreferencesDraft, locale: string): string | null => {
  if (value.percentageAlertsEnabled && !value.thresholdPercentages.length) {
    return copy(locale, 'Agrega al menos un porcentaje para activar las alertas.', 'Add at least one percentage to enable alerts.');
  }
  if (value.thresholdPercentages.length > 20) {
    return copy(locale, 'Puedes configurar hasta 20 porcentajes.', 'You can configure up to 20 percentages.');
  }
  const normalized = value.thresholdPercentages.map(item => String(item));
  if (normalized.some(item => normalizeCustomThreshold(item) === null)) {
    return copy(
      locale,
      'Cada porcentaje debe ser positivo y tener hasta cuatro decimales.',
      'Each percentage must be positive and use at most four decimals.',
    );
  }
  if (new Set(value.thresholdPercentages.map(item => item.toFixed(4))).size !== value.thresholdPercentages.length) {
    return copy(locale, 'Los porcentajes no pueden repetirse.', 'Percentages must be unique.');
  }
  return null;
};

export const BudgetNotificationPreferencesEditor = ({
  locale,
  type,
  value,
  onChange,
  disabled = false,
  readOnly = false,
  idPrefix,
}: {
  locale: string;
  type: BudgetType;
  value: BudgetNotificationPreferencesDraft;
  onChange: (next: BudgetNotificationPreferencesDraft) => void;
  disabled?: boolean;
  readOnly?: boolean;
  idPrefix: string;
}) => {
  const [customValue, setCustomValue] = useState('');
  const [customError, setCustomError] = useState<string | null>(null);
  const thresholdTitle =
    type === 'EXPENSE' ? copy(locale, 'Alertas de gasto', 'Spending alerts') : copy(locale, 'Alertas de ingresos', 'Income alerts');
  const thresholdDescription =
    type === 'EXPENSE'
      ? copy(
          locale,
          'Recibe una notificación cuando tus gastos alcancen determinados porcentajes del límite.',
          'Receive a notification when spending reaches selected percentages of the limit.',
        )
      : copy(
          locale,
          'Recibe una notificación cuando tus ingresos alcancen determinados porcentajes de la meta.',
          'Receive a notification when income reaches selected percentages of the goal.',
        );
  const updateThreshold = (index: number, raw: string) => {
    const parsed = normalizeCustomThreshold(raw);
    if (parsed === null) return;
    const next = [...value.thresholdPercentages];
    next[index] = parsed;
    onChange({ ...value, thresholdPercentages: next });
  };
  const addThreshold = (raw: string) => {
    const parsed = normalizeCustomThreshold(raw);
    if (parsed === null) {
      setCustomError(
        copy(
          locale,
          'Introduce un porcentaje positivo de hasta cuatro decimales.',
          'Enter a positive percentage with up to four decimals.',
        ),
      );
      return;
    }
    if (value.thresholdPercentages.some(existing => existing.toFixed(4) === parsed.toFixed(4))) {
      setCustomError(copy(locale, 'Este porcentaje ya está configurado.', 'This percentage is already configured.'));
      return;
    }
    if (value.thresholdPercentages.length >= 20) {
      setCustomError(copy(locale, 'Puedes configurar hasta 20 porcentajes.', 'You can configure up to 20 percentages.'));
      return;
    }
    onChange({ ...value, thresholdPercentages: [...value.thresholdPercentages, parsed].sort((left, right) => left - right) });
    setCustomValue('');
    setCustomError(null);
  };

  if (readOnly) {
    return (
      <div data-cy={`${idPrefix}ReadOnly`}>
        <dl className="row mb-0 small">
          <dt className="col-sm-5 text-muted">{thresholdTitle}</dt>
          <dd className="col-sm-7">
            {value.percentageAlertsEnabled ? copy(locale, 'Activadas', 'Enabled') : copy(locale, 'Desactivadas', 'Disabled')}
          </dd>
          {value.thresholdPercentages.length ? (
            <>
              <dt className="col-sm-5 text-muted">{copy(locale, 'Porcentajes', 'Percentages')}</dt>
              <dd className="col-sm-7">{value.thresholdPercentages.map(item => `${item}%`).join(', ')}</dd>
            </>
          ) : null}
          <dt className="col-sm-5 text-muted">{copy(locale, 'Resumen al cerrar el período', 'Period-close summary')}</dt>
          <dd className="col-sm-7 mb-0">
            {value.periodCloseEnabled ? copy(locale, 'Activado', 'Enabled') : copy(locale, 'Desactivado', 'Disabled')}
          </dd>
        </dl>
      </div>
    );
  }

  return (
    <div className="d-flex flex-column gap-4" data-cy={`${idPrefix}Editor`}>
      <section aria-labelledby={`${idPrefix}-threshold-title`}>
        <div className="d-flex flex-wrap justify-content-between align-items-start gap-2">
          <div>
            <h3 id={`${idPrefix}-threshold-title`} className="h6 mb-1">
              {thresholdTitle}
            </h3>
            <p className="small text-muted mb-0">{thresholdDescription}</p>
          </div>
          <div className="form-check form-switch mb-0">
            <Input
              id={`${idPrefix}-threshold-enabled`}
              type="switch"
              checked={value.percentageAlertsEnabled}
              onChange={event => onChange({ ...value, percentageAlertsEnabled: event.target.checked })}
              disabled={disabled}
              data-cy={`${idPrefix}ThresholdEnabled`}
            />
            <Label for={`${idPrefix}-threshold-enabled`} check>
              {value.percentageAlertsEnabled ? copy(locale, 'Activadas', 'Enabled') : copy(locale, 'Desactivadas', 'Disabled')}
            </Label>
          </div>
        </div>
        {/* Keep the draft editable while alerts are off. Selecting a value is not activation; only the independent
            switch controls whether the server evaluates threshold crossings. */}
        <fieldset disabled={disabled} className="mt-3">
          <legend className="visually-hidden">{thresholdTitle}</legend>
          <div className="d-flex flex-wrap gap-2 align-items-center mb-3">
            <span className="small text-muted me-1">{copy(locale, 'Sugerencias:', 'Suggestions:')}</span>
            {suggestions.map(suggestion => (
              <Button
                key={suggestion}
                type="button"
                size="sm"
                color="secondary"
                outline
                onClick={() => addThreshold(String(suggestion))}
                data-cy={`${idPrefix}Suggestion${suggestion}`}
              >
                {suggestion}%
              </Button>
            ))}
          </div>
          {value.thresholdPercentages.length ? (
            <div className="row g-2 mb-3" aria-label={copy(locale, 'Porcentajes configurados', 'Configured percentages')}>
              {value.thresholdPercentages.map((threshold, index) => (
                <div className="col-12 col-sm-6 col-lg-4" key={`${threshold}-${index}`}>
                  <div className="input-group input-group-sm">
                    <Input
                      aria-label={copy(locale, `Porcentaje ${index + 1}`, `Percentage ${index + 1}`)}
                      type="number"
                      min="0.0001"
                      step="0.0001"
                      inputMode="decimal"
                      value={threshold}
                      onChange={event => updateThreshold(index, event.target.value)}
                      data-cy={`${idPrefix}Threshold-${index}`}
                    />
                    <span className="input-group-text">%</span>
                    <Button
                      type="button"
                      color="secondary"
                      outline
                      onClick={() =>
                        onChange({ ...value, thresholdPercentages: value.thresholdPercentages.filter((_, item) => item !== index) })
                      }
                      aria-label={copy(locale, `Quitar ${threshold}%`, `Remove ${threshold}%`)}
                      data-cy={`${idPrefix}RemoveThreshold-${index}`}
                    >
                      ×
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="small text-muted mb-3">
              {copy(locale, 'Aún no has elegido porcentajes.', 'You have not selected percentages yet.')}
            </p>
          )}
          <div className="row g-2 align-items-end">
            <div className="col-12 col-sm-5">
              <Label for={`${idPrefix}-custom-threshold`} className="small mb-1">
                {copy(locale, 'Porcentaje personalizado', 'Custom percentage')}
              </Label>
              <Input
                id={`${idPrefix}-custom-threshold`}
                type="number"
                min="0.0001"
                step="0.0001"
                inputMode="decimal"
                value={customValue}
                onChange={event => {
                  setCustomValue(event.target.value);
                  setCustomError(null);
                }}
                data-cy={`${idPrefix}CustomThreshold`}
              />
            </div>
            <div className="col-12 col-sm-auto">
              <Button type="button" color="secondary" outline onClick={() => addThreshold(customValue)} data-cy={`${idPrefix}AddThreshold`}>
                {copy(locale, 'Añadir porcentaje', 'Add percentage')}
              </Button>
            </div>
          </div>
          {customError ? (
            <div className="invalid-feedback d-block" role="alert">
              {customError}
            </div>
          ) : null}
        </fieldset>
      </section>

      <section aria-labelledby={`${idPrefix}-period-close-title`}>
        <div className="d-flex flex-wrap justify-content-between align-items-start gap-2">
          <div>
            <h3 id={`${idPrefix}-period-close-title`} className="h6 mb-1">
              {copy(locale, 'Resumen al cerrar el período', 'Period-close summary')}
            </h3>
            <p className="small text-muted mb-0">
              {copy(
                locale,
                'Recibe un resumen de tu presupuesto cuando termine un período.',
                'Receive a summary of this budget when a period ends.',
              )}
            </p>
          </div>
          <div className="form-check form-switch mb-0">
            <Input
              id={`${idPrefix}-period-close-enabled`}
              type="switch"
              checked={value.periodCloseEnabled}
              onChange={event => onChange({ ...value, periodCloseEnabled: event.target.checked })}
              disabled={disabled}
              data-cy={`${idPrefix}PeriodCloseEnabled`}
            />
            <Label for={`${idPrefix}-period-close-enabled`} check>
              {value.periodCloseEnabled ? copy(locale, 'Activado', 'Enabled') : copy(locale, 'Desactivado', 'Disabled')}
            </Label>
          </div>
        </div>
      </section>
    </div>
  );
};
