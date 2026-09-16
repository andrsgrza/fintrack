import React, { useMemo } from 'react';
import { Modal, ModalBody, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';

import DescriptionNormalizationRuleConfiguredForm from 'app/entities/description-normalization-rule/components/description-normalization-rule-configured-form';
import TransactionRuleConfiguredForm from 'app/entities/transaction-rule/components/transaction-rule-configured-form';
import { IDescriptionNormalizationRuleConfigured } from 'app/shared/model/description-normalization-rule-configured.model';
import { ITransactionRuleConfigured } from 'app/shared/model/transaction-rule-configured.model';
import { ContextualRuleSaveAction, IContextualRuleSaveAction } from 'app/shared/model/contextual-rule-save-action.model';
import { DescriptionNormalizationRuleOperator } from 'app/shared/model/enumerations/description-normalization-rule-operator.model';
import { RuleConditionLogic } from 'app/shared/model/enumerations/rule-condition-logic.model';
import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';

export type TransactionIngestionRuleCreationKind = 'NORMALIZATION' | 'TRANSACTION';

export interface ITransactionIngestionRuleCreationRowContext {
  ingestionRecordId?: number;
  candidateId?: number;
  originalDescription?: string | null;
  currentDescription?: string | null;
  flow?: string | null;
  accountName?: string | null;
  categoryId?: number | null;
  categoryName?: string | null;
  tagIds?: number[];
  tagNames?: string[];
}

export interface ITransactionIngestionRuleCreationContext {
  kind: TransactionIngestionRuleCreationKind;
  row?: ITransactionIngestionRuleCreationRowContext;
}

interface TransactionIngestionRuleCreationModalProps {
  context: ITransactionIngestionRuleCreationContext | null;
  onClose: () => void;
  onSaved: (
    kind: TransactionIngestionRuleCreationKind,
    action: ContextualRuleSaveAction,
    row?: ITransactionIngestionRuleCreationRowContext,
  ) => void | Promise<void>;
}

const rowSaveActions: IContextualRuleSaveAction[] = [
  { action: 'SAVE', labelKey: 'fintrackApp.transactionIngestion.workflow.ruleCreation.save', label: 'Save', dataCy: 'contextualRuleSave' },
  {
    action: 'REEVALUATE_ROW',
    labelKey: 'fintrackApp.transactionIngestion.workflow.ruleCreation.saveAndReevaluateRow',
    label: 'Save and reevaluate this row',
    dataCy: 'contextualRuleSaveAndReevaluateRow',
  },
  {
    action: 'REEVALUATE_ALL',
    labelKey: 'fintrackApp.transactionIngestion.workflow.ruleCreation.saveAndReevaluateAll',
    label: 'Save and reevaluate all',
    dataCy: 'contextualRuleSaveAndReevaluateAll',
  },
];

const globalSaveActions: IContextualRuleSaveAction[] = [
  { action: 'SAVE', labelKey: 'fintrackApp.transactionIngestion.workflow.ruleCreation.save', label: 'Save', dataCy: 'contextualRuleSave' },
  {
    action: 'REEVALUATE_ALL',
    labelKey: 'fintrackApp.transactionIngestion.workflow.ruleCreation.saveAndReevaluateAll',
    label: 'Save and reevaluate all',
    dataCy: 'contextualRuleSaveAndReevaluateAll',
  },
];

const normalizationInitialValue = (row?: ITransactionIngestionRuleCreationRowContext): IDescriptionNormalizationRuleConfigured => ({
  name: row?.currentDescription ? `Normalize ${row.currentDescription}` : '',
  active: false,
  conditionOperator: RuleConditionLogic.ALL,
  resultingDescription: row?.currentDescription ?? '',
  conditions: row?.originalDescription
    ? [{ operator: DescriptionNormalizationRuleOperator.CONTAINS, value: row.originalDescription, caseSensitive: false }]
    : [],
});

const transactionInitialValue = (row?: ITransactionIngestionRuleCreationRowContext): ITransactionRuleConfigured => ({
  name: row?.currentDescription ? `Rule for ${row.currentDescription}` : '',
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

/** Contextual rule composition. It deliberately does not navigate to generated CRUD routes. */
export const TransactionIngestionRuleCreationModal = ({ context, onClose, onSaved }: TransactionIngestionRuleCreationModalProps) => {
  const isOpen = context !== null;
  const row = context?.row;
  const saveActions = row ? rowSaveActions : globalSaveActions;
  const normalizationInitial = useMemo(() => normalizationInitialValue(row), [row]);
  const transactionInitial = useMemo(() => transactionInitialValue(row), [row]);

  return (
    <Modal
      isOpen={isOpen}
      toggle={onClose}
      size="lg"
      scrollable
      data-cy="transactionIngestionRuleCreationModal"
      data-testid="transactionIngestionRuleCreationModal"
    >
      <ModalHeader toggle={onClose}>
        {context?.kind === 'NORMALIZATION' ? (
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.normalizationTitle">
            New normalization rule
          </Translate>
        ) : (
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.transactionTitle">New transaction rule</Translate>
        )}
      </ModalHeader>
      <ModalBody>
        {row?.accountName && context?.kind === 'TRANSACTION' ? (
          <small className="text-muted d-block mb-3" data-testid="contextualTransactionRuleAccount">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.accountContext">Candidate account</Translate>:{' '}
            {row.accountName}
          </small>
        ) : null}
        {context?.kind === 'NORMALIZATION' ? (
          <DescriptionNormalizationRuleConfiguredForm
            key={`normalization-${row?.ingestionRecordId ?? 'global'}`}
            initialValue={normalizationInitial}
            saveActions={saveActions}
            onSaved={(_, action) => onSaved('NORMALIZATION', action, row)}
            onCancel={onClose}
          />
        ) : null}
        {context?.kind === 'TRANSACTION' ? (
          <TransactionRuleConfiguredForm
            key={`transaction-${row?.candidateId ?? 'global'}`}
            initialValue={transactionInitial}
            saveActions={saveActions}
            onSaved={(_, action) => onSaved('TRANSACTION', action, row)}
            onCancel={onClose}
          />
        ) : null}
      </ModalBody>
    </Modal>
  );
};

export default TransactionIngestionRuleCreationModal;
