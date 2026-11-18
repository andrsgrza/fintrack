import axios from 'axios';

export type BudgetNotificationSnapshot = {
  budgetId: number;
  budgetName: string;
  budgetType: 'EXPENSE' | 'INCOME';
  currency: string;
  periodStart: string;
  periodEndExclusive: string;
  timeZone: string;
  eventAt: string;
  newAmount: number;
  newProgressPercentage: number;
  targetAmount: number;
  newlyCrossedPercentages: number[];
};

export type BudgetNotification = {
  id: number;
  budgetId: number;
  notificationType: string;
  snapshot: BudgetNotificationSnapshot | null;
  eventAt: string;
  materializedAt: string;
  readAt: string | null;
};

export type NotificationPage = {
  notifications: BudgetNotification[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export const getNotifications = async (params: { page: number; size: number; unreadOnly?: boolean }): Promise<NotificationPage> => {
  const response = await axios.get<NotificationPage>('api/notifications', { params });
  return response.data;
};

export const getUnreadNotificationCount = async (): Promise<number> => {
  const response = await axios.get<{ unreadCount: number }>('api/notifications/unread-count');
  return response.data.unreadCount;
};

export const markNotificationRead = async (notificationId: number): Promise<BudgetNotification> => {
  const response = await axios.patch<BudgetNotification>(`api/notifications/${notificationId}/read`);
  return response.data;
};

export const markAllNotificationsRead = async (): Promise<number> => {
  const response = await axios.post<{ markedCount: number }>('api/notifications/mark-all-read');
  return response.data.markedCount;
};
