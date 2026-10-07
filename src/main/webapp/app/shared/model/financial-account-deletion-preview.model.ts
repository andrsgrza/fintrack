export type FinancialAccountDeletionBlockerCode =
  | 'CROSS_ACCOUNT_TRANSFER'
  | 'BUDGET_SCOPE_WOULD_BROADEN'
  | 'RULE_ACCOUNT_CONDITION_REFERENCE'
  | 'CORRUPT_CANDIDATE'
  | 'CORRUPT_INGESTION_GRAPH'
  | 'UNSUPPORTED_API_IMPORT_CANDIDATE';

export interface IFinancialAccountDeletionBlocker {
  code: FinancialAccountDeletionBlockerCode;
  count: number;
  relatedIds: number[];
}

export interface IFinancialAccountDeletionPreviewCounts {
  financialTransactions: number;
  manualCandidates: number;
  manualDraftCandidates: number;
  manualPostedCandidates: number;
  transactionIngestions: number;
  ingestionRecords: number;
  fileImportCandidates: number;
  creditAccountDetails: number;
  budgetLinks: number;
  subscriptions: number;
  crossAccountTransfers: number;
  ruleAccountReferences: number;
}

export interface IFinancialAccountDeletionPreview {
  accountId: number;
  accountName: string;
  counts: IFinancialAccountDeletionPreviewCounts;
  blockers: IFinancialAccountDeletionBlocker[];
  canHardDelete: boolean;
}

export interface IFinancialAccountHardDeleteBlocked {
  code: 'ACCOUNT_HARD_DELETE_BLOCKED';
  blockers: IFinancialAccountDeletionBlocker[];
}
