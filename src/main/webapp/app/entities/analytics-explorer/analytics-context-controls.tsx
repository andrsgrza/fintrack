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

const selectionSummary = (locale: string, count: number, allEs: string, allEn: string) =>
  count ? copy(locale, `${count} seleccionadas`, `${count} selected`) : copy(locale, allEs, allEn);

/** A compact execution-only context. It never changes the query definition. */
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
  const periodSummary = relative
    ? relativePeriodLabel(locale, context.time.relativePeriod ?? '')
    : copy(locale, 'Rango personalizado', 'Custom range');

  return (
    <section data-cy="analyticsContext" aria-labelledby="analytics-context-heading">
      <div className="d-flex flex-wrap justify-content-between gap-2 align-items-start mb-3">
        <div>
          <p className="text-uppercase small fw-semibold text-primary mb-1">
            {copy(locale, 'Contexto de visualización', 'Visualization context')}
          </p>
          <h2 id="analytics-context-heading" className="h5 mb-1">
            {copy(locale, '¿Qué parte de tus finanzas quieres ver?', 'Which part of your finances do you want to see?')}
          </h2>
        </div>
        <span className="small text-muted">
          {periodSummary} · {selectionSummary(locale, context.accountIds.length, 'Todas las cuentas', 'All accounts')} ·{' '}
          {selectionSummary(locale, context.currencyCodes.length, 'Todas las monedas', 'All currencies')}
        </span>
      </div>

      <div className="row g-2 g-md-3">
        <div className="col-12 col-md-4">
          <Label className="small text-uppercase fw-semibold text-muted mb-1" for="analytics-context-period">
            {copy(locale, 'Periodo', 'Period')}
          </Label>
          <Input
            id="analytics-context-period"
            type="select"
            disabled={disabled}
            value={relative ? (context.time.relativePeriod ?? '') : 'ABSOLUTE_RANGE'}
            data-cy="analyticsContextTimeMode"
            onChange={event => {
              if (event.target.value === 'ABSOLUTE_RANGE') {
                updateTime({
                  mode: 'ABSOLUTE_RANGE',
                  relativePeriod: null,
                  from: context.time.from ?? '',
                  through: context.time.through ?? '',
                });
              } else {
                updateTime({ mode: 'RELATIVE', relativePeriod: event.target.value, from: null, through: null });
              }
            }}
          >
            {catalog.supportedRelativePeriods.map(period => (
              <option key={period} value={period}>
                {relativePeriodLabel(locale, period)}
              </option>
            ))}
            <option value="ABSOLUTE_RANGE">{copy(locale, 'Rango personalizado…', 'Custom range…')}</option>
          </Input>
          {!relative ? (
            <div className="row g-2 mt-1">
              <div className="col-6">
                <Label className="visually-hidden" for="analytics-context-from">
                  {copy(locale, 'Desde', 'From')}
                </Label>
                <Input
                  id="analytics-context-from"
                  type="date"
                  disabled={disabled}
                  value={context.time.from ?? ''}
                  data-cy="analyticsContextFrom"
                  onChange={event => updateTime({ from: event.target.value })}
                />
              </div>
              <div className="col-6">
                <Label className="visually-hidden" for="analytics-context-through">
                  {copy(locale, 'Hasta', 'Through')}
                </Label>
                <Input
                  id="analytics-context-through"
                  type="date"
                  disabled={disabled}
                  value={context.time.through ?? ''}
                  data-cy="analyticsContextThrough"
                  onChange={event => updateTime({ through: event.target.value })}
                />
              </div>
            </div>
          ) : null}
        </div>

        <div className="col-12 col-md-4">
          <details className="border rounded-2 h-100 p-2" data-cy="analyticsAccountPicker">
            <summary className="small text-uppercase fw-semibold text-muted" role="button">
              {copy(locale, 'Cuentas', 'Accounts')} · {selectionSummary(locale, context.accountIds.length, 'Todas', 'All')}
            </summary>
            <div className="pt-2">
              <Label className="visually-hidden" for="analytics-account-search">
                {copy(locale, 'Buscar cuenta', 'Search accounts')}
              </Label>
              <Input
                id="analytics-account-search"
                type="search"
                bsSize="sm"
                value={accountSearch}
                disabled={disabled}
                onChange={event => setAccountSearch(event.target.value)}
                placeholder={copy(locale, 'Buscar una cuenta', 'Search an account')}
              />
              <div className="mt-2 d-flex flex-column gap-1" style={{ maxHeight: '12rem', overflowY: 'auto' }}>
                {visibleAccounts.map(account => (
                  <Label check className="border rounded-2 px-2 py-1 small" key={account.id}>
                    <Input
                      type="checkbox"
                      checked={context.accountIds.includes(account.id)}
                      disabled={disabled}
                      onChange={() => onChange({ ...context, accountIds: toggle(context.accountIds, account.id) })}
                    />{' '}
                    {account.name}
                    <span className="text-muted">
                      {' '}
                      · {account.accountType ? label(locale, account.accountType) : ''} · {account.currency}
                    </span>
                  </Label>
                ))}
                {!visibleAccounts.length ? (
                  <span className="small text-muted">{copy(locale, 'No hay coincidencias.', 'No matches.')}</span>
                ) : null}
              </div>
            </div>
          </details>
        </div>

        <div className="col-12 col-md-4">
          <details className="border rounded-2 h-100 p-2" data-cy="analyticsCurrencyPicker">
            <summary className="small text-uppercase fw-semibold text-muted" role="button">
              {copy(locale, 'Monedas', 'Currencies')} · {selectionSummary(locale, context.currencyCodes.length, 'Todas', 'All')}
            </summary>
            <div className="pt-2">
              <Label className="visually-hidden" for="analytics-currency-search">
                {copy(locale, 'Buscar moneda', 'Search currencies')}
              </Label>
              <Input
                id="analytics-currency-search"
                type="search"
                bsSize="sm"
                value={currencySearch}
                disabled={disabled}
                onChange={event => setCurrencySearch(event.target.value)}
                placeholder={copy(locale, 'Buscar una moneda', 'Search a currency')}
              />
              <div className="mt-2 d-flex flex-wrap gap-2">
                {visibleCurrencies.map(currency => (
                  <Label check className="border rounded-pill px-2 py-1 small" key={currency}>
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
            </div>
          </details>
        </div>
      </div>
    </section>
  );
};
