import React, { useEffect, useRef, useState } from 'react';
import {
  Button,
  ButtonGroup,
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
import { ITagSelectable } from 'app/shared/model/tag-selectable.model';
import { ProductTagSelector, ProductTagSelectorTag } from 'app/shared/ui/product-tag-selector';
import { AppliedRuleAttribution, BatchCandidate, BatchRulePreview } from './transaction-batch-api';
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

const AppliedBy = ({ attribution, href }: { attribution?: AppliedRuleAttribution | null; href: string }) => {
  if (!attribution?.name) return null;
  return (
    <div className="small text-muted mt-1" data-testid="batchAppliedRuleAttribution">
      Aplicada por{' '}
      {attribution.id ? (
        <a href={`${href}/${attribution.id}/view`} className="transaction-batch-rule-link">
          {attribution.name}
        </a>
      ) : (
        attribution.name
      )}
    </div>
  );
};

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
        <AppliedBy attribution={candidate?.descriptionAppliedByRule} href="/description-normalization-rule" />
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
            <ButtonGroup className="d-flex" aria-labelledby="batch-editor-flow-label">
              <Button
                type="button"
                color={values.flow === 'IN' ? 'success' : 'light'}
                outline={values.flow !== 'IN'}
                onClick={() => update('flow', 'IN')}
                disabled={saving}
                data-cy="batchEditorFlow-IN"
              >
                INGRESO
              </Button>
              <Button
                type="button"
                color={values.flow === 'OUT' ? 'secondary' : 'light'}
                outline={values.flow !== 'OUT'}
                onClick={() => update('flow', 'OUT')}
                disabled={saving}
                data-cy="batchEditorFlow-OUT"
              >
                GASTO
              </Button>
            </ButtonGroup>
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
            <AppliedBy attribution={candidate?.categoryAppliedByRule} href="/transaction-rule" />
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
            />
            {values.tags.map(tag => {
              const appliedByRule = candidate?.tags.find(current => current.id === tag.id)?.appliedByRule;
              return <AppliedBy key={tag.id ?? tag.name} attribution={appliedByRule} href="/transaction-rule" />;
            })}
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
  saving,
  error,
  fieldError,
  createMode,
  onClose,
  onSave,
  onMaterialChange,
  onApplySuggestion,
}: {
  open: boolean;
  sessionId: string;
  candidate?: BatchCandidate;
  preview?: BatchRulePreview;
  categories: ICategorySelectable[];
  tags: ITagSelectable[];
  currency?: string | null;
  saving: boolean;
  error?: string;
  fieldError?: 'category' | 'amount' | 'transactionDate' | 'postingDate' | null;
  createMode: boolean;
  onClose: () => void;
  onSave: (values: BatchEditorValues) => void;
  onMaterialChange?: (values: BatchEditorValues) => Promise<BatchCandidate | undefined>;
  onApplySuggestion?: (domain: BatchSuggestionDomain, values: BatchEditorValues) => Promise<BatchCandidate | undefined>;
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
          <OptionalDetailsSection values={values} update={update} saving={busy} />
        </ModalBody>
        <ModalFooter>
          <Button type="button" color="secondary" outline onClick={onClose} disabled={busy}>
            Cancelar
          </Button>
          <Button color="primary" disabled={busy} data-cy="transactionBatchEditorSave">
            {busy ? <Spinner size="sm" /> : 'Guardar movimiento'}
          </Button>
        </ModalFooter>
      </Form>
    </Modal>
  );
};

export default TransactionBatchEditorModal;
