import React from 'react';

import { BudgetNotification } from './notification-api';
import { copy, dateRange, formatBudgetAmount } from '../budget/budget-utils';

const formatEventDate = (value: string, locale: string, timeZone?: string) =>
  new Intl.DateTimeFormat(locale.startsWith('es') ? 'es-MX' : 'en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: timeZone || 'UTC',
  }).format(new Date(value));

export const NotificationContent = ({
  notification,
  locale,
  compact = false,
}: {
  notification: BudgetNotification;
  locale: string;
  compact?: boolean;
}) => {
  const snapshot = notification.snapshot;
  if (!snapshot) {
    return (
      <span className="small text-muted">
        {copy(locale, 'Notificación histórica no disponible.', 'Historical notification is unavailable.')}
      </span>
    );
  }
  const isExpense = snapshot.budgetType === 'EXPENSE';
  const amountLabel = isExpense
    ? copy(locale, 'Gasto registrado', 'Expense recorded')
    : copy(locale, 'Ingreso registrado', 'Income earned');
  const targetLabel = isExpense ? copy(locale, 'Límite', 'Limit') : copy(locale, 'Meta', 'Goal');
  const thresholds = snapshot.newlyCrossedPercentages?.length
    ? snapshot.newlyCrossedPercentages.map(value => `${Number(value)}%`).join(', ')
    : copy(locale, 'Umbral registrado', 'Recorded threshold');
  return (
    <div className="notification-content">
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
            {Number(snapshot.newProgressPercentage).toFixed(2)}%
          </div>
          <div className="text-muted">
            {copy(locale, 'Periodo', 'Period')}: {dateRange(snapshot.periodStart, snapshot.periodEndExclusive, locale)}
          </div>
        </div>
      )}
      <div className="small text-muted mt-1">{formatEventDate(notification.eventAt, locale, snapshot.timeZone)}</div>
    </div>
  );
};
