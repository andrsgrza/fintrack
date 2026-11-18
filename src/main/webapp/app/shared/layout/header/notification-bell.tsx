import React, { useEffect, useState } from 'react';
import { Dropdown, DropdownItem, DropdownMenu, DropdownToggle, Spinner } from 'reactstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { useNavigate } from 'react-router-dom';

import {
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  BudgetNotification,
} from 'app/entities/notifications/notification-api';
import { NotificationContent } from 'app/entities/notifications/notification-content';
import { useNotificationCenter } from 'app/shared/notifications/notification-center';
import { useAppSelector } from 'app/config/store';
import { copy } from 'app/entities/budget/budget-utils';

const PREVIEW_SIZE = 6;

const NotificationBell = () => {
  const locale = useAppSelector(state => state.locale.currentLocale) || 'en';
  const navigate = useNavigate();
  const { unreadCount, refreshUnreadCount } = useNotificationCenter();
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<BudgetNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [updating, setUpdating] = useState(false);

  const loadPreview = async () => {
    setLoading(true);
    setError(false);
    try {
      const page = await getNotifications({ page: 0, size: PREVIEW_SIZE });
      setNotifications(page.notifications);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open) {
      void refreshUnreadCount();
      void loadPreview();
    }
  }, [open]); // Opening is intentionally not a read action.

  const openNotification = async (notification: BudgetNotification) => {
    setUpdating(true);
    try {
      const updated = await markNotificationRead(notification.id);
      setNotifications(items => items.map(item => (item.id === updated.id ? updated : item)));
      await refreshUnreadCount();
      const date = updated.snapshot?.periodStart;
      navigate(`/budgets/${updated.budgetId}${date ? `?referenceDate=${date}` : ''}`);
      setOpen(false);
    } catch {
      setError(true);
    } finally {
      setUpdating(false);
    }
  };

  const markAll = async () => {
    setUpdating(true);
    try {
      await markAllNotificationsRead();
      await refreshUnreadCount();
      await loadPreview();
    } catch {
      setError(true);
    } finally {
      setUpdating(false);
    }
  };

  return (
    <Dropdown nav isOpen={open} toggle={() => setOpen(value => !value)} data-cy="notificationBell">
      <DropdownToggle
        nav
        className="position-relative"
        aria-label={copy(locale, 'Notificaciones', 'Notifications')}
        title={copy(locale, 'Notificaciones', 'Notifications')}
      >
        <FontAwesomeIcon icon="bell" />
        {unreadCount > 0 ? (
          <span
            className="notification-badge"
            aria-label={`${unreadCount} ${copy(locale, 'notificaciones no leídas', 'unread notifications')}`}
          >
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        ) : null}
      </DropdownToggle>
      <DropdownMenu
        end
        className="notification-preview shadow"
        aria-label={copy(locale, 'Notificaciones recientes', 'Recent notifications')}
      >
        <div className="d-flex align-items-center justify-content-between px-3 py-2 border-bottom gap-2">
          <strong>{copy(locale, 'Notificaciones', 'Notifications')}</strong>
          <button className="btn btn-link btn-sm p-0" type="button" onClick={() => void markAll()} disabled={updating || unreadCount === 0}>
            {copy(locale, 'Marcar todas como leídas', 'Mark all as read')}
          </button>
        </div>
        {loading ? (
          <div className="p-3 text-center">
            <Spinner size="sm" />
          </div>
        ) : null}
        {error ? (
          <div className="p-3 small text-danger">
            {copy(locale, 'No se pudieron cargar las notificaciones.', 'Notifications could not be loaded.')}
          </div>
        ) : null}
        {!loading && !error && notifications.length === 0 ? (
          <div className="p-3 small text-muted">{copy(locale, 'No tienes notificaciones.', 'You have no notifications.')}</div>
        ) : null}
        {!loading &&
          notifications.map(notification => (
            <DropdownItem
              key={notification.id}
              tag="button"
              className={`notification-preview-item ${notification.readAt ? '' : 'notification-unread'}`}
              onClick={() => void openNotification(notification)}
              disabled={updating}
            >
              <NotificationContent notification={notification} locale={locale} compact />
            </DropdownItem>
          ))}
        <div className="border-top px-3 py-2">
          <button
            className="btn btn-link btn-sm p-0"
            type="button"
            onClick={() => {
              setOpen(false);
              navigate('/notifications');
            }}
          >
            {copy(locale, 'Ver todas', 'View all')}
          </button>
        </div>
      </DropdownMenu>
    </Dropdown>
  );
};

export default NotificationBell;
