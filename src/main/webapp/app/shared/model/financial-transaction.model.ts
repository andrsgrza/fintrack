import dayjs from 'dayjs';
import { IFinancialAccount } from 'app/shared/model/financial-account.model';
import { ICategory } from 'app/shared/model/category.model';
import { IFinancialSubscription } from 'app/shared/model/financial-subscription.model';
import { ITransactionIngestion } from 'app/shared/model/transaction-ingestion.model';
import { ITag } from 'app/shared/model/tag.model';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { TransactionOrigin } from 'app/shared/model/enumerations/transaction-origin.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';

export interface IFinancialTransaction {
  id?: number;
  transactionDate?: dayjs.Dayjs;
  postingDate?: dayjs.Dayjs | null;
  description?: string;
  amount?: number;
  flow?: keyof typeof TransactionFlow;
  origin?: keyof typeof TransactionOrigin;
  externalReference?: string | null;
  notes?: string | null;
  createdAt?: dayjs.Dayjs;
  updatedAt?: dayjs.Dayjs;
  account?: IFinancialAccount;
  category?: ICategory | null;
  financialSubscription?: IFinancialSubscription | null;
  transactionIngestion?: ITransactionIngestion | null;
  tags?: ITag[] | null;
}

export interface IFinancialTransactionRulePreviewRequest {
  accountId?: number;
  description?: string;
  amount?: number;
  flow?: keyof typeof TransactionFlow;
  origin?: keyof typeof TransactionOrigin;
  transactionDate?: string;
  postingDate?: string | null;
  externalReference?: string | null;
  categoryId?: number | null;
  tagIds?: number[];
}

export interface ICategorySuggestion {
  categoryId?: number;
  categoryName?: string;
  sourceRuleId?: number;
  sourceRuleName?: string;
  conflictsWithCurrentValue?: boolean;
  currentCategoryId?: number;
  currentCategoryName?: string;
}

export interface ITagSuggestion {
  tagId?: number;
  tagName?: string;
  sourceRuleId?: number;
  sourceRuleName?: string;
  alreadyPresent?: boolean;
  duplicateOfEarlierSuggestion?: boolean;
}

export interface IRuleOutputConflict {
  field?: string;
  currentValueId?: number;
  currentValueLabel?: string;
  suggestedValueId?: number;
  suggestedValueLabel?: string;
  sourceRuleId?: number;
  sourceRuleName?: string;
  reason?: string;
}

export interface ISkippedRuleOutput {
  field?: string;
  sourceRuleId?: number;
  sourceRuleName?: string;
  reason?: string;
  valueId?: number;
  valueLabel?: string;
}

export interface IRuleMatchResult {
  ruleId?: number;
  ruleName?: string;
  priority?: number;
  conditionLogic?: keyof typeof RuleConditionLogic;
  proposedOutputs?: string[];
}

export interface IFinancialTransactionRulePreviewResponse {
  suggestedCategory?: ICategorySuggestion | null;
  suggestedTags?: ITagSuggestion[];
  conflicts?: IRuleOutputConflict[];
  skippedOutputs?: ISkippedRuleOutput[];
  matchedRules?: IRuleMatchResult[];
  hasSuggestions?: boolean;
  hasConflicts?: boolean;
}

export const defaultValue: Readonly<IFinancialTransaction> = {};
