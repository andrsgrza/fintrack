import React, { ReactNode, createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';

import { getUnreadNotificationCount } from 'app/entities/notifications/notification-api';

type NotificationCenterValue = {
  unreadCount: number;
  loading: boolean;
  refreshUnreadCount: () => Promise<void>;
};

const NotificationCenterContext = createContext<NotificationCenterValue>({
  unreadCount: 0,
  loading: false,
  refreshUnreadCount: () => Promise.resolve(),
});

const POLL_INTERVAL_MS = 30_000;

/** One app-wide poller avoids stale counters and duplicate timers from individual Budget screens. */
export const NotificationCenterProvider = ({ isAuthenticated, children }: { isAuthenticated: boolean; children: ReactNode }) => {
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const requestInFlight = useRef<Promise<void> | null>(null);
  const active = useRef(true);

  const refreshUnreadCount = useCallback(async () => {
    if (!isAuthenticated) {
      setUnreadCount(0);
      return;
    }
    if (requestInFlight.current) return requestInFlight.current;
    setLoading(true);
    const request = getUnreadNotificationCount()
      .then(count => {
        if (active.current) setUnreadCount(count);
      })
      .catch(() => {
        // The bell remains usable; preview/history expose actionable API errors when opened.
      })
      .finally(() => {
        requestInFlight.current = null;
        if (active.current) setLoading(false);
      });
    requestInFlight.current = request;
    return request;
  }, [isAuthenticated]);

  useEffect(() => {
    active.current = true;
    if (!isAuthenticated) {
      setUnreadCount(0);
      return () => {
        active.current = false;
      };
    }
    const refreshWhenVisible = () => {
      if (document.visibilityState !== 'hidden') void refreshUnreadCount();
    };
    refreshWhenVisible();
    document.addEventListener('visibilitychange', refreshWhenVisible);
    const interval = window.setInterval(refreshWhenVisible, POLL_INTERVAL_MS);
    return () => {
      active.current = false;
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
    };
  }, [isAuthenticated, refreshUnreadCount]);

  return (
    <NotificationCenterContext.Provider value={{ unreadCount, loading, refreshUnreadCount }}>{children}</NotificationCenterContext.Provider>
  );
};

export const useNotificationCenter = () => useContext(NotificationCenterContext);
