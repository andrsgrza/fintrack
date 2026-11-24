import React from 'react';
import { act, render, screen } from '@testing-library/react';

import { NotificationCenterProvider, useNotificationCenter } from './notification-center';
import { getUnreadNotificationCount } from 'app/entities/notifications/notification-api';

jest.mock('app/entities/notifications/notification-api', () => ({ getUnreadNotificationCount: jest.fn() }));

const mockGetUnreadCount = getUnreadNotificationCount as jest.Mock;

const Probe = () => {
  const { unreadCount } = useNotificationCenter();
  return <output>{unreadCount}</output>;
};

describe('NotificationCenterProvider', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
    mockGetUnreadCount.mockResolvedValue(3);
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' });
  });

  afterEach(() => jest.useRealTimers());

  it('performs one authenticated initial refresh and then one shared 30-second visible-tab refresh', async () => {
    render(
      <NotificationCenterProvider isAuthenticated>
        <Probe />
      </NotificationCenterProvider>,
    );
    await act(() => Promise.resolve());
    expect(screen.getByText('3')).toBeTruthy();
    expect(mockGetUnreadCount).toHaveBeenCalledTimes(1);

    await act(() => {
      jest.advanceTimersByTime(30_000);
      return Promise.resolve();
    });
    expect(mockGetUnreadCount).toHaveBeenCalledTimes(2);
  });

  it('does not poll while hidden and refreshes immediately when the tab returns', async () => {
    render(
      <NotificationCenterProvider isAuthenticated>
        <Probe />
      </NotificationCenterProvider>,
    );
    await act(() => Promise.resolve());
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' });
    await act(() => {
      jest.advanceTimersByTime(30_000);
      return Promise.resolve();
    });
    expect(mockGetUnreadCount).toHaveBeenCalledTimes(1);

    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' });
    await act(() => {
      document.dispatchEvent(new Event('visibilitychange'));
      return Promise.resolve();
    });
    expect(mockGetUnreadCount).toHaveBeenCalledTimes(2);
  });
});
