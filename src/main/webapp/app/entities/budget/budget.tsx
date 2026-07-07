import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { TextFormat, Translate, getSortState } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { APP_DATE_FORMAT, APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { ASC, DESC } from 'app/shared/util/pagination.constants';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './budget.reducer';

export const Budget = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const budgetList = useAppSelector(state => state.budget.entities);
  const loading = useAppSelector(state => state.budget.loading);

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
      <h2 id="budget-heading" data-cy="BudgetHeading">
        <Translate contentKey="fintrackApp.budget.home.title">Budgets</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.budget.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/budget/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.budget.home.createLabel">Create new Budget</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {budgetList && budgetList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="fintrackApp.budget.id">ID</Translate> <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('name')}>
                  <Translate contentKey="fintrackApp.budget.name">Name</Translate> <FontAwesomeIcon icon={getSortIconByFieldName('name')} />
                </th>
                <th className="hand" onClick={sort('amount')}>
                  <Translate contentKey="fintrackApp.budget.amount">Amount</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('amount')} />
                </th>
                <th className="hand" onClick={sort('currency')}>
                  <Translate contentKey="fintrackApp.budget.currency">Currency</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('currency')} />
                </th>
                <th className="hand" onClick={sort('period')}>
                  <Translate contentKey="fintrackApp.budget.period">Period</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('period')} />
                </th>
                <th className="hand" onClick={sort('startDate')}>
                  <Translate contentKey="fintrackApp.budget.startDate">Start Date</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('startDate')} />
                </th>
                <th className="hand" onClick={sort('endDate')}>
                  <Translate contentKey="fintrackApp.budget.endDate">End Date</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('endDate')} />
                </th>
                <th className="hand" onClick={sort('status')}>
                  <Translate contentKey="fintrackApp.budget.status">Status</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('status')} />
                </th>
                <th className="hand" onClick={sort('tagMatchMode')}>
                  <Translate contentKey="fintrackApp.budget.tagMatchMode">Tag Match Mode</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('tagMatchMode')} />
                </th>
                <th className="hand" onClick={sort('warningPercentage')}>
                  <Translate contentKey="fintrackApp.budget.warningPercentage">Warning Percentage</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('warningPercentage')} />
                </th>
                <th className="hand" onClick={sort('createdAt')}>
                  <Translate contentKey="fintrackApp.budget.createdAt">Created At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('createdAt')} />
                </th>
                <th className="hand" onClick={sort('updatedAt')}>
                  <Translate contentKey="fintrackApp.budget.updatedAt">Updated At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('updatedAt')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.budget.user">User</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.budget.accounts">Accounts</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.budget.categories">Categories</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.budget.tags">Tags</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {budgetList.map((budget, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/budget/${budget.id}`} color="link" size="sm">
                      {budget.id}
                    </Button>
                  </td>
                  <td>{budget.name}</td>
                  <td>{budget.amount}</td>
                  <td>{budget.currency}</td>
                  <td>
                    <Translate contentKey={`fintrackApp.BudgetPeriod.${budget.period}`} />
                  </td>
                  <td>{budget.startDate ? <TextFormat type="date" value={budget.startDate} format={APP_LOCAL_DATE_FORMAT} /> : null}</td>
                  <td>{budget.endDate ? <TextFormat type="date" value={budget.endDate} format={APP_LOCAL_DATE_FORMAT} /> : null}</td>
                  <td>
                    <Translate contentKey={`fintrackApp.BudgetStatus.${budget.status}`} />
                  </td>
                  <td>
                    <Translate contentKey={`fintrackApp.TagMatchMode.${budget.tagMatchMode}`} />
                  </td>
                  <td>{budget.warningPercentage}</td>
                  <td>{budget.createdAt ? <TextFormat type="date" value={budget.createdAt} format={APP_DATE_FORMAT} /> : null}</td>
                  <td>{budget.updatedAt ? <TextFormat type="date" value={budget.updatedAt} format={APP_DATE_FORMAT} /> : null}</td>
                  <td>{budget.user ? budget.user.login : ''}</td>
                  <td>
                    {budget.accounts
                      ? budget.accounts.map((val, j) => (
                          <span key={j}>
                            <Link to={`/financial-account/${val.id}`}>{val.name}</Link>
                            {j === budget.accounts.length - 1 ? '' : ', '}
                          </span>
                        ))
                      : null}
                  </td>
                  <td>
                    {budget.categories
                      ? budget.categories.map((val, j) => (
                          <span key={j}>
                            <Link to={`/category/${val.id}`}>{val.name}</Link>
                            {j === budget.categories.length - 1 ? '' : ', '}
                          </span>
                        ))
                      : null}
                  </td>
                  <td>
                    {budget.tags
                      ? budget.tags.map((val, j) => (
                          <span key={j}>
                            <Link to={`/tag/${val.id}`}>{val.name}</Link>
                            {j === budget.tags.length - 1 ? '' : ', '}
                          </span>
                        ))
                      : null}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button tag={Link} to={`/budget/${budget.id}`} color="info" size="sm" data-cy="entityDetailsButton">
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      <Button tag={Link} to={`/budget/${budget.id}/edit`} color="primary" size="sm" data-cy="entityEditButton">
                        <FontAwesomeIcon icon="pencil-alt" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.edit">Edit</Translate>
                        </span>
                      </Button>
                      <Button
                        onClick={() => (window.location.href = `/budget/${budget.id}/delete`)}
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
              <Translate contentKey="fintrackApp.budget.home.notFound">No Budgets found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default Budget;
