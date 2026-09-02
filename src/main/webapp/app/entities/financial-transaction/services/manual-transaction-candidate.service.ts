import axios from 'axios';

import { ITransactionCandidate } from 'app/shared/model/transaction-candidate.model';

const apiUrl = 'api/transaction-candidates';

export const createManualDraft = (payload: ITransactionCandidate) => axios.post<ITransactionCandidate>(`${apiUrl}/manual`, payload);

export const getManualDraft = (id: string | number) => axios.get<ITransactionCandidate>(`${apiUrl}/${id}`);

export const updateManualDraft = (id: string | number, payload: ITransactionCandidate) =>
  axios.patch<ITransactionCandidate>(`${apiUrl}/${id}/manual-draft`, payload, {
    headers: { 'Content-Type': 'application/merge-patch+json' },
  });

export const cancelManualDraft = (id: string | number) => axios.post<ITransactionCandidate>(`${apiUrl}/${id}/cancel`);

export const postManualDraft = (id: string | number) => axios.post<ITransactionCandidate>(`${apiUrl}/${id}/post`);
