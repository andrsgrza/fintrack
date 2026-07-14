import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';
import { Alert, Button, Table } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { ITransactionRuleCondition } from 'app/shared/model/transaction-rule-condition.model';

interface TransactionRuleConditionsRelatedListProps {
  transactionRuleId?: number | string;
}

export const TransactionRuleConditionsRelatedList = ({ transactionRuleId }: TransactionRuleConditionsRelatedListProps) => {
  const [conditions, setConditions] = useState<ITransactionRuleCondition[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!transactionRuleId) {
      return;
    }

    setLoading(true);
    setError(false);
    axios
      .get<ITransactionRuleCondition[]>(`api/transaction-rules/${transactionRuleId}/conditions`)
      .then(response => {
        setConditions(response.data);
      })
      .catch(() => {
        setError(true);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [transactionRuleId]);

  return (
    <section className="mt-4" data-cy="transactionRuleConditionsSection">
      <div className="d-flex align-items-center justify-content-between mb-2">
        <h3 className="mb-0">
          <Translate contentKey="fintrackApp.transactionRule.conditions">Conditions</Translate>
        </h3>
        {transactionRuleId ? (
          <Button
            tag={Link}
            to={`/transaction-rule-condition/new?transactionRuleId=${transactionRuleId}`}
            color="primary"
            size="sm"
            data-cy="addConditionButton"
          >
            <FontAwesomeIcon icon="plus" /> <Translate contentKey="fintrackApp.transactionRule.addCondition">Add condition</Translate>
          </Button>
        ) : null}
      </div>
      {loading ? (
        <p>
          <Translate contentKey="fintrackApp.transactionRule.loadingConditions">Loading conditions...</Translate>
        </p>
      ) : null}
      {error ? (
        <Alert color="warning" fade={false}>
          <Translate contentKey="fintrackApp.transactionRule.conditionsUnavailable">Conditions are not available.</Translate>
        </Alert>
      ) : null}
      {!loading && !error && conditions.length === 0 ? (
        <p>
          <Translate contentKey="fintrackApp.transactionRule.noConditionsYet">No conditions yet.</Translate>
        </p>
      ) : null}
      {!loading && !error && conditions.length > 0 ? (
        <Table responsive size="sm">
          <thead>
            <tr>
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
            {conditions.map(condition => (
              <tr key={condition.id}>
                <td>{condition.field ? translate(`fintrackApp.TransactionRuleField.${condition.field}`) : ''}</td>
                <td>{condition.operator ? translate(`fintrackApp.RuleOperator.${condition.operator}`) : ''}</td>
                <td>{condition.value}</td>
                <td>{condition.secondValue}</td>
                <td>{condition.caseSensitive ? 'true' : 'false'}</td>
                <td className="text-end">
                  <Button tag={Link} to={`/transaction-rule-condition/${condition.id}`} color="info" size="sm">
                    <FontAwesomeIcon icon="eye" />{' '}
                    <span className="d-none d-md-inline">
                      <Translate contentKey="entity.action.view">View</Translate>
                    </span>
                  </Button>{' '}
                  <Button tag={Link} to={`/transaction-rule-condition/${condition.id}/edit`} color="primary" size="sm">
                    <FontAwesomeIcon icon="pencil-alt" />{' '}
                    <span className="d-none d-md-inline">
                      <Translate contentKey="entity.action.edit">Edit</Translate>
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

export default TransactionRuleConditionsRelatedList;
