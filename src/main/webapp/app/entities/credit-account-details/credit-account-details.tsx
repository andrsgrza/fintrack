import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { Translate, getSortState } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { ASC, DESC } from 'app/shared/util/pagination.constants';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './credit-account-details.reducer';
import CreditAccountDetailsTechnicalNotice from './credit-account-details-technical-notice';

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
      <CreditAccountDetailsTechnicalNotice />
      <h2 id="credit-account-details-heading" data-cy="CreditAccountDetailsHeading">
        <Translate contentKey="fintrackApp.creditAccountDetails.home.title">Technical credit card details</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.creditAccountDetails.home.refreshListLabel">Refresh List</Translate>
          </Button>
        </div>
      </h2>
      <div className="table-responsive">
        {creditAccountDetailsList && creditAccountDetailsList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th>
                  <Translate contentKey="fintrackApp.creditAccountDetails.account">Account</Translate>
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
                <th />
              </tr>
            </thead>
            <tbody>
              {creditAccountDetailsList.map((creditAccountDetails, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    {creditAccountDetails.account ? (
                      <Link to={`/financial-account/${creditAccountDetails.account.id}`}>{creditAccountDetails.account.name}</Link>
                    ) : null}
                  </td>
                  <td>{creditAccountDetails.creditLimit}</td>
                  <td>{creditAccountDetails.statementDay}</td>
                  <td>{creditAccountDetails.paymentDueDay}</td>
                  <td>{creditAccountDetails.annualInterestRate}</td>
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
