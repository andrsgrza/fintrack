import React from 'react';
import { render, screen } from '@testing-library/react';

import { BudgetNotification } from './notification-api';
import { NotificationContent } from './notification-content';

const thresholdNotification = (budgetType: 'EXPENSE' | 'INCOME'): BudgetNotification => ({
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
    newAmount: '850.00',
    newProgressPercentage: '85.00',
    targetAmount: '1000.00',
    newlyCrossedPercentages: ['50.0000', '80.0000'],
  },
});

const periodCloseNotification = (budgetType: 'EXPENSE' | 'INCOME', overrides: Record<string, unknown> = {}): BudgetNotification => ({
  id: 4,
  budgetId: 9,
  notificationType: 'PERIOD_CLOSED',
  eventAt: '2026-12-01T06:00:00Z',
  materializedAt: '2026-12-01T06:00:01Z',
  readAt: null,
  snapshot: {
    budgetId: 9,
    budgetName: budgetType === 'EXPENSE' ? 'Supermercado muy importante' : 'Ingresos adicionales',
    budgetType,
    currency: 'MXN',
    periodStart: '2026-11-01',
    periodEndExclusive: '2026-12-01',
    timeZone: 'America/Mexico_City',
    revisionId: 45,
    targetAmount: budgetType === 'EXPENSE' ? '10000.00' : '20000.00',
    actualAmount: budgetType === 'EXPENSE' ? '8450.00' : '17500.00',
    progressPercentage: '84.50',
    evaluationStatus: 'EVALUATED',
    logicalClosedAt: '2026-12-01T06:00:00Z',
    capturedAt: '2026-12-01T06:00:05Z',
    logicalEventKey: 'PERIOD_CLOSED:9:2026-11-01',
    ...overrides,
  },
});

describe('NotificationContent', () => {
  it('renders immutable expense threshold values and every threshold without recomputing them', () => {
    render(<NotificationContent notification={thresholdNotification('EXPENSE')} locale="es" />);

    expect(screen.getByText('Comida')).toBeTruthy();
    expect(screen.getByText(/Alerta de gasto.*50\.00%, 80\.00%/)).toBeTruthy();
    expect(screen.getByText(/Gasto registrado:/)).toBeTruthy();
    expect(screen.getByText(/Límite:/)).toBeTruthy();
  });

  it('uses income language rather than treating a threshold as an expense', () => {
    render(<NotificationContent notification={thresholdNotification('INCOME')} locale="es" />);

    expect(screen.getByText(/Alerta de ingresos/)).toBeTruthy();
    expect(screen.getByText(/Ingreso registrado:/)).toBeTruthy();
    expect(screen.getByText(/Meta:/)).toBeTruthy();
  });

  it('renders an expense period close from the captured values, including an above-limit percentage', () => {
    render(
      <NotificationContent
        notification={periodCloseNotification('EXPENSE', { actualAmount: '12500.00', progressPercentage: '125.00' })}
        locale="es"
      />,
    );

    expect(screen.getByText('Período cerrado')).toBeTruthy();
    expect(screen.getByText('Supermercado muy importante')).toBeTruthy();
    expect(screen.getByText(/Registraste.*12,500.*límite.*10,000/)).toBeTruthy();
    expect(screen.getByText(/125\.00% del límite/)).toBeTruthy();
    expect(screen.getByText(/Período finalizado el/)).toBeTruthy();
  });

  it('uses income terminology and supports an authoritative zero-activity close', () => {
    render(
      <NotificationContent
        notification={periodCloseNotification('INCOME', { actualAmount: '0.00', progressPercentage: '0.00' })}
        locale="es"
      />,
    );

    expect(screen.getByText('Ingresos adicionales')).toBeTruthy();
    expect(screen.getByText('No se registraron ingresos durante este período.')).toBeTruthy();
    expect(screen.getByText('Ingresos registrados')).toBeTruthy();
    expect(screen.getByText('Avance').parentElement?.textContent).toContain('0%');
    expect(screen.getByText('Avance').parentElement?.textContent).toContain('de la meta');
  });

  it('distinguishes a delayed capture without changing the closed-period financial snapshot', () => {
    render(<NotificationContent notification={periodCloseNotification('EXPENSE', { capturedAt: '2026-12-03T18:15:00Z' })} locale="es" />);

    expect(screen.getByText(/Resultado registrado el/)).toBeTruthy();
    expect(screen.getAllByText(/8,450/)).toHaveLength(2);
  });

  it('uses a compact financial period-close summary and retains a meaningful delayed-capture date in the bell preview', () => {
    render(
      <NotificationContent notification={periodCloseNotification('EXPENSE', { capturedAt: '2026-12-03T18:15:00Z' })} locale="es" compact />,
    );

    expect(screen.getByText(/Registraste.*8,450.*límite.*10,000/)).toBeTruthy();
    expect(screen.getByText(/Resultado registrado el/)).toBeTruthy();
    expect(screen.queryByText('Gastos registrados')).toBeNull();
  });

  it('falls back safely rather than inventing an amount from an incomplete period-close snapshot', () => {
    render(<NotificationContent notification={periodCloseNotification('EXPENSE', { actualAmount: null })} locale="es" />);

    expect(screen.getByText('La información histórica del período cerrado no está disponible.')).toBeTruthy();
    expect(screen.queryByText(/Registraste/)).toBeNull();
  });
});
