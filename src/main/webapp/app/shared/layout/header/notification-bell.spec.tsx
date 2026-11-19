import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

import NotificationBell from './notification-bell';
import { getNotifications, markNotificationRead } from 'app/entities/notifications/notification-api';

const mockRefreshUnread = jest.fn().mockResolvedValue(undefined);

jest.mock('app/config/store', () => ({ useAppSelector: () => 'es' }));
jest.mock('app/shared/notifications/notification-center', () => ({
  useNotificationCenter: () => ({ unreadCount: 1, refreshUnreadCount: mockRefreshUnread }),
}));
jest.mock('app/entities/notifications/notification-api', () => ({
  ...jest.requireActual('app/entities/notifications/notification-api'),
  getNotifications: jest.fn(),
  markAllNotificationsRead: jest.fn(),
  markNotificationRead: jest.fn(),
}));

const mockGetNotifications = getNotifications as jest.Mock;
const mockMarkRead = markNotificationRead as jest.Mock;

const periodCloseNotification = {
  id: 32,
  budgetId: 14,
  notificationType: 'PERIOD_CLOSED',
  eventAt: '2026-12-01T06:00:00Z',
  materializedAt: '2026-12-01T06:00:02Z',
  readAt: null,
  snapshot: {
    budgetId: 14,
    budgetName: 'Cierre desde campana',
    budgetType: 'EXPENSE' as const,
    currency: 'MXN',
    periodStart: '2026-11-01',
    periodEndExclusive: '2026-12-01',
    timeZone: 'America/Mexico_City',
    revisionId: 9,
    targetAmount: '100.00',
    actualAmount: '60.00',
    progressPercentage: '60.00',
    evaluationStatus: 'EVALUATED',
    logicalClosedAt: '2026-12-01T06:00:00Z',
    capturedAt: '2026-12-01T06:00:02Z',
    logicalEventKey: 'PERIOD_CLOSED:14:2026-11-01',
  },
};

const Location = () => <span data-testid="location">{`${useLocation().pathname}${useLocation().search}`}</span>;

describe('NotificationBell', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGetNotifications.mockResolvedValue({ notifications: [periodCloseNotification], page: 0, size: 6, totalElements: 1, totalPages: 1 });
    mockMarkRead.mockResolvedValue({ ...periodCloseNotification, readAt: '2026-12-02T12:00:00Z' });
  });

  it('shows a period close without reading it, then reads and navigates to its immutable period on activation', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<NotificationBell />} />
          <Route path="/budgets/:budgetId" element={<Location />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByLabelText('Notificaciones'));
    expect(await screen.findByText('Período cerrado')).toBeTruthy();
    expect(screen.getByText(/Registraste.*60.*límite.*100/)).toBeTruthy();
    expect(mockMarkRead).not.toHaveBeenCalled();

    const item = screen.getByText('Cierre desde campana').closest('button');
    expect(item).not.toBeNull();
    if (!item) throw new Error('The period-close preview item was not rendered as a button.');
    fireEvent.click(item);

    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/budgets/14?referenceDate=2026-11-01'));
    expect(mockMarkRead).toHaveBeenCalledWith(32);
  });
});
