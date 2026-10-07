import axios from 'axios';

import { IFinancialAccount } from 'app/shared/model/financial-account.model';
import {
  IFinancialAccountDeletionPreview,
  IFinancialAccountHardDeleteBlocked,
} from 'app/shared/model/financial-account-deletion-preview.model';

const apiUrl = 'api/financial-accounts';

export const getFinancialAccountForDeletion = (id: string | number) => axios.get<IFinancialAccount>(`${apiUrl}/${id}`);

export const getFinancialAccountDeletionPreview = (id: string | number) =>
  axios.get<IFinancialAccountDeletionPreview>(`${apiUrl}/${id}/deletion-preview`);

export const hardDeleteFinancialAccount = (id: string | number) =>
  axios.delete<void | IFinancialAccountHardDeleteBlocked>(`${apiUrl}/${id}/hard-delete`);
