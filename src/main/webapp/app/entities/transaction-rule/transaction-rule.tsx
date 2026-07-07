import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { TextFormat, Translate, getSortState } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { APP_DATE_FORMAT } from 'app/config/constants';
import { ASC, DESC } from 'app/shared/util/pagination.constants';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './transaction-rule.reducer';

export const TransactionRule = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const transactionRuleList = useAppSelector(state => state.transactionRule.entities);
  const loading = useAppSelector(state => state.transactionRule.loading);

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
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="fintrackApp.transactionRule.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('name')}>
                  <Translate contentKey="fintrackApp.transactionRule.name">Name</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('name')} />
                </th>
                <th className="hand" onClick={sort('description')}>
                  <Translate contentKey="fintrackApp.transactionRule.description">Description</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('description')} />
                </th>
                <th className="hand" onClick={sort('priority')}>
                  <Translate contentKey="fintrackApp.transactionRule.priority">Priority</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('priority')} />
                </th>
                <th className="hand" onClick={sort('conditionLogic')}>
                  <Translate contentKey="fintrackApp.transactionRule.conditionLogic">Condition Logic</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('conditionLogic')} />
                </th>
                <th className="hand" onClick={sort('resultingDescription')}>
                  <Translate contentKey="fintrackApp.transactionRule.resultingDescription">Resulting Description</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('resultingDescription')} />
                </th>
                <th className="hand" onClick={sort('active')}>
                  <Translate contentKey="fintrackApp.transactionRule.active">Active</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('active')} />
                </th>
                <th className="hand" onClick={sort('createdAt')}>
                  <Translate contentKey="fintrackApp.transactionRule.createdAt">Created At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('createdAt')} />
                </th>
                <th className="hand" onClick={sort('updatedAt')}>
                  <Translate contentKey="fintrackApp.transactionRule.updatedAt">Updated At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('updatedAt')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.transactionRule.user">User</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.transactionRule.resultingCategory">Resulting Category</Translate>{' '}
                  <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.transactionRule.resultingFinancialSubscription">
                    Resulting Financial Subscription
                  </Translate>{' '}
                  <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.transactionRule.resultingTags">Resulting Tags</Translate>{' '}
                  <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {transactionRuleList.map((transactionRule, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/transaction-rule/${transactionRule.id}`} color="link" size="sm">
                      {transactionRule.id}
                    </Button>
                  </td>
                  <td>{transactionRule.name}</td>
                  <td>{transactionRule.description}</td>
                  <td>{transactionRule.priority}</td>
                  <td>
                    <Translate contentKey={`fintrackApp.RuleConditionLogic.${transactionRule.conditionLogic}`} />
                  </td>
                  <td>{transactionRule.resultingDescription}</td>
                  <td>{transactionRule.active ? 'true' : 'false'}</td>
                  <td>
                    {transactionRule.createdAt ? (
                      <TextFormat type="date" value={transactionRule.createdAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {transactionRule.updatedAt ? (
                      <TextFormat type="date" value={transactionRule.updatedAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>{transactionRule.user ? transactionRule.user.login : ''}</td>
                  <td>
                    {transactionRule.resultingCategory ? (
                      <Link to={`/category/${transactionRule.resultingCategory.id}`}>{transactionRule.resultingCategory.name}</Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td>
                    {transactionRule.resultingFinancialSubscription ? (
                      <Link to={`/financial-subscription/${transactionRule.resultingFinancialSubscription.id}`}>
                        {transactionRule.resultingFinancialSubscription.name}
                      </Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td>
                    {transactionRule.resultingTags
                      ? transactionRule.resultingTags.map((val, j) => (
                          <span key={j}>
                            <Link to={`/tag/${val.id}`}>{val.name}</Link>
                            {j === transactionRule.resultingTags.length - 1 ? '' : ', '}
                          </span>
                        ))
                      : null}
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
