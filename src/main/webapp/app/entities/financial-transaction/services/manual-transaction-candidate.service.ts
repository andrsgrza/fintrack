import axios from 'axios';

import { ITransactionCandidate } from 'app/shared/model/transaction-candidate.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';
import { TransactionCandidateClassificationReviewStatus } from 'app/shared/model/enumerations/transaction-candidate-classification-review-status.model';

const apiUrl = 'api/transaction-candidates';

export const createManualDraft = (payload: ITransactionCandidate) => axios.post<ITransactionCandidate>(`${apiUrl}/manual`, payload);

export const getManualDraft = (id: string | number) => axios.get<ITransactionCandidate>(`${apiUrl}/${id}`);

export const updateManualDraft = (id: string | number, payload: ITransactionCandidate) =>
  axios.patch<ITransactionCandidate>(`${apiUrl}/${id}/manual-draft`, payload, {
    headers: { 'Content-Type': 'application/merge-patch+json' },
  });

export const cancelManualDraft = (id: string | number) => axios.post<ITransactionCandidate>(`${apiUrl}/${id}/cancel`);

export const postManualDraft = (id: string | number) => axios.post<ITransactionCandidate>(`${apiUrl}/${id}/post`);

export interface IManualTransactionDraftSummary {
  id?: number;
  status?: string;
  classificationReviewStatus?: string;
  accountId?: number | null;
  accountName?: string | null;
  transactionDate?: string | null;
  description?: string | null;
  amount?: number | null;
  flow?: string | null;
  currencySnapshot?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  categoryName?: string | null;
  tagNames?: string[] | null;
}

export const getManualDrafts = () => axios.get<IManualTransactionDraftSummary[]>(`${apiUrl}/manual-drafts`);

export interface ITransactionCandidateCategorySuggestion {
  categoryId?: number;
  categoryName?: string;
  sourceRuleId?: number;
  sourceRuleName?: string;
  conflictsWithCurrentValue?: boolean;
  currentCategoryId?: number;
  currentCategoryName?: string;
}

export interface ITransactionCandidateTagSuggestion {
  tagId?: number;
  tagName?: string;
  sourceRuleId?: number;
  sourceRuleName?: string;
  alreadyPresent?: boolean;
  duplicateOfEarlierSuggestion?: boolean;
}

export interface ITransactionCandidateRuleOutputConflict {
  field?: string;
  currentValueId?: number;
  currentValueLabel?: string;
  suggestedValueId?: number;
  suggestedValueLabel?: string;
  sourceRuleId?: number;
  sourceRuleName?: string;
  reason?: string;
}

export interface ITransactionCandidateSkippedRuleOutput {
  field?: string;
  sourceRuleId?: number;
  sourceRuleName?: string;
  reason?: string;
  valueId?: number;
  valueLabel?: string;
}

export interface ITransactionCandidateRuleMatchResult {
  ruleId?: number;
  ruleName?: string;
  priority?: number;
  conditionLogic?: keyof typeof RuleConditionLogic;
  proposedOutputs?: string[];
}

export interface ITransactionCandidateRulePreviewResponse {
  candidateId?: number;
  candidateUpdatedAt?: string;
  classificationReviewStatus?: keyof typeof TransactionCandidateClassificationReviewStatus;
  suggestedCategory?: ITransactionCandidateCategorySuggestion | null;
  suggestedTags?: ITransactionCandidateTagSuggestion[];
  conflicts?: ITransactionCandidateRuleOutputConflict[];
  skippedOutputs?: ITransactionCandidateSkippedRuleOutput[];
  matchedRules?: ITransactionCandidateRuleMatchResult[];
  hasSuggestions?: boolean;
  hasConflicts?: boolean;
}

export interface ITransactionCandidateRuleApplyResponse {
  candidate?: ITransactionCandidate;
  evaluation?: ITransactionCandidateRulePreviewResponse;
  categoryApplied?: boolean;
  tagIdsApplied?: number[];
}

export const previewManualDraftRules = (id: string | number) =>
  axios.post<ITransactionCandidateRulePreviewResponse>(`${apiUrl}/${id}/rule-preview`);

export const applyManualDraftRules = (id: string | number) =>
  axios.post<ITransactionCandidateRuleApplyResponse>(`${apiUrl}/${id}/apply-rules`);
