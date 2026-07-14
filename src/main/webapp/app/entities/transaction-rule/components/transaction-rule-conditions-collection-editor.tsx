import React, { useCallback, useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Table } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import TransactionRuleConditionFormSection from 'app/entities/transaction-rule-condition/components/transaction-rule-condition-form-section';
import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';
import { cleanEntity } from 'app/shared/util/entity-utils';

interface TransactionRuleConditionsCollectionEditorProps {
  transactionRuleId?: number | string;
  onConditionsStateChange?: (state: { count: number; loaded: boolean; failed: boolean }) => void;
  onConditionMutation?: () => void;
}

const apiUrl = 'api/transaction-rule-conditions';

const sortConditions = (conditions: ITransactionRuleCondition[]) =>
  [...conditions].sort((a, b) => {
    const positionCompare = (a.position ?? 0) - (b.position ?? 0);
    if (positionCompare !== 0) {
      return positionCompare;
    }
    return (a.id ?? 0) - (b.id ?? 0);
  });

export const TransactionRuleConditionsCollectionEditor = ({
  transactionRuleId,
  onConditionsStateChange,
  onConditionMutation,
}: TransactionRuleConditionsCollectionEditorProps) => {
  const [conditions, setConditions] = useState<ITransactionRuleCondition[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [editingCondition, setEditingCondition] = useState<ITransactionRuleCondition | null>(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const sortedConditions = useMemo(() => sortConditions(conditions), [conditions]);
  const nextPosition = sortedConditions.length === 0 ? 0 : Math.max(...sortedConditions.map(condition => condition.position ?? 0)) + 1;

  const publishConditionsState = useCallback(
    (nextConditions: ITransactionRuleCondition[], loaded: boolean, failed: boolean) => {
      onConditionsStateChange?.({ count: nextConditions.length, loaded, failed });
    },
    [onConditionsStateChange],
  );

  const loadConditions = useCallback(() => {
    if (!transactionRuleId) {
      return Promise.resolve();
    }

    setLoading(true);
    setLoadError(false);
    return axios
      .get<ITransactionRuleCondition[]>(`api/transaction-rules/${transactionRuleId}/conditions`)
      .then(response => {
        const nextConditions = sortConditions(response.data);
        setConditions(nextConditions);
        publishConditionsState(nextConditions, true, false);
      })
      .catch(() => {
        setLoadError(true);
        publishConditionsState([], false, true);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [transactionRuleId, publishConditionsState]);

  useEffect(() => {
    loadConditions();
  }, [loadConditions]);

  const closeForm = () => {
    setShowAddForm(false);
    setEditingCondition(null);
    setMutationError(null);
  };

  const handleCreate = (entity: ITransactionRuleCondition) => {
    setSubmitting(true);
    setMutationError(null);
    axios
      .post<ITransactionRuleCondition>(apiUrl, cleanEntity(entity))
      .then(() => {
        closeForm();
        onConditionMutation?.();
        return loadConditions();
      })
      .catch(() => {
        setMutationError(translate('fintrackApp.transactionRule.conditionSaveFailed'));
      })
      .finally(() => {
        setSubmitting(false);
      });
  };

  const handleUpdate = (entity: ITransactionRuleCondition) => {
    setSubmitting(true);
    setMutationError(null);
    const patchEntity = {
      id: entity.id,
      field: entity.field,
      operator: entity.operator,
      value: entity.value,
      secondValue: entity.secondValue,
      caseSensitive: entity.caseSensitive,
      position: entity.position,
    };
    axios
      .patch<ITransactionRuleCondition>(`${apiUrl}/${entity.id}`, cleanEntity(patchEntity))
      .then(() => {
        closeForm();
        onConditionMutation?.();
        return loadConditions();
      })
      .catch(() => {
        setMutationError(translate('fintrackApp.transactionRule.conditionSaveFailed'));
      })
      .finally(() => {
        setSubmitting(false);
      });
  };

  const handleDelete = (condition: ITransactionRuleCondition) => {
    if (!condition.id || !window.confirm(translate('fintrackApp.transactionRule.confirmDeleteCondition'))) {
      return;
    }

    setMutationError(null);
    axios
      .delete(`${apiUrl}/${condition.id}`)
      .then(() => {
        onConditionMutation?.();
        return loadConditions();
      })
      .catch(() => {
        setMutationError(translate('fintrackApp.transactionRule.conditionDeleteFailed'));
      });
  };

  return (
    <section className="mt-4" data-cy="transactionRuleConditionsEditor">
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
        <Translate contentKey="fintrackApp.transactionRule.activeRequiresCondition">Active rules require at least one condition.</Translate>
      </p>

      {loading ? (
        <p>
          <Translate contentKey="fintrackApp.transactionRule.loadingConditions">Loading conditions...</Translate>
        </p>
      ) : null}
      {loadError ? (
        <Alert color="warning" fade={false}>
          <Translate contentKey="fintrackApp.transactionRule.conditionsUnavailable">Conditions are not available.</Translate>
        </Alert>
      ) : null}
      {mutationError ? (
        <Alert color="danger" fade={false} data-cy="conditionMutationError">
          {mutationError}
        </Alert>
      ) : null}

      {showAddForm ? (
        <div className="border rounded p-3 mb-3" data-cy="embeddedConditionForm">
          <h4>
            <Translate contentKey="fintrackApp.transactionRule.addCondition">Add condition</Translate>
          </h4>
          <TransactionRuleConditionFormSection
            isNew
            fixedTransactionRuleId={transactionRuleId}
            defaultPosition={nextPosition}
            showParentSelector={false}
            submitting={submitting}
            submitLabelKey="fintrackApp.transactionRule.saveCondition"
            submitLabel="Save condition"
            onSubmit={handleCreate}
            onCancel={closeForm}
          />
        </div>
      ) : null}

      {editingCondition ? (
        <div className="border rounded p-3 mb-3" data-cy="embeddedConditionForm">
          <h4>
            <Translate contentKey="fintrackApp.transactionRule.editCondition">Edit condition</Translate>
          </h4>
          <TransactionRuleConditionFormSection
            initialCondition={editingCondition}
            isNew={false}
            fixedTransactionRuleId={transactionRuleId}
            showParentSelector={false}
            submitting={submitting}
            submitLabelKey="fintrackApp.transactionRule.updateCondition"
            submitLabel="Update condition"
            onSubmit={handleUpdate}
            onCancel={closeForm}
          />
        </div>
      ) : null}

      {!loading && !loadError && sortedConditions.length === 0 ? (
        <p>
          <Translate contentKey="fintrackApp.transactionRule.noConditionsYet">No conditions yet.</Translate>
        </p>
      ) : null}

      {!loadError && sortedConditions.length > 0 ? (
        <Table responsive size="sm">
          <thead>
            <tr>
              <th>
                <Translate contentKey="fintrackApp.transactionRuleCondition.position">Position</Translate>
              </th>
              <th>
                <Translate contentKey="fintrackApp.transactionRuleCondition.field">Field</Translate>
              </th>
              <th>
                <Translate contentKey="fintrackApp.transactionRuleCondition.operator">Operator</Translate>
              </th>
              <th>
                <Translate contentKey="fintrackApp.transactionRuleCondition.value">Value</Translate>
              </th>
              <th>
                <Translate contentKey="fintrackApp.transactionRuleCondition.secondValue">Second Value</Translate>
              </th>
              <th>
                <Translate contentKey="fintrackApp.transactionRuleCondition.caseSensitive">Case Sensitive</Translate>
              </th>
              <th />
            </tr>
          </thead>
          <tbody>
            {sortedConditions.map(condition => (
              <tr key={condition.id}>
                <td>{condition.position}</td>
                <td>{condition.field ? translate(`fintrackApp.TransactionRuleField.${condition.field}`) : ''}</td>
                <td>{condition.operator ? translate(`fintrackApp.RuleOperator.${condition.operator}`) : ''}</td>
                <td>{condition.value}</td>
                <td>{condition.secondValue}</td>
                <td>{condition.caseSensitive ? 'true' : 'false'}</td>
                <td className="text-end">
                  <Button color="primary" size="sm" onClick={() => setEditingCondition(condition)} data-cy="editConditionButton">
                    <FontAwesomeIcon icon="pencil-alt" />{' '}
                    <span className="d-none d-md-inline">
                      <Translate contentKey="entity.action.edit">Edit</Translate>
                    </span>
                  </Button>{' '}
                  <Button color="danger" size="sm" onClick={() => handleDelete(condition)} data-cy="deleteConditionButton">
                    <FontAwesomeIcon icon="trash" />{' '}
                    <span className="d-none d-md-inline">
                      <Translate contentKey="fintrackApp.transactionRule.deleteCondition">Delete condition</Translate>
                    </span>
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      ) : null}
    </section>
  );
};

export default TransactionRuleConditionsCollectionEditor;
