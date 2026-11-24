import React, { useCallback, useEffect, useState } from 'react';
import { Button } from 'reactstrap';
import { useNavigate } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { useNotificationCenter } from 'app/shared/notifications/notification-center';
import { copy } from 'app/entities/budget/budget-utils';
import {
  BudgetNotification,
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  notificationPeriodStart,
  NotificationPage,
} from './notification-api';
import { NotificationContent } from './notification-content';

const PAGE_SIZE = 20;

const NotificationHistory = () => {
  const locale = useAppSelector(state => state.locale.currentLocale) || 'en';
  const navigate = useNavigate();
  const { refreshUnreadCount } = useNotificationCenter();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [pageNumber, setPageNumber] = useState(0);
  const [page, setPage] = useState<NotificationPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [updating, setUpdating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPage(await getNotifications({ page: pageNumber, size: PAGE_SIZE, unreadOnly }));
      await refreshUnreadCount();
    } catch {
      setError(copy(locale, 'No se pudieron cargar las notificaciones.', 'Notifications could not be loaded.'));
    } finally {
      setLoading(false);
    }
  }, [locale, pageNumber, refreshUnreadCount, unreadOnly]);

  useEffect(() => {
    void load();
  }, [load]);

  const openNotification = async (notification: BudgetNotification) => {
    setUpdating(true);
    setError(null);
    try {
      const updated = await markNotificationRead(notification.id);
      await refreshUnreadCount();
      const periodStart = notificationPeriodStart(updated);
      if (!periodStart) {
        setError(
          copy(
            locale,
            'La notificación no contiene el período histórico necesario para abrir el presupuesto.',
            'This notification does not contain the historical period needed to open the budget.',
          ),
        );
        return;
      }
      navigate(`/budgets/${updated.budgetId}?referenceDate=${periodStart}`);
    } catch {
      setError(
        copy(
          locale,
          'No se pudo marcar la notificación como leída. Inténtalo de nuevo.',
          'The notification could not be marked as read. Try again.',
        ),
      );
    } finally {
      setUpdating(false);
    }
  };

  const markAll = async () => {
    setUpdating(true);
    setError(null);
    try {
      await markAllNotificationsRead();
      await refreshUnreadCount();
      await load();
    } catch {
      setError(copy(locale, 'No se pudieron actualizar las notificaciones.', 'Notifications could not be updated.'));
    } finally {
      setUpdating(false);
    }
  };

  const changeFilter = (unread: boolean) => {
    setUnreadOnly(unread);
    setPageNumber(0);
  };

  return (
    <ProductPage wide>
      <ProductPageHeader
        title={copy(locale, 'Notificaciones', 'Notifications')}
        subtitle={copy(locale, 'Alertas históricas de tus presupuestos.', 'Historical alerts from your budgets.')}
      />
      <ProductSection title={copy(locale, 'Historial', 'History')}>
        <div className="d-flex flex-wrap gap-2 justify-content-between align-items-center mb-3">
          <div className="btn-group" role="group" aria-label={copy(locale, 'Filtro de notificaciones', 'Notification filter')}>
            <Button color={!unreadOnly ? 'primary' : 'secondary'} outline={unreadOnly} onClick={() => changeFilter(false)}>
              {copy(locale, 'Todas', 'All')}
            </Button>
            <Button color={unreadOnly ? 'primary' : 'secondary'} outline={!unreadOnly} onClick={() => changeFilter(true)}>
              {copy(locale, 'No leídas', 'Unread')}
            </Button>
          </div>
          <Button
            color="secondary"
            outline
            size="sm"
            disabled={updating || !page?.notifications.some(item => !item.readAt)}
            onClick={() => void markAll()}
          >
            {copy(locale, 'Marcar todas como leídas', 'Mark all as read')}
          </Button>
        </div>
        {loading ? <p role="status">{copy(locale, 'Cargando notificaciones…', 'Loading notifications…')}</p> : null}
        {error ? (
          <div className="alert alert-danger">
            <p className="mb-2">{error}</p>
            <Button size="sm" onClick={() => void load()}>
              {copy(locale, 'Reintentar', 'Retry')}
            </Button>
          </div>
        ) : null}
        {!loading && !error && page?.notifications.length === 0 ? (
          <p className="text-muted">
            {copy(locale, 'No hay notificaciones para este filtro.', 'There are no notifications for this filter.')}
          </p>
        ) : null}
        {!loading &&
          page?.notifications.map(notification => (
            <article
              key={notification.id}
              className={`border rounded p-3 mb-2 ${notification.readAt ? '' : 'notification-unread'}`}
              data-cy={`notification-${notification.id}`}
            >
              <div className="notification-history-item-body">
                <NotificationContent notification={notification} locale={locale} />
                <Button size="sm" color="primary" outline disabled={updating} onClick={() => void openNotification(notification)}>
                  {copy(locale, 'Abrir presupuesto', 'Open budget')}
                </Button>
              </div>
            </article>
          ))}
        {page && page.totalPages > 1 ? (
          <div className="d-flex justify-content-between align-items-center mt-3">
            <Button outline disabled={pageNumber === 0 || loading} onClick={() => setPageNumber(value => value - 1)}>
              {copy(locale, 'Anterior', 'Previous')}
            </Button>
            <span className="small">
              {pageNumber + 1} / {page.totalPages}
            </span>
            <Button outline disabled={pageNumber + 1 >= page.totalPages || loading} onClick={() => setPageNumber(value => value + 1)}>
              {copy(locale, 'Siguiente', 'Next')}
            </Button>
          </div>
        ) : null}
      </ProductSection>
    </ProductPage>
  );
};

export default NotificationHistory;
