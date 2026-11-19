import axios from 'axios';

export type BudgetNotificationType = 'THRESHOLD_CROSSED' | 'PERIOD_CLOSED';

export type BudgetThresholdNotificationSnapshot = {
  budgetId: number;
  budgetName: string;
  budgetType: 'EXPENSE' | 'INCOME';
  currency: string;
  periodStart: string;
  periodEndExclusive: string;
  timeZone: string;
  eventAt: string;
  newAmount: string;
  newProgressPercentage: string;
  targetAmount: string;
  newlyCrossedPercentages: string[];
};

export type BudgetPeriodCloseNotificationSnapshot = {
  budgetId: number;
  budgetName: string;
  budgetType: 'EXPENSE' | 'INCOME';
  currency: string;
  periodStart: string;
  periodEndExclusive: string;
  timeZone: string;
  revisionId: number;
  targetAmount: string;
  actualAmount: string;
  progressPercentage: string;
  evaluationStatus: string;
  logicalClosedAt: string;
  capturedAt: string;
  logicalEventKey: string;
};

export type BudgetNotificationSnapshot = BudgetThresholdNotificationSnapshot | BudgetPeriodCloseNotificationSnapshot;

export type BudgetNotification = {
  id: number;
  budgetId: number;
  /**
   * Kept as a string at the HTTP boundary: an older or malformed persisted record must not crash the whole
   * notification centre before the renderer can show its safe fallback.
   */
  notificationType: string;
  snapshot: unknown;
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

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null;

const isFiniteNumber = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value);
const isBudgetDecimal = (value: unknown): value is string => typeof value === 'string' && /^-?\d+(?:\.\d+)?$/.test(value);

const isLocalDate = (value: unknown): value is string => {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
};

const isInstant = (value: unknown): value is string => typeof value === 'string' && !Number.isNaN(Date.parse(value));

const isBudgetType = (value: unknown): value is 'EXPENSE' | 'INCOME' => value === 'EXPENSE' || value === 'INCOME';

const isCurrency = (value: unknown): value is string => typeof value === 'string' && /^[A-Z]{3}$/.test(value);

const isTimeZone = (value: unknown): value is string => {
  if (typeof value !== 'string' || !value) return false;
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: value }).format();
    return true;
  } catch {
    return false;
  }
};

const hasSharedBudgetSnapshotFields = (snapshot: Record<string, unknown>) =>
  isFiniteNumber(snapshot.budgetId) &&
  typeof snapshot.budgetName === 'string' &&
  isBudgetType(snapshot.budgetType) &&
  isCurrency(snapshot.currency) &&
  isLocalDate(snapshot.periodStart) &&
  isLocalDate(snapshot.periodEndExclusive) &&
  isTimeZone(snapshot.timeZone);

/** Runtime guard for immutable threshold snapshots returned as JSON by the REST API. */
export const getThresholdNotificationSnapshot = (notification: BudgetNotification): BudgetThresholdNotificationSnapshot | null => {
  if (notification.notificationType !== 'THRESHOLD_CROSSED' || !isRecord(notification.snapshot)) return null;
  const snapshot = notification.snapshot;
  if (
    !hasSharedBudgetSnapshotFields(snapshot) ||
    !isInstant(snapshot.eventAt) ||
    !isBudgetDecimal(snapshot.newAmount) ||
    !isBudgetDecimal(snapshot.newProgressPercentage) ||
    !isBudgetDecimal(snapshot.targetAmount) ||
    !Array.isArray(snapshot.newlyCrossedPercentages) ||
    !snapshot.newlyCrossedPercentages.every(isBudgetDecimal)
  ) {
    return null;
  }
  return snapshot as BudgetThresholdNotificationSnapshot;
};

/** Runtime guard for BUDGET-6C-C's immutable period-close snapshots. */
export const getPeriodCloseNotificationSnapshot = (notification: BudgetNotification): BudgetPeriodCloseNotificationSnapshot | null => {
  if (notification.notificationType !== 'PERIOD_CLOSED' || !isRecord(notification.snapshot)) return null;
  const snapshot = notification.snapshot;
  if (
    !hasSharedBudgetSnapshotFields(snapshot) ||
    !isFiniteNumber(snapshot.revisionId) ||
    !isBudgetDecimal(snapshot.targetAmount) ||
    !isBudgetDecimal(snapshot.actualAmount) ||
    !isBudgetDecimal(snapshot.progressPercentage) ||
    typeof snapshot.evaluationStatus !== 'string' ||
    !isInstant(snapshot.logicalClosedAt) ||
    !isInstant(snapshot.capturedAt) ||
    typeof snapshot.logicalEventKey !== 'string'
  ) {
    return null;
  }
  return snapshot as BudgetPeriodCloseNotificationSnapshot;
};

/** Only a validated immutable period can drive historical Budget navigation. */
export const notificationPeriodStart = (notification: BudgetNotification): string | null => {
  const threshold = getThresholdNotificationSnapshot(notification);
  if (threshold) return threshold.periodStart;
  const periodClose = getPeriodCloseNotificationSnapshot(notification);
  return periodClose?.periodStart ?? null;
};
