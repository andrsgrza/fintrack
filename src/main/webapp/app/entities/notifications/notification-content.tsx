import React from 'react';

import {
  BudgetNotification,
  BudgetPeriodCloseNotificationSnapshot,
  BudgetThresholdNotificationSnapshot,
  getPeriodCloseNotificationSnapshot,
  getThresholdNotificationSnapshot,
} from './notification-api';
import { copy, dateRange, formatBudgetAmount, formatBudgetPercentage, isBudgetDecimalZero } from '../budget/budget-utils';

const regionalLocale = (locale: string) => (locale.startsWith('es') ? 'es-MX' : 'en-US');

const formatEventDate = (value: string, locale: string, timeZone: string) =>
  new Intl.DateTimeFormat(regionalLocale(locale), {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone,
  }).format(new Date(value));

const formatEventDay = (value: string, locale: string, timeZone: string) =>
  new Intl.DateTimeFormat(regionalLocale(locale), {
    dateStyle: 'long',
    timeZone,
  }).format(new Date(value));

const localCalendarDay = (value: string, timeZone: string) => {
  const parts = new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone,
  }).formatToParts(new Date(value));
  return parts
    .filter(part => part.type !== 'literal')
    .map(part => `${part.type}:${part.value}`)
    .join('|');
};

const unavailable = (locale: string, periodClose = false) =>
  copy(
    locale,
    periodClose ? 'La información histórica del período cerrado no está disponible.' : 'La notificación histórica no está disponible.',
    periodClose ? 'The closed-period historical information is unavailable.' : 'Historical notification is unavailable.',
  );

const PeriodCloseContent = ({
  snapshot,
  locale,
  compact,
}: {
  snapshot: BudgetPeriodCloseNotificationSnapshot;
  locale: string;
  compact: boolean;
}) => {
  // A period-close event only exposes financial values when B6C-C captured an authoritative evaluation. This keeps
  // a malformed or incomplete snapshot from masquerading as a zero-activity period.
  if (snapshot.evaluationStatus !== 'EVALUATED') {
    return <span className="small text-muted">{unavailable(locale, true)}</span>;
  }

  const isExpense = snapshot.budgetType === 'EXPENSE';
  const actual = formatBudgetAmount(snapshot.actualAmount, snapshot.currency, locale);
  const target = formatBudgetAmount(snapshot.targetAmount, snapshot.currency, locale);
  const period = dateRange(snapshot.periodStart, snapshot.periodEndExclusive, locale);
  const progress = formatBudgetPercentage(snapshot.progressPercentage, locale);
  const noActivity = isBudgetDecimalZero(snapshot.actualAmount);
  const summary = noActivity
    ? isExpense
      ? copy(locale, 'No se registraron gastos durante este período.', 'No expenses were recorded during this period.')
      : copy(locale, 'No se registraron ingresos durante este período.', 'No income was recorded during this period.')
    : isExpense
      ? copy(locale, `Registraste ${actual} de un límite de ${target}.`, `You recorded ${actual} of a ${target} limit.`)
      : copy(locale, `Registraste ${actual} de una meta de ${target}.`, `You recorded ${actual} of a ${target} goal.`);
  const actualLabel = isExpense
    ? copy(locale, 'Gastos registrados', 'Expenses recorded')
    : copy(locale, 'Ingresos registrados', 'Income recorded');
  const targetLabel = isExpense ? copy(locale, 'Límite', 'Limit') : copy(locale, 'Meta', 'Goal');
  const progressLabel = isExpense ? copy(locale, 'del límite', 'of limit') : copy(locale, 'de la meta', 'of goal');
  const delayedCapture =
    localCalendarDay(snapshot.logicalClosedAt, snapshot.timeZone) !== localCalendarDay(snapshot.capturedAt, snapshot.timeZone);

  return (
    <div className="notification-content" data-cy="periodCloseNotificationContent">
      <div className="d-flex flex-wrap align-items-baseline gap-2">
        <span className="notification-type-label">{copy(locale, 'Período cerrado', 'Period closed')}</span>
        <span className="fw-semibold text-break">{snapshot.budgetName}</span>
      </div>
      <div className="small mt-1">{summary}</div>
      {compact ? (
        <div className="small text-muted mt-1">
          {period} · {progress}
        </div>
      ) : (
        <>
          <dl className="notification-detail-list small mt-2 mb-0">
            <div>
              <dt>{actualLabel}</dt>
              <dd>{actual}</dd>
            </div>
            <div>
              <dt>{targetLabel}</dt>
              <dd>{target}</dd>
            </div>
            <div>
              <dt>{copy(locale, 'Avance', 'Progress')}</dt>
              <dd>
                {progress} {progressLabel}
              </dd>
            </div>
          </dl>
          <div className="small text-muted mt-2">
            {copy(locale, 'Periodo', 'Period')}: {period}
          </div>
        </>
      )}
      <div className="small text-muted mt-1">
        {copy(locale, 'Período finalizado el', 'Period ended on')} {formatEventDay(snapshot.logicalClosedAt, locale, snapshot.timeZone)}
      </div>
      {delayedCapture ? (
        <div className="small text-muted mt-1">
          {copy(locale, 'Resultado registrado el', 'Result recorded on')}{' '}
          {compact
            ? formatEventDay(snapshot.capturedAt, locale, snapshot.timeZone)
            : formatEventDate(snapshot.capturedAt, locale, snapshot.timeZone)}
        </div>
      ) : null}
    </div>
  );
};

const ThresholdContent = ({
  snapshot,
  locale,
  compact,
}: {
  snapshot: BudgetThresholdNotificationSnapshot;
  locale: string;
  compact: boolean;
}) => {
  const isExpense = snapshot.budgetType === 'EXPENSE';
  const amountLabel = isExpense
    ? copy(locale, 'Gasto registrado', 'Expense recorded')
    : copy(locale, 'Ingreso registrado', 'Income earned');
  const targetLabel = isExpense ? copy(locale, 'Límite', 'Limit') : copy(locale, 'Meta', 'Goal');
  const thresholds = snapshot.newlyCrossedPercentages.length
    ? snapshot.newlyCrossedPercentages.map(value => formatBudgetPercentage(value, locale)).join(', ')
    : copy(locale, 'Umbral registrado', 'Recorded threshold');
  return (
    <div className="notification-content" data-cy="thresholdNotificationContent">
      <div className="fw-semibold text-break">{snapshot.budgetName}</div>
      <div className="small text-muted">
        {isExpense ? copy(locale, 'Alerta de gasto', 'Expense alert') : copy(locale, 'Alerta de ingresos', 'Income alert')} · {thresholds}
      </div>
      {!compact && (
        <div className="small mt-1">
          <div>
            {amountLabel}: {formatBudgetAmount(snapshot.newAmount, snapshot.currency, locale)}
          </div>
          <div>
            {targetLabel}: {formatBudgetAmount(snapshot.targetAmount, snapshot.currency, locale)} ·{' '}
            {formatBudgetPercentage(snapshot.newProgressPercentage, locale)}
          </div>
          <div className="text-muted">
            {copy(locale, 'Periodo', 'Period')}: {dateRange(snapshot.periodStart, snapshot.periodEndExclusive, locale)}
          </div>
        </div>
      )}
      <div className="small text-muted mt-1">{formatEventDate(snapshot.eventAt, locale, snapshot.timeZone)}</div>
    </div>
  );
};

/** Shared, runtime-safe renderer for the bell preview and complete notification history. */
export const NotificationContent = ({
  notification,
  locale,
  compact = false,
}: {
  notification: BudgetNotification;
  locale: string;
  compact?: boolean;
}) => {
  const periodClose = getPeriodCloseNotificationSnapshot(notification);
  if (periodClose) return <PeriodCloseContent snapshot={periodClose} locale={locale} compact={compact} />;

  const threshold = getThresholdNotificationSnapshot(notification);
  if (threshold) return <ThresholdContent snapshot={threshold} locale={locale} compact={compact} />;

  return <span className="small text-muted">{unavailable(locale, notification.notificationType === 'PERIOD_CLOSED')}</span>;
};
