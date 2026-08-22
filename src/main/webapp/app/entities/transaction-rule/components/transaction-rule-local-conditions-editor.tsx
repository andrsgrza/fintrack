import React, { useMemo, useState } from 'react';
import { Alert, Button, FormText, Table } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import TransactionRuleConditionFormSection from 'app/entities/transaction-rule-condition/components/transaction-rule-condition-form-section';
import { buildTransactionRuleConditionSummary } from 'app/entities/transaction-rule-condition/transaction-rule-condition-display';
import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';
import { RuleOperator } from 'app/shared/model/enumerations/rule-operator.model';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { TransactionRuleField } from 'app/shared/model/enumerations/transaction-rule-field.model';

export interface LocalTransactionRuleCondition extends ITransactionRuleCondition {
  clientId?: string;
  autoRequiredFlow?: boolean;
}

interface TransactionRuleLocalConditionsEditorProps {
  conditions: LocalTransactionRuleCondition[];
  onChange: (conditions: LocalTransactionRuleCondition[]) => void;
  lockedFlow?: keyof typeof TransactionFlow | null;
}

const newClientId = () => `condition-${Date.now()}-${Math.random().toString(36).slice(2)}`;

const sortConditions = (conditions: LocalTransactionRuleCondition[]) =>
  [...conditions].sort((a, b) => {
    const positionCompare = (a.position ?? 0) - (b.position ?? 0);
    if (positionCompare !== 0) {
      return positionCompare;
    }
    return (a.id ?? 0) - (b.id ?? 0);
  });

const isLockedFlowCondition = (condition: LocalTransactionRuleCondition, lockedFlow?: keyof typeof TransactionFlow | null) =>
  Boolean(
    lockedFlow &&
      condition.field === TransactionRuleField.FLOW &&
      condition.operator === RuleOperator.EQUALS &&
      condition.value === lockedFlow,
  );

export const TransactionRuleLocalConditionsEditor = ({ conditions, onChange, lockedFlow }: TransactionRuleLocalConditionsEditorProps) => {
  const [editingCondition, setEditingCondition] = useState<LocalTransactionRuleCondition | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const sortedConditions = useMemo(() => sortConditions(conditions), [conditions]);

  const closeForm = () => {
    setShowAddForm(false);
    setEditingCondition(null);
  };

  const normalizePositions = (nextConditions: LocalTransactionRuleCondition[]) =>
    nextConditions.map((condition, index) => ({ ...condition, position: index }));

  const handleCreate = (entity: ITransactionRuleCondition) => {
    onChange(normalizePositions([...conditions, { ...entity, clientId: newClientId() }]));
    closeForm();
  };

  const handleUpdate = (entity: ITransactionRuleCondition) => {
    if (!editingCondition) {
      return;
    }
    onChange(
      normalizePositions(
        conditions.map(condition =>
          condition.clientId === editingCondition.clientId
            ? {
                ...condition,
                ...entity,
                id: editingCondition.id,
                clientId: editingCondition.clientId,
                autoRequiredFlow: editingCondition.autoRequiredFlow,
              }
            : condition,
        ),
      ),
    );
    closeForm();
  };

  const handleDelete = (conditionToDelete: LocalTransactionRuleCondition) => {
    onChange(normalizePositions(conditions.filter(condition => condition.clientId !== conditionToDelete.clientId)));
  };

  return (
    <section className="mt-4" data-cy="transactionRuleConfiguredConditionsEditor">
      <div className="d-flex align-items-center justify-content-between mb-2">
        <h3 className="mb-0">
          <Translate contentKey="fintrackApp.transactionRule.conditions">Conditions</Translate>
        </h3>
        {!showAddForm && !editingCondition ? (
          <Button color="primary" size="sm" onClick={() => setShowAddForm(true)} data-cy="addConditionButton">
            <FontAwesomeIcon icon="plus" /> <Translate contentKey="fintrackApp.transactionRule.addCondition">Add condition</Translate>
          </Button>
        ) : null}
      </div>

      <p className="text-muted">
        <Translate contentKey="fintrackApp.transactionRule.configuredConditionsHelp">Define conditions before saving this rule.</Translate>
      </p>

      {showAddForm ? (
        <div className="border rounded p-3 mb-3" data-cy="embeddedConditionForm" onSubmit={event => event.stopPropagation()}>
          <h4>
            <Translate contentKey="fintrackApp.transactionRule.addCondition">Add condition</Translate>
          </h4>
          <TransactionRuleConditionFormSection
            isNew
            showParentSelector={false}
            submitLabelKey="fintrackApp.transactionRule.saveCondition"
            submitLabel="Save condition"
            onSubmit={handleCreate}
            onCancel={closeForm}
          />
        </div>
      ) : null}

      {editingCondition ? (
        <div className="border rounded p-3 mb-3" data-cy="embeddedConditionForm" onSubmit={event => event.stopPropagation()}>
          <h4>
            <Translate contentKey="fintrackApp.transactionRule.editCondition">Edit condition</Translate>
          </h4>
          <TransactionRuleConditionFormSection
            initialCondition={editingCondition}
            isNew={false}
            showParentSelector={false}
            submitLabelKey="fintrackApp.transactionRule.updateCondition"
            submitLabel="Update condition"
            onSubmit={handleUpdate}
            onCancel={closeForm}
          />
        </div>
      ) : null}

      {sortedConditions.length === 0 ? (
        <Alert color="warning" fade={false} data-cy="noConditionsValidation">
          <Translate contentKey="fintrackApp.transactionRule.validation.conditionsRequired">
            Transaction rule must have at least one condition.
          </Translate>
        </Alert>
      ) : null}

      {sortedConditions.length > 0 ? (
        <Table responsive size="sm">
          <thead>
            <tr>
              <th>
                <Translate contentKey="fintrackApp.transactionRuleCondition.condition">Condition</Translate>
              </th>
              <th />
            </tr>
          </thead>
          <tbody>
            {sortedConditions.map(condition => {
              const locked = isLockedFlowCondition(condition, lockedFlow);
              return (
                <tr key={condition.clientId ?? condition.id}>
                  <td>
                    {buildTransactionRuleConditionSummary(condition, translate)}
                    {locked ? (
                      <FormText className="d-block" data-cy="lockedFlowConditionHelp">
                        <Translate contentKey="fintrackApp.transactionRule.validation.requiredFlowLocked">
                          This Flow condition is required by the selected category.
                        </Translate>
                      </FormText>
                    ) : null}
                  </td>
                  <td className="text-end">
                    <Button color="primary" size="sm" onClick={() => setEditingCondition(condition)} data-cy="editConditionButton">
                      <FontAwesomeIcon icon="pencil-alt" />{' '}
                      <span className="d-none d-md-inline">
                        <Translate contentKey="entity.action.edit">Edit</Translate>
                      </span>
                    </Button>{' '}
                    {!locked ? (
                      <Button color="danger" size="sm" onClick={() => handleDelete(condition)} data-cy="deleteConditionButton">
                        <FontAwesomeIcon icon="trash" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="fintrackApp.transactionRule.deleteCondition">Delete condition</Translate>
                        </span>
                      </Button>
                    ) : null}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </Table>
      ) : null}
    </section>
  );
};

export default TransactionRuleLocalConditionsEditor;
