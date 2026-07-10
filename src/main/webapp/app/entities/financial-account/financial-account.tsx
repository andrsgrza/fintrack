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

import { getEntities } from './financial-account.reducer';

export const FinancialAccount = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const financialAccountList = useAppSelector(state => state.financialAccount.entities);
  const loading = useAppSelector(state => state.financialAccount.loading);

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
      <h2 id="financial-account-heading" data-cy="FinancialAccountHeading">
        <Translate contentKey="fintrackApp.financialAccount.home.title">Financial Accounts</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.financialAccount.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/financial-account/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.financialAccount.home.createLabel">Create new Financial Account</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {financialAccountList && financialAccountList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="fintrackApp.financialAccount.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('name')}>
                  <Translate contentKey="fintrackApp.financialAccount.name">Name</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('name')} />
                </th>
                <th className="hand" onClick={sort('institutionName')}>
                  <Translate contentKey="fintrackApp.financialAccount.institutionName">Institution Name</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('institutionName')} />
                </th>
                <th className="hand" onClick={sort('accountType')}>
                  <Translate contentKey="fintrackApp.financialAccount.accountType">Account Type</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('accountType')} />
                </th>
                <th className="hand" onClick={sort('currency')}>
                  <Translate contentKey="fintrackApp.financialAccount.currency">Currency</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('currency')} />
                </th>
                <th className="hand" onClick={sort('initialBalance')}>
                  <Translate contentKey="fintrackApp.financialAccount.initialBalance">Initial Balance</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('initialBalance')} />
                </th>
                <th className="hand" onClick={sort('initialBalanceDate')}>
                  <Translate contentKey="fintrackApp.financialAccount.initialBalanceDate">Initial Balance Date</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('initialBalanceDate')} />
                </th>
                <th className="hand" onClick={sort('lastFourDigits')}>
                  <Translate contentKey="fintrackApp.financialAccount.lastFourDigits">Last Four Digits</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('lastFourDigits')} />
                </th>
                <th className="hand" onClick={sort('description')}>
                  <Translate contentKey="fintrackApp.financialAccount.description">Description</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('description')} />
                </th>
                <th className="hand" onClick={sort('color')}>
                  <Translate contentKey="fintrackApp.financialAccount.color">Color</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('color')} />
                </th>
                <th className="hand" onClick={sort('icon')}>
                  <Translate contentKey="fintrackApp.financialAccount.icon">Icon</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('icon')} />
                </th>
                <th className="hand" onClick={sort('active')}>
                  <Translate contentKey="fintrackApp.financialAccount.active">Active</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('active')} />
                </th>
                <th className="hand" onClick={sort('createdAt')}>
                  <Translate contentKey="fintrackApp.financialAccount.createdAt">Created At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('createdAt')} />
                </th>
                <th className="hand" onClick={sort('updatedAt')}>
                  <Translate contentKey="fintrackApp.financialAccount.updatedAt">Updated At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('updatedAt')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialAccount.budgets">Budgets</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialAccount.transactionIngestions">Transaction Ingestions</Translate>{' '}
                  <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {financialAccountList.map((financialAccount, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/financial-account/${financialAccount.id}`} color="link" size="sm">
                      {financialAccount.id}
                    </Button>
                  </td>
                  <td>{financialAccount.name}</td>
                  <td>{financialAccount.institutionName}</td>
                  <td>
                    <Translate contentKey={`fintrackApp.AccountType.${financialAccount.accountType}`} />
                  </td>
                  <td>
                    <Translate contentKey={`fintrackApp.CurrencyCode.${financialAccount.currency}`} />
                  </td>
                  <td>{financialAccount.initialBalance}</td>
                  <td>
                    {financialAccount.initialBalanceDate ? (
                      <TextFormat type="date" value={financialAccount.initialBalanceDate} format={APP_LOCAL_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>{financialAccount.lastFourDigits}</td>
                  <td>{financialAccount.description}</td>
                  <td>{financialAccount.color}</td>
                  <td>{financialAccount.icon}</td>
                  <td>{financialAccount.active ? 'true' : 'false'}</td>
                  <td>
                    {financialAccount.createdAt ? (
                      <TextFormat type="date" value={financialAccount.createdAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {financialAccount.updatedAt ? (
                      <TextFormat type="date" value={financialAccount.updatedAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {financialAccount.budgets
                      ? financialAccount.budgets.map((val, j) => (
                          <span key={j}>
                            <Link to={`/budget/${val.id}`}>{val.id}</Link>
                            {j === financialAccount.budgets.length - 1 ? '' : ', '}
                          </span>
                        ))
                      : null}
                  </td>
                  <td>
                    {financialAccount.transactionIngestions
                      ? financialAccount.transactionIngestions.map((val, j) => (
                          <span key={j}>
                            <Link to={`/transaction-ingestion/${val.id}`}>{val.id}</Link>
                            {j === financialAccount.transactionIngestions.length - 1 ? '' : ', '}
                          </span>
                        ))
                      : null}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button
                        tag={Link}
                        to={`/financial-account/${financialAccount.id}`}
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
                        to={`/financial-account/${financialAccount.id}/edit`}
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
                        onClick={() => (window.location.href = `/financial-account/${financialAccount.id}/delete`)}
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
              <Translate contentKey="fintrackApp.financialAccount.home.notFound">No Financial Accounts found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default FinancialAccount;
