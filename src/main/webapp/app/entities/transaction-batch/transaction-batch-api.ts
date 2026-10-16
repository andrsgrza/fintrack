import axios from 'axios';

export type BatchTag = { id?: number; name?: string; color?: string | null; active?: boolean | null; source?: string };
export type BatchCandidate = {
  id: number;
  version: number;
  rowPosition: number;
  source: 'MANUAL' | 'FILE_IMPORT';
  validationStatus: string;
  transactionDate?: string | null;
  postingDate?: string | null;
  description?: string | null;
  /** Immutable temporary CSV baseline; absent for manual rows. */
  sourceDescription?: string | null;
  externalReference?: string | null;
  notes?: string | null;
  signedAmount?: number | null;
  flow?: string | null;
  categoryId?: number | null;
  categoryName?: string | null;
  categoryColor?: string | null;
  categoryActive?: boolean | null;
  tags: BatchTag[];
  inputIssues: { field: string; reasonCode: string; submittedValue?: string | null }[];
};

export type BatchRulePreview = {
  candidateId: number;
  candidateVersion: number;
  descriptionEvaluable: boolean;
  descriptionMatched: boolean;
  suggestedDescription?: string | null;
  classificationEvaluable: boolean;
  suggestedCategoryId?: number | null;
  suggestedTagIds: number[];
  eligibleTagIds: number[];
};

export type BatchRuleSettings = Pick<
  TransactionBatchWorkspace,
  'autoApplyDescriptions' | 'autoApplyCategories' | 'autoApplyTags' | 'protectManualChanges'
>;

export type TransactionBatchWorkspace = {
  id: number;
  version: number;
  status: 'OPEN' | 'PUBLISHED';
  accountId: number;
  accountName: string;
  autoApplyDescriptions: boolean;
  autoApplyCategories: boolean;
  autoApplyTags: boolean;
  protectManualChanges: boolean;
  updatedAt: string;
  candidates: BatchCandidate[];
};

export type BatchReceipt = {
  batchId: number;
  status: 'PUBLISHED';
  operationKey: string;
  publishedAt: string;
  publishedTransactionCount: number;
  receiptExpiresAt: string;
  replayed: boolean;
};

/** Every row command returns the server-authoritative row and workspace versions. */
export type BatchRowMutation = {
  candidate: BatchCandidate;
  batchVersion: number;
};

const apiUrl = 'api/transaction-batches';

export const listBatches = () => axios.get<TransactionBatchWorkspace[]>(apiUrl).then(response => response.data);
export const getBatch = (id: string | number) => axios.get<TransactionBatchWorkspace>(`${apiUrl}/${id}`).then(response => response.data);
export const createBatch = (accountId: number) =>
  axios.post<TransactionBatchWorkspace>(apiUrl, { accountId }).then(response => response.data);
export const addManualRow = (id: number, expectedBatchVersion: number, values = {}) =>
  axios.post<BatchRowMutation>(`${apiUrl}/${id}/candidates`, { expectedBatchVersion, ...values }).then(response => response.data);
export const patchRow = (batchId: number, candidateId: number, values: object) =>
  axios.patch<BatchRowMutation>(`${apiUrl}/${batchId}/candidates/${candidateId}`, values).then(response => response.data);
export const setRowCategory = (batchId: number, candidateId: number, expectedCandidateVersion: number, categoryId?: number) =>
  axios
    .put<BatchRowMutation>(`${apiUrl}/${batchId}/candidates/${candidateId}/category`, { expectedCandidateVersion, categoryId })
    .then(response => response.data);
export const replaceRowTags = (batchId: number, candidateId: number, expectedCandidateVersion: number, tagIds: number[]) =>
  axios
    .put<BatchRowMutation>(`${apiUrl}/${batchId}/candidates/${candidateId}/tags`, { expectedCandidateVersion, tagIds })
    .then(response => response.data);
export const appendCsv = (batchId: number, expectedBatchVersion: number, operationKey: string, file: File) => {
  const body = new FormData();
  body.append('expectedBatchVersion', String(expectedBatchVersion));
  body.append('operationKey', operationKey);
  body.append('file', file);
  return axios.post(`${apiUrl}/${batchId}/append-csv`, body).then(response => response.data);
};
export const saveSettings = (batchId: number, settings: object) =>
  axios.put(`${apiUrl}/${batchId}/settings`, settings).then(response => response.data);
export const previewRules = (batchId: number) =>
  axios.get<BatchRulePreview[]>(`${apiUrl}/${batchId}/rule-preview`).then(response => response.data);
export const applyRules = (batchId: number, candidateId: number, command: object) =>
  axios.post(`${apiUrl}/${batchId}/candidates/${candidateId}/apply-rules`, command).then(response => response.data);
export const reevaluate = (batchId: number) => axios.post(`${apiUrl}/${batchId}/reevaluate`).then(response => response.data);
export const reevaluateRow = (batchId: number, candidateId: number) =>
  axios.post(`${apiUrl}/${batchId}/candidates/${candidateId}/reevaluate`).then(response => response.data);
export const publishBatch = (batchId: number, expectedBatchVersion: number, operationKey: string) =>
  axios.post<BatchReceipt>(`${apiUrl}/${batchId}/publish`, { expectedBatchVersion, operationKey }).then(response => response.data);
export const getReceipt = (batchId: number) => axios.get<BatchReceipt>(`${apiUrl}/${batchId}/receipt`).then(response => response.data);
export const deleteRow = (batchId: number, candidateId: number, expectedCandidateVersion: number) =>
  axios.delete(`${apiUrl}/${batchId}/candidates/${candidateId}`, { params: { expectedCandidateVersion } });
export const discardBatch = (batchId: number, expectedBatchVersion: number) =>
  axios.delete(`${apiUrl}/${batchId}`, { params: { expectedBatchVersion } });
