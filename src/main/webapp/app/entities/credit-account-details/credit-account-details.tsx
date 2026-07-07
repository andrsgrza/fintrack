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

import { getEntities } from './credit-account-details.reducer';

export const CreditAccountDetails = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const creditAccountDetailsList = useAppSelector(state => state.creditAccountDetails.entities);
  const loading = useAppSelector(state => state.creditAccountDetails.loading);

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
      <h2 id="credit-account-details-heading" data-cy="CreditAccountDetailsHeading">
        <Translate contentKey="fintrackApp.creditAccountDetails.home.title">Credit Account Details</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.creditAccountDetails.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link
            to="/credit-account-details/new"
            className="btn btn-primary jh-create-entity"
            id="jh-create-entity"
            data-cy="entityCreateButton"
          >
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.creditAccountDetails.home.createLabel">Create new Credit Account Details</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {creditAccountDetailsList && creditAccountDetailsList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="fintrackApp.creditAccountDetails.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('creditLimit')}>
                  <Translate contentKey="fintrackApp.creditAccountDetails.creditLimit">Credit Limit</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('creditLimit')} />
                </th>
                <th className="hand" onClick={sort('statementDay')}>
                  <Translate contentKey="fintrackApp.creditAccountDetails.statementDay">Statement Day</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('statementDay')} />
                </th>
                <th className="hand" onClick={sort('paymentDueDay')}>
                  <Translate contentKey="fintrackApp.creditAccountDetails.paymentDueDay">Payment Due Day</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('paymentDueDay')} />
                </th>
                <th className="hand" onClick={sort('annualInterestRate')}>
                  <Translate contentKey="fintrackApp.creditAccountDetails.annualInterestRate">Annual Interest Rate</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('annualInterestRate')} />
                </th>
                <th className="hand" onClick={sort('createdAt')}>
                  <Translate contentKey="fintrackApp.creditAccountDetails.createdAt">Created At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('createdAt')} />
                </th>
                <th className="hand" onClick={sort('updatedAt')}>
                  <Translate contentKey="fintrackApp.creditAccountDetails.updatedAt">Updated At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('updatedAt')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.creditAccountDetails.account">Account</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {creditAccountDetailsList.map((creditAccountDetails, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/credit-account-details/${creditAccountDetails.id}`} color="link" size="sm">
                      {creditAccountDetails.id}
                    </Button>
                  </td>
                  <td>{creditAccountDetails.creditLimit}</td>
                  <td>{creditAccountDetails.statementDay}</td>
                  <td>{creditAccountDetails.paymentDueDay}</td>
                  <td>{creditAccountDetails.annualInterestRate}</td>
                  <td>
                    {creditAccountDetails.createdAt ? (
                      <TextFormat type="date" value={creditAccountDetails.createdAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {creditAccountDetails.updatedAt ? (
                      <TextFormat type="date" value={creditAccountDetails.updatedAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {creditAccountDetails.account ? (
                      <Link to={`/financial-account/${creditAccountDetails.account.id}`}>{creditAccountDetails.account.name}</Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button
                        tag={Link}
                        to={`/credit-account-details/${creditAccountDetails.id}`}
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
                        to={`/credit-account-details/${creditAccountDetails.id}/edit`}
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
                        onClick={() => (window.location.href = `/credit-account-details/${creditAccountDetails.id}/delete`)}
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
              <Translate contentKey="fintrackApp.creditAccountDetails.home.notFound">No Credit Account Details found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default CreditAccountDetails;
