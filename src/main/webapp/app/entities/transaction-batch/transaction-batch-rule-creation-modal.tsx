import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Modal, ModalBody, ModalHeader } from 'reactstrap';

import DescriptionNormalizationRuleConfiguredForm from 'app/entities/description-normalization-rule/components/description-normalization-rule-configured-form';
import TransactionRuleConfiguredForm from 'app/entities/transaction-rule/components/transaction-rule-configured-form';
import { IDescriptionNormalizationRuleConfigured } from 'app/shared/model/description-normalization-rule-configured.model';
import { ITransactionRuleConfigured } from 'app/shared/model/transaction-rule-configured.model';
import { ContextualRuleSaveAction, IContextualRuleSaveAction } from 'app/shared/model/contextual-rule-save-action.model';
import { DescriptionNormalizationRuleOperator } from 'app/shared/model/enumerations/description-normalization-rule-operator.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';
import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';

/** Context only: the configured rule editors remain the sole rule authoring forms. */
export type TransactionBatchRuleCreationKind = 'NORMALIZATION' | 'TRANSACTION';

export interface ITransactionBatchRuleCreationRowContext {
  candidateId: number;
  rowPosition: number;
  source: 'MANUAL' | 'FILE_IMPORT';
  sourceDescription?: string | null;
  currentDescription?: string | null;
  flow?: string | null;
  accountName?: string | null;
  categoryId?: number | null;
  categoryName?: string | null;
  tagIds?: number[];
  tagNames?: string[];
}

export interface ITransactionBatchRuleCreationContext {
  kind: TransactionBatchRuleCreationKind;
  row?: ITransactionBatchRuleCreationRowContext;
}

interface TransactionBatchRuleCreationModalProps {
  context: ITransactionBatchRuleCreationContext | null;
  onClose: () => void;
  /** Called exactly once after the configured editor has durably created the rule. */
  onCreated: (rule: { id?: number; name?: string | null }, action: ContextualRuleSaveAction) => void;
  /** Uses the existing batch orchestration; the modal never evaluates or applies rules itself. */
  onReevaluate: (context: ITransactionBatchRuleCreationContext, action: Exclude<ContextualRuleSaveAction, 'SAVE'>) => Promise<void>;
}

const rowSaveActions: IContextualRuleSaveAction[] = [
  {
    action: 'SAVE',
    labelKey: 'fintrackApp.transactionIngestion.workflow.ruleCreation.save',
    label: 'Guardar',
    dataCy: 'batchContextualRuleSave',
  },
  {
    action: 'REEVALUATE_ROW',
    labelKey: 'fintrackApp.transactionIngestion.workflow.ruleCreation.saveAndReevaluateRow',
    label: 'Guardar y reevaluar esta fila',
    dataCy: 'batchContextualRuleSaveAndReevaluateRow',
  },
  {
    action: 'REEVALUATE_ALL',
    labelKey: 'fintrackApp.transactionIngestion.workflow.ruleCreation.saveAndReevaluateAll',
    label: 'Guardar y reevaluar lote',
    dataCy: 'batchContextualRuleSaveAndReevaluateBatch',
  },
];

const batchSaveActions: IContextualRuleSaveAction[] = [
  {
    action: 'SAVE',
    labelKey: 'fintrackApp.transactionIngestion.workflow.ruleCreation.save',
    label: 'Guardar',
    dataCy: 'batchContextualRuleSave',
  },
  {
    action: 'REEVALUATE_ALL',
    labelKey: 'fintrackApp.transactionIngestion.workflow.ruleCreation.saveAndReevaluateAll',
    label: 'Guardar y reevaluar lote',
    dataCy: 'batchContextualRuleSaveAndReevaluateBatch',
  },
];

const normalizationInitialValue = (row?: ITransactionBatchRuleCreationRowContext): IDescriptionNormalizationRuleConfigured => ({
  name: row?.currentDescription ? `Normalizar ${row.currentDescription}` : '',
  active: true,
  conditionOperator: RuleConditionLogic.ALL,
  resultingDescription: row?.currentDescription ?? '',
  // The source baseline is intentionally only available for a temporary CSV candidate.
  conditions: row?.sourceDescription
    ? [{ operator: DescriptionNormalizationRuleOperator.CONTAINS, value: row.sourceDescription, caseSensitive: false }]
    : [],
});

const transactionInitialValue = (row?: ITransactionBatchRuleCreationRowContext): ITransactionRuleConfigured => ({
  name: row?.currentDescription ? `Regla para ${row.currentDescription}` : '',
  active: true,
  conditionLogic: RuleConditionLogic.ALL,
  resultingCategory: row?.categoryId ? { id: row.categoryId, name: row.categoryName ?? undefined } : null,
  resultingTags: (row?.tagIds ?? []).map((id, index) => ({ id, name: row?.tagNames?.[index] })),
  conditions: row?.currentDescription
    ? [
        {
          field: TransactionRuleField.DESCRIPTION,
          operator: RuleOperator.CONTAINS,
          value: row.currentDescription,
          caseSensitive: false,
          position: 0,
        },
        ...(row.flow
          ? [
              {
                field: TransactionRuleField.FLOW,
                operator: RuleOperator.EQUALS,
                value: row.flow,
                caseSensitive: false,
                position: 1,
              },
            ]
          : []),
      ]
    : [],
});

/**
 * Contextual presentation only. Saving remains in the established DNR/TransactionRule configured endpoints, and
 * reevaluation remains in the TransactionBatch command boundary.
 */
const TransactionBatchRuleCreationModal = ({ context, onClose, onCreated, onReevaluate }: TransactionBatchRuleCreationModalProps) => {
  const row = context?.row;
  const [createdRule, setCreatedRule] = useState<{ id?: number; name?: string | null } | null>(null);
  const [followUpAction, setFollowUpAction] = useState<Exclude<ContextualRuleSaveAction, 'SAVE'> | null>(null);
  const [retryAction, setRetryAction] = useState<Exclude<ContextualRuleSaveAction, 'SAVE'> | null>(null);
  const [followUpError, setFollowUpError] = useState<string | null>(null);

  useEffect(() => {
    setCreatedRule(null);
    setFollowUpAction(null);
    setRetryAction(null);
    setFollowUpError(null);
  }, [context]);

  const normalizationInitial = useMemo(() => normalizationInitialValue(row), [row]);
  const transactionInitial = useMemo(() => transactionInitialValue(row), [row]);
  const normalizationUnavailable = Boolean(
    context?.kind === 'NORMALIZATION' && row && (row.source !== 'FILE_IMPORT' || !row.sourceDescription?.trim()),
  );
  const transactionUnavailable = Boolean(context?.kind === 'TRANSACTION' && row && !row.currentDescription?.trim());
  const unavailable = normalizationUnavailable || transactionUnavailable;
  const saveActions = row ? rowSaveActions : batchSaveActions;

  const runFollowUp = async (action: Exclude<ContextualRuleSaveAction, 'SAVE'>) => {
    if (!context || followUpAction) return;
    setFollowUpAction(action);
    setFollowUpError(null);
    try {
      await onReevaluate(context, action);
      onClose();
    } catch {
      // The rule was already persisted by the configured editor. Keep this modal in a retry-only state so a retry
      // cannot issue another create request.
      setFollowUpError('La regla se guardó, pero no se pudo reevaluar el lote. Puedes reintentar sólo la reevaluación.');
      setRetryAction(action);
    } finally {
      setFollowUpAction(null);
    }
  };

  const ruleSaved = async (rule: { id?: number; name?: string | null }, action: ContextualRuleSaveAction) => {
    setCreatedRule(rule);
    onCreated(rule, action);
    if (action === 'SAVE') {
      onClose();
      return;
    }
    await runFollowUp(action);
  };

  const title = context?.kind === 'NORMALIZATION' ? 'Nueva regla de descripción' : 'Nueva regla de clasificación';
  const savedRuleLabel = createdRule?.name?.trim() || (createdRule?.id ? `Regla #${createdRule.id}` : 'La regla');

  return (
    <Modal
      isOpen={context !== null}
      toggle={onClose}
      size="lg"
      scrollable
      labelledBy="transaction-batch-rule-creation-title"
      data-cy="transactionBatchRuleCreationModal"
      data-testid="transactionBatchRuleCreationModal"
    >
      <ModalHeader id="transaction-batch-rule-creation-title" toggle={onClose}>
        {title}
      </ModalHeader>
      <ModalBody>
        {followUpError ? (
          <Alert color="warning" fade={false} data-cy="batchContextualRulePartialSuccess" data-testid="batchContextualRulePartialSuccess">
            <p className="mb-2">
              <strong>{savedRuleLabel}</strong> se creó correctamente.
            </p>
            <p className="mb-3">{followUpError}</p>
            <Button
              color="warning"
              onClick={() => retryAction && runFollowUp(retryAction)}
              disabled={followUpAction !== null}
              data-cy="batchContextualRuleRetryReevaluation"
              data-testid="batchContextualRuleRetryReevaluation"
            >
              Reintentar reevaluación
            </Button>{' '}
            <Button color="secondary" outline onClick={onClose} disabled={followUpAction !== null}>
              Cerrar
            </Button>
          </Alert>
        ) : null}
        {!createdRule && context ? (
          <>
            {unavailable ? (
              <Alert color="warning" fade={false} data-cy="batchContextualRuleSourceUnavailable">
                {normalizationUnavailable
                  ? 'Esta fila no tiene una descripción fuente CSV válida. No se puede crear una regla de descripción sin inventar un baseline.'
                  : 'Esta fila no tiene una descripción actual válida para preparar una regla de clasificación.'}
              </Alert>
            ) : (
              <Alert color="info" fade={false} data-cy="batchContextualRuleExplanation">
                {context.kind === 'NORMALIZATION' ? (
                  <>
                    Las reglas de descripción coinciden con la descripción fuente temporal del CSV. Guardar sólo crea la regla; reevaluar
                    sigue siendo una operación separada.
                    {row ? (
                      <span className="d-block small mt-2">
                        Fila {row.rowPosition} · fuente: <strong>{row.sourceDescription}</strong> · resultado actual:{' '}
                        <strong>{row.currentDescription || 'Sin descripción'}</strong>
                      </span>
                    ) : null}
                  </>
                ) : (
                  <>
                    Las reglas de clasificación usan los valores actuales del candidato y generan sugerencias. La cuenta se muestra sólo
                    como contexto; no se convierte en una condición implícita.
                    {row ? (
                      <span className="d-block small mt-2">
                        Fila {row.rowPosition} · {row.source === 'MANUAL' ? 'Manual' : 'CSV'} · cuenta: {row.accountName} · descripción:{' '}
                        <strong>{row.currentDescription || 'Sin descripción'}</strong>
                      </span>
                    ) : null}
                  </>
                )}
              </Alert>
            )}
            {unavailable ? (
              <Button type="button" color="secondary" onClick={onClose}>
                Cerrar
              </Button>
            ) : context.kind === 'NORMALIZATION' ? (
              <DescriptionNormalizationRuleConfiguredForm
                key={`batch-normalization-${row?.candidateId ?? 'scratch'}`}
                initialValue={normalizationInitial}
                saveActions={saveActions}
                onSaved={ruleSaved}
                onCancel={onClose}
              />
            ) : (
              <TransactionRuleConfiguredForm
                key={`batch-transaction-${row?.candidateId ?? 'scratch'}`}
                initialValue={transactionInitial}
                saveActions={saveActions}
                onSaved={ruleSaved}
                onCancel={onClose}
              />
            )}
          </>
        ) : null}
      </ModalBody>
    </Modal>
  );
};

export default TransactionBatchRuleCreationModal;
