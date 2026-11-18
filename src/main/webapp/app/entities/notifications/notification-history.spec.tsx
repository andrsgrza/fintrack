import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

import NotificationHistory from './notification-history';
import { getNotifications, markAllNotificationsRead, markNotificationRead } from './notification-api';

const mockRefreshUnread = jest.fn().mockResolvedValue(undefined);

jest.mock('app/config/store', () => ({ useAppSelector: () => 'es' }));
jest.mock('app/shared/notifications/notification-center', () => ({
  useNotificationCenter: () => ({ refreshUnreadCount: mockRefreshUnread }),
}));
jest.mock('./notification-api', () => ({
  getNotifications: jest.fn(),
  markNotificationRead: jest.fn(),
  markAllNotificationsRead: jest.fn(),
}));

const mockGetNotifications = getNotifications as jest.Mock;
const mockMarkRead = markNotificationRead as jest.Mock;
const mockMarkAll = markAllNotificationsRead as jest.Mock;

const notification = {
  id: 4,
  budgetId: 19,
  notificationType: 'THRESHOLD_CROSSED',
  eventAt: '2026-03-15T18:00:00Z',
  materializedAt: '2026-03-15T18:00:01Z',
  readAt: null,
  snapshot: {
    budgetId: 19,
    budgetName: 'Comida',
    budgetType: 'EXPENSE' as const,
    currency: 'MXN',
    periodStart: '2026-03-01',
    periodEndExclusive: '2026-04-01',
    timeZone: 'America/Mexico_City',
    eventAt: '2026-03-15T18:00:00Z',
    newAmount: 850,
    newProgressPercentage: 85,
    targetAmount: 1000,
    newlyCrossedPercentages: [80],
  },
};

const Location = () => (
  <span data-testid="location">
    {useLocation().pathname}
    {useLocation().search}
  </span>
);

const renderHistory = () =>
  render(
    <MemoryRouter initialEntries={['/notifications']}>
      <Routes>
        <Route path="/notifications" element={<NotificationHistory />} />
        <Route path="/budgets/:budgetId" element={<Location />} />
      </Routes>
    </MemoryRouter>,
  );

describe('NotificationHistory', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRefreshUnread.mockResolvedValue(undefined);
    mockGetNotifications.mockResolvedValue({ notifications: [notification], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('uses the server unread filter and navigates only after marking the immutable event read', async () => {
    mockMarkRead.mockResolvedValue({ ...notification, readAt: '2026-03-15T18:00:02Z' });
    renderHistory();

    expect(await screen.findByText('Comida')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'No leídas' }));
    await waitFor(() => expect(mockGetNotifications).toHaveBeenLastCalledWith({ page: 0, size: 20, unreadOnly: true }));
    await waitFor(() => expect(screen.queryByRole('status')).toBeNull());
    fireEvent.click(screen.getByRole('button', { name: 'Abrir presupuesto' }));
    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/budgets/19?referenceDate=2026-03-01'));
    expect(mockMarkRead).toHaveBeenCalledWith(4);
  });

  it('marks all through the server command rather than changing local read flags', async () => {
    renderHistory();
    await screen.findByText('Comida');
    await waitFor(() => expect(screen.queryByRole('status')).toBeNull());
    fireEvent.click(screen.getByRole('button', { name: 'Marcar todas como leídas' }));
    await waitFor(() => expect(mockMarkAll).toHaveBeenCalledTimes(1));
    expect(mockGetNotifications).toHaveBeenCalledTimes(2);
  });
});
