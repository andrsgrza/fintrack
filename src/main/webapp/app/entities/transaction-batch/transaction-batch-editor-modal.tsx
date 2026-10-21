import React, { useEffect, useRef, useState } from 'react';
import { translate } from 'react-jhipster';
import {
  Button,
  Col,
  Form,
  FormFeedback,
  FormGroup,
  Input,
  InputGroup,
  InputGroupText,
  Label,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Row,
  Spinner,
} from 'reactstrap';

import { ICategorySelectable } from 'app/shared/model/category-selectable.model';
import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';
import { ITagSelectable } from 'app/shared/model/tag-selectable.model';
import { formatFinancialAccountLabel } from 'app/entities/financial-account/financial-account-labels';
import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { ProductTagSelector, ProductTagSelectorTag } from 'app/shared/ui/product-tag-selector';
import { AppliedRuleIndicator } from 'app/shared/ui/applied-rule-indicator';
import { FinancialFlowSelector } from 'app/shared/ui/financial-flow-selector';
import {
  BatchCandidate,
  BatchCreateCounterpartConfiguration,
  BatchCreateCounterpartInput,
  BatchRulePreview,
  BatchTransferCounterpart,
  BatchTransferCounterpartPage,
} from './transaction-batch-api';
import { amountMagnitude } from './transaction-batch-presentation';

export type BatchEditorValues = {
  transactionDate: string;
  postingDate: string;
  samePostingDate: boolean;
  description: string;
  flow: 'IN' | 'OUT';
  amount: string;
  categoryId?: number;
  tags: ProductTagSelectorTag[];
  externalReference: string;
  notes: string;
};

export type BatchSuggestionDomain = 'DESCRIPTION' | 'CATEGORY' | 'TAGS';

const blankValues = (): BatchEditorValues => ({
  transactionDate: '',
  postingDate: '',
  samePostingDate: true,
  description: '',
  flow: 'OUT',
  amount: '',
  categoryId: undefined,
  tags: [],
  externalReference: '',
  notes: '',
});

const valuesFor = (candidate?: BatchCandidate): BatchEditorValues => {
  if (!candidate) return blankValues();
  const transactionDate = candidate.transactionDate ?? '';
  const postingDate = candidate.postingDate ?? '';
  return {
    transactionDate,
    postingDate,
    samePostingDate: !!transactionDate && transactionDate === postingDate,
    description: candidate.description ?? '',
    flow: candidate.flow === 'IN' ? 'IN' : 'OUT',
    amount: amountMagnitude(candidate.signedAmount),
    categoryId: candidate.categoryId ?? undefined,
    tags: candidate.tags,
    externalReference: candidate.externalReference ?? '',
    notes: candidate.notes ?? '',
  };
};

/** A manual create modal remains purely local until a user supplies a meaningful draft value. */
const hasMaterialDraftValue = (values: BatchEditorValues) =>
  Boolean(
    values.transactionDate ||
      values.postingDate ||
      values.description.trim() ||
      values.amount ||
      values.categoryId ||
      values.tags.length ||
      values.externalReference.trim() ||
      values.notes.trim(),
  );

const materialDraftFingerprint = (values: BatchEditorValues) =>
  JSON.stringify({
    transactionDate: values.transactionDate,
    postingDate: values.postingDate,
    description: values.description,
    flow: values.flow,
    amount: values.amount,
    categoryId: values.categoryId,
    tagIds: values.tags.flatMap(tag => (tag.id ? [tag.id] : [])),
    externalReference: values.externalReference,
    notes: values.notes,
  });

const categoryStyle = (color?: string | null): React.CSSProperties | undefined =>
  color ? { borderInlineStart: `0.3rem solid ${color}`, backgroundColor: `${color}12` } : undefined;

const Suggestion = ({
  title,
  content,
  ruleName,
  ruleHref,
  onApply,
  disabled,
}: {
  title: string;
  content: React.ReactNode;
  ruleName?: string | null;
  ruleHref?: string | null;
  onApply: () => void;
  disabled: boolean;
}) => (
  <div className="transaction-batch-domain-suggestion mt-2" data-testid={`batchEditorSuggestion-${title}`}>
    <div className="small text-muted">{title}</div>
    <div className="fw-semibold">{content}</div>
    {ruleName ? (
      <div className="small text-muted mt-1">
        Sugerido por{' '}
        {ruleHref ? (
          <a href={ruleHref} className="transaction-batch-rule-link">
            {ruleName}
          </a>
        ) : (
          ruleName
        )}
      </div>
    ) : null}
    <Button type="button" size="sm" color="primary" outline className="mt-2" onClick={onApply} disabled={disabled}>
      Aplicar {title.toLocaleLowerCase()}
    </Button>
  </div>
);

type EditorFieldError = 'category' | 'amount' | 'transactionDate' | 'postingDate' | null;
type UpdateEditorValue = <K extends keyof BatchEditorValues>(key: K, value: BatchEditorValues[K]) => void;

const TransactionFieldsSection = ({
  candidate,
  preview,
  values,
  update,
  setValues,
  currency,
  saving,
  fieldError,
  onApplySuggestion,
}: {
  candidate?: BatchCandidate;
  preview?: BatchRulePreview;
  values: BatchEditorValues;
  update: UpdateEditorValue;
  setValues: React.Dispatch<React.SetStateAction<BatchEditorValues>>;
  currency?: string | null;
  saving: boolean;
  fieldError: EditorFieldError;
  onApplySuggestion?: (domain: BatchSuggestionDomain) => void;
}) => {
  const descriptionSuggestion =
    candidate?.source === 'FILE_IMPORT' &&
    preview?.suggestedDescription?.trim() &&
    preview.suggestedDescription.trim() !== values.description.trim();

  return (
    <section className="transaction-batch-editor-section">
      <h3 className="transaction-batch-editor-heading">Transacción</h3>
      <Row className="g-3">
        <Col md="6">
          <FormGroup>
            <Label for="batch-editor-transaction-date">Fecha de la transacción</Label>
            <Input
              id="batch-editor-transaction-date"
              type="date"
              value={values.transactionDate}
              invalid={fieldError === 'transactionDate'}
              onChange={event => update('transactionDate', event.target.value)}
              disabled={saving}
            />
            {fieldError === 'transactionDate' ? <FormFeedback>Revisa la fecha de la transacción.</FormFeedback> : null}
          </FormGroup>
        </Col>
        <Col md="6">
          <FormGroup>
            <Label for="batch-editor-posting-date">Fecha de posteo</Label>
            <Input
              id="batch-editor-posting-date"
              type="date"
              value={values.postingDate}
              invalid={fieldError === 'postingDate'}
              onChange={event => update('postingDate', event.target.value)}
              disabled={saving || values.samePostingDate}
            />
            {fieldError === 'postingDate' ? <FormFeedback>Revisa la fecha de posteo.</FormFeedback> : null}
          </FormGroup>
          <FormGroup check className="d-flex align-items-center gap-2 mb-0">
            <Input
              id="batch-editor-same-posting-date"
              type="checkbox"
              checked={values.samePostingDate}
              onChange={event =>
                setValues(current => ({
                  ...current,
                  samePostingDate: event.target.checked,
                  postingDate: event.target.checked ? current.transactionDate : current.postingDate,
                }))
              }
              disabled={saving}
            />
            <Label check for="batch-editor-same-posting-date">
              Usar la misma fecha para el posteo
            </Label>
          </FormGroup>
        </Col>
      </Row>
      <FormGroup className="mt-3">
        <Label for="batch-editor-description">Descripción</Label>
        <Input
          id="batch-editor-description"
          value={values.description}
          onChange={event => update('description', event.target.value)}
          disabled={saving}
        />
        <div className="mt-1">
          <AppliedRuleIndicator
            attribution={candidate?.descriptionAppliedByRule}
            href="/description-normalization-rule"
            dataCy="batchAppliedRuleAttribution"
          />
        </div>
        {descriptionSuggestion ? (
          <Suggestion
            title="descripción sugerida"
            content={preview?.suggestedDescription}
            ruleName={preview?.descriptionRuleName}
            ruleHref={preview?.descriptionRuleId ? `/description-normalization-rule/${preview.descriptionRuleId}/view` : undefined}
            onApply={() => onApplySuggestion?.('DESCRIPTION')}
            disabled={saving || !onApplySuggestion}
          />
        ) : null}
      </FormGroup>
      <Row className="g-3">
        <Col md="6">
          <FormGroup>
            <Label id="batch-editor-flow-label">Tipo</Label>
            <FinancialFlowSelector
              flow={values.flow}
              onChange={flow => update('flow', flow)}
              incomeLabel="INGRESO"
              expenseLabel="GASTO"
              disabled={saving}
              ariaLabelledBy="batch-editor-flow-label"
              dataCy="batchEditorFlow"
            />
          </FormGroup>
        </Col>
        <Col md="6">
          <FormGroup>
            <Label for="batch-editor-amount">Monto</Label>
            <InputGroup>
              <Input
                id="batch-editor-amount"
                type="number"
                min="0"
                step="0.01"
                value={values.amount}
                invalid={fieldError === 'amount'}
                onChange={event => update('amount', event.target.value)}
                disabled={saving}
              />
              <InputGroupText>{currency ?? '—'}</InputGroupText>
              {fieldError === 'amount' ? <FormFeedback>Ingresa un monto positivo válido.</FormFeedback> : null}
            </InputGroup>
          </FormGroup>
        </Col>
      </Row>
    </section>
  );
};

const ClassificationSection = ({
  candidate,
  preview,
  categories,
  tags,
  values,
  update,
  saving,
  fieldError,
  onApplySuggestion,
}: {
  candidate?: BatchCandidate;
  preview?: BatchRulePreview;
  categories: ICategorySelectable[];
  tags: ITagSelectable[];
  values: BatchEditorValues;
  update: UpdateEditorValue;
  saving: boolean;
  fieldError: EditorFieldError;
  onApplySuggestion?: (domain: BatchSuggestionDomain) => void;
}) => {
  const selectedCategory = categories.find(category => category.id === values.categoryId);
  const suggestedCategory = categories.find(category => category.id === preview?.suggestedCategoryId);
  const suggestedTags = (preview?.eligibleTagIds ?? [])
    .filter(id => !values.tags.some(tag => tag.id === id))
    .map(id => tags.find(tag => tag.id === id))
    .filter((tag): tag is ITagSelectable => !!tag);

  return (
    <section className="transaction-batch-editor-section mt-4">
      <h3 className="transaction-batch-editor-heading">Clasificación</h3>
      <Row className="g-3">
        <Col md="6">
          <FormGroup>
            <Label for="batch-editor-category">Categoría</Label>
            <Input
              id="batch-editor-category"
              type="select"
              value={values.categoryId ?? ''}
              style={categoryStyle(selectedCategory?.color)}
              invalid={fieldError === 'category'}
              onChange={event => update('categoryId', event.target.value ? Number(event.target.value) : undefined)}
              disabled={saving}
            >
              <option value="">Sin categoría</option>
              {categories
                .filter(category => category.active !== false || category.id === values.categoryId)
                .map(category => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                    {category.active === false ? ' (inactiva)' : ''}
                  </option>
                ))}
            </Input>
            <div className="mt-1">
              <AppliedRuleIndicator
                attribution={candidate?.categoryAppliedByRule}
                href="/transaction-rule"
                dataCy="batchAppliedRuleAttribution"
              />
            </div>
            {fieldError === 'category' ? (
              <FormFeedback>
                La categoría elegida sólo es válida para el otro tipo de movimiento. Elige otra categoría o cambia el tipo.
              </FormFeedback>
            ) : null}
            {!values.categoryId && suggestedCategory ? (
              <Suggestion
                title="categoría sugerida"
                content={suggestedCategory.name}
                ruleName={preview?.suggestedCategoryRuleName}
                ruleHref={preview?.suggestedCategoryRuleId ? `/transaction-rule/${preview.suggestedCategoryRuleId}/view` : undefined}
                onApply={() => onApplySuggestion?.('CATEGORY')}
                disabled={saving || !onApplySuggestion}
              />
            ) : null}
          </FormGroup>
        </Col>
        <Col md="6">
          <FormGroup>
            <Label>Etiquetas</Label>
            <ProductTagSelector
              id="batch-editor-tags"
              dataCy="batchEditorTags"
              selectedTags={values.tags}
              availableTags={tags}
              onChange={selected => update('tags', selected)}
              disabled={saving}
              tagAdornment={tag => (
                <AppliedRuleIndicator
                  attribution={candidate?.tags.find(current => current.id === tag.id)?.appliedByRule}
                  href="/transaction-rule"
                  dataCy="batchAppliedRuleAttribution"
                />
              )}
            />
            {suggestedTags.length ? (
              <Suggestion
                title="etiquetas sugeridas"
                content={suggestedTags.map(tag => tag.name).join(', ')}
                ruleName={preview?.eligibleTagSuggestions?.[0]?.ruleName}
                ruleHref={
                  preview?.eligibleTagSuggestions?.[0]?.ruleId
                    ? `/transaction-rule/${preview.eligibleTagSuggestions[0].ruleId}/view`
                    : undefined
                }
                onApply={() => onApplySuggestion?.('TAGS')}
                disabled={saving || !onApplySuggestion}
              />
            ) : null}
          </FormGroup>
        </Col>
      </Row>
    </section>
  );
};

const OptionalDetailsSection = ({ values, update, saving }: { values: BatchEditorValues; update: UpdateEditorValue; saving: boolean }) => (
  <section className="transaction-batch-editor-section mt-4">
    <h3 className="transaction-batch-editor-heading">Detalles opcionales</h3>
    <details>
      <summary className="fw-semibold">Más detalles</summary>
      <Row className="g-3 pt-3">
        <Col md="6">
          <FormGroup>
            <Label for="batch-editor-reference">Referencia externa</Label>
            <Input
              id="batch-editor-reference"
              value={values.externalReference}
              onChange={event => update('externalReference', event.target.value)}
              disabled={saving}
            />
          </FormGroup>
        </Col>
        <Col md="6">
          <FormGroup className="mb-0">
            <Label for="batch-editor-notes">Notas</Label>
            <Input
              id="batch-editor-notes"
              type="textarea"
              value={values.notes}
              onChange={event => update('notes', event.target.value)}
              disabled={saving}
            />
          </FormGroup>
        </Col>
      </Row>
    </details>
  </section>
);

const counterpartLabel = (counterpart: BatchTransferCounterpart) =>
  [counterpart.accountName, counterpart.description, counterpart.transactionDate, counterpart.amount, counterpart.currency]
    .filter(value => value !== null && value !== undefined && value !== '')
    .join(' · ');

const transferTranslation = (key: string, interpolate?: Record<string, string | number>) =>
  translate(`fintrackApp.transactionBatch.transfer.${key}`, interpolate);

type CounterpartMode = 'CREATE_COUNTERPART' | 'LINK_EXISTING';

const InternalTransferSection = ({
  candidate,
  values,
  sourceAccountId,
  currency,
  saving,
  onSearch,
  onSelect,
  onConfigureCreate,
  onClear,
}: {
  candidate?: BatchCandidate;
  values: BatchEditorValues;
  sourceAccountId?: number;
  currency?: string | null;
  saving: boolean;
  onSearch?: (options: { search?: string; accountId?: number; page?: number }) => Promise<BatchTransferCounterpartPage>;
  onSelect?: (counterpart: BatchTransferCounterpart) => Promise<BatchCandidate | undefined>;
  onConfigureCreate?: (configuration: BatchCreateCounterpartInput) => Promise<BatchCandidate | undefined>;
  onClear?: () => Promise<BatchCandidate | undefined>;
}) => {
  const [enabled, setEnabled] = useState(Boolean(candidate?.internalTransfer?.enabled));
  const [mode, setMode] = useState<CounterpartMode>(
    candidate?.internalTransfer?.mode === 'LINK_EXISTING' ? 'LINK_EXISTING' : 'CREATE_COUNTERPART',
  );
  const [search, setSearch] = useState('');
  const [accountFilterId, setAccountFilterId] = useState('');
  const [createAccountId, setCreateAccountId] = useState('');
  const [createPostingDate, setCreatePostingDate] = useState('');
  const [createDescription, setCreateDescription] = useState('');
  const [createExternalReference, setCreateExternalReference] = useState('');
  const [createNotes, setCreateNotes] = useState('');
  const [accounts, setAccounts] = useState<IFinancialAccountSelectable[]>([]);
  const [page, setPage] = useState(0);
  const [results, setResults] = useState<BatchTransferCounterpart[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const latestSearchRequest = useRef(0);
  const counterpart = candidate?.internalTransfer?.counterpart ?? null;
  const persistedCreate = candidate?.internalTransfer?.createCounterpart ?? null;
  const canSearch = Boolean(candidate && sourceAccountId && values.transactionDate && values.amount && values.flow);
  const counterpartAccounts = accounts.filter(account => account.id !== sourceAccountId && (!currency || account.currency === currency));

  const initializeCreateCounterpart = () => {
    const configuration = candidate?.internalTransfer?.mode === 'CREATE_COUNTERPART' ? persistedCreate : null;
    setCreateAccountId(configuration?.accountId ? String(configuration.accountId) : '');
    setCreatePostingDate(configuration?.postingDate ?? candidate?.postingDate ?? values.postingDate ?? '');
    setCreateDescription(configuration?.description ?? candidate?.description ?? values.description ?? '');
    setCreateExternalReference(configuration?.externalReference ?? candidate?.externalReference ?? values.externalReference ?? '');
    setCreateNotes(configuration?.notes ?? candidate?.notes ?? values.notes ?? '');
  };

  useEffect(() => {
    setEnabled(Boolean(candidate?.internalTransfer?.enabled));
    setMode(
      candidate?.internalTransfer?.mode === 'CREATE_COUNTERPART'
        ? 'CREATE_COUNTERPART'
        : candidate?.internalTransfer?.enabled
          ? 'LINK_EXISTING'
          : 'CREATE_COUNTERPART',
    );
    setSearch('');
    setAccountFilterId('');
    setPage(0);
    setResults([]);
    setTotalElements(0);
    setTotalPages(0);
    setError('');
    const configuration = candidate?.internalTransfer?.mode === 'CREATE_COUNTERPART' ? persistedCreate : null;
    setCreateAccountId(configuration?.accountId ? String(configuration.accountId) : '');
    setCreatePostingDate(configuration?.postingDate ?? candidate?.postingDate ?? values.postingDate ?? '');
    setCreateDescription(configuration?.description ?? candidate?.description ?? values.description ?? '');
    setCreateExternalReference(configuration?.externalReference ?? candidate?.externalReference ?? values.externalReference ?? '');
    setCreateNotes(configuration?.notes ?? candidate?.notes ?? values.notes ?? '');
  }, [candidate?.id, candidate?.version]);

  useEffect(() => {
    if (!enabled || !sourceAccountId) return;
    let mounted = true;
    getSelectableFinancialAccounts(sourceAccountId)
      .then(nextAccounts => {
        if (mounted) setAccounts(nextAccounts);
      })
      .catch(() => {
        if (mounted) setAccounts([]);
      });
    return () => {
      mounted = false;
    };
  }, [enabled, sourceAccountId]);

  useEffect(() => {
    if (!enabled || mode !== 'LINK_EXISTING' || !onSearch || !canSearch) {
      latestSearchRequest.current += 1;
      if (enabled && !canSearch) {
        setResults([]);
        setTotalElements(0);
        setTotalPages(0);
      }
      return;
    }
    const requestId = latestSearchRequest.current + 1;
    latestSearchRequest.current = requestId;
    const load = async () => {
      setSearching(true);
      setError('');
      try {
        const response = await onSearch({ search, accountId: accountFilterId ? Number(accountFilterId) : undefined, page });
        if (requestId !== latestSearchRequest.current) return;
        setResults(response.content ?? []);
        setTotalElements(response.totalElements ?? response.content?.length ?? 0);
        setTotalPages(response.totalPages ?? 0);
      } catch {
        if (requestId !== latestSearchRequest.current) return;
        setResults([]);
        setTotalElements(0);
        setTotalPages(0);
        setError(transferTranslation('searchFailure'));
      } finally {
        if (requestId === latestSearchRequest.current) setSearching(false);
      }
    };
    const delay = search.trim() ? window.setTimeout(() => void load(), 250) : undefined;
    if (!delay) void load();
    return () => {
      latestSearchRequest.current += 1;
      if (delay) window.clearTimeout(delay);
    };
  }, [accountFilterId, canSearch, enabled, mode, onSearch, page, search]);

  const selectCounterpart = async (next: BatchTransferCounterpart) => {
    if (!onSelect) return;
    setBusy(true);
    setError('');
    try {
      const updated = await onSelect(next);
      if (!updated) setError(transferTranslation('selectFailure'));
      else {
        setEnabled(true);
        setResults([]);
      }
    } finally {
      setBusy(false);
    }
  };

  const clear = async () => {
    if (!onClear) return;
    setBusy(true);
    setError('');
    try {
      const updated = await onClear();
      if (!updated) setError(transferTranslation('clearFailure'));
      else {
        setEnabled(false);
        setResults([]);
      }
    } finally {
      setBusy(false);
    }
  };

  const configureCreate = async () => {
    if (!onConfigureCreate || !createAccountId || !createDescription.trim()) return;
    setBusy(true);
    setError('');
    try {
      const updated = await onConfigureCreate({
        accountId: Number(createAccountId),
        postingDate: createPostingDate || undefined,
        description: createDescription,
        externalReference: createExternalReference || undefined,
        notes: createNotes || undefined,
      });
      if (!updated) setError(transferTranslation('configureFailure'));
      else setEnabled(true);
    } finally {
      setBusy(false);
    }
  };

  const selectMode = (nextMode: CounterpartMode) => {
    if (nextMode === mode) return;
    setError('');
    setMode(nextMode);
    setSearch('');
    setResults([]);
    setPage(0);
    if (nextMode === 'CREATE_COUNTERPART') initializeCreateCounterpart();
  };

  return (
    <section className="transaction-batch-editor-section mt-4" data-cy="transactionBatchTransferSection">
      <h3 className="transaction-batch-editor-heading">{transferTranslation('title')}</h3>
      <FormGroup switch className="mb-3">
        <Input
          id="batch-editor-internal-transfer"
          type="switch"
          role="switch"
          checked={enabled}
          disabled={saving || busy}
          onChange={event => {
            if (event.target.checked) {
              setEnabled(true);
              setError('');
            } else if (candidate?.internalTransfer?.enabled) {
              void clear();
            } else {
              setEnabled(false);
            }
          }}
          data-cy="transactionBatchInternalTransferToggle"
        />
        <Label check for="batch-editor-internal-transfer">
          {transferTranslation('title')}
        </Label>
      </FormGroup>
      {!enabled ? <p className="small text-muted mb-0">{transferTranslation('offHelp')}</p> : null}
      {enabled ? (
        <div className="transaction-batch-transfer-counterpart border rounded-3 p-3">
          <div className="d-flex flex-wrap gap-2 mb-3" role="group" aria-label={transferTranslation('mode')}>
            <Button
              type="button"
              size="sm"
              color={mode === 'CREATE_COUNTERPART' ? 'primary' : 'secondary'}
              outline={mode !== 'CREATE_COUNTERPART'}
              onClick={() => selectMode('CREATE_COUNTERPART')}
              disabled={saving || busy}
              data-cy="transactionBatchCreateCounterpartMode"
            >
              {transferTranslation('createCounterpart')}
            </Button>
            <Button
              type="button"
              size="sm"
              color={mode === 'LINK_EXISTING' ? 'primary' : 'secondary'}
              outline={mode !== 'LINK_EXISTING'}
              onClick={() => selectMode('LINK_EXISTING')}
              disabled={saving || busy}
              data-cy="transactionBatchLinkExistingMode"
            >
              {transferTranslation('linkExisting')}
            </Button>
          </div>
          {mode === 'CREATE_COUNTERPART' ? (
            <div data-cy="transactionBatchCreateCounterpartConfiguration">
              <p className="small text-muted mb-3">{transferTranslation('createHelp')}</p>
              {candidate?.internalTransfer?.stale ? (
                <div className="alert alert-warning py-2" role="alert" data-cy="transactionBatchTransferStale">
                  {transferTranslation('createStale')}
                </div>
              ) : null}
              <Row className="g-3">
                <Col md="6">
                  <FormGroup>
                    <Label for="batch-editor-create-counterpart-account">{transferTranslation('counterpartAccount')}</Label>
                    <Input
                      id="batch-editor-create-counterpart-account"
                      type="select"
                      value={createAccountId}
                      onChange={event => setCreateAccountId(event.target.value)}
                      disabled={saving || busy}
                      data-cy="transactionBatchCreateCounterpartAccount"
                    >
                      <option value="">{transferTranslation('selectCounterpartAccount')}</option>
                      {counterpartAccounts.map(account => (
                        <option value={account.id} key={account.id}>
                          {formatFinancialAccountLabel(account)}
                        </option>
                      ))}
                    </Input>
                  </FormGroup>
                </Col>
                <Col md="6">
                  <FormGroup>
                    <Label for="batch-editor-create-counterpart-posting-date">{transferTranslation('counterpartPostingDate')}</Label>
                    <Input
                      id="batch-editor-create-counterpart-posting-date"
                      type="date"
                      value={createPostingDate}
                      onChange={event => setCreatePostingDate(event.target.value)}
                      disabled={saving || busy}
                      data-cy="transactionBatchCreateCounterpartPostingDate"
                    />
                  </FormGroup>
                </Col>
              </Row>
              <FormGroup>
                <Label for="batch-editor-create-counterpart-description">{transferTranslation('counterpartDescription')}</Label>
                <Input
                  id="batch-editor-create-counterpart-description"
                  value={createDescription}
                  onChange={event => setCreateDescription(event.target.value)}
                  disabled={saving || busy}
                  data-cy="transactionBatchCreateCounterpartDescription"
                />
              </FormGroup>
              <Row className="g-3">
                <Col md="6">
                  <FormGroup>
                    <Label for="batch-editor-create-counterpart-reference">{transferTranslation('counterpartReference')}</Label>
                    <Input
                      id="batch-editor-create-counterpart-reference"
                      value={createExternalReference}
                      onChange={event => setCreateExternalReference(event.target.value)}
                      disabled={saving || busy}
                      data-cy="transactionBatchCreateCounterpartReference"
                    />
                  </FormGroup>
                </Col>
                <Col md="6">
                  <FormGroup>
                    <Label for="batch-editor-create-counterpart-notes">{transferTranslation('counterpartNotes')}</Label>
                    <Input
                      id="batch-editor-create-counterpart-notes"
                      value={createNotes}
                      onChange={event => setCreateNotes(event.target.value)}
                      disabled={saving || busy}
                      data-cy="transactionBatchCreateCounterpartNotes"
                    />
                  </FormGroup>
                </Col>
              </Row>
              <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
                {persistedCreate ? (
                  <span className="small text-muted" data-cy="transactionBatchCreateCounterpartSaved">
                    {transferTranslation('createSaved', { account: persistedCreate.accountName ?? '' })}
                  </span>
                ) : (
                  <span className="small text-muted">{transferTranslation('createPending')}</span>
                )}
                <Button
                  type="button"
                  color="primary"
                  size="sm"
                  onClick={() => void configureCreate()}
                  disabled={saving || busy || !createAccountId || !createDescription.trim()}
                  data-cy="transactionBatchSaveCreateCounterpart"
                >
                  {transferTranslation('saveCreate')}
                </Button>
              </div>
            </div>
          ) : (
            <>
              <Label className="fw-semibold d-block" for="batch-editor-counterpart-search">
                {counterpart ? transferTranslation('changeCounterpart') : transferTranslation('counterpart')}
              </Label>
              {candidate?.internalTransfer?.stale ? (
                <div className="alert alert-warning py-2" role="alert" data-cy="transactionBatchTransferStale">
                  {transferTranslation('stale')}
                </div>
              ) : null}
              {counterpart ? (
                <div
                  className="d-flex flex-wrap align-items-center justify-content-between gap-2"
                  data-cy="transactionBatchSelectedCounterpart"
                  aria-live="polite"
                >
                  <div>
                    <div className="fw-semibold">{counterpart.accountName}</div>
                    <div className="small text-muted">{counterpartLabel(counterpart)}</div>
                  </div>
                  <Button type="button" color="secondary" outline size="sm" onClick={() => void clear()} disabled={saving || busy}>
                    {transferTranslation('remove')}
                  </Button>
                </div>
              ) : null}
              {!canSearch ? (
                <p className="small text-muted mb-0">{transferTranslation('incomplete')}</p>
              ) : (
                <>
                  <Row className="g-2">
                    <Col md="6">
                      <FormGroup className="mb-0">
                        <Label for="batch-editor-counterpart-account-filter">{transferTranslation('filterAccount')}</Label>
                        <Input
                          id="batch-editor-counterpart-account-filter"
                          type="select"
                          value={accountFilterId}
                          onChange={event => {
                            setAccountFilterId(event.target.value);
                            setPage(0);
                          }}
                          disabled={saving || busy}
                          data-cy="transactionBatchCounterpartAccountFilter"
                        >
                          <option value="">{transferTranslation('allEligibleAccounts')}</option>
                          {counterpartAccounts.map(account => (
                            <option value={account.id} key={account.id}>
                              {formatFinancialAccountLabel(account)}
                            </option>
                          ))}
                        </Input>
                      </FormGroup>
                    </Col>
                    <Col md="6">
                      <FormGroup className="mb-0">
                        <Label for="batch-editor-counterpart-search">{transferTranslation('search')}</Label>
                        <Input
                          id="batch-editor-counterpart-search"
                          value={search}
                          onChange={event => {
                            setSearch(event.target.value);
                            setPage(0);
                          }}
                          placeholder={transferTranslation('searchPlaceholder')}
                          disabled={saving || busy}
                          data-cy="transactionBatchCounterpartSearch"
                        />
                      </FormGroup>
                    </Col>
                  </Row>
                  <div className="small text-muted mt-3" aria-live="polite" data-cy="transactionBatchCounterpartCount">
                    {searching ? (
                      <>
                        <Spinner size="sm" className="me-2" />
                        {transferTranslation('loading')}
                      </>
                    ) : (
                      transferTranslation('resultCount', { count: totalElements })
                    )}
                  </div>
                  {!searching && !results.length && !error ? (
                    <p className="small text-muted mt-2 mb-0">{transferTranslation('noResults')}</p>
                  ) : null}
                  {results.length ? (
                    <div
                      className="d-flex flex-column gap-2 mt-3"
                      data-cy="transactionBatchCounterpartResults"
                      aria-label={transferTranslation('searchResults')}
                      role="listbox"
                    >
                      {results.map(result => {
                        const isSelected = counterpart?.transactionId === result.transactionId;
                        return (
                          <button
                            type="button"
                            key={result.transactionId}
                            className={`btn ${isSelected ? 'btn-primary' : 'btn-light'} counterpart-result text-start border`}
                            onClick={() => void selectCounterpart(result)}
                            disabled={saving || busy}
                            aria-selected={isSelected}
                            role="option"
                          >
                            <span className="d-flex justify-content-between gap-2">
                              <strong>{result.description}</strong>
                              {isSelected ? '✓' : null}
                            </span>
                            <span className="d-block small">{counterpartLabel(result)}</span>
                          </button>
                        );
                      })}
                    </div>
                  ) : null}
                  {totalPages > 1 ? (
                    <div className="d-flex justify-content-end align-items-center gap-2 mt-3">
                      <Button
                        type="button"
                        size="sm"
                        color="secondary"
                        outline
                        disabled={searching || page === 0}
                        onClick={() => setPage(page - 1)}
                      >
                        {transferTranslation('previous')}
                      </Button>
                      <span className="small text-muted">
                        {page + 1} / {totalPages}
                      </span>
                      <Button
                        type="button"
                        size="sm"
                        color="secondary"
                        outline
                        disabled={searching || page + 1 >= totalPages}
                        onClick={() => setPage(page + 1)}
                      >
                        {transferTranslation('next')}
                      </Button>
                    </div>
                  ) : null}
                </>
              )}
            </>
          )}
          {error ? (
            <div className="small text-danger mt-2" role="alert">
              {error}
            </div>
          ) : null}
        </div>
      ) : null}
    </section>
  );
};

/**
 * The Batch editor deliberately shares the entry composition of the manual
 * transaction form while keeping Batch persistence at its own product boundary.
 */
const TransactionBatchEditorModal = ({
  open,
  sessionId,
  candidate,
  preview,
  categories,
  tags,
  currency,
  sourceAccountId,
  saving,
  error,
  fieldError,
  createMode,
  onClose,
  onSave,
  onMaterialChange,
  onApplySuggestion,
  onSearchTransferCounterparts,
  onSelectTransferCounterpart,
  onConfigureCreateTransferCounterpart,
  onClearTransfer,
}: {
  open: boolean;
  sessionId: string;
  candidate?: BatchCandidate;
  preview?: BatchRulePreview;
  categories: ICategorySelectable[];
  tags: ITagSelectable[];
  currency?: string | null;
  sourceAccountId?: number;
  saving: boolean;
  error?: string;
  fieldError?: 'category' | 'amount' | 'transactionDate' | 'postingDate' | null;
  createMode: boolean;
  onClose: () => void;
  onSave: (values: BatchEditorValues) => void;
  onMaterialChange?: (values: BatchEditorValues) => Promise<BatchCandidate | undefined>;
  onApplySuggestion?: (domain: BatchSuggestionDomain, values: BatchEditorValues) => Promise<BatchCandidate | undefined>;
  onSearchTransferCounterparts?: (options: { search?: string; accountId?: number; page?: number }) => Promise<BatchTransferCounterpartPage>;
  onSelectTransferCounterpart?: (counterpart: BatchTransferCounterpart) => Promise<BatchCandidate | undefined>;
  onConfigureCreateTransferCounterpart?: (
    configuration: Required<Pick<BatchCreateCounterpartConfiguration, 'accountId' | 'description'>> & BatchCreateCounterpartConfiguration,
  ) => Promise<BatchCandidate | undefined>;
  onClearTransfer?: () => Promise<BatchCandidate | undefined>;
}) => {
  const [values, setValues] = useState<BatchEditorValues>(() => valuesFor(candidate));
  const [suggestionApplying, setSuggestionApplying] = useState(false);
  const attemptedMaterialFingerprints = useRef(new Set<string>());

  useEffect(() => {
    if (open) setValues(valuesFor(candidate));
    if (open) attemptedMaterialFingerprints.current.clear();
  }, [open, sessionId]);

  const materialFingerprint = materialDraftFingerprint(values);
  useEffect(() => {
    if (!open || !createMode || !onMaterialChange || !hasMaterialDraftValue(values)) return;
    if (attemptedMaterialFingerprints.current.has(materialFingerprint)) return;
    const timer = window.setTimeout(() => {
      attemptedMaterialFingerprints.current.add(materialFingerprint);
      void onMaterialChange(values);
    }, 350);
    return () => window.clearTimeout(timer);
  }, [createMode, materialFingerprint, onMaterialChange, open, values]);

  const update: UpdateEditorValue = (key, value) =>
    setValues(current => {
      const next = { ...current, [key]: value };
      if (key === 'transactionDate' && current.samePostingDate) next.postingDate = value as string;
      return next;
    });

  const busy = saving || suggestionApplying;
  const applySuggestion = async (domain: BatchSuggestionDomain) => {
    if (!onApplySuggestion || busy) return;
    setSuggestionApplying(true);
    try {
      const next = await onApplySuggestion(domain, values);
      if (next) setValues(valuesFor(next));
    } finally {
      setSuggestionApplying(false);
    }
  };

  return (
    <Modal isOpen={open} toggle={busy ? undefined : onClose} size="lg" contentClassName="transaction-batch-editor-modal">
      <Form
        onSubmit={event => {
          event.preventDefault();
          if (!busy) onSave(values);
        }}
      >
        <ModalHeader toggle={busy ? undefined : onClose}>{createMode ? 'Añadir movimiento' : 'Editar movimiento'}</ModalHeader>
        <ModalBody>
          {error ? (
            <div role="alert" className="alert alert-danger">
              {error}
            </div>
          ) : null}
          <TransactionFieldsSection
            candidate={candidate}
            preview={preview}
            values={values}
            update={update}
            setValues={setValues}
            currency={currency}
            saving={busy}
            fieldError={fieldError ?? null}
            onApplySuggestion={onApplySuggestion ? applySuggestion : undefined}
          />
          <ClassificationSection
            candidate={candidate}
            preview={preview}
            categories={categories}
            tags={tags}
            values={values}
            update={update}
            saving={busy}
            fieldError={fieldError ?? null}
            onApplySuggestion={onApplySuggestion ? applySuggestion : undefined}
          />
          <InternalTransferSection
            candidate={candidate}
            values={values}
            sourceAccountId={sourceAccountId}
            currency={currency}
            saving={busy}
            onSearch={onSearchTransferCounterparts}
            onSelect={onSelectTransferCounterpart}
            onConfigureCreate={onConfigureCreateTransferCounterpart}
            onClear={onClearTransfer}
          />
          <OptionalDetailsSection values={values} update={update} saving={busy} />
        </ModalBody>
        <ModalFooter>
          <Button type="button" color="secondary" outline onClick={onClose} disabled={busy}>
            Cancelar
          </Button>
          <Button color="primary" disabled={busy} data-cy="transactionBatchEditorSave">
            {busy ? (
              <Spinner size="sm" />
            ) : candidate?.internalTransfer?.mode === 'CREATE_COUNTERPART' ? (
              transferTranslation('savePendingCreate')
            ) : candidate?.internalTransfer?.enabled ? (
              transferTranslation('savePendingLink')
            ) : (
              'Guardar movimiento'
            )}
          </Button>
        </ModalFooter>
      </Form>
    </Modal>
  );
};

export default TransactionBatchEditorModal;
