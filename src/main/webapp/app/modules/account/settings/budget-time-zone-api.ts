import axios from 'axios';

export type BudgetTimeZoneEffectiveChange = {
  budgetId: number;
  budgetName: string;
  effectivePeriodStart: string;
};

export type BudgetProfileTimeZone = {
  timeZone: string | null;
  configurationRequired: boolean;
  effectiveChanges: BudgetTimeZoneEffectiveChange[];
};

const apiUrl = 'api/account/budget-time-zone';

export const getBudgetProfileTimeZone = async (): Promise<BudgetProfileTimeZone> => {
  const response = await axios.get<BudgetProfileTimeZone>(apiUrl);
  return response.data;
};

/** Called only by the explicit confirmation action in the settings form. */
export const updateBudgetProfileTimeZone = async (timeZone: string): Promise<BudgetProfileTimeZone> => {
  const response = await axios.put<BudgetProfileTimeZone>(apiUrl, { timeZone });
  return response.data;
};
