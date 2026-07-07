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

import { getEntities } from './financial-subscription.reducer';

export const FinancialSubscription = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const financialSubscriptionList = useAppSelector(state => state.financialSubscription.entities);
  const loading = useAppSelector(state => state.financialSubscription.loading);

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
      <h2 id="financial-subscription-heading" data-cy="FinancialSubscriptionHeading">
        <Translate contentKey="fintrackApp.financialSubscription.home.title">Financial Subscriptions</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.financialSubscription.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link
            to="/financial-subscription/new"
            className="btn btn-primary jh-create-entity"
            id="jh-create-entity"
            data-cy="entityCreateButton"
          >
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.financialSubscription.home.createLabel">Create new Financial Subscription</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {financialSubscriptionList && financialSubscriptionList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="fintrackApp.financialSubscription.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('name')}>
                  <Translate contentKey="fintrackApp.financialSubscription.name">Name</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('name')} />
                </th>
                <th className="hand" onClick={sort('description')}>
                  <Translate contentKey="fintrackApp.financialSubscription.description">Description</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('description')} />
                </th>
                <th className="hand" onClick={sort('status')}>
                  <Translate contentKey="fintrackApp.financialSubscription.status">Status</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('status')} />
                </th>
                <th className="hand" onClick={sort('expectedAmount')}>
                  <Translate contentKey="fintrackApp.financialSubscription.expectedAmount">Expected Amount</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('expectedAmount')} />
                </th>
                <th className="hand" onClick={sort('amountTolerancePercentage')}>
                  <Translate contentKey="fintrackApp.financialSubscription.amountTolerancePercentage">
                    Amount Tolerance Percentage
                  </Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('amountTolerancePercentage')} />
                </th>
                <th className="hand" onClick={sort('currency')}>
                  <Translate contentKey="fintrackApp.financialSubscription.currency">Currency</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('currency')} />
                </th>
                <th className="hand" onClick={sort('recurrenceUnit')}>
                  <Translate contentKey="fintrackApp.financialSubscription.recurrenceUnit">Recurrence Unit</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('recurrenceUnit')} />
                </th>
                <th className="hand" onClick={sort('intervalCount')}>
                  <Translate contentKey="fintrackApp.financialSubscription.intervalCount">Interval Count</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('intervalCount')} />
                </th>
                <th className="hand" onClick={sort('startDate')}>
                  <Translate contentKey="fintrackApp.financialSubscription.startDate">Start Date</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('startDate')} />
                </th>
                <th className="hand" onClick={sort('nextExpectedDate')}>
                  <Translate contentKey="fintrackApp.financialSubscription.nextExpectedDate">Next Expected Date</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('nextExpectedDate')} />
                </th>
                <th className="hand" onClick={sort('endDate')}>
                  <Translate contentKey="fintrackApp.financialSubscription.endDate">End Date</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('endDate')} />
                </th>
                <th className="hand" onClick={sort('automaticPayment')}>
                  <Translate contentKey="fintrackApp.financialSubscription.automaticPayment">Automatic Payment</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('automaticPayment')} />
                </th>
                <th className="hand" onClick={sort('notes')}>
                  <Translate contentKey="fintrackApp.financialSubscription.notes">Notes</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('notes')} />
                </th>
                <th className="hand" onClick={sort('createdAt')}>
                  <Translate contentKey="fintrackApp.financialSubscription.createdAt">Created At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('createdAt')} />
                </th>
                <th className="hand" onClick={sort('updatedAt')}>
                  <Translate contentKey="fintrackApp.financialSubscription.updatedAt">Updated At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('updatedAt')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialSubscription.user">User</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialSubscription.account">Account</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialSubscription.category">Category</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialSubscription.tags">Tags</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {financialSubscriptionList.map((financialSubscription, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/financial-subscription/${financialSubscription.id}`} color="link" size="sm">
                      {financialSubscription.id}
                    </Button>
                  </td>
                  <td>{financialSubscription.name}</td>
                  <td>{financialSubscription.description}</td>
                  <td>
                    <Translate contentKey={`fintrackApp.SubscriptionStatus.${financialSubscription.status}`} />
                  </td>
                  <td>{financialSubscription.expectedAmount}</td>
                  <td>{financialSubscription.amountTolerancePercentage}</td>
                  <td>
                    <Translate contentKey={`fintrackApp.CurrencyCode.${financialSubscription.currency}`} />
                  </td>
                  <td>
                    <Translate contentKey={`fintrackApp.RecurrenceUnit.${financialSubscription.recurrenceUnit}`} />
                  </td>
                  <td>{financialSubscription.intervalCount}</td>
                  <td>
                    {financialSubscription.startDate ? (
                      <TextFormat type="date" value={financialSubscription.startDate} format={APP_LOCAL_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {financialSubscription.nextExpectedDate ? (
                      <TextFormat type="date" value={financialSubscription.nextExpectedDate} format={APP_LOCAL_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {financialSubscription.endDate ? (
                      <TextFormat type="date" value={financialSubscription.endDate} format={APP_LOCAL_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>{financialSubscription.automaticPayment ? 'true' : 'false'}</td>
                  <td>{financialSubscription.notes}</td>
                  <td>
                    {financialSubscription.createdAt ? (
                      <TextFormat type="date" value={financialSubscription.createdAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {financialSubscription.updatedAt ? (
                      <TextFormat type="date" value={financialSubscription.updatedAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>{financialSubscription.user ? financialSubscription.user.login : ''}</td>
                  <td>
                    {financialSubscription.account ? (
                      <Link to={`/financial-account/${financialSubscription.account.id}`}>{financialSubscription.account.name}</Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td>
                    {financialSubscription.category ? (
                      <Link to={`/category/${financialSubscription.category.id}`}>{financialSubscription.category.name}</Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td>
                    {financialSubscription.tags
                      ? financialSubscription.tags.map((val, j) => (
                          <span key={j}>
                            <Link to={`/tag/${val.id}`}>{val.name}</Link>
                            {j === financialSubscription.tags.length - 1 ? '' : ', '}
                          </span>
                        ))
                      : null}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button
                        tag={Link}
                        to={`/financial-subscription/${financialSubscription.id}`}
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
                        to={`/financial-subscription/${financialSubscription.id}/edit`}
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
                        onClick={() => (window.location.href = `/financial-subscription/${financialSubscription.id}/delete`)}
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
              <Translate contentKey="fintrackApp.financialSubscription.home.notFound">No Financial Subscriptions found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default FinancialSubscription;
