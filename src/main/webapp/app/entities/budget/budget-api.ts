import axios from 'axios';

export type BudgetType = 'EXPENSE' | 'INCOME';
export type BudgetOperationalStatus = 'SCHEDULED' | 'ACTIVE' | 'PAUSED' | 'COMPLETED';
export type BudgetScopeMode = 'ALL' | 'SELECTED' | 'NONE';
export type BudgetTagMatchMode = 'ANY' | 'ALL';
/** Exact plain decimal transmitted by the Budget HTTP contract; never coerce monetary values through Number. */
export type BudgetDecimal = string;

export type BudgetPeriod = { index: number; startInclusive: string; endExclusive: string };

export type BudgetEvaluation = {
  budgetId: number;
  budgetName: string;
  budgetType: BudgetType;
  currency: string;
  periodIndex: number;
  startInclusive: string;
  endExclusive: string;
  targetAmount: BudgetDecimal;
  operationalStatus: BudgetOperationalStatus;
  periodTiming: 'HISTORICAL' | 'CURRENT' | 'FUTURE';
  evaluationApplicable: boolean;
  actualAmount: BudgetDecimal | null;
  progressPercentage: BudgetDecimal | null;
  remainingAmount: BudgetDecimal | null;
  goalStatus: string;
  evaluationStatus: string;
  semanticAvailability: string | null;
  failureReason: string | null;
  message: string | null;
};

/** Immutable configuration resolved by the backend for one historical Budget period. */
export type BudgetRevision = {
  id: number;
  number: number;
  recordedAt: string;
  targetAmount: BudgetDecimal;
  intervalUnit: 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR';
  intervalCount: number;
  alignment: 'ANCHORED' | 'NATURAL' | 'CUSTOM';
  anchorDate: string;
  customDayOfMonth: number | null;
  recurrenceMode: 'ONCE' | 'RECURRING';
  termination: 'NONE' | 'AFTER_N_PERIODS' | 'END_DATE';
  terminationPeriodCount: number | null;
  recurrenceEndDate: string | null;
  accountScopeMode: 'ALL' | 'SELECTED';
  accountIds: number[];
  categoryScopeMode: 'ALL' | 'SELECTED';
  categoryIds: number[];
  tagScopeMode: 'NONE' | 'SELECTED';
  tagIds: number[];
  tagMatchMode: BudgetTagMatchMode;
};

export type BudgetEvaluationHistory = {
  firstPeriodIndex: number;
  size: number;
  evaluations: BudgetEvaluation[];
};

/** A server-authoritative calendar period and its operational availability. */
export type BudgetOperationalPeriod = {
  index: number;
  startInclusive: string;
  endExclusive: string;
  effectiveRevisionId: number;
  status: BudgetOperationalStatus;
  evaluationEligible: boolean;
};

/** Immutable schedule audit entry.  An inactive entry remains in history after cancellation/replacement. */
export type BudgetRevisionSchedule = {
  id: number;
  operation: 'SCHEDULED' | 'CANCELLED';
  revisionId: number | null;
  targetScheduleId: number | null;
  effectivePeriodStart: string;
  sequenceNumber: number;
  recordedAt: string;
  active: boolean;
};

export type BudgetHistory = {
  budgetId: number;
  version: number;
  revisions: BudgetRevision[];
  schedules: BudgetRevisionSchedule[];
  lifecycleEvents: Array<{
    id: number;
    type: string;
    targetEventId: number | null;
    effectivePeriodStart: string;
    sequenceNumber: number;
    recordedAt: string;
  }>;
};

export type BudgetLifecycleCommandPayload = {
  expectedVersion: number;
  clientRequestId: string;
  timeZone: string;
};

export type BudgetOverviewItem = {
  budgetId: number;
  name: string;
  description: string | null;
  type: BudgetType;
  currency: string;
  operationalStatus: BudgetOperationalStatus;
  hasPeriodAtReferenceDate: boolean;
  referenceMessage: string | null;
  evaluation: BudgetEvaluation | null;
};

export type BudgetOverview = { referenceDate: string | null; timeZone: string | null; budgets: BudgetOverviewItem[] };

export type CalendarPreviewDraft = {
  timeZone: string;
  intervalCount: number;
  intervalUnit: string;
  alignment: string;
  anchorDate: string;
  customDayOfMonth: number | null;
  recurrenceMode: string;
  termination: string;
  terminationPeriodCount: number | null;
  recurrenceEndDate: string | null;
};

export type BudgetCreatePayload = {
  name: string;
  description: string | null;
  type: BudgetType;
  currency: string;
  clientRequestId: string;
  timeZone: string;
  initialRevision: Omit<CalendarPreviewDraft, 'timeZone'> & {
    targetAmount: BudgetDecimal;
    accountScopeMode: 'ALL' | 'SELECTED';
    accountIds: number[];
    categoryScopeMode: 'ALL' | 'SELECTED';
    categoryIds: number[];
    tagScopeMode: 'NONE' | 'SELECTED';
    tagIds: number[];
    tagMatchMode: BudgetTagMatchMode;
  };
  notificationPreferences?: BudgetNotificationPreferencesPayload;
};

/** Mutable delivery settings; they never alter the saved financial revision or semantic calculation. */
export type BudgetNotificationPreferencesPayload = {
  percentageAlertsEnabled: boolean;
  /** Exact plain-decimal percentages. Never coerce these values through JavaScript Number. */
  thresholdPercentages: BudgetDecimal[];
  periodCloseEnabled: boolean;
  expectedVersion?: number | null;
};

export type BudgetNotificationPreferences = {
  budgetId: number;
  configured: boolean;
  version: number | null;
  percentageAlertsEnabled: boolean;
  thresholdPercentages: BudgetDecimal[];
  periodCloseEnabled: boolean;
  periodCloseEnabledSince: string | null;
  updatedAt: string | null;
};

export type BudgetCreated = { id: number; name: string; type: BudgetType; currency: string };

export type BudgetRevisionConfiguration = Omit<BudgetCreatePayload['initialRevision'], 'targetAmount'> & {
  targetAmount: BudgetDecimal;
};

export type BudgetRevisionSchedulePayload = {
  expectedVersion: number;
  clientRequestId: string;
  timeZone: string;
  applyToCurrentPeriod: boolean;
  effectivePeriodStart: string | null;
  revision: BudgetRevisionConfiguration;
};

export type BudgetMutationResult = {
  budgetId: number;
  version: number;
  revisionId: number | null;
  revisionScheduleId: number | null;
  lifecycleEventId: number | null;
  effectivePeriodStart: string | null;
};

export const getBudgetOverview = async (params: {
  referenceDate?: string;
  type?: BudgetType | '';
  status?: BudgetOperationalStatus | '';
}): Promise<BudgetOverview> => {
  const response = await axios.get<BudgetOverview>('api/budgets/overview', { params });
  return response.data;
};

export const previewBudgetCalendar = async (
  draft: CalendarPreviewDraft,
  signal?: AbortSignal,
): Promise<{ localToday: string; periods: BudgetPeriod[] }> => {
  const response = await axios.post<{ localToday: string; periods: BudgetPeriod[] }>('api/budgets/calendar-preview', draft, { signal });
  return response.data;
};

export const createBudget = async (payload: BudgetCreatePayload): Promise<BudgetCreated> => {
  const response = await axios.post<BudgetCreated>('api/budgets', payload);
  return response.data;
};

export const getBudgetNotificationPreferences = async (budgetId: number): Promise<BudgetNotificationPreferences> => {
  const response = await axios.get<BudgetNotificationPreferences>(`api/budgets/${budgetId}/notification-preferences`);
  return response.data;
};

export const updateBudgetNotificationPreferences = async (
  budgetId: number,
  payload: BudgetNotificationPreferencesPayload,
): Promise<BudgetNotificationPreferences> => {
  const response = await axios.put<BudgetNotificationPreferences>(`api/budgets/${budgetId}/notification-preferences`, payload);
  return response.data;
};

export const getBudgetEvaluationHistory = async (
  budgetId: number,
  params: { firstPeriodIndex: number; size: number },
): Promise<BudgetEvaluationHistory> => {
  const response = await axios.get<BudgetEvaluationHistory>(`api/budgets/${budgetId}/evaluations`, { params });
  return response.data;
};

export const getBudgetEffectiveRevision = async (budgetId: number, periodStart: string): Promise<BudgetRevision> => {
  const response = await axios.get<BudgetRevision>(`api/budgets/${budgetId}/effective-revision`, { params: { periodStart } });
  return response.data;
};

export const getBudgetHistory = async (budgetId: number): Promise<BudgetHistory> => {
  const response = await axios.get<BudgetHistory>(`api/budgets/${budgetId}/history`);
  return response.data;
};

export const getBudgetCurrentEvaluation = async (budgetId: number): Promise<BudgetEvaluation> => {
  const response = await axios.get<BudgetEvaluation>(`api/budgets/${budgetId}/evaluation/current`);
  return response.data;
};

/** Resolves a known budget period without treating a future scheduled period as current. */
export const getBudgetEvaluationAtDate = async (budgetId: number, referenceDate: string): Promise<BudgetEvaluation> => {
  const response = await axios.get<BudgetEvaluation>(`api/budgets/${budgetId}/evaluation/at`, { params: { referenceDate } });
  return response.data;
};

export const getBudgetOperationalPeriods = async (
  budgetId: number,
  params: { firstIndex: number; lastIndex: number },
): Promise<BudgetOperationalPeriod[]> => {
  const response = await axios.get<BudgetOperationalPeriod[]>(`api/budgets/${budgetId}/periods`, { params });
  return response.data;
};

export const updateBudgetMetadata = async (
  budgetId: number,
  payload: { expectedVersion: number; name: string; description: string | null },
): Promise<BudgetMutationResult> => {
  const response = await axios.put<BudgetMutationResult>(`api/budgets/${budgetId}/metadata`, payload);
  return response.data;
};

export const scheduleBudgetRevision = async (budgetId: number, payload: BudgetRevisionSchedulePayload): Promise<BudgetMutationResult> => {
  const response = await axios.post<BudgetMutationResult>(`api/budgets/${budgetId}/revisions`, payload);
  return response.data;
};

export const replaceBudgetRevision = async (
  budgetId: number,
  scheduleId: number,
  payload: BudgetRevisionSchedulePayload,
): Promise<BudgetMutationResult> => {
  const response = await axios.post<BudgetMutationResult>(`api/budgets/${budgetId}/revisions/${scheduleId}/replace`, payload);
  return response.data;
};

export const cancelBudgetRevision = async (
  budgetId: number,
  scheduleId: number,
  payload: { expectedVersion: number; clientRequestId: string; timeZone: string },
): Promise<BudgetMutationResult> => {
  const response = await axios.post<BudgetMutationResult>(`api/budgets/${budgetId}/revisions/${scheduleId}/cancel`, payload);
  return response.data;
};

export const requestBudgetPause = async (budgetId: number, payload: BudgetLifecycleCommandPayload): Promise<BudgetMutationResult> => {
  const response = await axios.post<BudgetMutationResult>(`api/budgets/${budgetId}/pause`, payload);
  return response.data;
};

export const cancelBudgetPause = async (
  budgetId: number,
  eventId: number,
  payload: BudgetLifecycleCommandPayload,
): Promise<BudgetMutationResult> => {
  const response = await axios.post<BudgetMutationResult>(`api/budgets/${budgetId}/pause/${eventId}/cancel`, payload);
  return response.data;
};

export const requestBudgetResume = async (budgetId: number, payload: BudgetLifecycleCommandPayload): Promise<BudgetMutationResult> => {
  const response = await axios.post<BudgetMutationResult>(`api/budgets/${budgetId}/resume`, payload);
  return response.data;
};

export const requestBudgetFinish = async (budgetId: number, payload: BudgetLifecycleCommandPayload): Promise<BudgetMutationResult> => {
  const response = await axios.post<BudgetMutationResult>(`api/budgets/${budgetId}/finish`, payload);
  return response.data;
};

export const deleteBudget = async (budgetId: number, expectedVersion: number): Promise<void> => {
  await axios.delete(`api/budgets/${budgetId}`, { data: { expectedVersion } });
};
