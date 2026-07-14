import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { TextFormat, Translate, getSortState, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { APP_DATE_FORMAT } from 'app/config/constants';
import { ASC, DESC } from 'app/shared/util/pagination.constants';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './transaction-rule.reducer';
import {
  buildTransactionRuleResultSummary,
  formatTransactionRuleConditionLogic,
  formatTransactionRuleStatus,
} from './transaction-rule-display';

export const TransactionRule = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'priority'), pageLocation.search));

  const transactionRuleList = useAppSelector(state => state.transactionRule.entities);
  const loading = useAppSelector(state => state.transactionRule.loading);

  const getAllEntities = () => {
    dispatch(
      getEntities({
        sort:
          sortState.sort === 'priority' ? `${sortState.sort},${sortState.order}&sort=id,${ASC}` : `${sortState.sort},${sortState.order}`,
      }),
    );
  };

  const sortEntities = () => {
    getAllEntities();
    const endURL = `?sort=${sortState.sort},${sortState.order}`;
    if (pageLocation.search !== endURL) {
      navigate(`${pageLocation.pathname}${endURL}`);
    }
  };

  useEffect(() => {
    sortEntities();
  }, [sortState.order, sortState.sort]);

  const sort = p => () => {
    setSortState({
      ...sortState,
      order: sortState.order === ASC ? DESC : ASC,
      sort: p,
    });
  };

  const handleSyncList = () => {
    sortEntities();
  };

  const getSortIconByFieldName = (fieldName: string) => {
    const sortFieldName = sortState.sort;
    const order = sortState.order;
    if (sortFieldName !== fieldName) {
      return faSort;
    }
    return order === ASC ? faSortUp : faSortDown;
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
        {transactionRuleList && transactionRuleList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('name')}>
                  <Translate contentKey="fintrackApp.transactionRule.name">Name</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('name')} />
                </th>
                <th className="hand" onClick={sort('active')}>
                  <Translate contentKey="fintrackApp.transactionRule.status.label">Status</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('active')} />
                </th>
                <th className="hand" onClick={sort('priority')}>
                  <Translate contentKey="fintrackApp.transactionRule.order">Order</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('priority')} />
                </th>
                <th className="hand" onClick={sort('conditionLogic')}>
                  <Translate contentKey="fintrackApp.transactionRule.conditionSummary.label">Conditions</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('conditionLogic')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.transactionRule.result.label">Result</Translate>
                </th>
                <th className="hand" onClick={sort('updatedAt')}>
                  <Translate contentKey="fintrackApp.transactionRule.updated">Updated</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('updatedAt')} />
                </th>
                <th className="text-end">
                  <Translate contentKey="fintrackApp.transactionRule.actions">Actions</Translate>
                </th>
              </tr>
            </thead>
            <tbody>
              {transactionRuleList.map((transactionRule, i) => (
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
