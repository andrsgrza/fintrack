import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { Translate, getSortState } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { ASC, DESC } from 'app/shared/util/pagination.constants';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './transaction-rule-condition.reducer';

export const TransactionRuleCondition = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const transactionRuleConditionList = useAppSelector(state => state.transactionRuleCondition.entities);
  const loading = useAppSelector(state => state.transactionRuleCondition.loading);

  const getAllEntities = () => {
    dispatch(
      getEntities({
        sort: `${sortState.sort},${sortState.order}`,
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
      <h2 id="transaction-rule-condition-heading" data-cy="TransactionRuleConditionHeading">
        <Translate contentKey="fintrackApp.transactionRuleCondition.home.title">Transaction Rule Conditions</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.transactionRuleCondition.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link
            to="/transaction-rule-condition/new"
            className="btn btn-primary jh-create-entity"
            id="jh-create-entity"
            data-cy="entityCreateButton"
          >
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.transactionRuleCondition.home.createLabel">Create new Transaction Rule Condition</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {transactionRuleConditionList && transactionRuleConditionList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="fintrackApp.transactionRuleCondition.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('field')}>
                  <Translate contentKey="fintrackApp.transactionRuleCondition.field">Field</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('field')} />
                </th>
                <th className="hand" onClick={sort('operator')}>
                  <Translate contentKey="fintrackApp.transactionRuleCondition.operator">Operator</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('operator')} />
                </th>
                <th className="hand" onClick={sort('value')}>
                  <Translate contentKey="fintrackApp.transactionRuleCondition.value">Value</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('value')} />
                </th>
                <th className="hand" onClick={sort('secondValue')}>
                  <Translate contentKey="fintrackApp.transactionRuleCondition.secondValue">Second Value</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('secondValue')} />
                </th>
                <th className="hand" onClick={sort('caseSensitive')}>
                  <Translate contentKey="fintrackApp.transactionRuleCondition.caseSensitive">Case Sensitive</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('caseSensitive')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.transactionRuleCondition.transactionRule">Transaction Rule</Translate>{' '}
                  <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {transactionRuleConditionList.map((transactionRuleCondition, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/transaction-rule-condition/${transactionRuleCondition.id}`} color="link" size="sm">
                      {transactionRuleCondition.id}
                    </Button>
                  </td>
                  <td>
                    <Translate contentKey={`fintrackApp.TransactionRuleField.${transactionRuleCondition.field}`} />
                  </td>
                  <td>
                    <Translate contentKey={`fintrackApp.RuleOperator.${transactionRuleCondition.operator}`} />
                  </td>
                  <td>{transactionRuleCondition.value}</td>
                  <td>{transactionRuleCondition.secondValue}</td>
                  <td>{transactionRuleCondition.caseSensitive ? 'true' : 'false'}</td>
                  <td>
                    {transactionRuleCondition.transactionRule ? (
                      <Link to={`/transaction-rule/${transactionRuleCondition.transactionRule.id}`}>
                        {transactionRuleCondition.transactionRule.name}
                      </Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button
                        tag={Link}
                        to={`/transaction-rule-condition/${transactionRuleCondition.id}`}
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
                        to={`/transaction-rule-condition/${transactionRuleCondition.id}/edit`}
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
                        onClick={() => (window.location.href = `/transaction-rule-condition/${transactionRuleCondition.id}/delete`)}
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
              <Translate contentKey="fintrackApp.transactionRuleCondition.home.notFound">No Transaction Rule Conditions found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default TransactionRuleCondition;
