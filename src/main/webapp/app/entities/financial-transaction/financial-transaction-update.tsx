import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';
import dayjs from 'dayjs';
import { Alert, Button, Col, FormText, Row, Spinner } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { mapIdList } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getFinancialAccounts } from 'app/entities/financial-account/financial-account.reducer';
import { getEntities as getCategories } from 'app/entities/category/category.reducer';
import { getEntities as getTags } from 'app/entities/tag/tag.reducer';
import {
  IFinancialTransactionRulePreviewRequest,
  IFinancialTransactionRulePreviewResponse,
} from 'app/shared/model/financial-transaction.model';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { createEntity, getEntity, partialUpdateEntity, reset } from './financial-transaction.reducer';

const isCategoryCompatibleWithFlow = (category, flow) => {
  if (!category?.categoryType || !flow) {
    return true;
  }
  if (flow === 'OUT') {
    return category.categoryType === 'EXPENSE' || category.categoryType === 'BOTH';
  }
  if (flow === 'IN') {
    return category.categoryType === 'INCOME' || category.categoryType === 'BOTH';
  }
  return true;
};

const toOptionalNumber = value => {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  return typeof value === 'number' ? value : Number(value);
};

const RULE_PREVIEW_URL = 'api/financial-transactions/rule-preview';

const emptyCreateDraft = {
  account: '',
  transactionDate: '',
  postingDate: '',
  description: '',
  amount: '',
  externalReference: '',
  notes: '',
};

const getSelectedTagIds = event =>
  Array.from(event.target.selectedOptions)
    .map((option: HTMLOptionElement) => option.value)
    .filter(Boolean);

const getCreateDefaultValues = () => ({ flow: 'IN' });

const getEditDefaultValues = financialTransactionEntity => ({
  flow: 'IN',
  ...financialTransactionEntity,
  account: financialTransactionEntity?.account?.id,
  category: financialTransactionEntity?.category?.id,
  tags: financialTransactionEntity?.tags?.map(e => e.id.toString()),
});

const hasSuggestedOutputs = (previewResponse: IFinancialTransactionRulePreviewResponse | null) =>
  !!previewResponse?.suggestedCategory || !!previewResponse?.suggestedTags?.some(suggestion => !suggestion.alreadyPresent);

const getCategoryConflict = (previewResponse: IFinancialTransactionRulePreviewResponse | null) =>
  previewResponse?.conflicts?.find(conflict => conflict.field === 'CATEGORY');

const validateStepOneDraft = (createDraft, selectedAccountId, selectedFlow) => {
  if (!selectedAccountId && !createDraft.account) {
    return translate('fintrackApp.financialTransaction.rulePreview.validation.accountRequired');
  }
  if (!createDraft.description?.trim()) {
    return translate('fintrackApp.financialTransaction.rulePreview.validation.descriptionRequired');
  }
  const amount = toOptionalNumber(createDraft.amount);
  if (!amount || amount <= 0) {
    return translate('fintrackApp.financialTransaction.rulePreview.validation.amountRequired');
  }
  if (!selectedFlow) {
    return translate('fintrackApp.financialTransaction.rulePreview.validation.flowRequired');
  }
  if (!createDraft.transactionDate) {
    return translate('fintrackApp.financialTransaction.rulePreview.validation.transactionDateRequired');
  }
  return '';
};

const buildRulePreviewPayload = (
  createDraft,
  selectedAccountId,
  selectedFlow,
  selectedCategoryId,
  selectedTagIds,
): IFinancialTransactionRulePreviewRequest => ({
  accountId: toOptionalNumber(selectedAccountId || createDraft.account),
  description: createDraft.description,
  amount: toOptionalNumber(createDraft.amount),
  flow: selectedFlow,
  origin: 'MANUAL',
  transactionDate: createDraft.transactionDate,
  postingDate: createDraft.postingDate || null,
  externalReference: createDraft.externalReference || null,
  categoryId: selectedCategoryId ? Number(selectedCategoryId) : null,
  tagIds: selectedTagIds.map(Number),
});

const buildCreateEntityPayload = (createDraft, selectedFlow, selectedCategoryId, selectedTagIds, categories, tags, financialAccounts) => ({
  transactionDate: createDraft.transactionDate ? dayjs(createDraft.transactionDate) : undefined,
  postingDate: createDraft.postingDate ? dayjs(createDraft.postingDate) : null,
  description: createDraft.description,
  amount: toOptionalNumber(createDraft.amount),
  flow: selectedFlow,
  category: selectedCategoryId ? categories.find(it => it.id?.toString() === selectedCategoryId) : null,
  tags: selectedTagIds.map(tagId => tags.find(it => it.id?.toString() === tagId)).filter(Boolean),
  externalReference: createDraft.externalReference || null,
  notes: createDraft.notes || null,
  account: financialAccounts.find(it => it.id?.toString() === createDraft.account),
  origin: 'MANUAL' as const,
});

const buildUpdateEntityPayload = (values, selectedFlow, selectedCategoryId, categories) => ({
  id: values.id,
  transactionDate: values.transactionDate ? dayjs(values.transactionDate) : undefined,
  postingDate: values.postingDate ? dayjs(values.postingDate) : null,
  description: values.description,
  amount: toOptionalNumber(values.amount),
  flow: selectedFlow,
  category: selectedCategoryId ? categories.find(it => it.id?.toString() === selectedCategoryId) : null,
  tags: mapIdList(values.tags),
  notes: values.notes || null,
});

const RulePreviewAlerts = ({
  isNew,
  createStep,
  stepValidationError,
  previewError,
  previewResponse,
  hasPreviewSuggestions,
  categoryConflict,
}) => (
  <>
    {isNew ? (
      <Alert color="info" fade={false} data-testid="transaction-create-step">
        {translate(
          createStep === 1 ? 'fintrackApp.financialTransaction.rulePreview.step1' : 'fintrackApp.financialTransaction.rulePreview.step2',
        )}
      </Alert>
    ) : null}
    {stepValidationError ? (
      <Alert color="danger" fade={false} data-testid="step-validation-error">
        {stepValidationError}
      </Alert>
    ) : null}
    {previewError ? (
      <Alert color="danger" fade={false} data-testid="preview-error">
        {previewError}
      </Alert>
    ) : null}
    {isNew && createStep === 2 ? (
      <Alert color={hasPreviewSuggestions ? 'success' : 'secondary'} fade={false} data-testid="preview-summary">
        <Translate
          contentKey={
            hasPreviewSuggestions
              ? 'fintrackApp.financialTransaction.rulePreview.suggestionsPrefilled'
              : 'fintrackApp.financialTransaction.rulePreview.noSuggestions'
          }
        >
          Suggestions from rules were prefilled. You can change them before saving.
        </Translate>
      </Alert>
    ) : null}
    {isNew && createStep === 2 && categoryConflict ? (
      <Alert color="warning" fade={false} data-testid="preview-conflict">
        {translate('fintrackApp.financialTransaction.rulePreview.categoryConflict', {
          suggested: categoryConflict.suggestedValueLabel,
          current: categoryConflict.currentValueLabel,
        })}
      </Alert>
    ) : null}
    {isNew && createStep === 2 && previewResponse?.matchedRules?.length ? (
      <Alert color="light" fade={false} data-testid="matched-rules">
        <Translate contentKey="fintrackApp.financialTransaction.rulePreview.matchedRules">Matched rules</Translate>
        {': '}
        {previewResponse.matchedRules.map(rule => rule.ruleName).join(', ')}
      </Alert>
    ) : null}
  </>
);

const TransactionDetailsFields = ({
  isNew,
  createStep,
  financialTransactionEntity,
  financialAccounts,
  selectedAccount,
  selectedFlow,
  transactionFlowValues,
  createDraft,
  onDraftChange,
  onAccountChange,
  onFlowChange,
}) => {
  if (isNew && createStep !== 1) {
    return null;
  }

  return [
    <ValidatedField
      key="account"
      id="financial-transaction-account"
      name="account"
      data-cy="account"
      label={translate('fintrackApp.financialTransaction.account')}
      type={isNew ? 'select' : 'text'}
      readOnly={!isNew}
      disabled={!isNew}
      value={!isNew ? (financialTransactionEntity.account?.name ?? '') : createDraft.account}
      onChange={isNew ? onDraftChange('account') : onAccountChange}
    >
      {isNew ? (
        <>
          <option value="" key="0" />
          {financialAccounts?.map(otherEntity => (
            <option value={otherEntity.id} key={otherEntity.id}>
              {otherEntity.name}
            </option>
          ))}
        </>
      ) : null}
    </ValidatedField>,
    isNew ? (
      <FormText key="accountRequired">
        <Translate contentKey="entity.validation.required">This field is required.</Translate>
      </FormText>
    ) : null,
    <ValidatedField
      key="transactionDate"
      label={translate('fintrackApp.financialTransaction.transactionDate')}
      id="financial-transaction-transactionDate"
      name="transactionDate"
      data-cy="transactionDate"
      type="date"
      value={isNew ? createDraft.transactionDate : undefined}
      onChange={isNew ? onDraftChange('transactionDate') : undefined}
      validate={{
        required: { value: true, message: translate('entity.validation.required') },
      }}
    />,
    <ValidatedField
      key="postingDate"
      label={translate('fintrackApp.financialTransaction.postingDate')}
      id="financial-transaction-postingDate"
      name="postingDate"
      data-cy="postingDate"
      type="date"
      value={isNew ? createDraft.postingDate : undefined}
      onChange={isNew ? onDraftChange('postingDate') : undefined}
    />,
    <ValidatedField
      key="description"
      label={translate('fintrackApp.financialTransaction.description')}
      id="financial-transaction-description"
      name="description"
      data-cy="description"
      type="text"
      value={isNew ? createDraft.description : undefined}
      onChange={isNew ? onDraftChange('description') : undefined}
      validate={{
        required: { value: true, message: translate('entity.validation.required') },
        minLength: { value: 1, message: translate('entity.validation.minlength', { min: 1 }) },
        maxLength: { value: 500, message: translate('entity.validation.maxlength', { max: 500 }) },
      }}
    />,
    <ValidatedField
      key="flow"
      label={translate('fintrackApp.financialTransaction.flow')}
      id="financial-transaction-flow"
      name="flow"
      data-cy="flow"
      type="select"
      value={selectedFlow}
      onChange={onFlowChange}
    >
      {transactionFlowValues.map(transactionFlow => (
        <option value={transactionFlow} key={transactionFlow}>
          {translate(`fintrackApp.TransactionFlow.${transactionFlow}`)}
        </option>
      ))}
    </ValidatedField>,
    <ValidatedField
      key="amount"
      label={translate('fintrackApp.financialTransaction.amount')}
      id="financial-transaction-amount"
      name="amount"
      data-cy="amount"
      type="text"
      value={isNew ? createDraft.amount : undefined}
      onChange={isNew ? onDraftChange('amount') : undefined}
      validate={{
        required: { value: true, message: translate('entity.validation.required') },
        min: { value: 0.01, message: translate('entity.validation.min', { min: 0.01 }) },
        validate: v => (isNumber(v) && Number(v) > 0) || translate('entity.validation.min', { min: 0.01 }),
      }}
    />,
    selectedAccount?.currency ? <FormText key="currency">{selectedAccount.currency}</FormText> : null,
    isNew ? (
      <ValidatedField
        key="externalReference"
        label={translate('fintrackApp.financialTransaction.externalReference')}
        id="financial-transaction-externalReference"
        name="externalReference"
        data-cy="externalReference"
        type="text"
        value={createDraft.externalReference}
        onChange={onDraftChange('externalReference')}
        validate={{
          maxLength: { value: 150, message: translate('entity.validation.maxlength', { max: 150 }) },
        }}
      />
    ) : null,
    <ValidatedField
      key="notes"
      label={translate('fintrackApp.financialTransaction.notes')}
      id="financial-transaction-notes"
      name="notes"
      data-cy="notes"
      type="text"
      value={isNew ? createDraft.notes : undefined}
      onChange={isNew ? onDraftChange('notes') : undefined}
      validate={{
        maxLength: { value: 1000, message: translate('entity.validation.maxlength', { max: 1000 }) },
      }}
    />,
  ];
};

const CategorizationFields = ({
  isNew,
  createStep,
  selectedCategoryId,
  selectedTagIds,
  filteredCategories,
  tags,
  onCategoryChange,
  onTagsChange,
}) => {
  if (isNew && createStep !== 2) {
    return null;
  }

  return [
    <ValidatedField
      key="category"
      id="financial-transaction-category"
      name="category"
      data-cy="category"
      label={translate('fintrackApp.financialTransaction.category')}
      type="select"
      value={selectedCategoryId}
      onChange={onCategoryChange}
    >
      <option value="" key="0" />
      {filteredCategories?.map(otherEntity => (
        <option value={otherEntity.id} key={otherEntity.id}>
          {otherEntity.name}
        </option>
      ))}
    </ValidatedField>,
    <ValidatedField
      key="tags"
      label={translate('fintrackApp.financialTransaction.tags')}
      id="financial-transaction-tags"
      data-cy="tags"
      type="select"
      multiple
      name="tags"
      value={isNew ? selectedTagIds : undefined}
      onChange={isNew ? onTagsChange : undefined}
    >
      <option value="" key="0" />
      {tags?.map(otherEntity => (
        <option value={otherEntity.id} key={otherEntity.id}>
          {otherEntity.name}
        </option>
      ))}
    </ValidatedField>,
  ];
};

const FormActions = ({ isNew, createStep, updating, previewLoading, onBackToDetails, onPreviewNext }) => (
  <>
    <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/financial-transaction" replace color="info">
      <FontAwesomeIcon icon="arrow-left" />
      &nbsp;
      <span className="d-none d-md-inline">
        <Translate contentKey="entity.action.back">Back</Translate>
      </span>
    </Button>
    &nbsp;
    {isNew && createStep === 2 ? (
      <>
        <Button color="secondary" id="back-to-details" type="button" onClick={onBackToDetails}>
          <FontAwesomeIcon icon="arrow-left" />
          &nbsp;
          <Translate contentKey="fintrackApp.financialTransaction.rulePreview.backToDetails">Back to details</Translate>
        </Button>
        &nbsp;
      </>
    ) : null}
    {isNew && createStep === 1 ? (
      <Button color="primary" id="preview-rules" type="button" disabled={previewLoading} onClick={onPreviewNext}>
        {previewLoading ? <Spinner size="sm" /> : <FontAwesomeIcon icon="search" />}
        &nbsp;
        <Translate contentKey="fintrackApp.financialTransaction.rulePreview.nextToCategorization">Next</Translate>
      </Button>
    ) : null}
    {(!isNew || createStep === 2) && (
      <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={updating}>
        <FontAwesomeIcon icon="save" />
        &nbsp;
        <Translate contentKey="entity.action.save">Save</Translate>
      </Button>
    )}
  </>
);

export const FinancialTransactionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const categories = useAppSelector(state => state.category.entities);
  const tags = useAppSelector(state => state.tag.entities);
  const financialTransactionEntity = useAppSelector(state => state.financialTransaction.entity);
  const loading = useAppSelector(state => state.financialTransaction.loading);
  const updating = useAppSelector(state => state.financialTransaction.updating);
  const updateSuccess = useAppSelector(state => state.financialTransaction.updateSuccess);
  const transactionFlowValues = Object.keys(TransactionFlow);
  const [selectedFlow, setSelectedFlow] = useState<keyof typeof TransactionFlow>('IN');
  const [selectedCategoryId, setSelectedCategoryId] = useState('');
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState('');
  const [createStep, setCreateStep] = useState<1 | 2>(1);
  const [createDraft, setCreateDraft] = useState(emptyCreateDraft);
  const [previewResponse, setPreviewResponse] = useState<IFinancialTransactionRulePreviewResponse | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState('');
  const [stepValidationError, setStepValidationError] = useState('');

  const handleClose = () => {
    navigate(`/financial-transaction${location.search}`);
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getFinancialAccounts({}));
    dispatch(getCategories({}));
    dispatch(getTags({}));
  }, []);

  useEffect(() => {
    if (isNew) {
      setSelectedFlow('IN');
      setSelectedCategoryId('');
      setSelectedTagIds([]);
      setSelectedAccountId('');
      setCreateStep(1);
      setCreateDraft(emptyCreateDraft);
      setPreviewResponse(null);
      setPreviewError('');
      setStepValidationError('');
      return;
    }
    if (financialTransactionEntity?.id) {
      setSelectedFlow((financialTransactionEntity.flow as keyof typeof TransactionFlow) ?? 'IN');
      setSelectedCategoryId(financialTransactionEntity.category?.id?.toString() ?? '');
      setSelectedTagIds(financialTransactionEntity.tags?.map(tag => tag.id?.toString()).filter(Boolean) ?? []);
      setSelectedAccountId(financialTransactionEntity.account?.id?.toString() ?? '');
    }
  }, [isNew, financialTransactionEntity?.id]);

  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  const selectedAccount = useMemo(
    () =>
      financialAccounts.find(it => it.id?.toString() === selectedAccountId) ?? (!isNew ? financialTransactionEntity.account : undefined),
    [financialAccounts, selectedAccountId, financialTransactionEntity.account, isNew],
  );

  const filteredCategories = useMemo(
    () => categories.filter(category => isCategoryCompatibleWithFlow(category, selectedFlow)),
    [categories, selectedFlow],
  );

  const handleFlowChange = event => {
    const nextFlow = event.target.value as keyof typeof TransactionFlow;
    setSelectedFlow(nextFlow);
    const currentCategory = categories.find(category => category.id?.toString() === selectedCategoryId);
    if (currentCategory && !isCategoryCompatibleWithFlow(currentCategory, nextFlow)) {
      setSelectedCategoryId('');
    }
  };

  const updateCreateDraft = field => event => {
    const value = event.target.value;
    setCreateDraft(draft => ({ ...draft, [field]: value }));
    if (field === 'account') {
      setSelectedAccountId(value);
    }
  };

  const handleCategoryChange = event => {
    setSelectedCategoryId(event.target.value);
  };

  const handleTagsChange = event => {
    setSelectedTagIds(getSelectedTagIds(event));
  };

  const applyPreviewSuggestions = (preview: IFinancialTransactionRulePreviewResponse) => {
    if (!selectedCategoryId && preview.suggestedCategory?.categoryId && !preview.suggestedCategory.conflictsWithCurrentValue) {
      setSelectedCategoryId(preview.suggestedCategory.categoryId.toString());
    }

    const nextTagIds = new Set(selectedTagIds);
    preview.suggestedTags
      ?.filter(suggestion => suggestion.tagId && !suggestion.alreadyPresent && !suggestion.duplicateOfEarlierSuggestion)
      .forEach(suggestion => nextTagIds.add(suggestion.tagId.toString()));
    setSelectedTagIds(Array.from(nextTagIds));
  };

  const handlePreviewNext = async () => {
    setPreviewError('');
    setStepValidationError('');
    const validationError = validateStepOneDraft(createDraft, selectedAccountId, selectedFlow);
    if (validationError) {
      setStepValidationError(validationError);
      return;
    }

    setPreviewLoading(true);
    try {
      const response = await axios.post<IFinancialTransactionRulePreviewResponse>(
        RULE_PREVIEW_URL,
        buildRulePreviewPayload(createDraft, selectedAccountId, selectedFlow, selectedCategoryId, selectedTagIds),
      );
      setPreviewResponse(response.data);
      applyPreviewSuggestions(response.data);
      setCreateStep(2);
    } catch (error) {
      setPreviewError(translate('fintrackApp.financialTransaction.rulePreview.failed'));
      setCreateStep(1);
    } finally {
      setPreviewLoading(false);
    }
  };

  const saveEntity = values => {
    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }

    if (isNew) {
      dispatch(
        createEntity(
          buildCreateEntityPayload(createDraft, selectedFlow, selectedCategoryId, selectedTagIds, categories, tags, financialAccounts),
        ),
      );
    } else {
      dispatch(partialUpdateEntity(buildUpdateEntityPayload(values, selectedFlow, selectedCategoryId, categories)));
    }
  };

  const defaultValues = () => (isNew ? getCreateDefaultValues() : getEditDefaultValues(financialTransactionEntity));

  const hasPreviewSuggestions = hasSuggestedOutputs(previewResponse);

  const categoryConflict = getCategoryConflict(previewResponse);

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.financialTransaction.home.createOrEditLabel" data-cy="FinancialTransactionCreateUpdateHeading">
            <Translate contentKey="fintrackApp.financialTransaction.home.createOrEditLabel">
              Create or edit a FinancialTransaction
            </Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? (
            <p>Loading...</p>
          ) : (
            <ValidatedForm defaultValues={defaultValues()} onSubmit={saveEntity}>
              {!isNew ? (
                <ValidatedField
                  name="id"
                  required
                  readOnly
                  id="financial-transaction-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <RulePreviewAlerts
                isNew={isNew}
                createStep={createStep}
                stepValidationError={stepValidationError}
                previewError={previewError}
                previewResponse={previewResponse}
                hasPreviewSuggestions={hasPreviewSuggestions}
                categoryConflict={categoryConflict}
              />
              {TransactionDetailsFields({
                isNew,
                createStep,
                financialTransactionEntity,
                financialAccounts,
                selectedAccount,
                selectedFlow,
                transactionFlowValues,
                createDraft,
                onDraftChange: updateCreateDraft,
                onAccountChange: event => setSelectedAccountId(event.target.value),
                onFlowChange: handleFlowChange,
              })}
              {CategorizationFields({
                isNew,
                createStep,
                selectedCategoryId,
                selectedTagIds,
                filteredCategories,
                tags,
                onCategoryChange: handleCategoryChange,
                onTagsChange: handleTagsChange,
              })}
              <FormActions
                isNew={isNew}
                createStep={createStep}
                updating={updating}
                previewLoading={previewLoading}
                onBackToDetails={() => setCreateStep(1)}
                onPreviewNext={handlePreviewNext}
              />
            </ValidatedForm>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default FinancialTransactionUpdate;
