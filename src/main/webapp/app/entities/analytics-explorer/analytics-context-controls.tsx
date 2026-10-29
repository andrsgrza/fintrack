import React, { useMemo, useState } from 'react';
import { Input, Label } from 'reactstrap';

import { SemanticAnalyticsCatalog, VisualizationContext } from './analytics-explorer.model';
import { copy, label, relativePeriodLabel } from './analytics-explorer-utils';

type Account = { id: number; name: string; accountType?: string; currency?: string };
type Props = {
  catalog: SemanticAnalyticsCatalog;
  context: VisualizationContext;
  accounts: Account[];
  locale: string;
  disabled: boolean;
  onChange: (next: VisualizationContext) => void;
};

const toggle = <T,>(values: T[], value: T) =>
  values.includes(value) ? values.filter(candidate => candidate !== value) : [...values, value];

const scopeSummary = (locale: string, count: number, selectedEs: string, selectedEn: string) =>
  count ? copy(locale, `${count} ${selectedEs}`, `${count} ${selectedEn}`) : copy(locale, 'Todas', 'All');

/** Compact execution controls. They never mutate the semantic definition. */
export const AnalyticsContextControls = ({ catalog, context, accounts, locale, disabled, onChange }: Props) => {
  const [accountSearch, setAccountSearch] = useState('');
  const [currencySearch, setCurrencySearch] = useState('');
  const relative = context.time.mode === 'RELATIVE';
  const updateTime = (patch: Partial<VisualizationContext['time']>) => onChange({ ...context, time: { ...context.time, ...patch } });
  const currencies = useMemo(
    () => Array.from(new Set(accounts.map(account => account.currency).filter((value): value is string => !!value))).sort(),
    [accounts],
  );
  const visibleAccounts = accounts.filter(account =>
    `${account.name} ${account.accountType ?? ''} ${account.currency ?? ''}`
      .toLocaleLowerCase()
      .includes(accountSearch.toLocaleLowerCase()),
  );
  const visibleCurrencies = currencies.filter(currency => currency.toLocaleLowerCase().includes(currencySearch.toLocaleLowerCase()));

  return (
    <section data-cy="analyticsContext" aria-labelledby="analytics-context-heading">
      <div className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
        <div>
          <p className="text-uppercase small fw-semibold text-muted mb-1">{copy(locale, 'Alcance de resultados', 'Result scope')}</p>
          <h2 id="analytics-context-heading" className="h5 mb-1">
            {copy(locale, '¿Para qué periodo y cuentas?', 'Which period and accounts?')}
          </h2>
          <p className="small text-muted mb-0">
            {copy(
              locale,
              'Este alcance se aplica sólo al ejecutar. No cambia los cálculos de la consulta.',
              'This scope applies only when running. It never changes the query calculations.',
            )}
          </p>
        </div>
      </div>
      <div className="row g-3 align-items-end">
        <div className="col-12 col-sm-6 col-lg-3">
          <Label for="analytics-context-time-mode">{copy(locale, 'Periodo', 'Period')}</Label>
          <Input
            id="analytics-context-time-mode"
            type="select"
            disabled={disabled}
            value={context.time.mode}
            data-cy="analyticsContextTimeMode"
            onChange={event =>
              event.target.value === 'RELATIVE'
                ? updateTime({
                    mode: 'RELATIVE',
                    relativePeriod: catalog.supportedRelativePeriods[0] ?? 'THIS_MONTH',
                    from: null,
                    through: null,
                  })
                : updateTime({ mode: 'ABSOLUTE_RANGE', relativePeriod: null, from: '', through: '' })
            }
          >
            <option value="RELATIVE">{copy(locale, 'Periodo relativo', 'Relative period')}</option>
            <option value="ABSOLUTE_RANGE">{copy(locale, 'Rango personalizado', 'Custom range')}</option>
          </Input>
        </div>
        {relative ? (
          <div className="col-12 col-sm-6 col-lg-3">
            <Label for="analytics-context-relative-period">{copy(locale, 'Seleccionar periodo', 'Select period')}</Label>
            <Input
              id="analytics-context-relative-period"
              type="select"
              disabled={disabled}
              value={context.time.relativePeriod ?? ''}
              data-cy="analyticsContextRelativePeriod"
              onChange={event => updateTime({ relativePeriod: event.target.value })}
            >
              {catalog.supportedRelativePeriods.map(period => (
                <option key={period} value={period}>
                  {relativePeriodLabel(locale, period)}
                </option>
              ))}
            </Input>
          </div>
        ) : (
          <>
            <div className="col-6 col-lg-3">
              <Label for="analytics-context-from">{copy(locale, 'Desde', 'From')}</Label>
              <Input
                id="analytics-context-from"
                type="date"
                disabled={disabled}
                value={context.time.from ?? ''}
                data-cy="analyticsContextFrom"
                onChange={event => updateTime({ from: event.target.value })}
              />
            </div>
            <div className="col-6 col-lg-3">
              <Label for="analytics-context-through">{copy(locale, 'Hasta', 'Through')}</Label>
              <Input
                id="analytics-context-through"
                type="date"
                disabled={disabled}
                value={context.time.through ?? ''}
                data-cy="analyticsContextThrough"
                onChange={event => updateTime({ through: event.target.value })}
              />
            </div>
          </>
        )}
        <div className="col-12 col-lg-3">
          <details className="border rounded-2 p-2" data-cy="analyticsCurrencyPicker">
            <summary className="small fw-semibold" role="button">
              {copy(locale, 'Monedas', 'Currencies')} · {scopeSummary(locale, context.currencyCodes.length, 'seleccionadas', 'selected')}
            </summary>
            <div className="pt-2">
              <Input
                aria-label={copy(locale, 'Buscar moneda', 'Search currencies')}
                bsSize="sm"
                value={currencySearch}
                disabled={disabled}
                onChange={event => setCurrencySearch(event.target.value)}
                placeholder={copy(locale, 'Buscar…', 'Search…')}
              />
              <div className="mt-2 d-flex flex-wrap gap-2">
                {visibleCurrencies.map(currency => (
                  <Label check className="border rounded-pill px-2 py-1" key={currency}>
                    <Input
                      type="checkbox"
                      checked={context.currencyCodes.includes(currency)}
                      disabled={disabled}
                      onChange={() => onChange({ ...context, currencyCodes: toggle(context.currencyCodes, currency) })}
                    />{' '}
                    {currency}
                  </Label>
                ))}
              </div>
              <p className="small text-muted mb-0 mt-2">
                {copy(
                  locale,
                  'Sin selección incluye todas; los importes no se mezclan.',
                  'No selection includes all; amounts are never mixed.',
                )}
              </p>
            </div>
          </details>
        </div>
        <div className="col-12 col-lg-3">
          <details className="border rounded-2 p-2" data-cy="analyticsAccountPicker">
            <summary className="small fw-semibold" role="button">
              {copy(locale, 'Cuentas', 'Accounts')} · {scopeSummary(locale, context.accountIds.length, 'seleccionadas', 'selected')}
            </summary>
            <div className="pt-2">
              <Input
                aria-label={copy(locale, 'Buscar cuenta', 'Search accounts')}
                bsSize="sm"
                value={accountSearch}
                disabled={disabled}
                onChange={event => setAccountSearch(event.target.value)}
                placeholder={copy(locale, 'Buscar por cuenta, tipo o moneda…', 'Search account, type, or currency…')}
              />
              <div className="mt-2 d-flex flex-column gap-1" style={{ maxHeight: '12rem', overflowY: 'auto' }}>
                {visibleAccounts.map(account => (
                  <Label check className="border rounded-2 px-2 py-1" key={account.id}>
                    <Input
                      type="checkbox"
                      checked={context.accountIds.includes(account.id)}
                      disabled={disabled}
                      onChange={() => onChange({ ...context, accountIds: toggle(context.accountIds, account.id) })}
                    />{' '}
                    {account.name}{' '}
                    <span className="text-muted">
                      · {account.accountType ? label(locale, account.accountType) : ''} · {account.currency}
                    </span>
                  </Label>
                ))}
                {!visibleAccounts.length ? (
                  <span className="small text-muted">{copy(locale, 'No hay coincidencias.', 'No matches.')}</span>
                ) : null}
              </div>
              <p className="small text-muted mb-0 mt-2">
                {copy(locale, 'Sin selección incluye todas tus cuentas accesibles.', 'No selection includes all accessible accounts.')}
              </p>
            </div>
          </details>
        </div>
      </div>
    </section>
  );
};
