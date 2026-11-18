import React from 'react';
import { render, screen } from '@testing-library/react';

import { BudgetNotification } from './notification-api';
import { NotificationContent } from './notification-content';

const notification = (budgetType: 'EXPENSE' | 'INCOME'): BudgetNotification => ({
  id: 3,
  budgetId: 8,
  notificationType: 'THRESHOLD_CROSSED',
  eventAt: '2026-03-15T18:00:00Z',
  materializedAt: '2026-03-15T18:00:01Z',
  readAt: null,
  snapshot: {
    budgetId: 8,
    budgetName: budgetType === 'EXPENSE' ? 'Comida' : 'Ingresos freelance',
    budgetType,
    currency: 'MXN',
    periodStart: '2026-03-01',
    periodEndExclusive: '2026-04-01',
    timeZone: 'America/Mexico_City',
    eventAt: '2026-03-15T18:00:00Z',
    newAmount: 850,
    newProgressPercentage: 85,
    targetAmount: 1000,
    newlyCrossedPercentages: [50, 80],
  },
});

describe('NotificationContent', () => {
  it('renders immutable expense snapshot values and every threshold without recomputing them', () => {
    render(<NotificationContent notification={notification('EXPENSE')} locale="es" />);

    expect(screen.getByText('Comida')).toBeTruthy();
    expect(screen.getByText(/Alerta de gasto.*50%, 80%/)).toBeTruthy();
    expect(screen.getByText(/Gasto registrado:/)).toBeTruthy();
    expect(screen.getByText(/Límite:/)).toBeTruthy();
  });

  it('uses income language rather than treating income as an expense', () => {
    render(<NotificationContent notification={notification('INCOME')} locale="es" />);

    expect(screen.getByText(/Alerta de ingresos/)).toBeTruthy();
    expect(screen.getByText(/Ingreso registrado:/)).toBeTruthy();
    expect(screen.getByText(/Meta:/)).toBeTruthy();
  });
});
