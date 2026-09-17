import axios from 'axios';

import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';

const apiUrl = 'api/financial-accounts/selectable';

/**
 * Returns active, owner-scoped product accounts. `includeId` is only for rendering an existing historical reference
 * during editing; callers must not use it as a general inactive-account selector.
 */
export const getSelectableFinancialAccounts = async (includeId?: number | null): Promise<IFinancialAccountSelectable[]> => {
  const response = await axios.get<IFinancialAccountSelectable[]>(apiUrl, {
    params: includeId === undefined || includeId === null ? undefined : { includeId },
  });
  return response.data;
};
