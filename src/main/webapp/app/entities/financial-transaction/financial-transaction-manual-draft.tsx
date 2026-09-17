import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Col, Form, FormGroup, FormText, Input, Label, Row, Spinner } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getEntities as getFinancialAccounts } from 'app/entities/financial-account/financial-account.reducer';
import { getEntities as getCategories } from 'app/entities/category/category.reducer';
import { getEntities as getTags } from 'app/entities/tag/tag.reducer';
import { ITransactionCandidate } from 'app/shared/model/transaction-candidate.model';
import { TransactionCandidateClassificationReviewStatus } from 'app/shared/model/enumerations/transaction-candidate-classification-review-status.model';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import {
  applyManualDraftRules,
  cancelManualDraft,
  createManualDraft,
  getManualDraft,
  ITransactionCandidateRulePreviewResponse,
  postManualDraft,
  previewManualDraftRules,
  updateManualDraft,
} from './services/manual-transaction-candidate.service';

const AUTOSAVE_DELAY_MS = 700;

type SaveState = 'UNSAVED' | 'CREATING' | 'SAVING' | 'SAVED' | 'FAILED' | 'POSTING' | 'POSTED' | 'CANCELLED';
type RuleActionState = 'IDLE' | 'PREVIEWING' | 'APPLYING' | 'FAILED';
type RulePreviewState = 'UNAVAILABLE' | 'STALE' | 'UPDATING' | 'UPDATED' | 'FAILED';

interface ManualDraftFormState {
  account: string;
  transactionDate: string;
  postingDate: string;
  description: string;
  amount: string;
  flow: keyof typeof TransactionFlow;
  externalReference: string;
  notes: string;
  category: string;
  tags: string[];
}

const emptyDraft: ManualDraftFormState = {
  account: '',
  transactionDate: '',
  postingDate: '',
  description: '',
  amount: '',
  flow: 'IN',
  externalReference: '',
  notes: '',
  category: '',
  tags: [],
};

const toOptionalNumber = (value: string | number | null | undefined) => {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  return typeof value === 'number' ? value : Number(value);
};

const selectedOptions = (event: React.ChangeEvent<HTMLSelectElement>): string[] =>
  Array.from(event.target.selectedOptions)
    .map((option: HTMLOptionElement) => option.value)
    .filter(Boolean);

const signedAmountFromDraft = (draft: ManualDraftFormState) => {
  const amount = toOptionalNumber(draft.amount);
  if (!amount || amount <= 0) {
    return undefined;
  }
  return draft.flow === 'OUT' ? -amount : amount;
};

const isMeaningfulDraft = (draft: ManualDraftFormState) =>
  !!draft.account ||
  !!draft.transactionDate ||
  !!draft.description.trim() ||
  !!signedAmountFromDraft(draft) ||
  !!draft.category ||
  draft.tags.length > 0;

const isObviouslyComplete = (draft: ManualDraftFormState) =>
  !!draft.account && !!draft.transactionDate && !!draft.description.trim() && !!signedAmountFromDraft(draft);

const ruleInputFields = new Set<keyof ManualDraftFormState>([
  'account',
  'transactionDate',
  'postingDate',
  'description',
  'amount',
  'flow',
  'externalReference',
]);

const buildRuleInputSignature = (draft: ManualDraftFormState) =>
  JSON.stringify({
    account: draft.account || null,
    transactionDate: draft.transactionDate || null,
    postingDate: draft.postingDate || null,
    description: draft.description.trim(),
    signedAmount: signedAmountFromDraft(draft) ?? null,
    flow: draft.flow || null,
    externalReference: draft.externalReference || null,
  });

const isRulePreviewCandidateEligible = (candidate: ITransactionCandidate | null, draft: ManualDraftFormState) =>
  !!candidate?.id &&
  candidate.source === 'MANUAL' &&
  candidate.status !== 'POSTED' &&
  candidate.status !== 'CANCELLED' &&
  candidate.status !== 'FAILED' &&
  !!draft.account &&
  !!draft.transactionDate &&
  !!draft.description.trim() &&
  !!signedAmountFromDraft(draft) &&
  !!draft.flow;

const draftFromCandidate = (candidate: ITransactionCandidate): ManualDraftFormState => {
  const signedAmount = candidate.signedAmount;
  const flow = signedAmount !== undefined && signedAmount !== null && signedAmount < 0 ? 'OUT' : (candidate.flow ?? 'IN');
  return {
    account: candidate.account?.id?.toString() ?? '',
    transactionDate: typeof candidate.transactionDate === 'string' ? candidate.transactionDate : '',
    postingDate: typeof candidate.postingDate === 'string' ? candidate.postingDate : '',
    description: candidate.description ?? '',
    amount: signedAmount !== undefined && signedAmount !== null ? Math.abs(signedAmount).toString() : '',
    flow,
    externalReference: candidate.externalReference ?? '',
    notes: candidate.notes ?? '',
    category: candidate.category?.id?.toString() ?? '',
    tags: candidate.tags?.map(tag => tag.id?.toString()).filter(Boolean) ?? [],
  };
};

const payloadFromDraft = (draft: ManualDraftFormState, includeClassification = false): ITransactionCandidate => {
  const payload: ITransactionCandidate = {
    account: draft.account ? { id: Number(draft.account) } : null,
    transactionDate: draft.transactionDate || null,
    postingDate: draft.postingDate || null,
    description: draft.description,
    signedAmount: signedAmountFromDraft(draft),
    externalReference: draft.externalReference || null,
    notes: draft.notes || null,
  };

  if (includeClassification) {
    payload.category = draft.category ? { id: Number(draft.category) } : null;
    payload.tags = draft.tags.map(tagId => ({ id: Number(tagId) }));
  }

  return payload;
};

const isClassificationReadyToPost = (status?: keyof typeof TransactionCandidateClassificationReviewStatus | null) =>
  status === 'SUGGESTED' || status === 'USER_SELECTED' || status === 'NOT_APPLICABLE';

const postClassificationBlockKey = (status?: keyof typeof TransactionCandidateClassificationReviewStatus | null) => {
  if (status === 'STALE') {
    return 'fintrackApp.financialTransaction.manualDraft.ruleSuggestions.stalePostBlock';
  }
  if (!isClassificationReadyToPost(status)) {
    return 'fintrackApp.financialTransaction.manualDraft.ruleSuggestions.notEvaluatedPostBlock';
  }
  return '';
};

const RulePreviewDetails = ({ rulePreview }: { rulePreview: ITransactionCandidateRulePreviewResponse }) => (
  <div data-testid="manual-draft-rule-preview">
    {rulePreview.suggestedCategory ? (
      <p className="mb-1">
        <strong>
          <Translate contentKey="fintrackApp.financialTransaction.manualDraft.ruleSuggestions.suggestedCategory">
            Suggested category
          </Translate>
          :
        </strong>{' '}
        {rulePreview.suggestedCategory.categoryName}
      </p>
    ) : null}
    {rulePreview.suggestedTags?.length ? (
      <p className="mb-1">
        <strong>
          <Translate contentKey="fintrackApp.financialTransaction.manualDraft.ruleSuggestions.suggestedTags">Suggested tags</Translate>:
        </strong>{' '}
        {rulePreview.suggestedTags.map(tag => tag.tagName).join(', ')}
      </p>
    ) : null}
    {rulePreview.hasConflicts || rulePreview.conflicts?.length ? (
      <Alert color="warning" fade={false} className="mt-2 mb-2" data-testid="manual-draft-rule-conflicts">
        <Translate contentKey="fintrackApp.financialTransaction.manualDraft.ruleSuggestions.conflicts">
          Some suggestions conflict with existing selections.
        </Translate>
      </Alert>
    ) : null}
    {rulePreview.matchedRules?.length ? (
      <p className="mb-0">
        <strong>
          <Translate contentKey="fintrackApp.financialTransaction.manualDraft.ruleSuggestions.matchedRules">Matched rules</Translate>:
        </strong>{' '}
        {rulePreview.matchedRules.map(rule => rule.ruleName).join(', ')}
      </p>
    ) : null}
  </div>
);

interface RuleSuggestionsSectionProps {
  candidate: ITransactionCandidate | null;
  readOnly: boolean;
  saveState: SaveState;
  ruleActionState: RuleActionState;
  ruleErrorMessage: string;
  rulePreview: ITransactionCandidateRulePreviewResponse | null;
  effectiveClassificationReviewStatus?: keyof typeof TransactionCandidateClassificationReviewStatus | null;
  onPreviewRules: () => void;
  onApplyRules: () => void;
  rulePreviewState: RulePreviewState;
}

const isRuleActionDisabled = (ruleActionState: RuleActionState, saveState: SaveState) =>
  ruleActionState === 'PREVIEWING' || ruleActionState === 'APPLYING' || saveState === 'CREATING' || saveState === 'SAVING';

const RuleSuggestionsSection = ({
  candidate,
  readOnly,
  saveState,
  ruleActionState,
  ruleErrorMessage,
  rulePreview,
  effectiveClassificationReviewStatus,
  onPreviewRules,
  onApplyRules,
  rulePreviewState,
}: RuleSuggestionsSectionProps) => {
  if (!candidate || readOnly) {
    return null;
  }

  const ruleActionDisabled = isRuleActionDisabled(ruleActionState, saveState);
  const hasPreview = !!rulePreview;
  const previewStateKey =
    rulePreviewState === 'UPDATED' && hasPreview && !rulePreview.hasSuggestions ? 'noSuggestions' : rulePreviewState.toLowerCase();
  const reviewedClassificationReviewStatus = isClassificationReadyToPost(effectiveClassificationReviewStatus)
    ? effectiveClassificationReviewStatus
    : isClassificationReadyToPost(rulePreview?.classificationReviewStatus)
      ? rulePreview?.classificationReviewStatus
      : null;
  let reviewMessageKey = '';
  if (reviewedClassificationReviewStatus === 'SUGGESTED') {
    reviewMessageKey = 'fintrackApp.financialTransaction.manualDraft.ruleSuggestions.review.suggested';
  } else if (reviewedClassificationReviewStatus === 'USER_SELECTED') {
    reviewMessageKey = 'fintrackApp.financialTransaction.manualDraft.ruleSuggestions.review.userSelected';
  } else if (reviewedClassificationReviewStatus === 'NOT_APPLICABLE') {
    reviewMessageKey = 'fintrackApp.financialTransaction.manualDraft.ruleSuggestions.review.notApplicable';
  } else if (rulePreviewState === 'UPDATED' && hasPreview && !isClassificationReadyToPost(effectiveClassificationReviewStatus)) {
    reviewMessageKey = rulePreview.hasSuggestions
      ? 'fintrackApp.financialTransaction.manualDraft.ruleSuggestions.review.pending'
      : 'fintrackApp.financialTransaction.manualDraft.ruleSuggestions.review.confirmNoSuggestions';
  }
  const canApplyPreview = hasPreview && rulePreviewState === 'UPDATED' && ruleActionState !== 'PREVIEWING';
  const showApplyButton = canApplyPreview && rulePreview.hasSuggestions;
  const showConfirmNoSuggestionsButton = canApplyPreview && !rulePreview.hasSuggestions;
  const applyLabelKey = showConfirmNoSuggestionsButton
    ? 'fintrackApp.financialTransaction.manualDraft.ruleSuggestions.confirmNoSuggestions'
    : 'fintrackApp.financialTransaction.manualDraft.ruleSuggestions.apply';
  const applyFallback = showConfirmNoSuggestionsButton ? 'Confirm no suggestions' : 'Apply suggestions';

  return (
    <section className="border rounded p-3 mb-3" data-testid="manual-draft-rule-suggestions">
      <h4>
        <Translate contentKey="fintrackApp.financialTransaction.manualDraft.ruleSuggestions.title">Rule suggestions</Translate>
      </h4>
      <p className="mb-2" data-testid="manual-draft-rule-preview-state">
        <Translate
          key={previewStateKey}
          contentKey={`fintrackApp.financialTransaction.manualDraft.ruleSuggestions.previewState.${previewStateKey}`}
        >
          Suggestions not available yet.
        </Translate>
      </p>
      {reviewMessageKey ? (
        <p className="mb-2" data-testid="manual-draft-classification-status">
          <Translate key={reviewMessageKey} contentKey={reviewMessageKey}>
            Review pending: apply suggestions before posting.
          </Translate>
        </p>
      ) : null}
      <div className="mb-2">
        {rulePreviewState === 'FAILED' ? (
          <>
            <Button
              color="info"
              size="sm"
              type="button"
              data-cy="manualDraftRetryRulesButton"
              onClick={onPreviewRules}
              disabled={ruleActionDisabled}
            >
              <FontAwesomeIcon icon="sync" />
              &nbsp;
              <Translate contentKey="fintrackApp.financialTransaction.manualDraft.ruleSuggestions.retry">Retry suggestions</Translate>
            </Button>
            &nbsp;
          </>
        ) : null}
        {showApplyButton || showConfirmNoSuggestionsButton ? (
          <Button
            color="primary"
            size="sm"
            type="button"
            data-cy="manualDraftApplyRulesButton"
            onClick={onApplyRules}
            disabled={ruleActionDisabled}
          >
            {ruleActionState === 'APPLYING' ? <Spinner size="sm" /> : <FontAwesomeIcon icon="save" />}
            &nbsp;
            <Translate contentKey={applyLabelKey}>{applyFallback}</Translate>
          </Button>
        ) : null}
      </div>
      {ruleErrorMessage ? (
        <Alert color="danger" fade={false} data-testid="manual-draft-rule-error">
          {ruleErrorMessage}
        </Alert>
      ) : null}
      {rulePreview && rulePreviewState !== 'UNAVAILABLE' ? <RulePreviewDetails rulePreview={rulePreview} /> : null}
    </section>
  );
};

export const FinancialTransactionManualDraft = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { draftId } = useParams<'draftId'>();

  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const categories = useAppSelector(state => state.category.entities);
  const tags = useAppSelector(state => state.tag.entities);

  const [draft, setDraft] = useState<ManualDraftFormState>(emptyDraft);
  const [candidate, setCandidate] = useState<ITransactionCandidate | null>(null);
  const [classificationReviewStatus, setClassificationReviewStatus] = useState<
    keyof typeof TransactionCandidateClassificationReviewStatus | null
  >(null);
  const [saveState, setSaveState] = useState<SaveState>('UNSAVED');
  const [errorMessage, setErrorMessage] = useState('');
  const [routeErrorKey, setRouteErrorKey] = useState('');
  const [rulePreview, setRulePreview] = useState<ITransactionCandidateRulePreviewResponse | null>(null);
  const [rulePreviewState, setRulePreviewState] = useState<RulePreviewState>('UNAVAILABLE');
  const [ruleActionState, setRuleActionState] = useState<RuleActionState>('IDLE');
  const [ruleErrorMessage, setRuleErrorMessage] = useState('');
  const [loadingCandidate, setLoadingCandidate] = useState(!!draftId);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const creatingRef = useRef(false);
  const savingRef = useRef<Promise<ITransactionCandidate | null> | null>(null);
  const dirtyRef = useRef(false);
  const classificationDirtyRef = useRef(false);
  const latestDraftRef = useRef<ManualDraftFormState>(emptyDraft);
  const candidateIdRef = useRef<number | null>(draftId ? Number(draftId) : null);
  const candidateRef = useRef<ITransactionCandidate | null>(null);
  const lastSavedRuleInputSignatureRef = useRef('');
  const lastPreviewedRuleInputSignatureRef = useRef('');
  const latestPreviewRequestIdRef = useRef(0);

  const readOnly =
    candidate?.status === 'POSTED' || candidate?.status === 'CANCELLED' || saveState === 'POSTED' || saveState === 'CANCELLED';
  const effectiveClassificationReviewStatus = classificationReviewStatus ?? candidate?.classificationReviewStatus;
  const classificationBlockKey =
    candidate?.status === 'READY_TO_POST' ? postClassificationBlockKey(effectiveClassificationReviewStatus) : '';
  const postDisabled =
    readOnly ||
    saveState === 'CREATING' ||
    saveState === 'POSTING' ||
    saveState === 'FAILED' ||
    !candidate?.id ||
    !isObviouslyComplete(draft) ||
    candidate.status !== 'READY_TO_POST' ||
    !isClassificationReadyToPost(effectiveClassificationReviewStatus);

  const selectedAccount = useMemo(
    () => financialAccounts.find(account => account.id?.toString() === draft.account),
    [financialAccounts, draft.account],
  );

  useEffect(() => {
    dispatch(getFinancialAccounts({}));
    dispatch(getCategories({}));
    dispatch(getTags({}));
  }, []);

  useEffect(() => {
    latestDraftRef.current = draft;
  }, [draft]);

  useEffect(() => {
    candidateRef.current = candidate;
  }, [candidate]);

  useEffect(() => {
    if (!draftId) {
      setLoadingCandidate(false);
      setRouteErrorKey('');
      return;
    }
    const numericDraftId = Number(draftId);
    candidateIdRef.current = numericDraftId;
    if (candidateRef.current?.id === numericDraftId) {
      setLoadingCandidate(false);
      return;
    }
    setLoadingCandidate(true);
    setRouteErrorKey('');
    getManualDraft(draftId)
      .then(response => {
        if (response.data.source !== 'MANUAL') {
          clearSaveTimer();
          setCandidate(null);
          setClassificationReviewStatus(null);
          candidateRef.current = null;
          candidateIdRef.current = null;
          setRouteErrorKey('fintrackApp.financialTransaction.manualDraft.notManual');
          setSaveState('FAILED');
          return;
        }
        applyServerCandidate(response.data);
        const loadedDraft = draftFromCandidate(response.data);
        setDraft(loadedDraft);
        latestDraftRef.current = loadedDraft;
        setSaveState(response.data.status === 'CANCELLED' ? 'CANCELLED' : response.data.status === 'POSTED' ? 'POSTED' : 'SAVED');
        void runAutoPreviewForSavedDraft(response.data, loadedDraft);
        if (response.data.status === 'POSTED' && response.data.financialTransaction?.id) {
          navigate(`/financial-transaction/${response.data.financialTransaction.id}`, { replace: true });
        }
      })
      .catch(() => {
        candidateIdRef.current = null;
        candidateRef.current = null;
        setCandidate(null);
        setClassificationReviewStatus(null);
        setRouteErrorKey('fintrackApp.financialTransaction.manualDraft.loadFailed');
        setSaveState('FAILED');
      })
      .finally(() => setLoadingCandidate(false));
  }, [draftId]);

  const clearSaveTimer = () => {
    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
      saveTimerRef.current = null;
    }
  };

  const applyServerCandidate = (nextCandidate: ITransactionCandidate | null) => {
    if (!nextCandidate) {
      return;
    }
    const candidateCopy = { ...nextCandidate };
    setCandidate(candidateCopy);
    setClassificationReviewStatus(candidateCopy.classificationReviewStatus ?? null);
    candidateRef.current = candidateCopy;
    candidateIdRef.current = candidateCopy.id ?? null;
  };

  const runAutoPreviewForSavedDraft = async (
    savedCandidate: ITransactionCandidate | null,
    savedDraft: ManualDraftFormState,
    savedSignature = buildRuleInputSignature(savedDraft),
  ) => {
    lastSavedRuleInputSignatureRef.current = savedSignature;
    if (!isRulePreviewCandidateEligible(savedCandidate, savedDraft)) {
      setRulePreviewState('UNAVAILABLE');
      setRulePreview(null);
      return;
    }
    if (savedSignature === lastPreviewedRuleInputSignatureRef.current) {
      return;
    }
    const requestId = latestPreviewRequestIdRef.current + 1;
    latestPreviewRequestIdRef.current = requestId;
    setRuleErrorMessage('');
    setRuleActionState('PREVIEWING');
    setRulePreviewState('UPDATING');
    try {
      const response = await previewManualDraftRules(savedCandidate.id);
      if (requestId !== latestPreviewRequestIdRef.current || savedSignature !== lastSavedRuleInputSignatureRef.current) {
        return;
      }
      setRulePreview(response.data);
      lastPreviewedRuleInputSignatureRef.current = savedSignature;
      setRulePreviewState('UPDATED');
      setRuleActionState('IDLE');
    } catch (error) {
      if (requestId !== latestPreviewRequestIdRef.current || savedSignature !== lastSavedRuleInputSignatureRef.current) {
        return;
      }
      setRuleActionState('FAILED');
      setRulePreviewState('FAILED');
      setRuleErrorMessage(
        error?.response?.data?.detail ?? translate('fintrackApp.financialTransaction.manualDraft.ruleSuggestions.previewFailed'),
      );
    }
  };

  const applyServerCandidateToForm = (nextCandidate: ITransactionCandidate | null) => {
    if (nextCandidate) {
      const nextDraft = draftFromCandidate(nextCandidate);
      applyServerCandidate(nextCandidate);
      setDraft(nextDraft);
      latestDraftRef.current = nextDraft;
    }
  };

  const patchLatestDraft = async () => {
    const currentId = candidateIdRef.current;
    if (!currentId || readOnly) {
      return candidate;
    }
    if (savingRef.current) {
      dirtyRef.current = true;
      return savingRef.current;
    }

    setSaveState('SAVING');
    setErrorMessage('');
    dirtyRef.current = false;
    const includeClassification = classificationDirtyRef.current;
    classificationDirtyRef.current = false;
    const savePromise = updateManualDraft(currentId, payloadFromDraft(latestDraftRef.current, includeClassification))
      .then(response => {
        applyServerCandidate(response.data);
        setSaveState('SAVED');
        if (!dirtyRef.current) {
          void runAutoPreviewForSavedDraft(response.data, latestDraftRef.current);
        }
        return response.data;
      })
      .catch(error => {
        if (includeClassification) {
          classificationDirtyRef.current = true;
        }
        setSaveState('FAILED');
        setErrorMessage(error?.response?.data?.detail ?? translate('fintrackApp.financialTransaction.manualDraft.saveFailed'));
        return null;
      })
      .finally(() => {
        savingRef.current = null;
        if (dirtyRef.current) {
          dirtyRef.current = false;
          void patchLatestDraft();
        }
      });
    savingRef.current = savePromise;
    return savePromise;
  };

  const createFirstDraft = async (nextDraft: ManualDraftFormState, includeClassification = false) => {
    if (creatingRef.current || candidateIdRef.current || !isMeaningfulDraft(nextDraft)) {
      return;
    }
    creatingRef.current = true;
    setSaveState('CREATING');
    setErrorMessage('');
    try {
      const response = await createManualDraft(payloadFromDraft(nextDraft, includeClassification));
      applyServerCandidate(response.data);
      setSaveState('SAVED');
      navigate(`/financial-transaction/drafts/${response.data.id}`, { replace: true });
      void runAutoPreviewForSavedDraft(response.data, latestDraftRef.current);
      if (latestDraftRef.current !== nextDraft) {
        dirtyRef.current = true;
        void patchLatestDraft();
      }
    } catch (error) {
      setSaveState('FAILED');
      setErrorMessage(error?.response?.data?.detail ?? translate('fintrackApp.financialTransaction.manualDraft.createFailed'));
    } finally {
      creatingRef.current = false;
    }
  };

  const scheduleAutosave = (nextDraft: ManualDraftFormState, includeClassification = false) => {
    if (readOnly) {
      return;
    }
    if (includeClassification) {
      classificationDirtyRef.current = true;
    }
    if (!candidateIdRef.current) {
      void createFirstDraft(nextDraft, includeClassification);
      return;
    }
    setSaveState('SAVING');
    dirtyRef.current = true;
    clearSaveTimer();
    saveTimerRef.current = setTimeout(() => {
      saveTimerRef.current = null;
      void patchLatestDraft();
    }, AUTOSAVE_DELAY_MS);
  };

  const updateDraftField = (field: keyof ManualDraftFormState) => event => {
    const value = event.target.value;
    setDraft(current => {
      const nextDraft = { ...current, [field]: value };
      latestDraftRef.current = nextDraft;
      if (ruleInputFields.has(field) && rulePreview) {
        setRulePreviewState('STALE');
      }
      scheduleAutosave(nextDraft, field === 'category');
      return nextDraft;
    });
  };

  const updateTags = event => {
    const value = selectedOptions(event);
    setDraft(current => {
      const nextDraft = { ...current, tags: value };
      latestDraftRef.current = nextDraft;
      scheduleAutosave(nextDraft, true);
      return nextDraft;
    });
  };

  const flushPendingSave = async () => {
    clearSaveTimer();
    if (!candidateIdRef.current && isMeaningfulDraft(latestDraftRef.current)) {
      await createFirstDraft(latestDraftRef.current, classificationDirtyRef.current);
    }
    if (savingRef.current) {
      await savingRef.current;
    }
    if (dirtyRef.current && candidateIdRef.current) {
      await patchLatestDraft();
    }
    return candidateRef.current;
  };

  const handlePost = async () => {
    setErrorMessage('');
    setSaveState('POSTING');
    const currentCandidate = await flushPendingSave();
    if (!candidateIdRef.current || currentCandidate?.status !== 'READY_TO_POST') {
      setSaveState('FAILED');
      setErrorMessage(translate('fintrackApp.financialTransaction.manualDraft.cannotPost'));
      return;
    }
    const classificationMessageKey = postClassificationBlockKey(classificationReviewStatus ?? currentCandidate.classificationReviewStatus);
    if (classificationMessageKey) {
      setSaveState('SAVED');
      setErrorMessage(translate(classificationMessageKey));
      return;
    }
    try {
      const response = await postManualDraft(candidateIdRef.current);
      setCandidate(response.data);
      setSaveState('POSTED');
      if (response.data.financialTransaction?.id) {
        navigate(`/financial-transaction/${response.data.financialTransaction.id}`, { replace: true });
      } else {
        navigate('/financial-transaction', { replace: true });
      }
    } catch (error) {
      setSaveState('FAILED');
      setErrorMessage(error?.response?.data?.detail ?? translate('fintrackApp.financialTransaction.manualDraft.postFailed'));
    }
  };

  const handlePreviewRules = async () => {
    setRuleErrorMessage('');
    setRuleActionState('PREVIEWING');
    const currentCandidate = await flushPendingSave();
    if (!candidateIdRef.current || !currentCandidate) {
      setRuleActionState('FAILED');
      setRuleErrorMessage(translate('fintrackApp.financialTransaction.manualDraft.ruleSuggestions.actionRequiresDraft'));
      return;
    }
    try {
      const response = await previewManualDraftRules(candidateIdRef.current);
      setRulePreview(response.data);
      const previewedSignature = buildRuleInputSignature(latestDraftRef.current);
      lastSavedRuleInputSignatureRef.current = previewedSignature;
      lastPreviewedRuleInputSignatureRef.current = previewedSignature;
      setRulePreviewState('UPDATED');
      setRuleActionState('IDLE');
    } catch (error) {
      setRuleActionState('FAILED');
      setRulePreviewState('FAILED');
      setRuleErrorMessage(
        error?.response?.data?.detail ?? translate('fintrackApp.financialTransaction.manualDraft.ruleSuggestions.previewFailed'),
      );
    }
  };

  const handleApplyRules = async () => {
    setRuleErrorMessage('');
    setRuleActionState('APPLYING');
    const currentCandidate = await flushPendingSave();
    if (!candidateIdRef.current || !currentCandidate) {
      setRuleActionState('FAILED');
      setRuleErrorMessage(translate('fintrackApp.financialTransaction.manualDraft.ruleSuggestions.actionRequiresDraft'));
      return;
    }
    try {
      const response = await applyManualDraftRules(candidateIdRef.current);
      const appliedClassificationReviewStatus =
        response.data.candidate?.classificationReviewStatus ?? (response.data.evaluation?.hasSuggestions ? 'SUGGESTED' : 'NOT_APPLICABLE');
      if (response.data.candidate) {
        applyServerCandidateToForm(response.data.candidate);
        const appliedSignature = buildRuleInputSignature(draftFromCandidate(response.data.candidate));
        lastSavedRuleInputSignatureRef.current = appliedSignature;
        lastPreviewedRuleInputSignatureRef.current = appliedSignature;
      }
      setClassificationReviewStatus(appliedClassificationReviewStatus);
      setRulePreview(
        response.data.evaluation ? { ...response.data.evaluation, classificationReviewStatus: appliedClassificationReviewStatus } : null,
      );
      setRulePreviewState('UPDATED');
      setRuleActionState('IDLE');
    } catch (error) {
      setRuleActionState('FAILED');
      setRuleErrorMessage(
        error?.response?.data?.detail ?? translate('fintrackApp.financialTransaction.manualDraft.ruleSuggestions.applyFailed'),
      );
    }
  };

  const handleCancel = async () => {
    clearSaveTimer();
    if (!candidateIdRef.current) {
      navigate('/financial-transaction');
      return;
    }
    try {
      await cancelManualDraft(candidateIdRef.current);
      setSaveState('CANCELLED');
      navigate('/financial-transaction', { replace: true });
    } catch (error) {
      setSaveState('FAILED');
      setErrorMessage(error?.response?.data?.detail ?? translate('fintrackApp.financialTransaction.manualDraft.cancelFailed'));
    }
  };

  if (loadingCandidate) {
    return <p>Loading...</p>;
  }

  if (routeErrorKey) {
    return (
      <Row className="justify-content-center">
        <Col md="8">
          <h2 data-cy="FinancialTransactionManualDraftHeading">
            <Translate contentKey="fintrackApp.financialTransaction.manualDraft.title">Create manual transaction</Translate>
          </h2>
          <Alert color="danger" fade={false} data-testid="manual-draft-route-error">
            <Translate contentKey={routeErrorKey}>This draft cannot be edited in the manual transaction flow.</Translate>
          </Alert>
          <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/financial-transaction" replace color="info">
            <FontAwesomeIcon icon="arrow-left" />
            &nbsp;
            <span className="d-none d-md-inline">
              <Translate contentKey="entity.action.back">Back</Translate>
            </span>
          </Button>
        </Col>
      </Row>
    );
  }

  return (
    <Row className="justify-content-center">
      <Col md="8">
        <h2 data-cy="FinancialTransactionManualDraftHeading">
          <Translate contentKey="fintrackApp.financialTransaction.manualDraft.title">Create manual transaction</Translate>
        </h2>
        {errorMessage ? (
          <Alert color="danger" fade={false} data-testid="manual-draft-error">
            {errorMessage}
          </Alert>
        ) : null}
        {candidate?.status === 'CANCELLED' ? (
          <Alert color="warning" fade={false}>
            <Translate contentKey="fintrackApp.financialTransaction.manualDraft.cancelledReadOnly">
              This draft was cancelled and cannot be edited.
            </Translate>
          </Alert>
        ) : null}
        {classificationBlockKey ? (
          <Alert color="warning" fade={false} data-testid="manual-draft-classification-block">
            <Translate contentKey={classificationBlockKey}>Refresh or apply rule suggestions before posting.</Translate>
          </Alert>
        ) : null}
        <Form>
          <FormGroup>
            <Label for="financial-transaction-account">
              <Translate contentKey="fintrackApp.financialTransaction.account">Account</Translate>
            </Label>
            <Input
              id="financial-transaction-account"
              name="account"
              data-cy="account"
              type="select"
              value={draft.account}
              onChange={updateDraftField('account')}
              disabled={readOnly}
            >
              <option value="" key="0" />
              {financialAccounts?.map(account => (
                <option value={account.id} key={account.id}>
                  {account.name}
                </option>
              ))}
            </Input>
            {selectedAccount?.currency ? <FormText>{selectedAccount.currency}</FormText> : null}
          </FormGroup>
          <FormGroup>
            <Label for="financial-transaction-transactionDate">
              <Translate contentKey="fintrackApp.financialTransaction.transactionDate">Transaction date</Translate>
            </Label>
            <Input
              id="financial-transaction-transactionDate"
              name="transactionDate"
              data-cy="transactionDate"
              type="date"
              value={draft.transactionDate}
              onChange={updateDraftField('transactionDate')}
              disabled={readOnly}
            />
          </FormGroup>
          <FormGroup>
            <Label for="financial-transaction-postingDate">
              <Translate contentKey="fintrackApp.financialTransaction.postingDate">Posting date</Translate>
            </Label>
            <Input
              id="financial-transaction-postingDate"
              name="postingDate"
              data-cy="postingDate"
              type="date"
              value={draft.postingDate}
              onChange={updateDraftField('postingDate')}
              disabled={readOnly}
            />
          </FormGroup>
          <FormGroup>
            <Label for="financial-transaction-description">
              <Translate contentKey="fintrackApp.financialTransaction.description">Description</Translate>
            </Label>
            <Input
              id="financial-transaction-description"
              name="description"
              data-cy="description"
              type="text"
              value={draft.description}
              onChange={updateDraftField('description')}
              disabled={readOnly}
            />
          </FormGroup>
          <FormGroup>
            <Label for="financial-transaction-flow">
              <Translate contentKey="fintrackApp.financialTransaction.flow">Type</Translate>
            </Label>
            <Input
              id="financial-transaction-flow"
              name="flow"
              data-cy="flow"
              type="select"
              value={draft.flow}
              onChange={updateDraftField('flow')}
              disabled={readOnly}
            >
              {Object.keys(TransactionFlow).map(transactionFlow => (
                <option value={transactionFlow} key={transactionFlow}>
                  {translate(`fintrackApp.TransactionFlow.${transactionFlow}`)}
                </option>
              ))}
            </Input>
          </FormGroup>
          <FormGroup>
            <Label for="financial-transaction-amount">
              <Translate contentKey="fintrackApp.financialTransaction.amount">Amount</Translate>
            </Label>
            <Input
              id="financial-transaction-amount"
              name="amount"
              data-cy="amount"
              type="text"
              value={draft.amount}
              onChange={updateDraftField('amount')}
              disabled={readOnly}
            />
          </FormGroup>
          <FormGroup>
            <Label for="financial-transaction-externalReference">
              <Translate contentKey="fintrackApp.financialTransaction.externalReference">External Reference</Translate>
            </Label>
            <Input
              id="financial-transaction-externalReference"
              name="externalReference"
              data-cy="externalReference"
              type="text"
              value={draft.externalReference}
              onChange={updateDraftField('externalReference')}
              disabled={readOnly}
            />
          </FormGroup>
          <FormGroup>
            <Label for="financial-transaction-notes">
              <Translate contentKey="fintrackApp.financialTransaction.notes">Notes</Translate>
            </Label>
            <Input
              id="financial-transaction-notes"
              name="notes"
              data-cy="notes"
              type="text"
              value={draft.notes}
              onChange={updateDraftField('notes')}
              disabled={readOnly}
            />
          </FormGroup>
          <FormGroup>
            <Label for="financial-transaction-category">
              <Translate contentKey="fintrackApp.financialTransaction.category">Category</Translate>
            </Label>
            <Input
              id="financial-transaction-category"
              name="category"
              data-cy="category"
              type="select"
              value={draft.category}
              onChange={updateDraftField('category')}
              disabled={readOnly}
            >
              <option value="" key="0" />
              {categories?.map(category => (
                <option value={category.id} key={category.id}>
                  {category.name}
                </option>
              ))}
            </Input>
          </FormGroup>
          <FormGroup>
            <Label for="financial-transaction-tags">
              <Translate contentKey="fintrackApp.financialTransaction.tags">Tags</Translate>
            </Label>
            <Input
              id="financial-transaction-tags"
              name="tags"
              data-cy="tags"
              type="select"
              multiple
              value={draft.tags}
              onChange={updateTags}
              disabled={readOnly}
            >
              {tags?.map(tag => (
                <option value={tag.id} key={tag.id}>
                  {tag.name}
                </option>
              ))}
            </Input>
          </FormGroup>
          <RuleSuggestionsSection
            candidate={candidate}
            readOnly={readOnly}
            saveState={saveState}
            ruleActionState={ruleActionState}
            ruleErrorMessage={ruleErrorMessage}
            rulePreview={rulePreview}
            effectiveClassificationReviewStatus={effectiveClassificationReviewStatus}
            onPreviewRules={handlePreviewRules}
            onApplyRules={handleApplyRules}
            rulePreviewState={rulePreviewState}
          />
          <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/financial-transaction" replace color="info">
            <FontAwesomeIcon icon="arrow-left" />
            &nbsp;
            <span className="d-none d-md-inline">
              <Translate contentKey="entity.action.back">Back</Translate>
            </span>
          </Button>
          &nbsp;
          <Button
            color="secondary"
            id="cancel-draft"
            data-cy="manualDraftCancelButton"
            type="button"
            onClick={handleCancel}
            disabled={readOnly || saveState === 'CREATING' || saveState === 'SAVING' || saveState === 'POSTING'}
          >
            <FontAwesomeIcon icon="ban" />
            &nbsp;
            <Translate contentKey="fintrackApp.financialTransaction.manualDraft.cancel">Cancel draft</Translate>
          </Button>
          &nbsp;
          <Button
            color="primary"
            id="post-draft"
            data-cy="manualDraftPostButton"
            type="button"
            disabled={postDisabled}
            onClick={handlePost}
          >
            {saveState === 'POSTING' ? <Spinner size="sm" /> : <FontAwesomeIcon icon="save" />}
            &nbsp;
            <Translate contentKey="fintrackApp.financialTransaction.manualDraft.post">Post transaction</Translate>
          </Button>
        </Form>
      </Col>
    </Row>
  );
};

export default FinancialTransactionManualDraft;
