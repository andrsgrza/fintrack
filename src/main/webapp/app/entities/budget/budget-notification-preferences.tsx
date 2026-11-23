import React, { useEffect, useRef, useState } from 'react';
import { Button, Input, Label } from 'reactstrap';

import { BudgetNotificationPreferencesPayload, BudgetType } from './budget-api';
import { copy, formatBudgetThresholdPercentage } from './budget-utils';

export type BudgetNotificationPreferencesDraft = Pick<
  BudgetNotificationPreferencesPayload,
  'percentageAlertsEnabled' | 'thresholdPercentages' | 'periodCloseEnabled'
>;

export const emptyNotificationPreferences = (): BudgetNotificationPreferencesDraft => ({
  percentageAlertsEnabled: false,
  thresholdPercentages: [],
  periodCloseEnabled: false,
});

const suggestions = ['50', '80', '100'];
const MAX_THRESHOLD_COUNT = 20;
const THRESHOLD_PATTERN = /^\d+(?:\.\d{1,4})?$/;

/**
 * Canonicalizes a valid plain decimal only for comparison. The caller still retains and transports the exact
 * trimmed string it received from the user or server, so percentages never travel through JavaScript Number.
 */
export const normalizeThresholdPercentage = (value: string): string | null => {
  const trimmed = value.trim();
  if (!THRESHOLD_PATTERN.test(trimmed)) return null;
  const [rawInteger, rawFraction = ''] = trimmed.split('.');
  const integer = rawInteger.replace(/^0+(?=\d)/, '');
  const fraction = rawFraction.replace(/0+$/, '');
  const significantDigits = `${integer}${fraction}`.replace(/^0+/, '');
  if (!significantDigits || significantDigits.length > 15) return null;
  return fraction ? `${integer}.${fraction}` : integer;
};

export const compareThresholdPercentages = (left: string, right: string) => {
  const normalizedLeft = normalizeThresholdPercentage(left);
  const normalizedRight = normalizeThresholdPercentage(right);
  if (normalizedLeft === null || normalizedRight === null) return left.localeCompare(right);
  const [leftInteger, leftFraction = ''] = normalizedLeft.split('.');
  const [rightInteger, rightFraction = ''] = normalizedRight.split('.');
  if (leftInteger.length !== rightInteger.length) return leftInteger.length - rightInteger.length;
  if (leftInteger !== rightInteger) return leftInteger < rightInteger ? -1 : 1;
  const paddedLeft = leftFraction.padEnd(4, '0');
  const paddedRight = rightFraction.padEnd(4, '0');
  return paddedLeft === paddedRight ? 0 : paddedLeft < paddedRight ? -1 : 1;
};

const sameThreshold = (left: string, right: string) => {
  const normalizedLeft = normalizeThresholdPercentage(left);
  const normalizedRight = normalizeThresholdPercentage(right);
  return normalizedLeft !== null && normalizedLeft === normalizedRight;
};

const thresholdError = (locale: string) =>
  copy(locale, 'Introduce un porcentaje positivo de hasta cuatro decimales.', 'Enter a positive percentage with up to four decimals.');

export const validateNotificationPreferences = (value: BudgetNotificationPreferencesDraft, locale: string): string | null => {
  if (value.percentageAlertsEnabled && !value.thresholdPercentages.length) {
    return copy(locale, 'Agrega al menos un porcentaje para activar las alertas.', 'Add at least one percentage to enable alerts.');
  }
  if (value.thresholdPercentages.length > MAX_THRESHOLD_COUNT) {
    return copy(locale, 'Puedes configurar hasta 20 porcentajes.', 'You can configure up to 20 percentages.');
  }
  const normalized = value.thresholdPercentages.map(normalizeThresholdPercentage);
  if (normalized.some(item => item === null)) return thresholdError(locale);
  if (new Set(normalized).size !== value.thresholdPercentages.length) {
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
  const [addingCustom, setAddingCustom] = useState(false);
  const [customValue, setCustomValue] = useState('');
  const [customError, setCustomError] = useState<string | null>(null);
  const customInput = useRef<HTMLInputElement>(null);
  const addCustomButton = useRef<HTMLButtonElement>(null);
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
  const configuredCount = value.thresholdPercentages.length;
  const atMaximum = configuredCount >= MAX_THRESHOLD_COUNT;

  useEffect(() => {
    if (addingCustom) customInput.current?.focus();
  }, [addingCustom]);

  const closeCustom = () => {
    setAddingCustom(false);
    setCustomValue('');
    setCustomError(null);
    requestAnimationFrame(() => addCustomButton.current?.focus());
  };

  const removeThreshold = (threshold: string) => {
    onChange({ ...value, thresholdPercentages: value.thresholdPercentages.filter(item => item !== threshold) });
  };

  const addThreshold = (raw: string) => {
    const exact = raw.trim();
    const normalized = normalizeThresholdPercentage(exact);
    if (normalized === null) {
      setCustomError(thresholdError(locale));
      return false;
    }
    if (value.thresholdPercentages.some(existing => sameThreshold(existing, exact))) {
      setCustomError(copy(locale, 'Este porcentaje ya está configurado.', 'This percentage is already configured.'));
      return false;
    }
    if (atMaximum) {
      setCustomError(copy(locale, 'Puedes configurar hasta 20 porcentajes.', 'You can configure up to 20 percentages.'));
      return false;
    }
    onChange({ ...value, thresholdPercentages: [...value.thresholdPercentages, exact].sort(compareThresholdPercentages) });
    setCustomError(null);
    return true;
  };

  const toggleSuggestion = (suggestion: string) => {
    const existing = value.thresholdPercentages.find(threshold => sameThreshold(threshold, suggestion));
    if (existing) {
      removeThreshold(existing);
      setCustomError(null);
      return;
    }
    void addThreshold(suggestion);
  };

  if (readOnly) {
    return (
      <div data-cy={`${idPrefix}ReadOnly`}>
        <dl className="row mb-0 small">
          <dt className="col-sm-5 text-muted">{thresholdTitle}</dt>
          <dd className="col-sm-7">
            {value.percentageAlertsEnabled ? copy(locale, 'Activadas', 'Enabled') : copy(locale, 'Desactivadas', 'Disabled')}
          </dd>
          {value.percentageAlertsEnabled && value.thresholdPercentages.length ? (
            <>
              <dt className="col-sm-5 text-muted">{copy(locale, 'Porcentajes', 'Percentages')}</dt>
              <dd className="col-sm-7">
                {value.thresholdPercentages.map(item => formatBudgetThresholdPercentage(item, locale)).join(', ')}
              </dd>
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

        {value.percentageAlertsEnabled ? (
          <fieldset disabled={disabled} className="mt-3" data-cy={`${idPrefix}ThresholdSelector`}>
            <legend className="small fw-semibold mb-2">{copy(locale, 'Avísame cuando alcance:', 'Notify me when it reaches:')}</legend>
            <div className="d-flex flex-wrap align-items-center gap-2">
              {suggestions.map(suggestion => {
                const selected = value.thresholdPercentages.some(threshold => sameThreshold(threshold, suggestion));
                return (
                  <Button
                    key={suggestion}
                    type="button"
                    size="sm"
                    color={selected ? 'primary' : 'secondary'}
                    outline={!selected}
                    aria-pressed={selected}
                    onClick={() => toggleSuggestion(suggestion)}
                    data-cy={`${idPrefix}Suggestion${suggestion}`}
                  >
                    {formatBudgetThresholdPercentage(suggestion, locale)}
                    {selected ? ' ✓' : ''}
                  </Button>
                );
              })}
              {value.thresholdPercentages
                .filter(threshold => !suggestions.some(suggestion => sameThreshold(threshold, suggestion)))
                .map(threshold => (
                  <Button
                    key={threshold}
                    type="button"
                    size="sm"
                    color="secondary"
                    outline
                    onClick={() => removeThreshold(threshold)}
                    aria-label={copy(
                      locale,
                      `Quitar ${formatBudgetThresholdPercentage(threshold, locale)}`,
                      `Remove ${formatBudgetThresholdPercentage(threshold, locale)}`,
                    )}
                    data-cy={`${idPrefix}CustomChip`}
                  >
                    {formatBudgetThresholdPercentage(threshold, locale)} <span aria-hidden="true">×</span>
                  </Button>
                ))}
              <Button
                innerRef={addCustomButton}
                type="button"
                size="sm"
                color="secondary"
                outline
                onClick={() => {
                  setAddingCustom(true);
                  setCustomError(null);
                }}
                disabled={atMaximum}
                data-cy={`${idPrefix}OpenCustomThreshold`}
              >
                + {copy(locale, 'Añadir porcentaje', 'Add percentage')}
              </Button>
            </div>
            <p className="small text-muted mt-2 mb-0" data-cy={`${idPrefix}ThresholdCount`}>
              {copy(
                locale,
                `${configuredCount} de ${MAX_THRESHOLD_COUNT} porcentajes configurados.`,
                `${configuredCount} of ${MAX_THRESHOLD_COUNT} percentages configured.`,
              )}
              {atMaximum ? ` ${copy(locale, 'Quita uno para añadir otro.', 'Remove one before adding another.')}` : ''}
            </p>
            {!configuredCount ? (
              <p className="small text-muted mb-0">
                {copy(locale, 'Aún no has elegido porcentajes.', 'You have not selected percentages yet.')}
              </p>
            ) : null}
            {addingCustom ? (
              <div className="row g-2 align-items-end mt-2" data-cy={`${idPrefix}CustomThresholdForm`}>
                <div className="col-12 col-sm-5">
                  <Label for={`${idPrefix}-custom-threshold`} className="small mb-1">
                    {copy(locale, 'Porcentaje personalizado', 'Custom percentage')}
                  </Label>
                  <Input
                    innerRef={customInput}
                    id={`${idPrefix}-custom-threshold`}
                    type="text"
                    inputMode="decimal"
                    autoComplete="off"
                    value={customValue}
                    onChange={event => {
                      setCustomValue(event.target.value);
                      setCustomError(null);
                    }}
                    aria-describedby={customError ? `${idPrefix}-custom-threshold-error` : undefined}
                    data-cy={`${idPrefix}CustomThreshold`}
                  />
                </div>
                <div className="col-12 col-sm-auto d-flex gap-2">
                  <Button
                    type="button"
                    color="primary"
                    onClick={() => {
                      if (addThreshold(customValue)) closeCustom();
                    }}
                    data-cy={`${idPrefix}AddThreshold`}
                  >
                    {copy(locale, 'Añadir', 'Add')}
                  </Button>
                  <Button type="button" color="secondary" outline onClick={closeCustom} data-cy={`${idPrefix}CancelCustomThreshold`}>
                    {copy(locale, 'Cancelar', 'Cancel')}
                  </Button>
                </div>
              </div>
            ) : null}
            {customError ? (
              <div id={`${idPrefix}-custom-threshold-error`} className="invalid-feedback d-block" role="alert">
                {customError}
              </div>
            ) : null}
          </fieldset>
        ) : null}
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
