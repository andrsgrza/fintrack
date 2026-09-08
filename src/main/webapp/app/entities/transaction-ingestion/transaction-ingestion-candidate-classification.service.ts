import axios from 'axios';

export interface IFileImportCandidateWorkflowSummary {
  id?: number;
  source?: string;
  status?: string;
  validationStatus?: string;
  classificationReviewStatus?: string;
  descriptionReviewStatus?: string;
  transactionDate?: string | null;
  postingDate?: string | null;
  description?: string | null;
  signedAmount?: number | string | null;
  amount?: number | string | null;
  flow?: string | null;
  currencySnapshot?: string | null;
  externalReference?: string | null;
  notes?: string | null;
  accountId?: number | null;
  accountName?: string | null;
  categoryId?: number | null;
  categoryName?: string | null;
  tagIds?: number[];
  tagNames?: string[];
  financialTransactionId?: number | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface IPrepareFileImportCandidateRow {
  ingestionRecordId?: number;
  recordIndex?: number;
  candidateId?: number;
  action?: string;
  error?: string | null;
}

export interface IPrepareFileImportCandidatesResponse {
  transactionIngestionId?: number;
  created?: number;
  updated?: number;
  unchanged?: number;
  skipped?: number;
  errors?: number;
  rows?: IPrepareFileImportCandidateRow[];
}

export interface IFileImportCandidateBatchRequest {
  candidateIds?: number[];
}

export interface IFileImportRuleMatch {
  ruleId?: number;
  ruleName?: string;
}

export interface IFileImportRuleOutputConflict {
  field?: string;
  reason?: string;
  ruleName?: string;
}

export interface IFileImportSkippedRuleOutput {
  field?: string;
  reason?: string;
  ruleName?: string;
}

export interface IFileImportCandidateCategorySuggestion {
  categoryId?: number;
  categoryName?: string;
  sourceRuleId?: number;
  sourceRuleName?: string;
  conflictsWithCurrentValue?: boolean;
  currentCategoryId?: number;
  currentCategoryName?: string;
  id?: number;
  name?: string;
  categoryType?: string;
}

export interface IFileImportCandidateTagSuggestion {
  tagId?: number;
  tagName?: string;
  sourceRuleId?: number;
  sourceRuleName?: string;
  alreadyPresent?: boolean;
  duplicateOfEarlierSuggestion?: boolean;
  id?: number;
  name?: string;
}

export interface IFileImportCandidateRulePreviewRow {
  candidateId?: number;
  ingestionRecordId?: number;
  recordIndex?: number;
  action?: string;
  error?: string | null;
  candidate?: IFileImportCandidateWorkflowSummary | null;
  suggestedCategory?: IFileImportCandidateCategorySuggestion | null;
  suggestedTags?: IFileImportCandidateTagSuggestion[];
  matchedRules?: IFileImportRuleMatch[];
  conflicts?: IFileImportRuleOutputConflict[];
  skippedOutputs?: IFileImportSkippedRuleOutput[];
  hasSuggestions?: boolean;
  hasConflicts?: boolean;
}

export interface IFileImportCandidateRulePreviewResponse {
  transactionIngestionId?: number;
  rows?: IFileImportCandidateRulePreviewRow[];
}

export interface IFileImportCandidateApplyRulesRow extends IFileImportCandidateRulePreviewRow {
  categoryApplied?: boolean;
  tagIdsApplied?: number[];
}

export interface IFileImportCandidateApplyRulesResponse {
  transactionIngestionId?: number;
  rows?: IFileImportCandidateApplyRulesRow[];
}

export interface IFileImportCandidateClassificationRequest {
  categoryId?: number | null;
  tagIds?: number[];
}

export interface IFileImportCandidateClassificationResponse {
  transactionIngestionId?: number;
  ingestionRecordId?: number;
  recordIndex?: number;
  candidate?: IFileImportCandidateWorkflowSummary | null;
}

const ingestionApiUrl = 'api/transaction-ingestions';

export const prepareFileImportCandidates = (ingestionId: number) =>
  axios.post<IPrepareFileImportCandidatesResponse>(`${ingestionApiUrl}/${ingestionId}/candidates/prepare`);

export const previewFileImportCandidateRules = (ingestionId: number, candidateIds?: number[]) =>
  axios.post<IFileImportCandidateRulePreviewResponse>(
    `${ingestionApiUrl}/${ingestionId}/candidates/rule-preview`,
    candidateIds?.length ? ({ candidateIds } satisfies IFileImportCandidateBatchRequest) : undefined,
  );

export const applyFileImportCandidateRules = (ingestionId: number, candidateIds?: number[]) =>
  axios.post<IFileImportCandidateApplyRulesResponse>(
    `${ingestionApiUrl}/${ingestionId}/candidates/apply-rules`,
    candidateIds?.length ? ({ candidateIds } satisfies IFileImportCandidateBatchRequest) : undefined,
  );

export const updateFileImportCandidateClassification = (
  ingestionId: number,
  candidateId: number,
  payload: IFileImportCandidateClassificationRequest,
) =>
  axios.patch<IFileImportCandidateClassificationResponse>(
    `${ingestionApiUrl}/${ingestionId}/candidates/${candidateId}/classification`,
    payload,
  );

export const confirmNoSuggestions = (ingestionId: number, candidateId: number) =>
  axios.post<IFileImportCandidateClassificationResponse>(
    `${ingestionApiUrl}/${ingestionId}/candidates/${candidateId}/confirm-no-suggestions`,
  );
