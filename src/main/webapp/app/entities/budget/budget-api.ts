import axios from 'axios';

export type BudgetType = 'EXPENSE' | 'INCOME';
export type BudgetOperationalStatus = 'SCHEDULED' | 'ACTIVE' | 'PAUSED' | 'COMPLETED';
export type BudgetScopeMode = 'ALL' | 'SELECTED' | 'NONE';
export type BudgetTagMatchMode = 'ANY' | 'ALL';

export type BudgetPeriod = { index: number; startInclusive: string; endExclusive: string };

export type BudgetEvaluation = {
  budgetId: number;
  budgetName: string;
  budgetType: BudgetType;
  currency: string;
  periodIndex: number;
  startInclusive: string;
  endExclusive: string;
  targetAmount: number;
  operationalStatus: BudgetOperationalStatus;
  periodTiming: 'HISTORICAL' | 'CURRENT' | 'FUTURE';
  evaluationApplicable: boolean;
  actualAmount: number | null;
  progressPercentage: number | null;
  remainingAmount: number | null;
  goalStatus: string;
  evaluationStatus: string;
  semanticAvailability: string | null;
  failureReason: string | null;
  message: string | null;
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

export type BudgetOverview = { referenceDate: string; timeZone: string; budgets: BudgetOverviewItem[] };

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
    targetAmount: number;
    accountScopeMode: 'ALL' | 'SELECTED';
    accountIds: number[];
    categoryScopeMode: 'ALL' | 'SELECTED';
    categoryIds: number[];
    tagScopeMode: 'NONE' | 'SELECTED';
    tagIds: number[];
    tagMatchMode: BudgetTagMatchMode;
  };
};

export type BudgetCreated = { id: number; name: string; type: BudgetType; currency: string };

export const getBudgetOverview = async (params: {
  referenceDate: string;
  timeZone: string;
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
