import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { TextFormat, Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faArrowDown, faArrowUp } from '@fortawesome/free-solid-svg-icons';
import { APP_DATE_FORMAT } from 'app/config/constants';
import { ASC } from 'app/shared/util/pagination.constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './transaction-rule.reducer';
import {
  buildTransactionRuleResultSummary,
  formatTransactionRuleConditionLogic,
  formatTransactionRuleStatus,
} from './transaction-rule-display';

export const TransactionRule = () => {
  const dispatch = useAppDispatch();

  const [reordering, setReordering] = useState(false);
  const [reorderError, setReorderError] = useState(false);

  const transactionRuleList = useAppSelector(state => state.transactionRule.entities);
  const loading = useAppSelector(state => state.transactionRule.loading);
  const orderedTransactionRuleList = [...transactionRuleList].sort((firstRule, secondRule) => {
    const firstPriority = firstRule.priority ?? Number.MAX_SAFE_INTEGER;
    const secondPriority = secondRule.priority ?? Number.MAX_SAFE_INTEGER;
    if (firstPriority !== secondPriority) {
      return firstPriority - secondPriority;
    }
    return (firstRule.id ?? Number.MAX_SAFE_INTEGER) - (secondRule.id ?? Number.MAX_SAFE_INTEGER);
  });

  const getAllEntities = () => {
    dispatch(
      getEntities({
        sort: `priority,${ASC}&sort=id,${ASC}`,
      }),
    );
  };

  const sortEntities = () => {
    getAllEntities();
  };

  useEffect(() => {
    sortEntities();
  }, []);

  const handleSyncList = () => {
    sortEntities();
  };

  const reorderRules = async (fromIndex: number, toIndex: number) => {
    const nextRules = [...orderedTransactionRuleList];
    const [movedRule] = nextRules.splice(fromIndex, 1);
    nextRules.splice(toIndex, 0, movedRule);

    setReorderError(false);
    setReordering(true);
    try {
      await axios.put('api/transaction-rules/reorder', { orderedIds: nextRules.map(rule => rule.id) });
      getAllEntities();
    } catch (error) {
      setReorderError(true);
    } finally {
      setReordering(false);
    }
  };

  return (
    <div>
      <h2 id="transaction-rule-heading" data-cy="TransactionRuleHeading">
        <Translate contentKey="fintrackApp.transactionRule.home.title">Transaction Rules</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.transactionRule.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/transaction-rule/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.transactionRule.home.createLabel">Create new Transaction Rule</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {reorderError ? (
          <div className="alert alert-danger" role="alert">
            <Translate contentKey="fintrackApp.transactionRule.reorderFailed">Could not reorder rules.</Translate>
          </div>
        ) : null}
        {orderedTransactionRuleList && orderedTransactionRuleList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th>
                  <Translate contentKey="fintrackApp.transactionRule.name">Name</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.transactionRule.status.label">Status</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.transactionRule.order">Order</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.transactionRule.conditionSummary.label">Conditions</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.transactionRule.result.label">Result</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.transactionRule.updated">Updated</Translate>
                </th>
                <th className="text-end">
                  <Translate contentKey="fintrackApp.transactionRule.actions">Actions</Translate>
                </th>
              </tr>
            </thead>
            <tbody>
              {orderedTransactionRuleList.map((transactionRule, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Link to={`/transaction-rule/${transactionRule.id}`}>{transactionRule.name}</Link>
                  </td>
                  <td>{formatTransactionRuleStatus(transactionRule, translate)}</td>
                  <td>
                    {transactionRule.priority === undefined || transactionRule.priority === null
                      ? null
                      : `#${transactionRule.priority + 1}`}
                  </td>
                  <td>{formatTransactionRuleConditionLogic(transactionRule.conditionLogic, translate)}</td>
                  <td>
                    {buildTransactionRuleResultSummary(transactionRule, translate).length > 0 ? (
                      buildTransactionRuleResultSummary(transactionRule, translate).map(result => (
                        <div key={result}>
                          <small>{result}</small>
                        </div>
                      ))
                    ) : (
                      <small>
                        <Translate contentKey="fintrackApp.transactionRule.result.empty">No result configured</Translate>
                      </small>
                    )}
                  </td>
                  <td>
                    {transactionRule.updatedAt ? (
                      <TextFormat type="date" value={transactionRule.updatedAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      {i > 0 ? (
                        <Button
                          color="secondary"
                          size="sm"
                          onClick={() => reorderRules(i, i - 1)}
                          disabled={reordering}
                          data-cy="entityMoveUpButton"
                        >
                          <FontAwesomeIcon icon={faArrowUp} />{' '}
                          <span className="d-none d-md-inline">
                            <Translate contentKey="fintrackApp.transactionRule.moveUp">Move up</Translate>
                          </span>
                        </Button>
                      ) : null}
                      {i < orderedTransactionRuleList.length - 1 ? (
                        <Button
                          color="secondary"
                          size="sm"
                          onClick={() => reorderRules(i, i + 1)}
                          disabled={reordering}
                          data-cy="entityMoveDownButton"
                        >
                          <FontAwesomeIcon icon={faArrowDown} />{' '}
                          <span className="d-none d-md-inline">
                            <Translate contentKey="fintrackApp.transactionRule.moveDown">Move down</Translate>
                          </span>
                        </Button>
                      ) : null}
                      <Button
                        tag={Link}
                        to={`/transaction-rule/${transactionRule.id}`}
                        color="info"
                        size="sm"
                        data-cy="entityDetailsButton"
                      >
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      <Button
                        tag={Link}
                        to={`/transaction-rule/${transactionRule.id}/edit`}
                        color="primary"
                        size="sm"
                        data-cy="entityEditButton"
                      >
                        <FontAwesomeIcon icon="pencil-alt" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.edit">Edit</Translate>
                        </span>
                      </Button>
                      <Button
                        onClick={() => (window.location.href = `/transaction-rule/${transactionRule.id}/delete`)}
                        color="danger"
                        size="sm"
                        data-cy="entityDeleteButton"
                      >
                        <FontAwesomeIcon icon="trash" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.delete">Delete</Translate>
                        </span>
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : (
          !loading && (
            <div className="alert alert-warning">
              <Translate contentKey="fintrackApp.transactionRule.home.notFound">No Transaction Rules found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default TransactionRule;
