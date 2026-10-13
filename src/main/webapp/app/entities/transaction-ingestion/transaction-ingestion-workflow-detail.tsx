import React, { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Badge,
  Button,
  Col,
  DropdownItem,
  DropdownMenu,
  DropdownToggle,
  Form,
  FormGroup,
  Input,
  Label,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Row,
  Spinner,
  Table,
  UncontrolledDropdown,
} from 'reactstrap';
import { TextFormat, Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { formatFinancialAccountLabel } from 'app/entities/financial-account/financial-account-labels';
import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';
import { ICategorySelectable } from 'app/shared/model/category-selectable.model';
import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';
import { ITagSelectable } from 'app/shared/model/tag-selectable.model';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { ProductTagSelector, ProductTagSelectorTag } from 'app/shared/ui/product-tag-selector';
import { TransactionTagChips } from 'app/entities/financial-transaction/transaction-presentation';
import { getEntity } from './transaction-ingestion.reducer';
import {
  applyFileImportCandidateRules,
  confirmNoSuggestions,
  FileImportCandidateRulePreviewScope,
  IFileImportCandidateRulePreviewRow,
  IFileImportCandidateRulePreviewResponse,
  IFileImportCandidateApplyRulesRow,
  IFileImportCandidateWorkflowSummary,
  IPrepareFileImportCandidatesResponse,
  prepareFileImportCandidates,
  previewFileImportCandidateRules,
  updateFileImportCandidateClassification,
} from './transaction-ingestion-candidate-classification.service';
import { IDescriptionReevaluationResult, reevaluateIngestionDescriptions } from './transaction-ingestion-description-reevaluation.service';
import TransactionIngestionRuleCreationModal, {
  ITransactionIngestionRuleCreationContext,
  ITransactionIngestionRuleCreationRowContext,
  TransactionIngestionRuleCreationKind,
} from './components/transaction-ingestion-rule-creation-modal';
import { ContextualRuleSaveAction } from 'app/shared/model/contextual-rule-save-action.model';

interface ICsvIngestionWorkflowMessage {
  code?: string;
  message?: string;
}

interface ICsvIngestionWorkflowCounts {
  recordsReceived?: number;
  recordsCreated?: number;
  recordsSkipped?: number;
  recordsRejected?: number;
  validRows?: number;
  invalidRows?: number;
}

interface ICsvIngestionDescriptionReview {
  source?: string | null;
  originalDescription?: string | null;
  normalizedDescription?: string | null;
  ruleId?: number | null;
  ruleName?: string | null;
  resultingDescription?: string | null;
  editedAt?: string | null;
  editedBy?: string | null;
}

interface ICompletedWorkflowCategory {
  id?: number;
  name?: string;
  color?: string | null;
  active?: boolean;
  parentCategory?: {
    id?: number;
    name?: string;
  } | null;
}

interface ICompletedWorkflowFinancialTransaction {
  id?: number;
  category?: ICompletedWorkflowCategory | null;
  tags?: Array<{ id?: number; name?: string; color?: string | null; active?: boolean | null }>;
}

interface ICsvIngestionWorkflowRow {
  ingestionRecordId?: number;
  recordIndex?: number;
  status?: string;
  financialTransactionId?: number | null;
  financialTransaction?: ICompletedWorkflowFinancialTransaction | null;
  transactionDate?: string;
  postingDate?: string | null;
  description?: string;
  signedAmount?: string;
  amount?: string;
  flow?: string;
  currency?: string;
  externalReference?: string | null;
  notes?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  descriptionReview?: ICsvIngestionDescriptionReview | null;
  candidate?: IFileImportCandidateWorkflowSummary | null;
  warnings?: ICsvIngestionWorkflowMessage[];
}

interface ICsvIngestionFileMetadata {
  originalFilename?: string;
  fileType?: string;
  contentType?: string | null;
  fileSizeBytes?: number;
  checksum?: string;
  storageKey?: string | null;
  parserName?: string;
  parserVersion?: string;
  statementStartDate?: string | null;
  statementEndDate?: string | null;
  createdAt?: string;
}

interface ICsvIngestionWorkflowResponse {
  transactionIngestionId?: number;
  fileIngestionId?: number;
  status?: string;
  sourceLabel?: string;
  counts?: ICsvIngestionWorkflowCounts;
  warnings?: ICsvIngestionWorkflowMessage[];
  fileMetadata?: ICsvIngestionFileMetadata;
  rows?: ICsvIngestionWorkflowRow[];
}

interface ICsvIngestionRecordReviewResponse {
  transactionIngestionId?: number;
  status?: string;
  counts?: ICsvIngestionWorkflowCounts;
  row?: ICsvIngestionWorkflowRow;
}

interface ICsvIngestionConfirmImportResponse {
  transactionIngestionId?: number;
  status?: string;
  createdNow?: number;
  alreadyImported?: number;
  skipped?: number;
  rejected?: number;
  failed?: number;
  counts?: ICsvIngestionWorkflowCounts;
  rows?: ICsvIngestionWorkflowRow[];
}

interface ICsvIngestionWorkflowRowEditDraft {
  transactionDate?: string;
  postingDate?: string;
  description?: string;
  signedAmount?: string;
  currency?: string;
  externalReference?: string;
  notes?: string;
}

type AutomaticApplicationScope = 'descriptions' | 'categories' | 'tags';
type CandidateReviewFilter = 'ALL' | 'SUGGESTIONS' | 'ERRORS' | 'DISABLED';

interface IIngestionAutomationConfig {
  autoApplyDescriptions: boolean;
  autoApplyCategories: boolean;
  autoApplyTags: boolean;
  protectManualChanges: boolean;
}

interface IAutomaticApplicationRequest {
  sequence: number;
  descriptions: boolean;
  categories: boolean;
  tags: boolean;
}

const defaultIngestionAutomationConfig: IIngestionAutomationConfig = {
  autoApplyDescriptions: false,
  autoApplyCategories: false,
  autoApplyTags: false,
  protectManualChanges: true,
};

const candidateReviewFilters: CandidateReviewFilter[] = ['ALL', 'SUGGESTIONS', 'ERRORS', 'DISABLED'];

const rowIdentity = (row: ICsvIngestionWorkflowRow) =>
  `${row.ingestionRecordId ?? 'index'}-${row.ingestionRecordId ?? row.recordIndex ?? 'unknown'}`;

const recordStatusColor = (status?: string) => {
  switch (status) {
    case 'VALID':
      return 'success';
    case 'REJECTED':
      return 'danger';
    case 'DISABLED':
      return 'secondary';
    case 'IMPORTED':
      return 'info';
    default:
      return 'secondary';
  }
};

const categoryIdentityStyle = (color?: string | null): React.CSSProperties | undefined =>
  color
    ? {
        backgroundColor: `${color}14`,
        borderColor: `${color}80`,
        borderInlineStart: `0.3rem solid ${color}`,
      }
    : undefined;

const completedCategoryLabel = (category?: ICompletedWorkflowCategory | null) => {
  if (!category?.name) {
    return undefined;
  }

  return category.parentCategory?.name ? `${category.parentCategory.name} › ${category.name}` : category.name;
};

const useMobileCandidateReview = () => {
  const query = '(max-width: 991.98px)';
  const [isMobile, setIsMobile] = useState(() => typeof window !== 'undefined' && window.matchMedia?.(query).matches);

  useEffect(() => {
    const mediaQuery = window.matchMedia?.(query);
    if (!mediaQuery) {
      return undefined;
    }
    const update = () => setIsMobile(mediaQuery.matches);
    update();
    mediaQuery.addEventListener?.('change', update);
    return () => mediaQuery.removeEventListener?.('change', update);
  }, []);

  return isMobile;
};

const createWorkflowApiUrl = 'api/transaction-ingestions/file';

const flowLabel = (flow?: string) => {
  if (!flow) {
    return '';
  }

  return translate(`fintrackApp.TransactionFlow.${flow}`, flow);
};

const renderWarningMessage = (warning: ICsvIngestionWorkflowMessage) => {
  if (warning.code === 'DUPLICATE_FILE_CHECKSUM') {
    return <Translate contentKey="fintrackApp.transactionIngestion.workflow.duplicateFile">Duplicate file</Translate>;
  }

  return warning.message || warning.code;
};

const renderIngestionStatus = (status?: string) => {
  if (!status) {
    return '';
  }

  return translate(`fintrackApp.transactionIngestion.workflow.ingestionStatus.${status}`, status);
};

const ingestionStatusBadgeColor = (status?: string) => {
  switch (status) {
    case 'READY':
    case 'COMPLETED':
      return 'success';
    case 'PARTIALLY_READY':
    case 'PARTIALLY_COMPLETED':
      return 'warning';
    case 'PROCESSING':
      return 'info';
    case 'FAILED':
      return 'danger';
    case 'PENDING':
    default:
      return 'secondary';
  }
};

const recordStatusLabel = (status?: string) => {
  if (!status) {
    return '';
  }

  return translate(`fintrackApp.IngestionRecordStatus.${status}`, status);
};

const descriptionReviewTestId = (row: ICsvIngestionWorkflowRow, suffix: string) =>
  `descriptionReview-${suffix}-${row.ingestionRecordId ?? row.recordIndex}`;

const hasTransientDescriptionSuggestion = (reevaluation?: IDescriptionReevaluationResult) =>
  Boolean(reevaluation?.suggestedDescription && (reevaluation.action === 'PREVIEWED' || reevaluation.action === 'MANUAL_PRESERVED'));

const hasApplicableDescriptionSuggestion = (
  row: ICsvIngestionWorkflowRow,
  reevaluation?: IDescriptionReevaluationResult,
  protectManualChanges = false,
) =>
  hasTransientDescriptionSuggestion(reevaluation) &&
  reevaluation?.suggestedDescription !== row.description &&
  (!protectManualChanges || (reevaluation?.action !== 'MANUAL_PRESERVED' && row.descriptionReview?.source !== 'USER_EDIT'));

const renderDescriptionReviewDetail = (labelKey: string, fallbackLabel: string, value?: React.ReactNode) =>
  value ? (
    <div>
      <strong>
        <Translate contentKey={labelKey}>{fallbackLabel}</Translate>:
      </strong>{' '}
      {value}
    </div>
  ) : null;

const DescriptionReviewDisplay = ({
  row,
  reevaluation,
  onApplySuggestion,
  applyingSuggestion = false,
}: {
  row: ICsvIngestionWorkflowRow;
  reevaluation?: IDescriptionReevaluationResult;
  onApplySuggestion?: (row: ICsvIngestionWorkflowRow) => void;
  applyingSuggestion?: boolean;
}) => {
  const review = row.descriptionReview;
  const source = review?.source;
  const hasSuggestion = hasApplicableDescriptionSuggestion(row, reevaluation);

  const renderReevaluationSuggestion = () =>
    hasSuggestion ? (
      <div className="mt-1">
        <small className="text-muted d-block" data-testid={descriptionReviewTestId(row, 'reevaluationSuggestion')}>
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.descriptionReview.reevaluationSuggestion">
            Reevaluation suggestion
          </Translate>
          : {reevaluation?.suggestedDescription}
        </small>
        {onApplySuggestion ? (
          <Button
            color="primary"
            size="sm"
            className="mt-1"
            disabled={applyingSuggestion}
            onClick={() => onApplySuggestion(row)}
            data-cy={`workflowApplyDescriptionSuggestion-${row.ingestionRecordId ?? row.recordIndex}`}
            data-testid={`workflowApplyDescriptionSuggestion-${row.ingestionRecordId ?? row.recordIndex}`}
          >
            {applyingSuggestion ? <Spinner size="sm" /> : null}{' '}
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.descriptionReview.applySuggestion">
              Apply suggested description
            </Translate>
          </Button>
        ) : null}
      </div>
    ) : null;

  if (source !== 'DESCRIPTION_RULE' && source !== 'USER_EDIT') {
    return (
      <div>
        <div data-testid={descriptionReviewTestId(row, 'current')}>{row.description}</div>
        {renderReevaluationSuggestion()}
      </div>
    );
  }

  const recordKey = row.ingestionRecordId ?? row.recordIndex;
  const isRuleSource = source === 'DESCRIPTION_RULE';

  return (
    <div>
      <div data-testid={descriptionReviewTestId(row, 'current')}>{row.description}</div>
      <div className="mt-1" data-testid={descriptionReviewTestId(row, 'metadata')}>
        <Badge color={isRuleSource ? 'info' : 'secondary'} pill data-testid={descriptionReviewTestId(row, 'badge')}>
          <Translate
            contentKey={
              isRuleSource
                ? 'fintrackApp.transactionIngestion.workflow.descriptionReview.autoNormalized'
                : 'fintrackApp.transactionIngestion.workflow.descriptionReview.editedManually'
            }
          >
            {isRuleSource ? 'Auto-normalized' : 'Edited manually'}
          </Translate>
        </Badge>
        {isRuleSource && review?.ruleName ? (
          <small className="text-muted ms-2" data-testid={descriptionReviewTestId(row, 'ruleName')}>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.descriptionReview.rule">Rule</Translate>: {review.ruleName}
          </small>
        ) : null}
        {!isRuleSource && review?.ruleName ? (
          <small className="text-muted d-block mt-1" data-testid={descriptionReviewTestId(row, 'manualNote')}>
            {translate('fintrackApp.transactionIngestion.workflow.descriptionReview.originallyAutoNormalizedBy', {
              ruleName: review.ruleName,
            })}
          </small>
        ) : null}
        {renderReevaluationSuggestion()}
        <details className="small mt-1" data-testid={descriptionReviewTestId(row, 'details')}>
          <summary>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.descriptionReview.details">Details</Translate>
          </summary>
          {renderDescriptionReviewDetail(
            'fintrackApp.transactionIngestion.workflow.descriptionReview.originalDescription',
            'Original description',
            review?.originalDescription,
          )}
          {renderDescriptionReviewDetail(
            'fintrackApp.transactionIngestion.workflow.descriptionReview.normalizedDescription',
            'Normalized description',
            review?.normalizedDescription,
          )}
          {renderDescriptionReviewDetail('fintrackApp.transactionIngestion.workflow.descriptionReview.rule', 'Rule', review?.ruleName)}
          {renderDescriptionReviewDetail(
            'fintrackApp.transactionIngestion.workflow.descriptionReview.editedAt',
            'Edited at',
            review?.editedAt ? <TextFormat value={review.editedAt} type="date" format={APP_DATE_FORMAT} /> : null,
          )}
          {renderDescriptionReviewDetail(
            'fintrackApp.transactionIngestion.workflow.descriptionReview.editedBy',
            'Edited by',
            review?.editedBy,
          )}
        </details>
      </div>
      <span className="visually-hidden" data-testid={`descriptionReview-source-${recordKey}`}>
        {source}
      </span>
    </div>
  );
};

const editableRowStatuses = ['VALID', 'REJECTED'];

const rowToEditDraft = (row: ICsvIngestionWorkflowRow): ICsvIngestionWorkflowRowEditDraft => ({
  transactionDate: row.transactionDate ?? '',
  postingDate: row.postingDate ?? '',
  description: row.description ?? '',
  signedAmount: row.signedAmount ?? '',
  currency: row.currency ?? '',
  externalReference: row.externalReference ?? '',
  notes: row.notes ?? '',
});

const optionalBlankToNull = (value?: string) => (value === undefined || value.trim() === '' ? null : value);

const editDraftPayload = (draft: ICsvIngestionWorkflowRowEditDraft) => ({
  transactionDate: draft.transactionDate ?? null,
  postingDate: optionalBlankToNull(draft.postingDate),
  description: draft.description ?? null,
  signedAmount: draft.signedAmount ?? null,
  currency: draft.currency ?? null,
  externalReference: optionalBlankToNull(draft.externalReference),
  notes: optionalBlankToNull(draft.notes),
});

const categoryCompatibleWithFlow = (category: ICategorySelectable, flow?: string) => {
  if (!flow || !category.categoryType || category.categoryType === 'BOTH') {
    return true;
  }
  return (flow === 'OUT' && category.categoryType === 'EXPENSE') || (flow === 'IN' && category.categoryType === 'INCOME');
};

const candidateSelectedTags = (
  candidate: IFileImportCandidateWorkflowSummary | null | undefined,
  selectableTags: ITagSelectable[],
): ProductTagSelectorTag[] =>
  (candidate?.tagIds ?? []).map((tagId, index) => {
    const selectableTag = selectableTags.find(tag => tag.id === tagId);
    const selectedTag = candidate?.selectedTags?.find(tag => tag.tagId === tagId);

    return (
      selectableTag ?? {
        id: tagId,
        name: selectedTag?.tagName ?? candidate?.tagNames?.[index] ?? '',
      }
    );
  });

const candidatePreviewRowsById = (rows: IFileImportCandidateRulePreviewRow[] = []) =>
  rows.reduce<Record<number, IFileImportCandidateRulePreviewRow>>((acc, row) => {
    if (row.candidateId !== undefined) {
      acc[row.candidateId] = row;
    }
    return acc;
  }, {});

const mergeScopedPreviewRows = (
  currentRows: Record<number, IFileImportCandidateRulePreviewRow>,
  rows: IFileImportCandidateRulePreviewRow[] = [],
  scope: FileImportCandidateRulePreviewScope,
  replaceAll: boolean,
) => {
  if (scope === 'ALL' && replaceAll) {
    return candidatePreviewRowsById(rows);
  }

  return rows.reduce<Record<number, IFileImportCandidateRulePreviewRow>>((nextRows, row) => {
    if (row.candidateId === undefined) {
      return nextRows;
    }
    const existing = nextRows[row.candidateId];
    const merged =
      scope === 'ALL'
        ? { ...existing, ...row }
        : scope === 'CATEGORY'
          ? {
              ...existing,
              ...row,
              suggestedTags: existing?.suggestedTags ?? [],
            }
          : {
              ...existing,
              ...row,
              suggestedCategory: existing?.suggestedCategory ?? null,
            };
    return {
      ...nextRows,
      [row.candidateId]: {
        ...merged,
        hasSuggestions: Boolean(merged.suggestedCategory) || (merged.suggestedTags ?? []).length > 0,
      },
    };
  }, currentRows);
};

const replaceWorkflowCandidate = (
  row: ICsvIngestionWorkflowRow,
  candidate?: IFileImportCandidateWorkflowSummary | null,
): ICsvIngestionWorkflowRow => {
  if (!candidate?.id || row.candidate?.id !== candidate.id) {
    return row;
  }
  return {
    ...row,
    candidate,
  };
};

const candidateHasReviewedClassification = (candidate?: IFileImportCandidateWorkflowSummary | null) =>
  ['SUGGESTED', 'USER_SELECTED', 'NOT_APPLICABLE'].includes(candidate?.classificationReviewStatus ?? '');

const suggestedCategoryName = (preview?: IFileImportCandidateRulePreviewRow) =>
  preview?.suggestedCategory?.categoryName ?? preview?.suggestedCategory?.name;

const suggestedTagName = (tag: NonNullable<IFileImportCandidateRulePreviewRow['suggestedTags']>[number]) => tag.tagName ?? tag.name;

const hasApplicableSuggestedCategory = (
  candidate: IFileImportCandidateWorkflowSummary | null | undefined,
  preview: IFileImportCandidateRulePreviewRow | undefined,
) => Boolean(suggestedCategoryName(preview)) && candidate?.categoryId == null && !preview?.suggestedCategory?.conflictsWithCurrentValue;

const hasApplicableSuggestedTags = (
  candidate: IFileImportCandidateWorkflowSummary | null | undefined,
  preview: IFileImportCandidateRulePreviewRow | undefined,
) => {
  const selectedTagIds = new Set(candidate?.tagIds ?? []);
  return (preview?.suggestedTags ?? []).some(tag => {
    const tagId = tag.tagId ?? tag.id;
    return tagId !== undefined && !tag.alreadyPresent && !tag.duplicateOfEarlierSuggestion && !selectedTagIds.has(tagId);
  });
};

const candidateBlocksClassification = (row: ICsvIngestionWorkflowRow) => {
  const candidate = row.candidate;
  if (!candidate?.id) {
    return 'missing';
  }
  if (candidate.source !== 'FILE_IMPORT') {
    return 'source';
  }
  if (['CANCELLED', 'FAILED'].includes(candidate.status ?? '')) {
    return 'final';
  }
  if (candidate.status !== 'READY_TO_POST') {
    return 'status';
  }
  return null;
};

const validateCandidateBackedWorkflow = (workflowResponse: ICsvIngestionWorkflowResponse) => {
  const validRows = (workflowResponse.rows ?? []).filter(row => row.status === 'VALID');
  return validRows.find(row => candidateBlocksClassification(row));
};

const candidatePreviewKey = (workflowResponse: ICsvIngestionWorkflowResponse) =>
  (workflowResponse.rows ?? [])
    .filter(row => row.status === 'VALID')
    .map(row => row.candidate?.id)
    .filter((candidateId): candidateId is number => candidateId !== undefined)
    .sort((left, right) => left - right)
    .join(',');

const handlePrepareResponse = (response: IPrepareFileImportCandidatesResponse) => {
  const failedRows = (response.rows ?? []).filter(row => row.action === 'ERROR' || row.error);
  if (failedRows.length > 0 || (response.errors ?? 0) > 0) {
    throw new Error(failedRows[0]?.error || 'Candidate preparation failed');
  }
};

const replaceReviewRow = (currentRow: ICsvIngestionWorkflowRow, updatedRow: ICsvIngestionWorkflowRow): ICsvIngestionWorkflowRow => ({
  ingestionRecordId: updatedRow.ingestionRecordId ?? currentRow.ingestionRecordId,
  recordIndex: updatedRow.recordIndex ?? currentRow.recordIndex,
  status: updatedRow.status,
  transactionDate: updatedRow.transactionDate,
  postingDate: updatedRow.postingDate,
  description: updatedRow.description,
  signedAmount: updatedRow.signedAmount,
  amount: updatedRow.amount,
  flow: updatedRow.flow,
  currency: updatedRow.currency,
  externalReference: updatedRow.externalReference,
  notes: updatedRow.notes,
  errorCode: updatedRow.errorCode,
  errorMessage: updatedRow.errorMessage,
  descriptionReview: updatedRow.descriptionReview,
  warnings: updatedRow.warnings ?? [],
});

export const TransactionIngestionWorkflowDetail = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const isReviewPage = Boolean(id);

  const transactionIngestionEntity = useAppSelector(state => state.transactionIngestion.entity);
  const transactionIngestionLoading = useAppSelector(state => state.transactionIngestion.loading);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const preparedCandidateKeyRef = useRef<string | null>(null);
  const previewedCandidateKeyRef = useRef<string | null>(null);
  const automaticApplicationRunRef = useRef(false);
  const automaticApplicationSequenceRef = useRef(0);
  const confirmationRequestInFlightRef = useRef(false);
  const isMobileCandidateReview = useMobileCandidateReview();

  const [accountId, setAccountId] = useState('');
  const [selectableAccounts, setSelectableAccounts] = useState<IFinancialAccountSelectable[]>([]);
  const [categories, setCategories] = useState<ICategorySelectable[]>([]);
  const [tags, setTags] = useState<ITagSelectable[]>([]);
  const [selectableAccountsLoading, setSelectableAccountsLoading] = useState(false);
  const [tagsLoading, setTagsLoading] = useState(false);
  const [tagsLoadError, setTagsLoadError] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  const [backendError, setBackendError] = useState<string | null>(null);
  const [workflow, setWorkflow] = useState<ICsvIngestionWorkflowResponse | null>(null);
  const [loadingReview, setLoadingReview] = useState(false);
  const [reviewActionInProgress, setReviewActionInProgress] = useState<number | null>(null);
  const [confirmingImport, setConfirmingImport] = useState(false);
  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false);
  const [confirmDialogError, setConfirmDialogError] = useState<string | null>(null);
  const [confirmationResult, setConfirmationResult] = useState<ICsvIngestionConfirmImportResponse | null>(null);
  const [editingRecordId, setEditingRecordId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState<ICsvIngestionWorkflowRowEditDraft>({});
  const [editSessionWarning, setEditSessionWarning] = useState<string | null>(null);
  const [expandedRowIds, setExpandedRowIds] = useState<Set<string>>(() => new Set());
  const [candidateReviewFilter, setCandidateReviewFilter] = useState<CandidateReviewFilter>('ALL');
  const [fileReviewUnavailable, setFileReviewUnavailable] = useState(false);
  const [classificationPreviewByCandidateId, setClassificationPreviewByCandidateId] = useState<
    Record<number, IFileImportCandidateRulePreviewRow>
  >({});
  const [loadingClassification, setLoadingClassification] = useState(false);
  const [classificationActionInProgress, setClassificationActionInProgress] = useState<number | null>(null);
  const [applyingAllScopedSuggestions, setApplyingAllScopedSuggestions] = useState(false);
  const [reviewOperationFeedback, setReviewOperationFeedback] = useState<{ color: 'success' | 'warning'; message: string } | null>(null);
  const [descriptionReevaluationByRecordId, setDescriptionReevaluationByRecordId] = useState<
    Record<number, IDescriptionReevaluationResult>
  >({});
  const [descriptionApplicationInProgress, setDescriptionApplicationInProgress] = useState<number | 'ALL' | null>(null);
  const [reevaluationInProgress, setReevaluationInProgress] = useState<{
    kind: 'DESCRIPTIONS' | 'CATEGORY' | 'TAGS' | 'ALL' | 'ROW';
    recordId?: number;
  } | null>(null);
  const [automationConfig, setAutomationConfig] = useState<IIngestionAutomationConfig>(defaultIngestionAutomationConfig);
  const [automaticApplicationRequests, setAutomaticApplicationRequests] = useState<IAutomaticApplicationRequest[]>([]);
  const [ruleCreationContext, setRuleCreationContext] = useState<ITransactionIngestionRuleCreationContext | null>(null);
  const [ruleCreationFeedback, setRuleCreationFeedback] = useState<{ color: 'success' | 'warning'; message: string } | null>(null);

  const loadWorkflow = useCallback(async (workflowId: number | string) => {
    setLoadingReview(true);
    setBackendError(null);
    setFileReviewUnavailable(false);
    try {
      const response = await axios.get<ICsvIngestionWorkflowResponse>(`api/transaction-ingestions/${workflowId}/workflow`);
      setWorkflow(response.data);
      return response.data;
    } catch (error) {
      setWorkflow(null);
      setFileReviewUnavailable(true);
      throw error;
    } finally {
      setLoadingReview(false);
    }
  }, []);

  useEffect(() => {
    if (!isReviewPage) {
      let mounted = true;
      setSelectableAccountsLoading(true);
      getSelectableFinancialAccounts()
        .then(accounts => {
          if (mounted) {
            setSelectableAccounts(accounts);
          }
        })
        .catch(() => {
          if (mounted) {
            setSelectableAccounts([]);
          }
        })
        .finally(() => {
          if (mounted) {
            setSelectableAccountsLoading(false);
          }
        });
      return () => {
        mounted = false;
      };
    }
    return undefined;
  }, [isReviewPage]);

  useEffect(() => {
    if (!isReviewPage || !id) {
      return;
    }

    dispatch(getEntity(id));
  }, [isReviewPage, id]);

  useEffect(() => {
    if (!isReviewPage || !id || transactionIngestionEntity?.id?.toString() !== id) {
      return;
    }

    if (transactionIngestionEntity.ingestionType !== 'FILE') {
      setWorkflow(null);
      setFileReviewUnavailable(false);
      setLoadingReview(false);
      return;
    }

    loadWorkflow(id).catch(() => undefined);
  }, [isReviewPage, id, transactionIngestionEntity?.id, transactionIngestionEntity?.ingestionType, loadWorkflow]);

  const clearFileInput = () => {
    setFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const applyReviewResponse = (data: ICsvIngestionRecordReviewResponse) => {
    if (!data.row) {
      return;
    }
    previewedCandidateKeyRef.current = null;
    const updatedRow = data.row;
    if (updatedRow.ingestionRecordId !== undefined) {
      setDescriptionReevaluationByRecordId(current => {
        const remaining = { ...current };
        delete remaining[updatedRow.ingestionRecordId];
        return remaining;
      });
    }
    setWorkflow(currentWorkflow => {
      if (!currentWorkflow) {
        return currentWorkflow;
      }
      return {
        ...currentWorkflow,
        status: data.status ?? currentWorkflow.status,
        counts: data.counts ?? currentWorkflow.counts,
        rows: (currentWorkflow.rows ?? []).map(row => {
          const sameRecord =
            row.ingestionRecordId === updatedRow.ingestionRecordId ||
            (updatedRow.ingestionRecordId === undefined && row.recordIndex === updatedRow.recordIndex);
          return sameRecord ? replaceReviewRow(row, updatedRow) : row;
        }),
      };
    });
  };

  const applyConfirmResponse = (data: ICsvIngestionConfirmImportResponse) => {
    setWorkflow(currentWorkflow => {
      if (!currentWorkflow) {
        return currentWorkflow;
      }
      return {
        ...currentWorkflow,
        status: data.status ?? currentWorkflow.status,
        counts: data.counts ?? currentWorkflow.counts,
        rows: data.rows ?? currentWorkflow.rows,
      };
    });
    setEditingRecordId(null);
    setEditDraft({});
    if ((data.status ?? workflow?.status) === 'COMPLETED') {
      setClassificationPreviewByCandidateId({});
    }
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLocalError(null);
    setBackendError(null);

    if (!accountId) {
      setLocalError('fintrackApp.transactionIngestion.workflow.errors.accountRequired');
      return;
    }

    if (!file) {
      setLocalError('fintrackApp.transactionIngestion.workflow.errors.fileRequired');
      return;
    }

    const formData = new FormData();
    formData.append('accountId', accountId);
    formData.append('file', file);

    setSubmitting(true);
    try {
      const response = await axios.post<ICsvIngestionWorkflowResponse>(createWorkflowApiUrl, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      const transactionIngestionId = response.data.transactionIngestionId;
      if (transactionIngestionId) {
        navigate(`/transaction-ingestion/${transactionIngestionId}`);
      }
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.createFailed'));
    } finally {
      clearFileInput();
      setSubmitting(false);
    }
  };

  const performRowAction = async (row: ICsvIngestionWorkflowRow, action: 'disable' | 'enable') => {
    if (!row.ingestionRecordId || !workflow?.transactionIngestionId) {
      return;
    }
    setBackendError(null);
    setReviewActionInProgress(row.ingestionRecordId);
    try {
      const response = await axios.post<ICsvIngestionRecordReviewResponse>(
        `api/transaction-ingestions/${workflow.transactionIngestionId}/records/${row.ingestionRecordId}/${action}`,
      );
      applyReviewResponse(response.data);
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.recordUpdateFailed'));
    } finally {
      setReviewActionInProgress(null);
    }
  };

  const startEditingRow = (row: ICsvIngestionWorkflowRow) => {
    if (!row.ingestionRecordId) {
      return;
    }
    if (editingRecordId !== null && editingRecordId !== row.ingestionRecordId) {
      setEditSessionWarning(translate('fintrackApp.transactionIngestion.workflow.candidateQueue.finishCurrentEdit'));
      return;
    }
    setBackendError(null);
    setEditSessionWarning(null);
    setEditingRecordId(row.ingestionRecordId);
    setEditDraft(rowToEditDraft(row));
    setExpandedRowIds(current => new Set(current).add(rowIdentity(row)));
  };

  const updateEditDraft = (field: keyof ICsvIngestionWorkflowRowEditDraft, value: string) => {
    setEditDraft(currentDraft => ({ ...currentDraft, [field]: value }));
  };

  const cancelEditingRow = () => {
    setEditingRecordId(null);
    setEditDraft({});
    setEditSessionWarning(null);
  };

  const saveEditingRow = async (row: ICsvIngestionWorkflowRow) => {
    if (!row.ingestionRecordId || !workflow?.transactionIngestionId) {
      return;
    }
    setBackendError(null);
    setReviewActionInProgress(row.ingestionRecordId);
    try {
      const response = await axios.patch<ICsvIngestionRecordReviewResponse>(
        `api/transaction-ingestions/${workflow.transactionIngestionId}/records/${row.ingestionRecordId}`,
        editDraftPayload(editDraft),
      );
      applyReviewResponse(response.data);
      cancelEditingRow();
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.recordUpdateFailed'));
    } finally {
      setReviewActionInProgress(null);
    }
  };

  const counts = workflow?.counts ?? {};
  const rows = workflow?.rows ?? [];
  const warnings = workflow?.warnings ?? [];
  const fileMetadata = workflow?.fileMetadata;
  const reviewActionsEnabled = workflow?.status === 'READY' || workflow?.status === 'PARTIALLY_READY';
  const failed = workflow?.status === 'FAILED';
  const confirmAvailable = workflow?.status === 'READY' && (counts.validRows ?? 0) > 0;
  const processing = workflow?.status === 'PROCESSING' || confirmingImport;
  const showNoTransactionsCreatedBanner = reviewActionsEnabled;
  const showActionsColumn = reviewActionsEnabled;
  const classificationRows = rows.filter(row => row.status === 'VALID');
  const hasEligibleDescriptionRows = classificationRows.length > 0;
  const reviewedClassificationRows = classificationRows.filter(row => candidateHasReviewedClassification(row.candidate)).length;
  const notEvaluatedClassificationRows = classificationRows.filter(
    row => row.candidate?.classificationReviewStatus === 'NOT_EVALUATED',
  ).length;
  const staleClassificationRows = classificationRows.filter(row => row.candidate?.classificationReviewStatus === 'STALE').length;
  const categorySuggestionCandidateIds = classificationRows
    .filter(row => {
      const candidateId = row.candidate?.id;
      return candidateId !== undefined && hasApplicableSuggestedCategory(row.candidate, classificationPreviewByCandidateId[candidateId]);
    })
    .map(row => row.candidate?.id)
    .filter((candidateId): candidateId is number => candidateId !== undefined);
  const tagSuggestionCandidateIds = classificationRows
    .filter(row => {
      const candidateId = row.candidate?.id;
      return candidateId !== undefined && hasApplicableSuggestedTags(row.candidate, classificationPreviewByCandidateId[candidateId]);
    })
    .map(row => row.candidate?.id)
    .filter((candidateId): candidateId is number => candidateId !== undefined);
  const rowsWithSuggestions = classificationRows.filter(row => {
    const candidateId = row.candidate?.id;
    const preview = candidateId === undefined ? undefined : classificationPreviewByCandidateId[candidateId];
    return hasApplicableSuggestedCategory(row.candidate, preview) || hasApplicableSuggestedTags(row.candidate, preview);
  }).length;
  const descriptionSuggestionRecordIds = rows
    .filter(
      row =>
        row.status === 'VALID' &&
        row.ingestionRecordId !== undefined &&
        hasApplicableDescriptionSuggestion(
          row,
          descriptionReevaluationByRecordId[row.ingestionRecordId],
          automationConfig.protectManualChanges,
        ),
    )
    .map(row => row.ingestionRecordId)
    .filter((recordId): recordId is number => recordId !== undefined);
  const classificationConfirmBlocked = Boolean(
    classificationRows.find(row => candidateBlocksClassification(row) || !candidateHasReviewedClassification(row.candidate)),
  );
  const classificationReevaluationAvailable =
    workflow?.status === 'READY' && classificationRows.length > 0 && !validateCandidateBackedWorkflow(workflow);
  const classificationReevaluationRunning =
    Boolean(reevaluationInProgress) || loadingClassification || applyingAllScopedSuggestions || descriptionApplicationInProgress !== null;
  const hasUnsavedRowEdit = editingRecordId !== null;

  const rowHasAvailableSuggestions = useCallback(
    (row: ICsvIngestionWorkflowRow) => {
      const candidateId = row.candidate?.id;
      const preview = candidateId === undefined ? undefined : classificationPreviewByCandidateId[candidateId];
      return (
        hasApplicableDescriptionSuggestion(
          row,
          descriptionReevaluationByRecordId[row.ingestionRecordId ?? -1],
          automationConfig.protectManualChanges,
        ) ||
        hasApplicableSuggestedCategory(row.candidate, preview) ||
        hasApplicableSuggestedTags(row.candidate, preview)
      );
    },
    [automationConfig.protectManualChanges, classificationPreviewByCandidateId, descriptionReevaluationByRecordId],
  );

  const rowHasReviewError = useCallback(
    (row: ICsvIngestionWorkflowRow) => row.status === 'REJECTED' || Boolean(row.errorCode || row.errorMessage),
    [],
  );

  const visibleRows = useMemo(
    () =>
      rows.filter(row => {
        switch (candidateReviewFilter) {
          case 'SUGGESTIONS':
            return rowHasAvailableSuggestions(row);
          case 'ERRORS':
            return rowHasReviewError(row);
          case 'DISABLED':
            return row.status === 'DISABLED';
          case 'ALL':
          default:
            return true;
        }
      }),
    [candidateReviewFilter, rowHasAvailableSuggestions, rowHasReviewError, rows],
  );

  const editingRowVisible = editingRecordId === null || visibleRows.some(row => row.ingestionRecordId === editingRecordId);

  const toggleExpandedRow = (row: ICsvIngestionWorkflowRow) => {
    const identity = rowIdentity(row);
    if (editingRecordId === row.ingestionRecordId && expandedRowIds.has(identity)) {
      return;
    }
    setExpandedRowIds(current => {
      const next = new Set(current);
      if (next.has(identity)) {
        next.delete(identity);
      } else {
        next.add(identity);
      }
      return next;
    });
  };

  const canDisable = (row: ICsvIngestionWorkflowRow) => reviewActionsEnabled && ['VALID', 'REJECTED'].includes(row.status ?? '');
  const canEnable = (row: ICsvIngestionWorkflowRow) => reviewActionsEnabled && row.status === 'DISABLED';
  const canEdit = (row: ICsvIngestionWorkflowRow) => reviewActionsEnabled && editableRowStatuses.includes(row.status ?? '');

  const updateCandidateInWorkflow = (candidate?: IFileImportCandidateWorkflowSummary | null) => {
    if (!candidate?.id) {
      return;
    }
    setWorkflow(currentWorkflow =>
      currentWorkflow
        ? {
            ...currentWorkflow,
            rows: (currentWorkflow.rows ?? []).map(row => replaceWorkflowCandidate(row, candidate)),
          }
        : currentWorkflow,
    );
  };

  const clearPersistedClassificationPreviews = (
    applyRows: IFileImportCandidateApplyRulesRow[] = [],
    scope: FileImportCandidateRulePreviewScope = 'ALL',
  ) => {
    const persistedCandidateIds = new Set(
      applyRows
        .filter(row => row.candidateId !== undefined && ['APPLIED', 'UNCHANGED'].includes(row.action ?? ''))
        .map(row => row.candidateId),
    );
    if (persistedCandidateIds.size === 0) {
      return;
    }
    setClassificationPreviewByCandidateId(currentPreviewById => {
      const remainingPreviewById = { ...currentPreviewById };
      persistedCandidateIds.forEach(candidateId => {
        if (scope === 'ALL') {
          delete remainingPreviewById[candidateId];
          return;
        }
        const preview = remainingPreviewById[candidateId];
        if (!preview) {
          return;
        }
        const reconciled = scope === 'CATEGORY' ? { ...preview, suggestedCategory: null } : { ...preview, suggestedTags: [] };
        remainingPreviewById[candidateId] = {
          ...reconciled,
          hasSuggestions: Boolean(reconciled.suggestedCategory) || (reconciled.suggestedTags ?? []).length > 0,
        };
      });
      return remainingPreviewById;
    });
  };

  const reconcileAutomaticClassificationPreviews = (
    applyRows: IFileImportCandidateApplyRulesRow[] = [],
    scope: FileImportCandidateRulePreviewScope,
    protectManualChanges: boolean,
  ) => {
    const persistedRows = applyRows.filter(
      row => row.candidateId !== undefined && ['APPLIED', 'UNCHANGED'].includes(row.action ?? '') && row.candidate,
    );
    if (persistedRows.length === 0) {
      return;
    }

    setClassificationPreviewByCandidateId(currentPreviewById => {
      const nextPreviewById = { ...currentPreviewById };
      persistedRows.forEach(row => {
        const candidateId = row.candidateId;
        const candidate = row.candidate;
        const previous = nextPreviewById[candidateId];
        const preserveCategorySuggestion =
          protectManualChanges &&
          (scope === 'ALL' || scope === 'CATEGORY') &&
          candidate?.categorySource === 'MANUAL' &&
          Boolean(row.suggestedCategory);
        const preserveTagSuggestions =
          protectManualChanges &&
          (scope === 'ALL' || scope === 'TAGS') &&
          (candidate?.selectedTags ?? []).some(tag => tag.source === 'MANUAL') &&
          (row.suggestedTags ?? []).length > 0;

        const suggestedCategory =
          scope === 'TAGS' ? (previous?.suggestedCategory ?? null) : preserveCategorySuggestion ? row.suggestedCategory : null;
        const suggestedTags =
          scope === 'CATEGORY' ? (previous?.suggestedTags ?? []) : preserveTagSuggestions ? (row.suggestedTags ?? []) : [];
        const hasSuggestions = Boolean(suggestedCategory) || suggestedTags.length > 0;

        if (!hasSuggestions) {
          if (scope === 'ALL') {
            delete nextPreviewById[candidateId];
          } else if (previous) {
            const reconciled = scope === 'CATEGORY' ? { ...previous, suggestedCategory: null } : { ...previous, suggestedTags: [] };
            nextPreviewById[candidateId] = {
              ...reconciled,
              hasSuggestions: Boolean(reconciled.suggestedCategory) || (reconciled.suggestedTags ?? []).length > 0,
            };
          }
          return;
        }

        nextPreviewById[candidateId] = {
          ...previous,
          ...row,
          suggestedCategory,
          suggestedTags,
          hasSuggestions,
        };
      });
      return nextPreviewById;
    });
  };

  const reloadWorkflowAfterRuleApply = async (transactionIngestionId: number) => {
    const reloadedWorkflow = await loadWorkflow(transactionIngestionId);
    // Apply has already persisted the returned candidate values. Avoid immediately recreating a
    // transient preview that would make those same suggestions look unapplied again.
    previewedCandidateKeyRef.current = candidatePreviewKey(reloadedWorkflow);
    return reloadedWorkflow;
  };

  const loadRulePreview = useCallback(
    async (transactionIngestionId: number, candidateIds?: number[], scope: FileImportCandidateRulePreviewScope = 'ALL') => {
      const response = await previewFileImportCandidateRules(transactionIngestionId, candidateIds, scope);
      const preview = response.data;
      setClassificationPreviewByCandidateId(current => mergeScopedPreviewRows(current, preview.rows ?? [], scope, !candidateIds?.length));
      return preview;
    },
    [],
  );

  const prepareCandidatesForUnifiedReview = useCallback(
    async (transactionIngestionId: number) => {
      setBackendError(null);
      setLoadingClassification(true);
      try {
        const prepareResponse = await prepareFileImportCandidates(transactionIngestionId);
        handlePrepareResponse(prepareResponse.data);
        const reloadedWorkflow = await loadWorkflow(transactionIngestionId);
        const blockedRow = validateCandidateBackedWorkflow(reloadedWorkflow);
        if (blockedRow) {
          setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.candidatePreparationFailed'));
          return;
        }
        const previewKey = candidatePreviewKey(reloadedWorkflow);
        previewedCandidateKeyRef.current = previewKey;
        await loadRulePreview(transactionIngestionId);
      } catch (error) {
        setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.candidatePreparationFailed'));
      } finally {
        setLoadingClassification(false);
      }
    },
    [loadRulePreview, loadWorkflow],
  );

  useEffect(() => {
    const transactionIngestionId = workflow?.transactionIngestionId;
    const validRows = (workflow?.rows ?? []).filter(row => row.status === 'VALID');
    const eligibleForClassification = workflow?.status === 'READY' && validRows.length > 0;

    if (!transactionIngestionId || !eligibleForClassification) {
      preparedCandidateKeyRef.current = null;
      previewedCandidateKeyRef.current = null;
      return;
    }

    const missingCandidateRecordIds = validRows
      .filter(row => !row.candidate?.id)
      .map(row => row.ingestionRecordId ?? row.recordIndex)
      .join(',');
    if (!missingCandidateRecordIds) {
      preparedCandidateKeyRef.current = null;
      return;
    }

    const preparationKey = `${transactionIngestionId}:${missingCandidateRecordIds}`;
    if (preparedCandidateKeyRef.current === preparationKey) {
      return;
    }
    preparedCandidateKeyRef.current = preparationKey;
    prepareCandidatesForUnifiedReview(transactionIngestionId).catch(() => undefined);
  }, [dispatch, prepareCandidatesForUnifiedReview, workflow]);

  useEffect(() => {
    if (!workflow?.transactionIngestionId || workflow.status !== 'READY') {
      return;
    }
    let mounted = true;
    const candidates = (workflow.rows ?? []).flatMap(row => (row.candidate ? [row.candidate] : []));
    const categoryIds = candidates
      .map(candidate => candidate.categoryId)
      .filter((categoryId): categoryId is number => categoryId !== undefined && categoryId !== null);
    const tagIds = candidates
      .flatMap(candidate => candidate.tagIds ?? [])
      .filter((tagId): tagId is number => tagId !== undefined && tagId !== null);
    setTagsLoading(true);
    setTagsLoadError(false);
    Promise.all([getSelectableCategories(categoryIds), getSelectableTags(tagIds)])
      .then(([nextCategories, nextTags]) => {
        if (mounted) {
          setCategories(nextCategories);
          setTags(nextTags);
        }
      })
      .catch(() => {
        if (mounted) {
          setCategories([]);
          setTagsLoadError(true);
        }
      })
      .finally(() => {
        if (mounted) {
          setTagsLoading(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, [workflow]);

  useEffect(() => {
    const transactionIngestionId = workflow?.transactionIngestionId;
    const validRows = (workflow?.rows ?? []).filter(row => row.status === 'VALID');
    const previewKey = candidatePreviewKey(workflow ?? {});
    if (
      !transactionIngestionId ||
      workflow?.status !== 'READY' ||
      validRows.length === 0 ||
      validRows.some(row => !row.candidate?.id) ||
      previewedCandidateKeyRef.current === previewKey
    ) {
      return;
    }

    previewedCandidateKeyRef.current = previewKey;
    setBackendError(null);
    setLoadingClassification(true);
    void (async () => {
      try {
        await loadRulePreview(transactionIngestionId);
      } catch (error) {
        setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.classificationPreviewFailed'));
      } finally {
        setLoadingClassification(false);
      }
    })();
  }, [loadRulePreview, workflow]);

  const reevaluateDescriptions = async (
    recordIds?: number[],
    apply = false,
    protectManualChanges = automationConfig.protectManualChanges,
  ) => {
    if (!workflow?.transactionIngestionId) {
      return;
    }
    const response = await reevaluateIngestionDescriptions(workflow.transactionIngestionId, recordIds, apply, protectManualChanges);
    const result = response.data;
    setDescriptionReevaluationByRecordId(current => {
      const next = { ...current };
      (result.rows ?? []).forEach(row => {
        if (row.ingestionRecordId === undefined) {
          return;
        }
        if (apply && (row.action === 'APPLIED' || row.action === 'NO_MATCH')) {
          delete next[row.ingestionRecordId];
          return;
        }
        next[row.ingestionRecordId] = row;
      });
      return next;
    });
    if (apply) {
      await loadWorkflow(workflow.transactionIngestionId);
    }
    return result;
  };

  const reevaluateRuleSuggestions = async (scope: FileImportCandidateRulePreviewScope, candidateIds?: number[]) => {
    if (!workflow?.transactionIngestionId) {
      return;
    }
    await loadRulePreview(workflow.transactionIngestionId, candidateIds, scope);
  };

  const automaticallyApplyRuleSuggestions = async (scope: FileImportCandidateRulePreviewScope, candidateIds?: number[]) => {
    if (!workflow?.transactionIngestionId) {
      return;
    }
    const response = await applyFileImportCandidateRules(
      workflow.transactionIngestionId,
      candidateIds,
      scope,
      true,
      automationConfig.protectManualChanges,
    );
    const applyRows = response.data.rows ?? [];
    applyRows.forEach(row => updateCandidateInWorkflow(row.candidate));
    reconcileAutomaticClassificationPreviews(applyRows, scope, automationConfig.protectManualChanges);
    await reloadWorkflowAfterRuleApply(workflow.transactionIngestionId);
  };

  const reevaluateClassificationWithAutomation = async (
    scope: Extract<FileImportCandidateRulePreviewScope, 'CATEGORY' | 'TAGS'>,
    candidateIds?: number[],
  ) => {
    const enabled = scope === 'CATEGORY' ? automationConfig.autoApplyCategories : automationConfig.autoApplyTags;
    if (enabled) {
      await automaticallyApplyRuleSuggestions(scope, candidateIds);
      return;
    }
    await reevaluateRuleSuggestions(scope, candidateIds);
  };

  const reevaluateAllClassificationWithAutomation = async (candidateIds?: number[]) => {
    const { autoApplyCategories, autoApplyTags } = automationConfig;
    if (autoApplyCategories && autoApplyTags) {
      await automaticallyApplyRuleSuggestions('ALL', candidateIds);
      return;
    }
    if (autoApplyCategories) {
      await automaticallyApplyRuleSuggestions('CATEGORY', candidateIds);
      await reevaluateRuleSuggestions('TAGS', candidateIds);
      return;
    }
    if (autoApplyTags) {
      await reevaluateRuleSuggestions('CATEGORY', candidateIds);
      await automaticallyApplyRuleSuggestions('TAGS', candidateIds);
      return;
    }
    await reevaluateRuleSuggestions('ALL', candidateIds);
  };

  const reevaluateDescriptionsOnly = async () => {
    if (!hasEligibleDescriptionRows || reevaluationInProgress || hasUnsavedRowEdit) {
      return;
    }
    setBackendError(null);
    setReviewOperationFeedback(null);
    setReevaluationInProgress({ kind: 'DESCRIPTIONS' });
    try {
      await reevaluateDescriptions(undefined, automationConfig.autoApplyDescriptions);
      setReviewOperationFeedback({
        color: 'success',
        message: translate('fintrackApp.transactionIngestion.workflow.reviewFeedback.descriptionsReevaluated'),
      });
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.descriptionReevaluationFailed'));
    } finally {
      setReevaluationInProgress(null);
    }
  };

  const applyDescriptionSuggestion = async (row: ICsvIngestionWorkflowRow) => {
    if (!workflow?.transactionIngestionId || !row.ingestionRecordId || descriptionApplicationInProgress !== null) {
      return;
    }
    setBackendError(null);
    setReviewOperationFeedback(null);
    setDescriptionApplicationInProgress(row.ingestionRecordId);
    try {
      // A per-row click is explicit consent to replace a USER_EDIT value with the currently reevaluated rule result.
      await reevaluateDescriptions([row.ingestionRecordId], true, false);
      setReviewOperationFeedback({
        color: 'success',
        message: translate('fintrackApp.transactionIngestion.workflow.reviewFeedback.descriptionApplied'),
      });
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.descriptionApplyFailed'));
    } finally {
      setDescriptionApplicationInProgress(null);
    }
  };

  const applyAllDescriptionSuggestions = async () => {
    if (!workflow?.transactionIngestionId || descriptionApplicationInProgress !== null || hasUnsavedRowEdit) {
      return;
    }
    if (descriptionSuggestionRecordIds.length === 0) {
      return;
    }

    setBackendError(null);
    setReviewOperationFeedback(null);
    setDescriptionApplicationInProgress('ALL');
    try {
      // Batch application remains subject to the page's manual-change protection setting.
      await reevaluateDescriptions(descriptionSuggestionRecordIds, true, automationConfig.protectManualChanges);
      setReviewOperationFeedback({
        color: 'success',
        message: translate('fintrackApp.transactionIngestion.workflow.reviewFeedback.descriptionsApplied'),
      });
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.descriptionApplyAllFailed'));
    } finally {
      setDescriptionApplicationInProgress(null);
    }
  };

  const reevaluateClassificationScope = async (scope: Extract<FileImportCandidateRulePreviewScope, 'CATEGORY' | 'TAGS'>) => {
    if (!classificationReevaluationAvailable || reevaluationInProgress || hasUnsavedRowEdit) {
      return;
    }
    setBackendError(null);
    setReviewOperationFeedback(null);
    setReevaluationInProgress({ kind: scope });
    try {
      await reevaluateClassificationWithAutomation(scope);
      setReviewOperationFeedback({
        color: 'success',
        message: translate(
          scope === 'CATEGORY'
            ? 'fintrackApp.transactionIngestion.workflow.reviewFeedback.categoriesReevaluated'
            : 'fintrackApp.transactionIngestion.workflow.reviewFeedback.tagsReevaluated',
        ),
      });
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.classificationPreviewFailed'));
    } finally {
      setReevaluationInProgress(null);
    }
  };

  const reevaluateAll = async () => {
    if (!hasEligibleDescriptionRows || !classificationReevaluationAvailable || reevaluationInProgress || hasUnsavedRowEdit) {
      return;
    }
    setBackendError(null);
    setReviewOperationFeedback(null);
    setReevaluationInProgress({ kind: 'ALL' });
    let descriptionsSucceeded = false;
    let classificationSucceeded = false;
    try {
      await reevaluateDescriptions(undefined, automationConfig.autoApplyDescriptions);
      descriptionsSucceeded = true;
    } catch (error) {
      // Continue with the independent read-only candidate preview and report the combined outcome below.
    }
    try {
      await reevaluateAllClassificationWithAutomation();
      classificationSucceeded = true;
    } catch (error) {
      // The first completed domain is retained; no suggestions are applied by either request.
    } finally {
      if (!descriptionsSucceeded || !classificationSucceeded) {
        setBackendError(
          translate(
            descriptionsSucceeded || classificationSucceeded
              ? 'fintrackApp.transactionIngestion.workflow.errors.reevaluationPartialFailed'
              : 'fintrackApp.transactionIngestion.workflow.errors.reevaluationFailed',
          ),
        );
      } else {
        setReviewOperationFeedback({
          color: 'success',
          message: translate('fintrackApp.transactionIngestion.workflow.reviewFeedback.allReevaluated'),
        });
      }
      setReevaluationInProgress(null);
    }
  };

  const reevaluateRow = async (row: ICsvIngestionWorkflowRow) => {
    const candidateId = row.candidate?.id;
    if (
      !row.ingestionRecordId ||
      candidateId === undefined ||
      !classificationReevaluationAvailable ||
      reevaluationInProgress ||
      hasUnsavedRowEdit
    ) {
      return;
    }
    setBackendError(null);
    setReviewOperationFeedback(null);
    setReevaluationInProgress({ kind: 'ROW', recordId: row.ingestionRecordId });
    let descriptionsSucceeded = false;
    let classificationSucceeded = false;
    try {
      await reevaluateDescriptions([row.ingestionRecordId], automationConfig.autoApplyDescriptions);
      descriptionsSucceeded = true;
    } catch (error) {
      // A scoped preview is still independent and can provide current category/tag suggestions.
    }
    try {
      await reevaluateAllClassificationWithAutomation([candidateId]);
      classificationSucceeded = true;
    } catch (error) {
      // Report below without fabricating local preview success.
    } finally {
      if (!descriptionsSucceeded || !classificationSucceeded) {
        setBackendError(
          translate(
            descriptionsSucceeded || classificationSucceeded
              ? 'fintrackApp.transactionIngestion.workflow.errors.reevaluationPartialFailed'
              : 'fintrackApp.transactionIngestion.workflow.errors.reevaluationFailed',
          ),
        );
      } else {
        setReviewOperationFeedback({
          color: 'success',
          message: translate('fintrackApp.transactionIngestion.workflow.reviewFeedback.rowReevaluated'),
        });
      }
      setReevaluationInProgress(null);
    }
  };

  const rowRuleCreationContext = (row: ICsvIngestionWorkflowRow): ITransactionIngestionRuleCreationRowContext => ({
    ingestionRecordId: row.ingestionRecordId,
    recordIndex: row.recordIndex,
    candidateId: row.candidate?.id,
    // A normalization rule must match the immutable FILE input. Never replace a missing raw/original value with the
    // current candidate description, which may already be normalized or manually edited.
    originalDescription: row.descriptionReview?.originalDescription ?? null,
    currentDescription: row.candidate?.description ?? row.description ?? null,
    flow: row.candidate?.flow ?? row.flow ?? null,
    accountName: row.candidate?.accountName ?? transactionIngestionEntity?.account?.name ?? null,
    categoryId: row.candidate?.categoryId ?? null,
    categoryName: row.candidate?.categoryName ?? null,
    tagIds: row.candidate?.tagIds ?? [],
    tagNames: row.candidate?.tagNames ?? [],
  });

  const openRuleCreation = (kind: TransactionIngestionRuleCreationKind, row?: ICsvIngestionWorkflowRow) => {
    setRuleCreationFeedback(null);
    setRuleCreationContext({ kind, ...(row ? { row: rowRuleCreationContext(row) } : {}) });
  };

  const handleRuleCreated = async (
    kind: TransactionIngestionRuleCreationKind,
    action: ContextualRuleSaveAction,
    row?: ITransactionIngestionRuleCreationRowContext,
  ) => {
    const shouldReevaluate = action !== 'SAVE';
    try {
      if (shouldReevaluate && kind === 'NORMALIZATION') {
        await reevaluateDescriptions(
          action === 'REEVALUATE_ROW' && row?.ingestionRecordId ? [row.ingestionRecordId] : undefined,
          automationConfig.autoApplyDescriptions,
        );
      }
      if (shouldReevaluate && kind === 'TRANSACTION') {
        if (action === 'REEVALUATE_ROW' && row?.candidateId !== undefined) {
          await reevaluateAllClassificationWithAutomation([row.candidateId]);
        } else {
          await reevaluateAllClassificationWithAutomation();
        }
      }
      let successMessageKey = 'fintrackApp.transactionIngestion.workflow.ruleCreation.saved';
      if (action === 'REEVALUATE_ROW') {
        successMessageKey = 'fintrackApp.transactionIngestion.workflow.ruleCreation.savedReevaluatedRow';
      } else if (action === 'REEVALUATE_ALL') {
        successMessageKey = 'fintrackApp.transactionIngestion.workflow.ruleCreation.savedReevaluatedAll';
      }
      setRuleCreationFeedback({
        color: 'success',
        message: translate(successMessageKey),
      });
    } catch (error) {
      setRuleCreationFeedback({
        color: 'warning',
        message: translate('fintrackApp.transactionIngestion.workflow.ruleCreation.savedReevaluationFailed'),
      });
    } finally {
      setRuleCreationContext(null);
    }
  };

  const updateAutomationSetting = (setting: keyof IIngestionAutomationConfig, enabled: boolean) => {
    if (hasUnsavedRowEdit) {
      return;
    }
    const wasEnabled = automationConfig[setting];
    setAutomationConfig(current => ({ ...current, [setting]: enabled }));
    if (setting === 'protectManualChanges' || wasEnabled || !enabled) {
      return;
    }

    const scopeBySetting: Record<Exclude<keyof IIngestionAutomationConfig, 'protectManualChanges'>, AutomaticApplicationScope> = {
      autoApplyDescriptions: 'descriptions',
      autoApplyCategories: 'categories',
      autoApplyTags: 'tags',
    };
    const scope = scopeBySetting[setting];
    setAutomaticApplicationRequests(current => [
      ...current,
      {
        sequence: ++automaticApplicationSequenceRef.current,
        descriptions: scope === 'descriptions',
        categories: scope === 'categories',
        tags: scope === 'tags',
      },
    ]);
  };

  useEffect(() => {
    const request = automaticApplicationRequests[0];
    const transactionIngestionId = workflow?.transactionIngestionId;
    const validRows = (workflow?.rows ?? []).filter(row => row.status === 'VALID');
    const candidatesPrepared = validRows.length > 0 && validRows.every(row => row.candidate?.id !== undefined);
    const descriptions = Boolean(request?.descriptions && automationConfig.autoApplyDescriptions);
    const categoriesEnabled = Boolean(request?.categories && automationConfig.autoApplyCategories);
    const tagsEnabled = Boolean(request?.tags && automationConfig.autoApplyTags);

    if (!request || !transactionIngestionId || workflow?.status !== 'READY' || !candidatesPrepared) {
      return;
    }
    if (!descriptions && !categoriesEnabled && !tagsEnabled) {
      setAutomaticApplicationRequests(current => current.filter(queuedRequest => queuedRequest.sequence !== request.sequence));
      return;
    }
    if (automaticApplicationRunRef.current) {
      return;
    }

    automaticApplicationRunRef.current = true;
    setBackendError(null);
    setReevaluationInProgress({ kind: 'ALL' });
    void (async () => {
      let descriptionsSucceeded = !descriptions;
      let classificationSucceeded = !categoriesEnabled && !tagsEnabled;
      try {
        if (descriptions) {
          await reevaluateDescriptions(undefined, true);
          descriptionsSucceeded = true;
        }
      } catch (error) {
        // Classification is independent. Preserve a successful scope rather than rolling it back.
      }
      try {
        if (categoriesEnabled && tagsEnabled) {
          await automaticallyApplyRuleSuggestions('ALL');
        } else if (categoriesEnabled) {
          await automaticallyApplyRuleSuggestions('CATEGORY');
        } else if (tagsEnabled) {
          await automaticallyApplyRuleSuggestions('TAGS');
        }
        classificationSucceeded = true;
      } catch (error) {
        // Report the partial outcome below. Persisted values from a completed scope remain valid.
      } finally {
        if (!descriptionsSucceeded || !classificationSucceeded) {
          setBackendError(
            translate(
              descriptionsSucceeded || classificationSucceeded
                ? 'fintrackApp.transactionIngestion.workflow.errors.reevaluationPartialFailed'
                : 'fintrackApp.transactionIngestion.workflow.errors.reevaluationFailed',
            ),
          );
        }
        automaticApplicationRunRef.current = false;
        setAutomaticApplicationRequests(current => current.filter(queuedRequest => queuedRequest.sequence !== request.sequence));
        setReevaluationInProgress(null);
      }
    })();
  }, [automaticApplicationRequests, automationConfig, workflow]);

  const updateClassificationCategory = async (candidateId: number, categoryId: string) => {
    if (!workflow?.transactionIngestionId || hasUnsavedRowEdit) {
      return;
    }
    setBackendError(null);
    setClassificationActionInProgress(candidateId);
    try {
      const response = await updateFileImportCandidateClassification(workflow.transactionIngestionId, candidateId, {
        categoryId: categoryId ? Number(categoryId) : null,
      });
      updateCandidateInWorkflow(response.data.candidate);
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.classificationUpdateFailed'));
    } finally {
      setClassificationActionInProgress(null);
    }
  };

  const updateClassificationTags = async (candidateId: number, tagIds: string[]) => {
    if (!workflow?.transactionIngestionId || hasUnsavedRowEdit) {
      return;
    }
    setBackendError(null);
    setClassificationActionInProgress(candidateId);
    try {
      const response = await updateFileImportCandidateClassification(workflow.transactionIngestionId, candidateId, {
        tagIds: Array.from(new Set(tagIds)).map(tagId => Number(tagId)),
      });
      updateCandidateInWorkflow(response.data.candidate);
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.classificationUpdateFailed'));
    } finally {
      setClassificationActionInProgress(null);
    }
  };

  const applySuggestionsForCandidate = async (
    candidateId: number,
    scope: Extract<FileImportCandidateRulePreviewScope, 'CATEGORY' | 'TAGS'>,
  ) => {
    if (!workflow?.transactionIngestionId || hasUnsavedRowEdit) {
      return;
    }
    setBackendError(null);
    setReviewOperationFeedback(null);
    setClassificationActionInProgress(candidateId);
    try {
      const response = await applyFileImportCandidateRules(workflow.transactionIngestionId, [candidateId], scope);
      const applyRows = response.data.rows ?? [];
      applyRows.forEach(row => updateCandidateInWorkflow(row.candidate));
      clearPersistedClassificationPreviews(applyRows, scope);
      await reloadWorkflowAfterRuleApply(workflow.transactionIngestionId);
      setReviewOperationFeedback({
        color: applyRows.some(row => row.action === 'SKIPPED') ? 'warning' : 'success',
        message: translate(
          applyRows.some(row => row.action === 'SKIPPED')
            ? 'fintrackApp.transactionIngestion.workflow.errors.classificationApplyAllPartial'
            : scope === 'CATEGORY'
              ? 'fintrackApp.transactionIngestion.workflow.reviewFeedback.categoryApplied'
              : 'fintrackApp.transactionIngestion.workflow.reviewFeedback.tagsApplied',
        ),
      });
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.classificationUpdateFailed'));
    } finally {
      setClassificationActionInProgress(null);
    }
  };

  const applyAllSuggestionsForScope = async (scope: Extract<FileImportCandidateRulePreviewScope, 'CATEGORY' | 'TAGS'>) => {
    if (
      !workflow?.transactionIngestionId ||
      !classificationReevaluationAvailable ||
      classificationReevaluationRunning ||
      classificationActionInProgress !== null ||
      hasUnsavedRowEdit
    ) {
      return;
    }
    setBackendError(null);
    setReviewOperationFeedback(null);
    setApplyingAllScopedSuggestions(true);
    try {
      // Omitting candidateIds applies this domain's current FILE_IMPORT suggestions across the ingestion.
      const response = await applyFileImportCandidateRules(workflow.transactionIngestionId, undefined, scope);
      const applyRows = response.data.rows ?? [];
      applyRows.forEach(row => updateCandidateInWorkflow(row.candidate));
      clearPersistedClassificationPreviews(applyRows, scope);
      await reloadWorkflowAfterRuleApply(workflow.transactionIngestionId);
      if (applyRows.some(row => row.action === 'SKIPPED')) {
        setReviewOperationFeedback({
          color: 'warning',
          message: translate('fintrackApp.transactionIngestion.workflow.errors.classificationApplyAllPartial'),
        });
      } else {
        setReviewOperationFeedback({
          color: 'success',
          message: translate(
            scope === 'CATEGORY'
              ? 'fintrackApp.transactionIngestion.workflow.reviewFeedback.categoriesApplied'
              : 'fintrackApp.transactionIngestion.workflow.reviewFeedback.allTagsApplied',
          ),
        });
      }
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.classificationApplyAllFailed'));
    } finally {
      setApplyingAllScopedSuggestions(false);
    }
  };

  const confirmCandidateHasNoSuggestions = async (candidateId: number) => {
    if (!workflow?.transactionIngestionId || hasUnsavedRowEdit) {
      return;
    }
    setBackendError(null);
    setReviewOperationFeedback(null);
    setClassificationActionInProgress(candidateId);
    try {
      const response = await confirmNoSuggestions(workflow.transactionIngestionId, candidateId);
      updateCandidateInWorkflow(response.data.candidate);
      setReviewOperationFeedback({
        color: 'success',
        message: translate('fintrackApp.transactionIngestion.workflow.reviewFeedback.noSuggestionsConfirmed'),
      });
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.confirmNoSuggestionsFailed'));
    } finally {
      setClassificationActionInProgress(null);
    }
  };

  const validateConfirmCandidates = (workflowResponse: ICsvIngestionWorkflowResponse) => {
    const validRows = (workflowResponse.rows ?? []).filter(row => row.status === 'VALID');
    return validRows.find(row => candidateBlocksClassification(row) || !candidateHasReviewedClassification(row.candidate));
  };

  const openConfirmDialog = () => {
    if (!confirmAvailable || hasUnsavedRowEdit || confirmingImport) {
      return;
    }
    setConfirmDialogError(null);
    setConfirmDialogOpen(true);
  };

  const closeConfirmDialog = () => {
    if (!confirmingImport) {
      setConfirmDialogOpen(false);
      setConfirmDialogError(null);
    }
  };

  const refreshWorkflowAfterUncertainConfirm = async (workflowId: number) => {
    try {
      const response = await axios.get<ICsvIngestionWorkflowResponse>(`api/transaction-ingestions/${workflowId}/workflow`);
      setWorkflow(response.data);
      return response.data;
    } catch (_error) {
      return null;
    }
  };

  const confirmImport = async () => {
    if (!workflow?.transactionIngestionId || hasUnsavedRowEdit || confirmingImport || confirmationRequestInFlightRef.current) {
      return;
    }
    confirmationRequestInFlightRef.current = true;
    setBackendError(null);
    setConfirmDialogError(null);
    setConfirmingImport(true);
    try {
      const reloadedWorkflow = await loadWorkflow(workflow.transactionIngestionId);
      const blockedRow = validateConfirmCandidates(reloadedWorkflow);
      if (blockedRow) {
        setConfirmDialogError(translate('fintrackApp.transactionIngestion.workflow.errors.classificationConfirmBlocked'));
        return;
      }
      if (reloadedWorkflow.status !== 'READY' || (reloadedWorkflow.counts?.validRows ?? 0) === 0) {
        setConfirmDialogError(translate('fintrackApp.transactionIngestion.workflow.errors.confirmNoLongerAvailable'));
        return;
      }
      const response = await axios.post<ICsvIngestionConfirmImportResponse>(
        `api/transaction-ingestions/${workflow.transactionIngestionId}/confirm`,
      );
      applyConfirmResponse(response.data);
      setConfirmationResult(response.data);
      setConfirmDialogOpen(false);
    } catch (error) {
      const reloadedWorkflow = await refreshWorkflowAfterUncertainConfirm(workflow.transactionIngestionId);
      if (reloadedWorkflow?.status === 'COMPLETED') {
        setConfirmationResult(null);
        setConfirmDialogOpen(false);
      } else {
        setConfirmDialogError(translate('fintrackApp.transactionIngestion.workflow.errors.confirmUncertain'));
      }
    } finally {
      confirmationRequestInFlightRef.current = false;
      setConfirmingImport(false);
    }
  };

  const renderCreateForm = () => (
    <Row className="justify-content-center">
      <Col md="10">
        <h2 data-cy="TransactionIngestionWorkflowDetailHeading">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.title">File import workflow</Translate>
        </h2>
        <p className="text-muted">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.help">
            Upload a canonical FINTRACK CSV file to create a persisted workflow.
          </Translate>
        </p>

        <Alert color="info" fade={false}>
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.noTransactionsCreated">
            No transactions were created yet.
          </Translate>
        </Alert>

        {localError ? (
          <Alert color="danger" data-cy="workflowLocalError" fade={false}>
            <Translate contentKey={localError} />
          </Alert>
        ) : null}
        {backendError ? (
          <Alert color="danger" data-cy="workflowBackendError" fade={false}>
            {backendError}
          </Alert>
        ) : null}

        <Form onSubmit={handleSubmit}>
          <FormGroup>
            <Label for="workflow-account">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.account">Account</Translate>
            </Label>
            <Input
              id="workflow-account"
              data-cy="workflowAccount"
              type="select"
              value={accountId}
              disabled={submitting || selectableAccountsLoading}
              onChange={event => setAccountId(event.target.value)}
            >
              <option value="" />
              {selectableAccounts.map(account => (
                <option value={account.id} key={account.id}>
                  {formatFinancialAccountLabel(account)}
                </option>
              ))}
            </Input>
          </FormGroup>
          <FormGroup>
            <Label for="workflow-file">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.csvFile">CSV file</Translate>
            </Label>
            <Input
              id="workflow-file"
              data-cy="workflowFile"
              type="file"
              innerRef={fileInputRef}
              accept=".csv,text/csv"
              disabled={submitting}
              onChange={event => setFile(event.target.files?.[0] ?? null)}
            />
            <small className="form-text text-muted">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.csvHint">
                Expected header: transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
              </Translate>
            </small>
          </FormGroup>
          <Button color="primary" type="submit" disabled={submitting} data-cy="workflowSubmit">
            {submitting ? <Spinner size="sm" /> : <FontAwesomeIcon icon="eye" />}{' '}
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.upload">Upload</Translate>
          </Button>
          &nbsp;
          <Button tag={Link} to="/transaction-ingestion" replace color="info">
            <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
          </Button>
        </Form>
      </Col>
    </Row>
  );

  const renderDateTime = (value?: any) => (value ? <TextFormat value={value} type="date" format={APP_DATE_FORMAT} /> : null);

  const renderReviewHeader = () => {
    const status = workflow?.status ?? transactionIngestionEntity.status;
    const account = transactionIngestionEntity.account;
    const filename = fileMetadata?.originalFilename;

    return (
      <ProductPageHeader
        headingId="transaction-ingestion-review-heading"
        dataCy="workflowReviewHeading"
        title={<Translate contentKey="fintrackApp.transactionIngestion.workflow.reviewTitle">Review Import</Translate>}
        subtitle={
          account || filename || showNoTransactionsCreatedBanner ? (
            <>
              {account ? <Link to={`/financial-account/${account.id}`}>{account.name}</Link> : null}
              {account && filename ? <span aria-hidden="true"> · </span> : null}
              {filename ? <span className="text-break">{filename}</span> : null}
              {showNoTransactionsCreatedBanner ? (
                <>
                  <br />
                  <small>
                    <Translate contentKey="fintrackApp.transactionIngestion.workflow.noTransactionsCreated">
                      No transactions were created yet.
                    </Translate>
                  </small>
                </>
              ) : null}
            </>
          ) : undefined
        }
        metadata={
          status ? (
            <Badge color={ingestionStatusBadgeColor(status)} pill data-cy="workflowHeaderStatus" data-testid="workflowHeaderStatus">
              {renderIngestionStatus(status)}
            </Badge>
          ) : undefined
        }
        actions={
          transactionIngestionEntity.id ? (
            <Button tag={Link} to="/transaction-ingestion" replace color="secondary" outline size="sm" data-cy="entityDetailsBackButton">
              <FontAwesomeIcon icon="arrow-left" />{' '}
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.backToImports">Back to imports</Translate>
            </Button>
          ) : undefined
        }
      />
    );
  };

  const renderConfirmDialog = () => {
    const status = workflow?.status;
    const account = transactionIngestionEntity.account;

    return (
      <Modal
        isOpen={confirmDialogOpen}
        toggle={closeConfirmDialog}
        centered
        backdrop="static"
        aria-labelledby="workflow-confirm-dialog-title"
        data-cy="workflowConfirmDialog"
        data-testid="workflowConfirmDialog"
      >
        <ModalHeader toggle={confirmingImport ? undefined : closeConfirmDialog} id="workflow-confirm-dialog-title">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.title">Confirm import</Translate>
        </ModalHeader>
        <ModalBody>
          <p>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.explanation">
              This will create final financial transactions from the eligible candidates saved in this review.
            </Translate>
          </p>
          <p className="text-muted small">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.backendAuthority">
              Rule previews are not decisions. Disabled rows are excluded, and the server validates the latest saved state before creating
              transactions.
            </Translate>
          </p>
          <dl className="row mb-0" data-cy="workflowConfirmDialogSummary">
            <dt className="col-sm-5">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.account">Account</Translate>
            </dt>
            <dd className="col-sm-7">{account ? formatFinancialAccountLabel(account) : '—'}</dd>
            <dt className="col-sm-5">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.originalFilename">Original filename</Translate>
            </dt>
            <dd className="col-sm-7 text-break">{fileMetadata?.originalFilename || '—'}</dd>
            <dt className="col-sm-5">
              <Translate contentKey="fintrackApp.transactionIngestion.status">Status</Translate>
            </dt>
            <dd className="col-sm-7">{renderIngestionStatus(status)}</dd>
            <dt className="col-sm-5">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.recordsReceived">Records received</Translate>
            </dt>
            <dd className="col-sm-7">{counts.recordsReceived ?? 0}</dd>
            <dt className="col-sm-5">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.validRows">Valid rows</Translate>
            </dt>
            <dd className="col-sm-7">{counts.validRows ?? 0}</dd>
            <dt className="col-sm-5">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.candidateRowsReviewed">
                Candidate rows reviewed
              </Translate>
            </dt>
            <dd className="col-sm-7">{reviewedClassificationRows}</dd>
            <dt className="col-sm-5">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.invalidRows">Invalid rows</Translate>
            </dt>
            <dd className="col-sm-7">{counts.invalidRows ?? 0}</dd>
            <dt className="col-sm-5">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.disabledRows">Disabled rows</Translate>
            </dt>
            <dd className="col-sm-7">{counts.recordsSkipped ?? 0}</dd>
          </dl>
          {confirmDialogError ? (
            <Alert color="danger" className="mt-3 mb-0" fade={false} data-testid="workflowConfirmDialogError">
              {confirmDialogError}
            </Alert>
          ) : null}
        </ModalBody>
        <ModalFooter>
          <Button
            color="secondary"
            outline
            onClick={closeConfirmDialog}
            disabled={confirmingImport}
            data-cy="workflowConfirmDialogCancel"
            data-testid="workflowConfirmDialogCancel"
          >
            <Translate contentKey="entity.action.cancel">Cancel</Translate>
          </Button>
          <Button
            color="success"
            onClick={confirmImport}
            disabled={confirmingImport || workflow?.status !== 'READY' || (workflow?.counts?.validRows ?? 0) === 0}
            data-cy="workflowConfirmDialogSubmit"
            data-testid="workflowConfirmDialogSubmit"
          >
            {confirmingImport ? <Spinner size="sm" /> : null}{' '}
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.submit">Create transactions</Translate>
          </Button>
        </ModalFooter>
      </Modal>
    );
  };

  const renderCompletionCounts = () => (
    <dl className="row row-cols-2 row-cols-md-4 g-2 mb-0 mt-2" data-cy="workflowCompletionCounts" data-testid="workflowCompletionCounts">
      <div className="col">
        <dt className="small text-muted fw-semibold mb-1">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.created">Created</Translate>
        </dt>
        <dd className="mb-0">{counts.recordsCreated ?? 0}</dd>
      </div>
      <div className="col">
        <dt className="small text-muted fw-semibold mb-1">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.skipped">Skipped</Translate>
        </dt>
        <dd className="mb-0">{counts.recordsSkipped ?? 0}</dd>
      </div>
      <div className="col">
        <dt className="small text-muted fw-semibold mb-1">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.rejected">Rejected</Translate>
        </dt>
        <dd className="mb-0">{counts.recordsRejected ?? 0}</dd>
      </div>
      {confirmationResult ? (
        <div className="col">
          <dt className="small text-muted fw-semibold mb-1">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.createdNow">Created now</Translate>
          </dt>
          <dd className="mb-0">{confirmationResult.createdNow ?? 0}</dd>
        </div>
      ) : null}
    </dl>
  );

  const renderReviewSummary = () => {
    if (!workflow) {
      return null;
    }

    const completedWorkflow = workflow.status === 'COMPLETED' || workflow.status === 'PARTIALLY_COMPLETED';
    const metrics = completedWorkflow
      ? [
          {
            label: <Translate contentKey="fintrackApp.transactionIngestion.workflow.recordsReceived">Records received</Translate>,
            value: counts.recordsReceived ?? 0,
          },
          {
            label: <Translate contentKey="fintrackApp.transactionIngestion.recordsCreated">Records created</Translate>,
            value: counts.recordsCreated ?? 0,
          },
          {
            label: <Translate contentKey="fintrackApp.transactionIngestion.recordsSkipped">Records skipped</Translate>,
            value: counts.recordsSkipped ?? 0,
          },
          {
            label: <Translate contentKey="fintrackApp.transactionIngestion.workflow.recordsRejected">Records rejected</Translate>,
            value: counts.recordsRejected ?? 0,
          },
        ]
      : [
          {
            label: <Translate contentKey="fintrackApp.transactionIngestion.workflow.recordsReceived">Records received</Translate>,
            value: counts.recordsReceived ?? 0,
          },
          {
            label: <Translate contentKey="fintrackApp.transactionIngestion.workflow.validRows">Valid rows</Translate>,
            value: counts.validRows ?? 0,
          },
          {
            label: <Translate contentKey="fintrackApp.transactionIngestion.workflow.invalidRows">Invalid rows</Translate>,
            value: counts.invalidRows ?? 0,
          },
          {
            label: <Translate contentKey="fintrackApp.transactionIngestion.workflow.disabledRows">Disabled rows</Translate>,
            value: counts.recordsSkipped ?? 0,
          },
        ];

    return (
      <ProductSection
        title={<Translate contentKey="fintrackApp.transactionIngestion.workflow.reviewSummary">Review summary</Translate>}
        className="mb-3"
        dataCy="workflowReviewSummary"
      >
        <dl className="row row-cols-2 row-cols-md-4 g-3 mb-0" data-cy="workflowCounts" data-testid="workflowCounts">
          {metrics.map(({ label, value }, index) => (
            <div className="col" key={index}>
              <dt className="small text-muted fw-semibold mb-1">{label}</dt>
              <dd className="h5 mb-0">{value}</dd>
            </div>
          ))}
        </dl>
      </ProductSection>
    );
  };

  const renderFileMetadata = () =>
    fileMetadata ? (
      <ProductSection
        title={<Translate contentKey="fintrackApp.transactionIngestion.workflow.fileDetails">File details</Translate>}
        className="mb-3"
        dataCy="workflowFileDetails"
      >
        <details data-cy="workflowFileDetailsDisclosure" data-testid="workflowFileDetailsDisclosure">
          <summary className="fw-semibold">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.showFileDetails">Show file details</Translate>
          </summary>
          <dl className="row mb-0 mt-3" data-cy="workflowMetadata">
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.originalFilename">Original filename</Translate>
            </dt>
            <dd className="col-sm-8 text-break">{fileMetadata.originalFilename}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.ingestionType">Ingestion type</Translate>
            </dt>
            <dd className="col-sm-8">{transactionIngestionEntity.ingestionType}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.sourceLabel">Source label</Translate>
            </dt>
            <dd className="col-sm-8 text-break">{workflow?.sourceLabel ?? transactionIngestionEntity.sourceLabel}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.fileType">File type</Translate>
            </dt>
            <dd className="col-sm-8">{fileMetadata.fileType}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.contentType">Content type</Translate>
            </dt>
            <dd className="col-sm-8 text-break">{fileMetadata.contentType}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.fileSizeBytes">File size bytes</Translate>
            </dt>
            <dd className="col-sm-8">{fileMetadata.fileSizeBytes}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.checksum">Checksum</Translate>
            </dt>
            <dd className="col-sm-8 text-break">{fileMetadata.checksum}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.storageKey">Storage key</Translate>
            </dt>
            <dd className="col-sm-8 text-break">{fileMetadata.storageKey}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.parserName">Parser name</Translate>
            </dt>
            <dd className="col-sm-8">{fileMetadata.parserName}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.parserVersion">Parser version</Translate>
            </dt>
            <dd className="col-sm-8">{fileMetadata.parserVersion}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.statementStartDate">Statement start date</Translate>
            </dt>
            <dd className="col-sm-8">{fileMetadata.statementStartDate}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.statementEndDate">Statement end date</Translate>
            </dt>
            <dd className="col-sm-8">{fileMetadata.statementEndDate}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.startedAt">Started at</Translate>
            </dt>
            <dd className="col-sm-8">{renderDateTime(transactionIngestionEntity.startedAt)}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.completedAt">Completed at</Translate>
            </dt>
            <dd className="col-sm-8">{renderDateTime(transactionIngestionEntity.completedAt)}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.createdAt">Created at</Translate>
            </dt>
            <dd className="col-sm-8">{renderDateTime(transactionIngestionEntity.createdAt)}</dd>
            <dt className="col-sm-4">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.fileCreatedAt">File created at</Translate>
            </dt>
            <dd className="col-sm-8">{renderDateTime(fileMetadata.createdAt)}</dd>
          </dl>
        </details>
      </ProductSection>
    ) : null;

  const renderClassificationNotes = (row?: IFileImportCandidateRulePreviewRow) => {
    const matchedRules = row?.matchedRules ?? [];
    const conflicts = row?.conflicts ?? [];
    const skippedOutputs = row?.skippedOutputs ?? [];
    const candidateId = row?.candidateId;

    return (
      <div className="small text-muted">
        {matchedRules.length > 0 ? (
          <div data-testid={`classificationMatchedRules-${candidateId}`}>
            <strong>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.matchedRules">Matched rules</Translate>:
            </strong>{' '}
            {matchedRules.map(rule => rule.ruleName || rule.ruleId).join(', ')}
          </div>
        ) : null}
        {conflicts.length > 0 ? (
          <div data-testid={`classificationConflicts-${candidateId}`}>
            <strong>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.conflicts">Conflicts</Translate>:
            </strong>{' '}
            {conflicts.map(conflict => conflict.reason || conflict.field).join(', ')}
          </div>
        ) : null}
        {skippedOutputs.length > 0 ? (
          <div data-testid={`classificationSkippedOutputs-${candidateId}`}>
            <strong>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.skippedOutputs">Skipped</Translate>:
            </strong>{' '}
            {skippedOutputs.map(skipped => skipped.reason || skipped.field).join(', ')}
          </div>
        ) : null}
      </div>
    );
  };

  const renderCandidateClassificationStatus = (candidate?: IFileImportCandidateWorkflowSummary | null) => {
    if (!candidate?.classificationReviewStatus) {
      return '';
    }
    return translate(
      `fintrackApp.transactionIngestion.workflow.classificationReviewStatus.${candidate.classificationReviewStatus}`,
      candidate.classificationReviewStatus,
    );
  };

  const reviewTestId = (name: string, candidateId: number | undefined, presentation: 'desktop' | 'mobile') =>
    `${presentation === 'desktop' ? '' : 'mobile'}${name}-${candidateId}`;

  const renderClassificationCategoryControl = (
    row: ICsvIngestionWorkflowRow,
    candidate: IFileImportCandidateWorkflowSummary | null | undefined,
    preview: IFileImportCandidateRulePreviewRow | undefined,
    actionBusy: boolean,
    rowBlocked: string | null,
    presentation: 'desktop' | 'mobile' = 'desktop',
  ) => {
    const candidateId = candidate?.id;
    const completedFinancialTransaction = row.financialTransaction;
    const compatibleCategories = categories.filter(category => categoryCompatibleWithFlow(category, candidate?.flow ?? row.flow));
    const previewCategoryName = suggestedCategoryName(preview);
    const categorySuggestionApplicable = hasApplicableSuggestedCategory(candidate, preview);
    const editable = reviewActionsEnabled && row.status === 'VALID' && Boolean(candidateId) && !rowBlocked;
    const completedCategory = completedFinancialTransaction?.category;
    const categoryValue = completedCategoryLabel(completedCategory) ?? candidate?.categoryName;
    const categoryColor = completedCategory?.color ?? categories.find(category => category.id === candidate?.categoryId)?.color;

    return (
      <div className="candidate-review-classification-control">
        <span className="candidate-review-classification-label">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.currentCategory">Current category</Translate>
        </span>
        {editable ? (
          <Input
            bsSize="sm"
            type="select"
            className="candidate-review-classification-editor mt-1"
            style={categoryIdentityStyle(categoryColor)}
            aria-label={translate('fintrackApp.transactionIngestion.workflow.classification.editCategory', {
              recordIndex: row.recordIndex ?? '',
            })}
            value={candidate?.categoryId ?? ''}
            disabled={actionBusy || hasUnsavedRowEdit}
            onChange={event => candidateId !== undefined && updateClassificationCategory(candidateId, event.target.value)}
            data-testid={reviewTestId('classificationCategory', candidateId, presentation)}
          >
            <option value="">{translate('fintrackApp.transactionIngestion.workflow.classification.noCategory')}</option>
            {compatibleCategories.map(category => (
              <option value={category.id} key={category.id} disabled={category.active === false && category.id !== candidate?.categoryId}>
                {category.name}
                {category.active === false ? ` (${translate('fintrackApp.category.inactive')})` : ''}
              </option>
            ))}
          </Input>
        ) : completedFinancialTransaction?.id ? (
          categoryValue ? (
            <span
              className="candidate-review-category-value candidate-review-category-read-only mt-1"
              style={categoryIdentityStyle(categoryColor)}
              data-testid={`classificationCategoryFinal-${completedFinancialTransaction.id}`}
            >
              {categoryValue}
              {completedCategory?.active === false ? (
                <span className="candidate-review-category-inactive"> ({translate('fintrackApp.category.inactive')})</span>
              ) : null}
            </span>
          ) : (
            <span
              className="candidate-review-category-empty text-muted d-inline-block mt-1"
              data-testid={`classificationCategoryEmptyFinal-${completedFinancialTransaction.id}`}
            >
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.noCategory">No category</Translate>
            </span>
          )
        ) : candidateId ? (
          <span className="visually-hidden" data-testid={`classificationCategoryReadOnly-${candidateId}`}>
            {candidate?.categoryName ?? ''}
          </span>
        ) : row.status === 'VALID' ? (
          <small className="text-muted" data-testid={`classificationCategoryPreparing-${row.ingestionRecordId ?? row.recordIndex}`}>
            <Spinner size="sm" /> <Translate contentKey="entity.action.loading">Loading...</Translate>
          </small>
        ) : null}
        {previewCategoryName ? (
          <div
            className="candidate-review-suggestion d-block mt-2"
            data-testid={reviewTestId('classificationSuggestedCategory', candidateId, presentation)}
          >
            <span>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.suggested">Suggested</Translate>:{' '}
              {previewCategoryName}
            </span>
            {editable && categorySuggestionApplicable ? (
              <Button
                size="sm"
                color="primary"
                className="candidate-review-suggestion-apply"
                disabled={actionBusy || hasUnsavedRowEdit}
                aria-label={translate('fintrackApp.transactionIngestion.workflow.classification.applySuggestedCategoryAria', {
                  category: previewCategoryName,
                  description: row.description ?? `#${row.recordIndex ?? ''}`,
                })}
                onClick={() => candidateId !== undefined && applySuggestionsForCandidate(candidateId, 'CATEGORY')}
                data-testid={reviewTestId('classificationApplySuggestedCategory', candidateId, presentation)}
              >
                {classificationActionInProgress === candidateId ? (
                  <>
                    <Spinner size="sm" />{' '}
                    <span className="visually-hidden">
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.applying">Applying</Translate>
                    </span>
                  </>
                ) : (
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.apply">Apply</Translate>
                )}
              </Button>
            ) : null}
          </div>
        ) : null}
      </div>
    );
  };

  const renderClassificationTagsControl = (
    row: ICsvIngestionWorkflowRow,
    candidate: IFileImportCandidateWorkflowSummary | null | undefined,
    preview: IFileImportCandidateRulePreviewRow | undefined,
    actionBusy: boolean,
    rowBlocked: string | null,
    presentation: 'desktop' | 'mobile' = 'desktop',
  ) => {
    const candidateId = candidate?.id;
    const completedFinancialTransaction = row.financialTransaction;
    const selectedTags = candidateSelectedTags(candidate, tags);
    const tagSuggestionApplicable = hasApplicableSuggestedTags(candidate, preview);
    const editable = reviewActionsEnabled && row.status === 'VALID' && Boolean(candidateId) && !rowBlocked;
    const dataCy = reviewTestId('classificationTags', candidateId, presentation);

    return (
      <div className="candidate-review-classification-control">
        <span className="candidate-review-classification-label">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.currentTags">Current tags</Translate>
        </span>
        {editable ? (
          <ProductTagSelector
            id={`classification-tags-${presentation}-${candidateId}`}
            dataCy={dataCy}
            selectedTags={selectedTags}
            availableTags={tags}
            loading={tagsLoading}
            error={tagsLoadError}
            disabled={actionBusy || hasUnsavedRowEdit}
            onChange={nextTags =>
              candidateId !== undefined &&
              updateClassificationTags(
                candidateId,
                nextTags.map(tag => tag.id?.toString()).filter((tagId): tagId is string => tagId !== undefined),
              )
            }
          />
        ) : completedFinancialTransaction?.id ? (
          <div className="candidate-review-completed-tags mt-1" data-testid={`classificationTagsFinal-${completedFinancialTransaction.id}`}>
            <TransactionTagChips
              tags={completedFinancialTransaction.tags ?? []}
              maxVisible={Number.MAX_SAFE_INTEGER}
              showEmpty
              showInactiveLabel
            />
          </div>
        ) : candidateId ? (
          <span data-testid={`classificationTagsReadOnly-${candidateId}`}>{(candidate?.tagNames ?? []).join(', ')}</span>
        ) : row.status === 'VALID' ? (
          <small className="text-muted" data-testid={`classificationTagsPreparing-${row.ingestionRecordId ?? row.recordIndex}`}>
            <Spinner size="sm" /> <Translate contentKey="entity.action.loading">Loading...</Translate>
          </small>
        ) : null}
        {(preview?.suggestedTags ?? []).length > 0 ? (
          <div
            className="candidate-review-suggestion d-block mt-2"
            data-testid={reviewTestId('classificationSuggestedTags', candidateId, presentation)}
          >
            <span>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.suggested">Suggested</Translate>:{' '}
              {(preview?.suggestedTags ?? []).map(tag => suggestedTagName(tag)).join(', ')}
            </span>
            {editable && tagSuggestionApplicable ? (
              <Button
                size="sm"
                color="primary"
                className="candidate-review-suggestion-apply"
                disabled={actionBusy || hasUnsavedRowEdit}
                aria-label={translate('fintrackApp.transactionIngestion.workflow.classification.applySuggestedTagsAria', {
                  tags: (preview?.suggestedTags ?? []).map(tag => suggestedTagName(tag)).join(', '),
                  description: row.description ?? `#${row.recordIndex ?? ''}`,
                })}
                onClick={() => candidateId !== undefined && applySuggestionsForCandidate(candidateId, 'TAGS')}
                data-testid={reviewTestId('classificationApplySuggestedTags', candidateId, presentation)}
              >
                {classificationActionInProgress === candidateId ? (
                  <>
                    <Spinner size="sm" />{' '}
                    <span className="visually-hidden">
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.applying">Applying</Translate>
                    </span>
                  </>
                ) : (
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.apply">Apply</Translate>
                )}
              </Button>
            ) : null}
          </div>
        ) : null}
      </div>
    );
  };

  const renderReviewActions = (
    row: ICsvIngestionWorkflowRow,
    isEditing: boolean,
    actionBusy: boolean,
    presentation: 'desktop' | 'mobile' = 'desktop',
  ) => {
    const recordKey = row.ingestionRecordId ?? row.recordIndex;
    const suffix = presentation === 'desktop' ? '' : '-mobile';
    return showActionsColumn ? (
      <div className="d-flex flex-wrap gap-2">
        {!isEditing && row.status === 'VALID' && row.candidate?.id && !candidateBlocksClassification(row) ? (
          <Button
            size="sm"
            color="secondary"
            disabled={classificationReevaluationRunning || hasUnsavedRowEdit}
            onClick={() => reevaluateRow(row)}
            data-testid={`workflowRowReevaluate-${recordKey}${suffix}`}
          >
            {reevaluationInProgress?.kind === 'ROW' && reevaluationInProgress.recordId === row.ingestionRecordId ? (
              <Spinner size="sm" />
            ) : (
              <FontAwesomeIcon icon="sync" />
            )}{' '}
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.reevaluation.row">Reevaluate this row</Translate>
          </Button>
        ) : null}
        {!isEditing && row.status === 'VALID' && row.candidate?.id ? (
          <UncontrolledDropdown group size="sm" data-testid={`workflowRowCreateRule-${recordKey}${suffix}`}>
            <DropdownToggle
              caret
              color="secondary"
              disabled={actionBusy || hasUnsavedRowEdit}
              data-cy={`workflowRowCreateRuleToggle-${recordKey}${suffix}`}
            >
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.createFromRow">Create rule</Translate>
            </DropdownToggle>
            <DropdownMenu>
              <DropdownItem
                onClick={() => openRuleCreation('NORMALIZATION', row)}
                data-testid={`workflowRowCreateNormalization-${recordKey}${suffix}`}
              >
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.createNormalizationFromRow">
                  Create normalization rule from this row
                </Translate>
              </DropdownItem>
              <DropdownItem
                onClick={() => openRuleCreation('TRANSACTION', row)}
                data-testid={`workflowRowCreateTransaction-${recordKey}${suffix}`}
              >
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.createTransactionFromRow">
                  Create transaction rule from this row
                </Translate>
              </DropdownItem>
            </DropdownMenu>
          </UncontrolledDropdown>
        ) : null}
        {!isEditing && canEdit(row) ? (
          <Button
            size="sm"
            color="primary"
            disabled={actionBusy || hasUnsavedRowEdit}
            onClick={() => startEditingRow(row)}
            data-testid={`workflowRowEdit-${recordKey}${suffix}`}
          >
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.edit">Edit</Translate>
          </Button>
        ) : null}
        {!isEditing && canDisable(row) ? (
          <Button
            size="sm"
            color="warning"
            disabled={actionBusy || hasUnsavedRowEdit}
            onClick={() => performRowAction(row, 'disable')}
            data-testid={`workflowRowDisable-${recordKey}${suffix}`}
          >
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.disable">Disable</Translate>
          </Button>
        ) : null}
        {!isEditing && canEnable(row) ? (
          <Button
            size="sm"
            color="success"
            disabled={actionBusy || hasUnsavedRowEdit}
            onClick={() => performRowAction(row, 'enable')}
            data-testid={`workflowRowEnable-${recordKey}${suffix}`}
          >
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.enable">Enable</Translate>
          </Button>
        ) : null}
      </div>
    ) : null;
  };

  const renderGeneralCandidateEditor = (row: ICsvIngestionWorkflowRow, actionBusy: boolean, presentation: 'desktop' | 'mobile') => {
    const recordKey = row.ingestionRecordId ?? row.recordIndex;
    const suffix = presentation === 'desktop' ? '' : '-mobile';
    return (
      <div className="candidate-review-edit-form" data-testid={`workflowRowEditForm-${recordKey}${suffix}`}>
        <Row className="g-3">
          <Col md="4">
            <Label for={`workflow-row-date-${presentation}-${recordKey}`}>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.transactionDate">Transaction date</Translate>
            </Label>
            <Input
              id={`workflow-row-date-${presentation}-${recordKey}`}
              bsSize="sm"
              value={editDraft.transactionDate ?? ''}
              onChange={event => updateEditDraft('transactionDate', event.target.value)}
            />
          </Col>
          <Col md="4">
            <Label for={`workflow-row-posting-date-${presentation}-${recordKey}`}>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.postingDate">Posting date</Translate>
            </Label>
            <Input
              id={`workflow-row-posting-date-${presentation}-${recordKey}`}
              bsSize="sm"
              value={editDraft.postingDate ?? ''}
              onChange={event => updateEditDraft('postingDate', event.target.value)}
            />
          </Col>
          <Col md="4">
            <Label for={`workflow-row-currency-${presentation}-${recordKey}`}>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.currency">Currency</Translate>
            </Label>
            <Input
              id={`workflow-row-currency-${presentation}-${recordKey}`}
              bsSize="sm"
              value={editDraft.currency ?? ''}
              onChange={event => updateEditDraft('currency', event.target.value)}
            />
          </Col>
          <Col md="12">
            <Label for={`workflow-row-description-${presentation}-${recordKey}`}>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.description">Description</Translate>
            </Label>
            <Input
              id={`workflow-row-description-${presentation}-${recordKey}`}
              bsSize="sm"
              value={editDraft.description ?? ''}
              onChange={event => updateEditDraft('description', event.target.value)}
            />
          </Col>
          <Col md="4">
            <Label for={`workflow-row-signed-amount-${presentation}-${recordKey}`}>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.signedAmount">Signed amount</Translate>
            </Label>
            <Input
              id={`workflow-row-signed-amount-${presentation}-${recordKey}`}
              bsSize="sm"
              value={editDraft.signedAmount ?? ''}
              onChange={event => updateEditDraft('signedAmount', event.target.value)}
            />
          </Col>
          <Col md="4">
            <Label for={`workflow-row-reference-${presentation}-${recordKey}`}>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.externalReference">External reference</Translate>
            </Label>
            <Input
              id={`workflow-row-reference-${presentation}-${recordKey}`}
              bsSize="sm"
              value={editDraft.externalReference ?? ''}
              onChange={event => updateEditDraft('externalReference', event.target.value)}
            />
          </Col>
          <Col md="4">
            <Label for={`workflow-row-notes-${presentation}-${recordKey}`}>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.notes">Notes</Translate>
            </Label>
            <Input
              id={`workflow-row-notes-${presentation}-${recordKey}`}
              bsSize="sm"
              value={editDraft.notes ?? ''}
              onChange={event => updateEditDraft('notes', event.target.value)}
            />
          </Col>
        </Row>
        <div className="d-flex flex-wrap gap-2 mt-3">
          <Button
            size="sm"
            color="primary"
            disabled={actionBusy}
            onClick={() => saveEditingRow(row)}
            data-testid={`workflowRowSave-${recordKey}${suffix}`}
          >
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.saveRow">Save row</Translate>
          </Button>
          <Button
            size="sm"
            color="secondary"
            disabled={actionBusy}
            onClick={cancelEditingRow}
            data-testid={`workflowRowCancel-${recordKey}${suffix}`}
          >
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.cancel">Cancel</Translate>
          </Button>
        </div>
      </div>
    );
  };

  const renderCandidateTransactionData = (
    row: ICsvIngestionWorkflowRow,
    isEditing: boolean,
    actionBusy: boolean,
    presentation: 'desktop' | 'mobile',
  ) => (
    <Col lg="6">
      <h4 className="candidate-review-detail-heading">
        <Translate contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.transactionData">Transaction data</Translate>
      </h4>
      {isEditing ? (
        renderGeneralCandidateEditor(row, actionBusy, presentation)
      ) : (
        <dl className="row candidate-review-data-list mb-0">
          <dt className="col-sm-5">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.transactionDate">Transaction date</Translate>
          </dt>
          <dd className="col-sm-7">{row.transactionDate || '—'}</dd>
          <dt className="col-sm-5">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.postingDate">Posting date</Translate>
          </dt>
          <dd className="col-sm-7">{row.postingDate || '—'}</dd>
          <dt className="col-sm-5">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.signedAmount">Signed amount</Translate>
          </dt>
          <dd className="col-sm-7">
            {row.signedAmount || '—'} {row.currency}
          </dd>
          <dt className="col-sm-5">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.amount">Amount</Translate>
          </dt>
          <dd className="col-sm-7">{row.amount || '—'}</dd>
          <dt className="col-sm-5">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.flow">Flow</Translate>
          </dt>
          <dd className="col-sm-7">{flowLabel(row.flow) || '—'}</dd>
          <dt className="col-sm-5">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.currency">Currency</Translate>
          </dt>
          <dd className="col-sm-7">{row.currency || '—'}</dd>
          <dt className="col-sm-5">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.externalReference">External reference</Translate>
          </dt>
          <dd className="col-sm-7 text-break">{row.externalReference || '—'}</dd>
          <dt className="col-sm-5">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.notes">Notes</Translate>
          </dt>
          <dd className="col-sm-7 text-break">{row.notes || '—'}</dd>
        </dl>
      )}
    </Col>
  );

  const renderCandidateDescriptionDetail = (row: ICsvIngestionWorkflowRow, isEditing: boolean) => (
    <Col lg="6">
      <h4 className="candidate-review-detail-heading">
        <Translate contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.descriptionNormalization">
          Description and normalization
        </Translate>
      </h4>
      <dl className="row candidate-review-data-list mb-2">
        <dt className="col-sm-5">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.descriptionReview.originalDescription">
            Original description
          </Translate>
        </dt>
        <dd className="col-sm-7 text-break">{row.descriptionReview?.originalDescription || row.description || '—'}</dd>
        <dt className="col-sm-5">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.currentDescription">
            Current description
          </Translate>
        </dt>
        <dd className="col-sm-7 text-break">{row.description || '—'}</dd>
      </dl>
      {!isEditing ? (
        <DescriptionReviewDisplay
          row={row}
          reevaluation={descriptionReevaluationByRecordId[row.ingestionRecordId ?? -1]}
          onApplySuggestion={reviewActionsEnabled && row.status === 'VALID' && !hasUnsavedRowEdit ? applyDescriptionSuggestion : undefined}
          applyingSuggestion={descriptionApplicationInProgress === row.ingestionRecordId}
        />
      ) : null}
    </Col>
  );

  const renderCandidateClassificationExplanation = (
    row: ICsvIngestionWorkflowRow,
    candidate: IFileImportCandidateWorkflowSummary | null | undefined,
    preview: IFileImportCandidateRulePreviewRow | undefined,
    rowBlocked: string | null,
    actionBusy: boolean,
    presentation: 'desktop' | 'mobile',
  ) => {
    const candidateId = candidate?.id;
    const suffix = presentation === 'desktop' ? '' : '-mobile';
    const hasApplicablePreview = hasApplicableSuggestedCategory(candidate, preview) || hasApplicableSuggestedTags(candidate, preview);

    return (
      <Col lg="6">
        <h4 className="candidate-review-detail-heading">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.classificationExplanation">
            Classification explanation
          </Translate>
        </h4>
        {candidateId ? (
          <>
            <div data-testid={`classificationStatus-${candidateId}`}>
              <strong>
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.reviewStatus">
                  Saved review status
                </Translate>
                :
              </strong>{' '}
              {renderCandidateClassificationStatus(candidate)}
            </div>
            {hasApplicablePreview ? (
              <small
                className="candidate-review-state-help d-block mt-1"
                data-testid={`classificationPreviewStateHelp-${candidateId}${suffix}`}
              >
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.suggestionIsPreview">
                  This is a current rule preview. It is not saved and does not change the persisted review status.
                </Translate>
              </small>
            ) : null}
            {renderClassificationNotes(preview)}
            {reviewActionsEnabled && row.status === 'VALID' && preview && !preview.hasSuggestions ? (
              <Button
                size="sm"
                color="secondary"
                className="mt-2"
                disabled={actionBusy || Boolean(rowBlocked) || hasUnsavedRowEdit}
                onClick={() => confirmCandidateHasNoSuggestions(candidateId)}
                data-testid={`classificationConfirmNoSuggestions-${candidateId}${suffix}`}
              >
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.confirmNoSuggestions">
                  Confirm no suggestions
                </Translate>
              </Button>
            ) : null}
          </>
        ) : row.status === 'VALID' ? (
          <small className="text-muted">
            <Spinner size="sm" /> <Translate contentKey="entity.action.loading">Loading...</Translate>
          </small>
        ) : (
          <small className="text-muted">{row.errorMessage || row.errorCode || '—'}</small>
        )}
      </Col>
    );
  };

  const renderCandidateRowActions = (
    row: ICsvIngestionWorkflowRow,
    isEditing: boolean,
    actionBusy: boolean,
    presentation: 'desktop' | 'mobile',
  ) => {
    const recordKey = row.ingestionRecordId ?? row.recordIndex;
    const suffix = presentation === 'desktop' ? '' : '-mobile';

    return (
      <Col lg="6">
        <h4 className="candidate-review-detail-heading">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.rowActions">Row actions</Translate>
        </h4>
        {!isEditing ? renderReviewActions(row, isEditing, actionBusy, presentation) : null}
        {rowHasReviewError(row) ? (
          <div className="small text-danger mt-2" role="alert" data-testid={`workflowRowError-${recordKey}${suffix}`}>
            {row.errorMessage || row.errorCode}
          </div>
        ) : null}
      </Col>
    );
  };

  const renderCandidateDetail = (row: ICsvIngestionWorkflowRow, presentation: 'desktop' | 'mobile') => {
    const candidate = row.candidate;
    const candidateId = candidate?.id;
    const preview = candidateId === undefined ? undefined : classificationPreviewByCandidateId[candidateId];
    const rowBlocked = candidateBlocksClassification(row);
    const isEditing = editingRecordId === row.ingestionRecordId;
    const actionBusy =
      processing ||
      applyingAllScopedSuggestions ||
      reviewActionInProgress === row.ingestionRecordId ||
      (candidateId !== undefined && classificationActionInProgress === candidateId) ||
      (hasUnsavedRowEdit && !isEditing);

    return (
      <section
        className="candidate-review-detail"
        aria-label={translate('fintrackApp.transactionIngestion.workflow.candidateQueue.detail')}
      >
        <Row className="g-4">
          {presentation === 'mobile' ? (
            <>
              <Col lg="6">
                <h4 className="candidate-review-detail-heading">
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.category">Category</Translate>
                </h4>
                {renderClassificationCategoryControl(row, candidate, preview, actionBusy || isEditing, rowBlocked, 'mobile')}
              </Col>
              <Col lg="6">
                <h4 className="candidate-review-detail-heading">
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.tags">Tags</Translate>
                </h4>
                {renderClassificationTagsControl(row, candidate, preview, actionBusy || isEditing, rowBlocked, 'mobile')}
              </Col>
            </>
          ) : null}
          {renderCandidateTransactionData(row, isEditing, actionBusy, presentation)}
          {renderCandidateDescriptionDetail(row, isEditing)}
          {renderCandidateClassificationExplanation(row, candidate, preview, rowBlocked, actionBusy, presentation)}
          {renderCandidateRowActions(row, isEditing, actionBusy, presentation)}
        </Row>
      </section>
    );
  };

  const renderReviewFilter = (filter: CandidateReviewFilter) => (
    <Button
      key={filter}
      size="sm"
      color={candidateReviewFilter === filter ? 'primary' : 'secondary'}
      outline={candidateReviewFilter !== filter}
      onClick={() => setCandidateReviewFilter(filter)}
      aria-pressed={candidateReviewFilter === filter}
      data-testid={`candidateReviewFilter-${filter}`}
    >
      <Translate contentKey={`fintrackApp.transactionIngestion.workflow.candidateQueue.filters.${filter}`} />
    </Button>
  );

  const renderCandidateReviewQueue = () => (
    <ProductSection
      title={<Translate contentKey="fintrackApp.transactionIngestion.workflow.recordsTable">Ingestion records</Translate>}
      className="mb-3"
      dataCy="workflowCandidateReviewQueue"
    >
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
        <div
          className="btn-group"
          role="group"
          aria-label={translate('fintrackApp.transactionIngestion.workflow.candidateQueue.filters.label')}
        >
          {candidateReviewFilters.map(renderReviewFilter)}
        </div>
        <small className="text-muted" data-testid="candidateReviewVisibleCount">
          <Translate
            contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.visibleCount"
            interpolate={{ count: visibleRows.length, total: rows.length }}
          >
            Showing {visibleRows.length.toString()} of {rows.length.toString()} movements
          </Translate>
        </small>
      </div>
      <small className="text-muted d-block mb-3">
        <Translate contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.filterScopeHint">
          Filters only change what is shown. Review and import actions still use the entire import.
        </Translate>
      </small>
      {hasUnsavedRowEdit && !editingRowVisible ? (
        <Alert color="info" fade={false} data-testid="candidateReviewHiddenEditNotice">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.hiddenEditNotice">
            An unsaved row edit is preserved. Return to All or a matching filter to continue it.
          </Translate>
        </Alert>
      ) : null}
      {visibleRows.length === 0 ? (
        <div className="candidate-review-empty text-center py-4" data-testid="candidateReviewEmptyFilter">
          <p className="mb-2">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.emptyFilter">
              No movements match this filter.
            </Translate>
          </p>
          {candidateReviewFilter !== 'ALL' ? (
            <Button size="sm" color="secondary" outline onClick={() => setCandidateReviewFilter('ALL')}>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.showAll">Show all movements</Translate>
            </Button>
          ) : null}
        </div>
      ) : (
        <>
          {!isMobileCandidateReview ? (
            <div className="candidate-review-desktop">
              <Table responsive className="candidate-review-table align-middle mb-0" data-cy="workflowRows">
                <thead>
                  <tr>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.date">Date</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.movement">Movement</Translate>
                    </th>
                    <th className="text-end">
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.amount">Amount</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.category">Category</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.tags">Tags</Translate>
                    </th>
                    <th className="text-end">
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.details">Details</Translate>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {visibleRows.map(row => {
                    const candidate = row.candidate;
                    const candidateId = candidate?.id;
                    const classificationPreview = candidateId === undefined ? undefined : classificationPreviewByCandidateId[candidateId];
                    const rowBlocked = candidateBlocksClassification(row);
                    const isEditing = editingRecordId === row.ingestionRecordId;
                    const actionBusy =
                      processing ||
                      applyingAllScopedSuggestions ||
                      reviewActionInProgress === row.ingestionRecordId ||
                      (candidateId !== undefined && classificationActionInProgress === candidateId) ||
                      hasUnsavedRowEdit;
                    const identity = rowIdentity(row);
                    const expanded = expandedRowIds.has(identity);
                    const recordKey = row.ingestionRecordId ?? row.recordIndex;
                    return (
                      <React.Fragment key={identity}>
                        <tr className={row.status === 'DISABLED' ? 'text-muted' : ''} data-testid={`workflowRow-${recordKey}`}>
                          <td className="text-nowrap">{row.transactionDate || '—'}</td>
                          <td className="candidate-review-movement">
                            <div className="fw-semibold text-break">{row.description || '—'}</div>
                            <div className="d-flex flex-wrap gap-1 mt-1">
                              <Badge color={recordStatusColor(row.status)} pill data-testid={`workflowRowStatus-${recordKey}`}>
                                {recordStatusLabel(row.status)}
                              </Badge>
                              {candidate?.classificationReviewStatus ? (
                                <Badge color="light" className="border text-dark fw-normal" pill>
                                  {renderCandidateClassificationStatus(candidate)}
                                </Badge>
                              ) : null}
                              {row.descriptionReview?.source === 'DESCRIPTION_RULE' ? (
                                <Badge color="info" pill>
                                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.descriptionReview.autoNormalized">
                                    Auto-normalized
                                  </Translate>
                                </Badge>
                              ) : null}
                            </div>
                            {rowHasReviewError(row) ? (
                              <small className="text-danger d-block mt-1">{row.errorMessage || row.errorCode}</small>
                            ) : null}
                          </td>
                          <td
                            className={`text-end text-nowrap fw-semibold ${row.flow === 'IN' ? 'text-success' : row.flow === 'OUT' ? 'text-danger' : ''}`}
                          >
                            {row.signedAmount || '—'} {row.currency || ''}
                          </td>
                          <td>{renderClassificationCategoryControl(row, candidate, classificationPreview, actionBusy, rowBlocked)}</td>
                          <td>{renderClassificationTagsControl(row, candidate, classificationPreview, actionBusy, rowBlocked)}</td>
                          <td className="text-end">
                            <Button
                              size="sm"
                              color="secondary"
                              outline
                              disabled={isEditing && expanded}
                              onClick={() => toggleExpandedRow(row)}
                              aria-expanded={expanded}
                              aria-controls={`candidate-review-detail-${identity}`}
                              data-testid={`workflowRowToggleDetails-${recordKey}`}
                            >
                              <Translate
                                contentKey={
                                  expanded
                                    ? 'fintrackApp.transactionIngestion.workflow.candidateQueue.collapse'
                                    : 'fintrackApp.transactionIngestion.workflow.candidateQueue.expand'
                                }
                              >
                                {expanded ? 'Hide details' : 'Show details'}
                              </Translate>
                            </Button>
                          </td>
                        </tr>
                        {expanded ? (
                          <tr className="candidate-review-expanded-row">
                            <td colSpan={6} id={`candidate-review-detail-${identity}`} data-testid={`workflowRowDetail-${recordKey}`}>
                              {renderCandidateDetail(row, 'desktop')}
                            </td>
                          </tr>
                        ) : null}
                      </React.Fragment>
                    );
                  })}
                </tbody>
              </Table>
            </div>
          ) : null}
          {isMobileCandidateReview ? (
            <div className="candidate-review-mobile" data-testid="candidateReviewMobileCards">
              {visibleRows.map(row => {
                const candidate = row.candidate;
                const completedFinancialTransaction = row.financialTransaction;
                const completedCategory = completedFinancialTransaction?.category;
                const mobileCategoryName = completedCategoryLabel(completedCategory) ?? candidate?.categoryName;
                const mobileCategoryColor =
                  completedCategory?.color ?? categories.find(category => category.id === candidate?.categoryId)?.color;
                const identity = rowIdentity(row);
                const expanded = expandedRowIds.has(identity);
                const recordKey = row.ingestionRecordId ?? row.recordIndex;
                return (
                  <article
                    key={identity}
                    className={`candidate-review-mobile-card${row.status === 'DISABLED' ? ' text-muted' : ''}`}
                    data-testid={`workflowMobileCard-${recordKey}`}
                  >
                    <div className="d-flex align-items-start justify-content-between gap-3">
                      <div className="min-w-0">
                        <div className="fw-semibold text-break">{row.description || '—'}</div>
                        <div className="small text-muted mt-1">{row.transactionDate || '—'}</div>
                        <div className="d-flex flex-wrap gap-1 mt-2">
                          <Badge color={recordStatusColor(row.status)} pill data-testid={`workflowMobileRowStatus-${recordKey}`}>
                            {recordStatusLabel(row.status)}
                          </Badge>
                          {mobileCategoryName && !expanded ? (
                            <span
                              className="candidate-review-category-value"
                              style={categoryIdentityStyle(mobileCategoryColor)}
                              data-testid={
                                completedFinancialTransaction?.id
                                  ? `classificationCategoryFinal-${completedFinancialTransaction.id}`
                                  : undefined
                              }
                            >
                              {mobileCategoryName}
                              {completedCategory?.active === false ? (
                                <span className="candidate-review-category-inactive"> ({translate('fintrackApp.category.inactive')})</span>
                              ) : null}
                            </span>
                          ) : !expanded && completedFinancialTransaction?.id ? (
                            <span
                              className="candidate-review-category-empty text-muted"
                              data-testid={`classificationCategoryEmptyFinal-${completedFinancialTransaction.id}`}
                            >
                              <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.noCategory">
                                No category
                              </Translate>
                            </span>
                          ) : null}
                          {rowHasAvailableSuggestions(row) ? (
                            <Badge color="warning" pill>
                              <Translate contentKey="fintrackApp.transactionIngestion.workflow.candidateQueue.suggestionsAvailable">
                                Suggestions available
                              </Translate>
                            </Badge>
                          ) : null}
                        </div>
                      </div>
                      <div
                        className={`text-end text-nowrap fw-semibold ${row.flow === 'IN' ? 'text-success' : row.flow === 'OUT' ? 'text-danger' : ''}`}
                      >
                        {row.signedAmount || '—'} {row.currency || ''}
                      </div>
                    </div>
                    <Button
                      size="sm"
                      color="secondary"
                      outline
                      className="mt-3"
                      disabled={editingRecordId === row.ingestionRecordId && expanded}
                      onClick={() => toggleExpandedRow(row)}
                      aria-expanded={expanded}
                      aria-controls={`candidate-review-mobile-detail-${identity}`}
                      data-testid={`workflowMobileToggleDetails-${recordKey}`}
                    >
                      <Translate
                        contentKey={
                          expanded
                            ? 'fintrackApp.transactionIngestion.workflow.candidateQueue.collapse'
                            : 'fintrackApp.transactionIngestion.workflow.candidateQueue.expand'
                        }
                      >
                        {expanded ? 'Hide details' : 'Show details'}
                      </Translate>
                    </Button>
                    {expanded ? (
                      <div id={`candidate-review-mobile-detail-${identity}`} className="mt-3">
                        {renderCandidateDetail(row, 'mobile')}
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </div>
          ) : null}
        </>
      )}
    </ProductSection>
  );

  const renderClassificationSummary = () =>
    reviewActionsEnabled && hasEligibleDescriptionRows ? (
      <ProductSection
        title={<Translate contentKey="fintrackApp.transactionIngestion.workflow.reviewTools">Review tools</Translate>}
        className="mb-3"
        dataCy="workflowClassificationControls"
      >
        <Alert color="info" fade={false} data-cy="workflowClassificationPersistedNotice">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.persistedNotice">
            Categories and tags are saved on candidates before confirming the import.
          </Translate>
        </Alert>
        <h4 className="h6 mb-2">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.createTitle">Create rules</Translate>
        </h4>
        <div className="d-flex flex-wrap gap-2 mb-3" data-cy="workflowRuleCreationActions">
          <Button
            color="secondary"
            size="sm"
            disabled={classificationReevaluationRunning || hasUnsavedRowEdit}
            onClick={() => openRuleCreation('NORMALIZATION')}
            data-cy="workflowNewNormalizationRule"
            data-testid="workflowNewNormalizationRule"
          >
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.newNormalization">New Description Rule</Translate>
          </Button>
          <Button
            color="secondary"
            size="sm"
            disabled={classificationReevaluationRunning || hasUnsavedRowEdit}
            onClick={() => openRuleCreation('TRANSACTION')}
            data-cy="workflowNewTransactionRule"
            data-testid="workflowNewTransactionRule"
          >
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.newTransaction">
              New Classification Rule
            </Translate>
          </Button>
        </div>
        <div className="border rounded p-2 mb-3" data-cy="workflowAutomationConfig" data-testid="workflowAutomationConfig">
          <h4 className="h6 mb-1">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.automation.title">Automatic review</Translate>
          </h4>
          <small className="text-muted d-block">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.automation.help">
              These page-local settings determine whether reevaluation also saves eligible results.
            </Translate>
          </small>
          <div className="d-flex flex-wrap gap-3 mt-2">
            <FormGroup check>
              <Input
                id="workflowAutoApplyDescriptions"
                type="checkbox"
                checked={automationConfig.autoApplyDescriptions}
                disabled={classificationReevaluationRunning || hasUnsavedRowEdit}
                onChange={event => updateAutomationSetting('autoApplyDescriptions', event.currentTarget.checked)}
                data-cy="workflowAutoApplyDescriptions"
                data-testid="workflowAutoApplyDescriptions"
              />
              <Label check htmlFor="workflowAutoApplyDescriptions">
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.automation.descriptions">
                  Auto-apply descriptions
                </Translate>
              </Label>
            </FormGroup>
            <FormGroup check>
              <Input
                id="workflowAutoApplyCategories"
                type="checkbox"
                checked={automationConfig.autoApplyCategories}
                disabled={classificationReevaluationRunning || hasUnsavedRowEdit}
                onChange={event => updateAutomationSetting('autoApplyCategories', event.currentTarget.checked)}
                data-cy="workflowAutoApplyCategories"
                data-testid="workflowAutoApplyCategories"
              />
              <Label check htmlFor="workflowAutoApplyCategories">
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.automation.categories">Auto-apply categories</Translate>
              </Label>
            </FormGroup>
            <FormGroup check>
              <Input
                id="workflowAutoApplyTags"
                type="checkbox"
                checked={automationConfig.autoApplyTags}
                disabled={classificationReevaluationRunning || hasUnsavedRowEdit}
                onChange={event => updateAutomationSetting('autoApplyTags', event.currentTarget.checked)}
                data-cy="workflowAutoApplyTags"
                data-testid="workflowAutoApplyTags"
              />
              <Label check htmlFor="workflowAutoApplyTags">
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.automation.tags">Auto-apply tags</Translate>
              </Label>
            </FormGroup>
            <FormGroup check>
              <Input
                id="workflowProtectManualChanges"
                type="checkbox"
                checked={automationConfig.protectManualChanges}
                disabled={classificationReevaluationRunning || hasUnsavedRowEdit}
                onChange={event => updateAutomationSetting('protectManualChanges', event.currentTarget.checked)}
                data-cy="workflowProtectManualChanges"
                data-testid="workflowProtectManualChanges"
              />
              <Label check htmlFor="workflowProtectManualChanges">
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.automation.protectManualChanges">
                  Protect manual changes
                </Translate>
              </Label>
            </FormGroup>
          </div>
          <small className="text-muted d-block mt-1">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.automation.protectManualHelp">
              Prevent automation from replacing changes made manually.
            </Translate>
          </small>
        </div>
        <Row className="mb-2" data-testid="classificationSummary">
          <Col md="2">
            <strong>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.reviewedRows">Reviewed</Translate>
            </strong>
            <div>{reviewedClassificationRows}</div>
          </Col>
          <Col md="2">
            <strong>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.notEvaluatedRows">Not evaluated</Translate>
            </strong>
            <div>{notEvaluatedClassificationRows}</div>
          </Col>
          <Col md="2">
            <strong>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.staleRows">Stale</Translate>
            </strong>
            <div>{staleClassificationRows}</div>
          </Col>
          <Col md="2">
            <strong>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.suggestionsAvailable">Suggestions</Translate>
            </strong>
            <div data-testid="classificationSuggestionCount">{rowsWithSuggestions}</div>
          </Col>
        </Row>
        {classificationConfirmBlocked ? (
          <Alert color="warning" fade={false} data-cy="workflowClassificationConfirmBlocked">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.confirmBlocked">
              Review every valid row before confirming import.
            </Translate>
          </Alert>
        ) : null}
        <h4 className="h6 mb-2">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.reevaluation.title">Reevaluate</Translate>
        </h4>
        <small className="text-muted d-block mb-2">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.reevaluation.help">
            Recalculate results using the current rules. With auto-apply enabled, eligible results can also be saved.
          </Translate>
        </small>
        <div className="d-flex flex-wrap gap-2" data-cy="workflowReevaluationControls">
          <Button
            color="secondary"
            size="sm"
            disabled={classificationReevaluationRunning || hasUnsavedRowEdit}
            onClick={reevaluateDescriptionsOnly}
            data-cy="workflowReevaluateDescriptions"
          >
            {reevaluationInProgress?.kind === 'DESCRIPTIONS' ? <Spinner size="sm" /> : <FontAwesomeIcon icon="sync" />}{' '}
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.reevaluation.descriptions">Reevaluate descriptions</Translate>
          </Button>
          <Button
            color="secondary"
            size="sm"
            disabled={!classificationReevaluationAvailable || classificationReevaluationRunning || hasUnsavedRowEdit}
            onClick={() => reevaluateClassificationScope('CATEGORY')}
            data-cy="workflowReevaluateCategories"
          >
            {reevaluationInProgress?.kind === 'CATEGORY' ? <Spinner size="sm" /> : <FontAwesomeIcon icon="sync" />}{' '}
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.reevaluation.categories">Reevaluate categories</Translate>
          </Button>
          <Button
            color="secondary"
            size="sm"
            disabled={!classificationReevaluationAvailable || classificationReevaluationRunning || hasUnsavedRowEdit}
            onClick={() => reevaluateClassificationScope('TAGS')}
            data-cy="workflowReevaluateTags"
          >
            {reevaluationInProgress?.kind === 'TAGS' ? <Spinner size="sm" /> : <FontAwesomeIcon icon="sync" />}{' '}
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.reevaluation.tags">Reevaluate tags</Translate>
          </Button>
          <Button
            color="primary"
            size="sm"
            disabled={!classificationReevaluationAvailable || classificationReevaluationRunning || hasUnsavedRowEdit}
            onClick={reevaluateAll}
            data-cy="workflowReevaluateAll"
          >
            {reevaluationInProgress?.kind === 'ALL' ? <Spinner size="sm" /> : <FontAwesomeIcon icon="sync" />}{' '}
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.reevaluation.all">Reevaluate all</Translate>
          </Button>
        </div>
        <div className="mt-3" data-cy="workflowSuggestionApplicationControls">
          <h4 className="h6 mb-1">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.applicationTitle">Apply</Translate>
          </h4>
          <small className="text-muted d-block mb-2">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.applicationHelp">
              Save eligible suggestions on candidates. These actions use the entire import, not just the visible rows.
            </Translate>
          </small>
          <div className="d-flex flex-wrap align-items-center gap-2">
            <Button
              color="primary"
              size="sm"
              disabled={descriptionSuggestionRecordIds.length === 0 || classificationReevaluationRunning || hasUnsavedRowEdit}
              onClick={applyAllDescriptionSuggestions}
              data-cy="workflowApplyAllDescriptionSuggestions"
              data-testid="workflowApplyAllDescriptionSuggestions"
            >
              {descriptionApplicationInProgress === 'ALL' ? <Spinner size="sm" /> : null}{' '}
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.descriptionReview.applyAllSuggestions">
                Apply all suggested descriptions
              </Translate>
            </Button>
            <Button
              color="success"
              size="sm"
              disabled={
                categorySuggestionCandidateIds.length === 0 ||
                !classificationReevaluationAvailable ||
                classificationReevaluationRunning ||
                classificationActionInProgress !== null ||
                hasUnsavedRowEdit
              }
              onClick={() => applyAllSuggestionsForScope('CATEGORY')}
              data-cy="workflowApplyAllSuggestedCategories"
              data-testid="workflowApplyAllSuggestedCategories"
            >
              {applyingAllScopedSuggestions ? <Spinner size="sm" /> : null}{' '}
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.applyAllSuggestedCategories">
                Apply all suggested categories
              </Translate>
            </Button>
            <Button
              color="success"
              size="sm"
              disabled={
                tagSuggestionCandidateIds.length === 0 ||
                !classificationReevaluationAvailable ||
                classificationReevaluationRunning ||
                classificationActionInProgress !== null ||
                hasUnsavedRowEdit
              }
              onClick={() => applyAllSuggestionsForScope('TAGS')}
              data-cy="workflowApplyAllSuggestedTags"
              data-testid="workflowApplyAllSuggestedTags"
            >
              {applyingAllScopedSuggestions ? <Spinner size="sm" /> : null}{' '}
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.classification.applyAllSuggestedTags">
                Apply all suggested tags
              </Translate>
            </Button>
          </div>
        </div>
      </ProductSection>
    ) : null;

  const renderReview = () => (
    <ProductPage wide>
      {renderReviewHeader()}

      {ruleCreationFeedback ? (
        <Alert
          color={ruleCreationFeedback.color}
          data-cy="workflowRuleCreationFeedback"
          data-testid="workflowRuleCreationFeedback"
          fade={false}
        >
          {ruleCreationFeedback.message}
        </Alert>
      ) : null}

      {backendError ? (
        <Alert color="danger" data-cy="workflowBackendError" fade={false}>
          {backendError}
        </Alert>
      ) : null}

      {reviewOperationFeedback ? (
        <Alert
          color={reviewOperationFeedback.color}
          role="status"
          data-cy="workflowOperationFeedback"
          data-testid={reviewOperationFeedback.color === 'warning' ? 'workflowApplyAllWarning' : 'workflowOperationFeedback'}
          fade={false}
        >
          {reviewOperationFeedback.message}
        </Alert>
      ) : null}

      {transactionIngestionLoading || loadingReview ? (
        <div className="mb-3">
          <Spinner size="sm" /> <Translate contentKey="entity.action.loading">Loading...</Translate>
        </div>
      ) : null}

      {transactionIngestionEntity?.ingestionType === 'API' ? (
        <Alert color="info" data-cy="apiIngestionTbd" fade={false}>
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.apiTbd">API ingestion detail is not implemented yet.</Translate>
        </Alert>
      ) : null}

      {fileReviewUnavailable && transactionIngestionEntity?.ingestionType === 'FILE' ? (
        <Alert color="info" data-cy="workflowUnavailable" fade={false}>
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.unavailable">
            No file workflow metadata or review rows are available yet.
          </Translate>
        </Alert>
      ) : null}

      {workflow ? (
        <>
          {renderReviewSummary()}

          {workflow.status === 'PARTIALLY_READY' ? (
            <Alert color="warning" data-cy="workflowConfirmBlocked" fade={false}>
              {(counts.validRows ?? 0) === 0 ? (
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.noValidRows">There are no valid rows to import.</Translate>
              ) : (
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.fixRejectedRows">
                  Fix or disable rejected rows before importing.
                </Translate>
              )}
              <div className="mt-1">
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.partiallyReadyGuidance">
                  Resolve the rows shown in the review queue. Confirmation stays unavailable until the workflow is ready.
                </Translate>
              </div>
            </Alert>
          ) : null}

          {workflow.status === 'COMPLETED' ? (
            <Alert color="success" data-cy="workflowCompleted" data-testid="workflowCompleted" fade={false}>
              <strong>
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.importCompleted">Import completed.</Translate>
              </strong>
              <div className="mt-1">
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.completedExplanation">
                  The final transaction records were created from the confirmed review.
                </Translate>
              </div>
              {renderCompletionCounts()}
              <div className="mt-3 d-flex flex-wrap gap-2">
                <Button tag={Link} to="/financial-transaction" color="success" outline size="sm" data-cy="workflowViewTransactions">
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.viewTransactions">
                    View transactions
                  </Translate>
                </Button>
              </div>
            </Alert>
          ) : null}

          {workflow.status === 'PARTIALLY_COMPLETED' ? (
            <Alert color="warning" data-cy="workflowPartiallyCompleted" data-testid="workflowPartiallyCompleted" fade={false}>
              <strong>
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.importPartiallyCompleted">
                  Import partially completed.
                </Translate>
              </strong>
              <div className="mt-1">
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.partiallyCompletedGuidance">
                  This is a final recovery state. Review the recorded outcome; confirmation cannot be resumed from this page.
                </Translate>
              </div>
              {renderCompletionCounts()}
              <div className="mt-3 d-flex flex-wrap gap-2">
                <Button tag={Link} to="/financial-transaction" color="warning" outline size="sm" data-cy="workflowViewTransactions">
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.viewTransactions">
                    View transactions
                  </Translate>
                </Button>
              </div>
            </Alert>
          ) : null}

          {workflow.status === 'PROCESSING' ? (
            <Alert color="info" data-cy="workflowProcessing" data-testid="workflowProcessing" fade={false}>
              <Spinner size="sm" />{' '}
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.importProcessing">Import is processing.</Translate>
            </Alert>
          ) : null}

          {failed ? (
            <Alert color="danger" data-cy="workflowFailed" data-testid="workflowFailed" fade={false}>
              <strong>
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.importFailed">Import failed.</Translate>
              </strong>
              {transactionIngestionEntity.errorMessage ? <div className="mt-1">{transactionIngestionEntity.errorMessage}</div> : null}
              <div className="mt-1">
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmation.failedGuidance">
                  No transactions were created. Return to imports to review this record or start a new import.
                </Translate>
              </div>
              <div className="mt-3 d-flex flex-wrap gap-2">
                <Button tag={Link} to="/transaction-ingestion" color="danger" outline size="sm">
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.backToImports">Back to imports</Translate>
                </Button>
                <Button tag={Link} to="/transaction-ingestion/new" color="danger" size="sm" data-cy="workflowStartNewImport">
                  <Translate contentKey="fintrackApp.transactionIngestion.home.newFileImportLabel">New file import</Translate>
                </Button>
              </div>
            </Alert>
          ) : null}

          {warnings.length > 0 ? (
            <Alert color="warning" data-cy="workflowWarnings" fade={false}>
              <strong>
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.warnings">Warnings</Translate>
              </strong>
              <ul className="mb-0">
                {warnings.map((warning, index) => (
                  <li key={`${warning.code}-${index}`}>{renderWarningMessage(warning)}</li>
                ))}
              </ul>
            </Alert>
          ) : null}

          {renderClassificationSummary()}

          {renderFileMetadata()}

          {editSessionWarning ? (
            <Alert color="warning" fade={false} data-testid="candidateReviewEditWarning">
              {editSessionWarning}
            </Alert>
          ) : null}

          {renderCandidateReviewQueue()}

          {confirmAvailable ? (
            <div className="mt-3">
              <Button
                color="success"
                disabled={
                  processing ||
                  loadingClassification ||
                  Boolean(reevaluationInProgress) ||
                  applyingAllScopedSuggestions ||
                  classificationConfirmBlocked ||
                  hasUnsavedRowEdit
                }
                onClick={openConfirmDialog}
                data-cy="workflowConfirmImport"
              >
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmImport">Confirm Import</Translate>
              </Button>
            </div>
          ) : null}
        </>
      ) : null}
      {renderConfirmDialog()}
      <TransactionIngestionRuleCreationModal
        context={ruleCreationContext}
        onClose={() => setRuleCreationContext(null)}
        onSaved={handleRuleCreated}
      />
    </ProductPage>
  );

  return <div>{isReviewPage ? renderReview() : renderCreateForm()}</div>;
};

export default TransactionIngestionWorkflowDetail;
