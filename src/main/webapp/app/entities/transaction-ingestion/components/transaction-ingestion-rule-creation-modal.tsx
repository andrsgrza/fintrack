import React, { useMemo } from 'react';
import { Alert, Button, Modal, ModalBody, ModalHeader } from 'reactstrap';
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
  recordIndex?: number;
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
    label: 'Save and reevaluate all applicable rows',
    dataCy: 'contextualRuleSaveAndReevaluateAll',
  },
];

const globalSaveActions: IContextualRuleSaveAction[] = [
  { action: 'SAVE', labelKey: 'fintrackApp.transactionIngestion.workflow.ruleCreation.save', label: 'Save', dataCy: 'contextualRuleSave' },
  {
    action: 'REEVALUATE_ALL',
    labelKey: 'fintrackApp.transactionIngestion.workflow.ruleCreation.saveAndReevaluateAll',
    label: 'Save and reevaluate all applicable rows',
    dataCy: 'contextualRuleSaveAndReevaluateAll',
  },
];

const normalizationInitialValue = (row?: ITransactionIngestionRuleCreationRowContext): IDescriptionNormalizationRuleConfigured => ({
  name: row?.currentDescription ? `Normalize ${row.currentDescription}` : '',
  // A row prefill always provides the required condition, so it can participate in an immediate reevaluation.
  // Scratch rules stay inactive until the user has configured at least one condition.
  active: Boolean(row?.originalDescription),
  conditionOperator: RuleConditionLogic.ALL,
  resultingDescription: row?.currentDescription ?? '',
  conditions: row?.originalDescription
    ? [{ operator: DescriptionNormalizationRuleOperator.CONTAINS, value: row.originalDescription, caseSensitive: false }]
    : [],
});

const ContextualRuleExplanation = ({
  context,
  normalizationContextUnavailable,
  transactionContextUnavailable,
}: {
  context: ITransactionIngestionRuleCreationContext;
  normalizationContextUnavailable: boolean;
  transactionContextUnavailable: boolean;
}) => {
  const row = context.row;
  const fromRow = Boolean(row);

  if (normalizationContextUnavailable || transactionContextUnavailable) {
    return (
      <Alert color="warning" fade={false} data-testid="contextualRuleSourceUnavailable">
        <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.sourceUnavailable">
          The selected row no longer has the source information needed to prepare this rule. Refresh the review and try again.
        </Translate>
      </Alert>
    );
  }

  if (context.kind === 'NORMALIZATION') {
    return (
      <Alert color="info" fade={false} data-testid="contextualNormalizationRuleExplanation">
        <p className="mb-2">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.normalizationExplanation">
            Description rules match the original file description. The first active match by priority supplies the normalized description.
          </Translate>
        </p>
        {fromRow ? (
          <>
            {row?.recordIndex !== undefined ? (
              <div className="small mb-1" data-testid="contextualRuleSelectedRow">
                <Translate
                  contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.selectedRow"
                  interpolate={{ recordIndex: row.recordIndex }}
                >
                  Selected row
                </Translate>
              </div>
            ) : null}
            <div className="small mb-1">
              <strong>
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.descriptionReview.originalDescription">
                  Original description
                </Translate>
                :
              </strong>{' '}
              {row?.originalDescription}
            </div>
            <div className="small mb-2">
              <strong>
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.descriptionReview.normalizedDescription">
                  Normalized description
                </Translate>
                :
              </strong>{' '}
              {row?.currentDescription}
            </div>
          </>
        ) : null}
        <small>
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.saveOnlyDoesNotReevaluate">
            Saving creates the rule only. Reevaluation is a separate action and does not change posted transactions.
          </Translate>
        </small>
      </Alert>
    );
  }

  return (
    <Alert color="info" fade={false} data-testid="contextualTransactionRuleExplanation">
      <p className="mb-2">
        <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.transactionExplanation">
          Classification rules generate category and tag suggestions. An earlier eligible category can take precedence, while matching tags
          can accumulate.
        </Translate>
      </p>
      {fromRow ? (
        <>
          {row?.recordIndex !== undefined ? (
            <div className="small mb-1" data-testid="contextualRuleSelectedRow">
              <Translate
                contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.selectedRow"
                interpolate={{ recordIndex: row.recordIndex }}
              >
                Selected row
              </Translate>
            </div>
          ) : null}
          <div className="small mb-1">
            <strong>
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.description">Description</Translate>:
            </strong>{' '}
            {row?.currentDescription}
          </div>
          {row?.flow ? (
            <div className="small mb-2">
              <strong>
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.flow">Flow</Translate>:
              </strong>{' '}
              {row.flow}
            </div>
          ) : null}
        </>
      ) : null}
      <small>
        <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.classificationReevaluationExplanation">
          Reevaluation refreshes suggestions. Categories and tags are applied only according to the current review action and automation
          settings.
        </Translate>
      </small>
    </Alert>
  );
};

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
  const normalizationContextUnavailable = Boolean(context?.kind === 'NORMALIZATION' && row && !row.originalDescription?.trim());
  const transactionContextUnavailable = Boolean(context?.kind === 'TRANSACTION' && row && !row.currentDescription?.trim());
  const contextUnavailable = normalizationContextUnavailable || transactionContextUnavailable;

  return (
    <Modal
      isOpen={isOpen}
      toggle={onClose}
      size="lg"
      scrollable
      labelledBy="transaction-ingestion-rule-creation-title"
      data-cy="transactionIngestionRuleCreationModal"
      data-testid="transactionIngestionRuleCreationModal"
    >
      <ModalHeader id="transaction-ingestion-rule-creation-title" toggle={onClose}>
        {context?.kind === 'NORMALIZATION' ? (
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.normalizationTitle">New Description Rule</Translate>
        ) : (
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.transactionTitle">
            New Classification Rule
          </Translate>
        )}
      </ModalHeader>
      <ModalBody>
        {context ? (
          <ContextualRuleExplanation
            context={context}
            normalizationContextUnavailable={normalizationContextUnavailable}
            transactionContextUnavailable={transactionContextUnavailable}
          />
        ) : null}
        {row?.accountName && context?.kind === 'TRANSACTION' ? (
          <small className="text-muted d-block mb-3" data-testid="contextualTransactionRuleAccount">
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.accountContext">Candidate account</Translate>:{' '}
            {row.accountName}
            <br />
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.ruleCreation.accountContextOnly">
              This account is context only and is not added as a matching condition.
            </Translate>
          </small>
        ) : null}
        {contextUnavailable ? (
          <Button type="button" color="secondary" onClick={onClose}>
            <Translate contentKey="entity.action.close">Close</Translate>
          </Button>
        ) : null}
        {context?.kind === 'NORMALIZATION' && !contextUnavailable ? (
          <DescriptionNormalizationRuleConfiguredForm
            key={`normalization-${row?.ingestionRecordId ?? 'global'}`}
            initialValue={normalizationInitial}
            saveActions={saveActions}
            onSaved={(_, action) => onSaved('NORMALIZATION', action, row)}
            onCancel={onClose}
          />
        ) : null}
        {context?.kind === 'TRANSACTION' && !contextUnavailable ? (
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
