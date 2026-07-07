import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { JhiItemCount, JhiPagination, TextFormat, Translate, getPaginationState } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { APP_DATE_FORMAT, APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { ASC, DESC, ITEMS_PER_PAGE, SORT } from 'app/shared/util/pagination.constants';
import { overridePaginationStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './financial-transaction.reducer';

export const FinancialTransaction = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [paginationState, setPaginationState] = useState(
    overridePaginationStateWithQueryParams(getPaginationState(pageLocation, ITEMS_PER_PAGE, 'id'), pageLocation.search),
  );

  const financialTransactionList = useAppSelector(state => state.financialTransaction.entities);
  const loading = useAppSelector(state => state.financialTransaction.loading);
  const totalItems = useAppSelector(state => state.financialTransaction.totalItems);

  const getAllEntities = () => {
    dispatch(
      getEntities({
        page: paginationState.activePage - 1,
        size: paginationState.itemsPerPage,
        sort: `${paginationState.sort},${paginationState.order}`,
      }),
    );
  };

  const sortEntities = () => {
    getAllEntities();
    const endURL = `?page=${paginationState.activePage}&sort=${paginationState.sort},${paginationState.order}`;
    if (pageLocation.search !== endURL) {
      navigate(`${pageLocation.pathname}${endURL}`);
    }
  };

  useEffect(() => {
    sortEntities();
  }, [paginationState.activePage, paginationState.order, paginationState.sort]);

  useEffect(() => {
    const params = new URLSearchParams(pageLocation.search);
    const page = params.get('page');
    const sort = params.get(SORT);
    if (page && sort) {
      const sortSplit = sort.split(',');
      setPaginationState({
        ...paginationState,
        activePage: +page,
        sort: sortSplit[0],
        order: sortSplit[1],
      });
    }
  }, [pageLocation.search]);

  const sort = p => () => {
    setPaginationState({
      ...paginationState,
      order: paginationState.order === ASC ? DESC : ASC,
      sort: p,
    });
  };

  const handlePagination = currentPage =>
    setPaginationState({
      ...paginationState,
      activePage: currentPage,
    });

  const handleSyncList = () => {
    sortEntities();
  };

  const getSortIconByFieldName = (fieldName: string) => {
    const sortFieldName = paginationState.sort;
    const order = paginationState.order;
    if (sortFieldName !== fieldName) {
      return faSort;
    }
    return order === ASC ? faSortUp : faSortDown;
  };

  return (
    <div>
      <h2 id="financial-transaction-heading" data-cy="FinancialTransactionHeading">
        <Translate contentKey="fintrackApp.financialTransaction.home.title">Financial Transactions</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.financialTransaction.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link
            to="/financial-transaction/new"
            className="btn btn-primary jh-create-entity"
            id="jh-create-entity"
            data-cy="entityCreateButton"
          >
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.financialTransaction.home.createLabel">Create new Financial Transaction</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {financialTransactionList && financialTransactionList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="fintrackApp.financialTransaction.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('transactionDate')}>
                  <Translate contentKey="fintrackApp.financialTransaction.transactionDate">Transaction Date</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('transactionDate')} />
                </th>
                <th className="hand" onClick={sort('postingDate')}>
                  <Translate contentKey="fintrackApp.financialTransaction.postingDate">Posting Date</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('postingDate')} />
                </th>
                <th className="hand" onClick={sort('description')}>
                  <Translate contentKey="fintrackApp.financialTransaction.description">Description</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('description')} />
                </th>
                <th className="hand" onClick={sort('amount')}>
                  <Translate contentKey="fintrackApp.financialTransaction.amount">Amount</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('amount')} />
                </th>
                <th className="hand" onClick={sort('flow')}>
                  <Translate contentKey="fintrackApp.financialTransaction.flow">Flow</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('flow')} />
                </th>
                <th className="hand" onClick={sort('origin')}>
                  <Translate contentKey="fintrackApp.financialTransaction.origin">Origin</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('origin')} />
                </th>
                <th className="hand" onClick={sort('externalReference')}>
                  <Translate contentKey="fintrackApp.financialTransaction.externalReference">External Reference</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('externalReference')} />
                </th>
                <th className="hand" onClick={sort('notes')}>
                  <Translate contentKey="fintrackApp.financialTransaction.notes">Notes</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('notes')} />
                </th>
                <th className="hand" onClick={sort('createdAt')}>
                  <Translate contentKey="fintrackApp.financialTransaction.createdAt">Created At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('createdAt')} />
                </th>
                <th className="hand" onClick={sort('updatedAt')}>
                  <Translate contentKey="fintrackApp.financialTransaction.updatedAt">Updated At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('updatedAt')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialTransaction.account">Account</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialTransaction.category">Category</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialTransaction.financialSubscription">Financial Subscription</Translate>{' '}
                  <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialTransaction.transactionIngestion">Transaction Ingestion</Translate>{' '}
                  <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {financialTransactionList.map((financialTransaction, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/financial-transaction/${financialTransaction.id}`} color="link" size="sm">
                      {financialTransaction.id}
                    </Button>
                  </td>
                  <td>
                    {financialTransaction.transactionDate ? (
                      <TextFormat type="date" value={financialTransaction.transactionDate} format={APP_LOCAL_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {financialTransaction.postingDate ? (
                      <TextFormat type="date" value={financialTransaction.postingDate} format={APP_LOCAL_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>{financialTransaction.description}</td>
                  <td>{financialTransaction.amount}</td>
                  <td>
                    <Translate contentKey={`fintrackApp.TransactionFlow.${financialTransaction.flow}`} />
                  </td>
                  <td>
                    <Translate contentKey={`fintrackApp.TransactionOrigin.${financialTransaction.origin}`} />
                  </td>
                  <td>{financialTransaction.externalReference}</td>
                  <td>{financialTransaction.notes}</td>
                  <td>
                    {financialTransaction.createdAt ? (
                      <TextFormat type="date" value={financialTransaction.createdAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {financialTransaction.updatedAt ? (
                      <TextFormat type="date" value={financialTransaction.updatedAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {financialTransaction.account ? (
                      <Link to={`/financial-account/${financialTransaction.account.id}`}>{financialTransaction.account.name}</Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td>
                    {financialTransaction.category ? (
                      <Link to={`/category/${financialTransaction.category.id}`}>{financialTransaction.category.name}</Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td>
                    {financialTransaction.financialSubscription ? (
                      <Link to={`/financial-subscription/${financialTransaction.financialSubscription.id}`}>
                        {financialTransaction.financialSubscription.name}
                      </Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td>
                    {financialTransaction.transactionIngestion ? (
                      <Link to={`/transaction-ingestion/${financialTransaction.transactionIngestion.id}`}>
                        {financialTransaction.transactionIngestion.id}
                      </Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button
                        tag={Link}
                        to={`/financial-transaction/${financialTransaction.id}`}
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
                        to={`/financial-transaction/${financialTransaction.id}/edit?page=${paginationState.activePage}&sort=${paginationState.sort},${paginationState.order}`}
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
                        onClick={() =>
                          (window.location.href = `/financial-transaction/${financialTransaction.id}/delete?page=${paginationState.activePage}&sort=${paginationState.sort},${paginationState.order}`)
                        }
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
              <Translate contentKey="fintrackApp.financialTransaction.home.notFound">No Financial Transactions found</Translate>
            </div>
          )
        )}
      </div>
      {totalItems ? (
        <div className={financialTransactionList && financialTransactionList.length > 0 ? '' : 'd-none'}>
          <div className="justify-content-center d-flex">
            <JhiItemCount page={paginationState.activePage} total={totalItems} itemsPerPage={paginationState.itemsPerPage} i18nEnabled />
          </div>
          <div className="justify-content-center d-flex">
            <JhiPagination
              activePage={paginationState.activePage}
              onSelect={handlePagination}
              maxButtons={5}
              itemsPerPage={paginationState.itemsPerPage}
              totalItems={totalItems}
            />
          </div>
        </div>
      ) : (
        ''
      )}
    </div>
  );
};

export default FinancialTransaction;
