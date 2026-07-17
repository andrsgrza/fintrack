import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { JhiItemCount, JhiPagination, TextFormat, Translate, getPaginationState } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { APP_DATE_FORMAT } from 'app/config/constants';
import { ASC, DESC, ITEMS_PER_PAGE, SORT } from 'app/shared/util/pagination.constants';
import { overridePaginationStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './transaction-ingestion.reducer';

export const TransactionIngestion = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [paginationState, setPaginationState] = useState(
    overridePaginationStateWithQueryParams(getPaginationState(pageLocation, ITEMS_PER_PAGE, 'id'), pageLocation.search),
  );

  const transactionIngestionList = useAppSelector(state => state.transactionIngestion.entities);
  const loading = useAppSelector(state => state.transactionIngestion.loading);
  const totalItems = useAppSelector(state => state.transactionIngestion.totalItems);

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
      <h2 id="transaction-ingestion-heading" data-cy="TransactionIngestionHeading">
        <Translate contentKey="fintrackApp.transactionIngestion.home.title">Transaction Ingestions</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.transactionIngestion.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link
            to="/transaction-ingestion/new"
            className="btn btn-primary jh-create-entity"
            id="jh-create-entity"
            data-cy="entityCreateButton"
          >
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.transactionIngestion.home.createLabel">Create new Transaction Ingestion</Translate>
          </Link>
          <Link to="/transaction-ingestion/file-preview/new" className="btn btn-primary jh-create-entity ms-2" data-cy="fileImportButton">
            <FontAwesomeIcon icon="cloud" />
            &nbsp;
            <Translate contentKey="fintrackApp.transactionIngestion.home.newFileImportLabel">New File Import</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {transactionIngestionList && transactionIngestionList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="fintrackApp.transactionIngestion.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('ingestionType')}>
                  <Translate contentKey="fintrackApp.transactionIngestion.ingestionType">Ingestion Type</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('ingestionType')} />
                </th>
                <th className="hand" onClick={sort('status')}>
                  <Translate contentKey="fintrackApp.transactionIngestion.status">Status</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('status')} />
                </th>
                <th className="hand" onClick={sort('sourceLabel')}>
                  <Translate contentKey="fintrackApp.transactionIngestion.sourceLabel">Source Label</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('sourceLabel')} />
                </th>
                <th className="hand" onClick={sort('startedAt')}>
                  <Translate contentKey="fintrackApp.transactionIngestion.startedAt">Started At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('startedAt')} />
                </th>
                <th className="hand" onClick={sort('completedAt')}>
                  <Translate contentKey="fintrackApp.transactionIngestion.completedAt">Completed At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('completedAt')} />
                </th>
                <th className="hand" onClick={sort('recordsReceived')}>
                  <Translate contentKey="fintrackApp.transactionIngestion.recordsReceived">Records Received</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('recordsReceived')} />
                </th>
                <th className="hand" onClick={sort('recordsCreated')}>
                  <Translate contentKey="fintrackApp.transactionIngestion.recordsCreated">Records Created</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('recordsCreated')} />
                </th>
                <th className="hand" onClick={sort('recordsSkipped')}>
                  <Translate contentKey="fintrackApp.transactionIngestion.recordsSkipped">Records Skipped</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('recordsSkipped')} />
                </th>
                <th className="hand" onClick={sort('recordsRejected')}>
                  <Translate contentKey="fintrackApp.transactionIngestion.recordsRejected">Records Rejected</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('recordsRejected')} />
                </th>
                <th className="hand" onClick={sort('errorMessage')}>
                  <Translate contentKey="fintrackApp.transactionIngestion.errorMessage">Error Message</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('errorMessage')} />
                </th>
                <th className="hand" onClick={sort('createdAt')}>
                  <Translate contentKey="fintrackApp.transactionIngestion.createdAt">Created At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('createdAt')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.transactionIngestion.account">Account</Translate>
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {transactionIngestionList.map((transactionIngestion, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/transaction-ingestion/${transactionIngestion.id}`} color="link" size="sm">
                      {transactionIngestion.id}
                    </Button>
                  </td>
                  <td>
                    <Translate contentKey={`fintrackApp.IngestionType.${transactionIngestion.ingestionType}`} />
                  </td>
                  <td>
                    <Translate contentKey={`fintrackApp.IngestionStatus.${transactionIngestion.status}`} />
                  </td>
                  <td>{transactionIngestion.sourceLabel}</td>
                  <td>
                    {transactionIngestion.startedAt ? (
                      <TextFormat type="date" value={transactionIngestion.startedAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {transactionIngestion.completedAt ? (
                      <TextFormat type="date" value={transactionIngestion.completedAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>{transactionIngestion.recordsReceived}</td>
                  <td>{transactionIngestion.recordsCreated}</td>
                  <td>{transactionIngestion.recordsSkipped}</td>
                  <td>{transactionIngestion.recordsRejected}</td>
                  <td>{transactionIngestion.errorMessage}</td>
                  <td>
                    {transactionIngestion.createdAt ? (
                      <TextFormat type="date" value={transactionIngestion.createdAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {transactionIngestion.account ? (
                      <Link to={`/financial-account/${transactionIngestion.account.id}`}>{transactionIngestion.account.name}</Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button
                        tag={Link}
                        to={`/transaction-ingestion/${transactionIngestion.id}`}
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
                        to={`/transaction-ingestion/${transactionIngestion.id}/edit?page=${paginationState.activePage}&sort=${paginationState.sort},${paginationState.order}`}
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
                          (window.location.href = `/transaction-ingestion/${transactionIngestion.id}/delete?page=${paginationState.activePage}&sort=${paginationState.sort},${paginationState.order}`)
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
              <Translate contentKey="fintrackApp.transactionIngestion.home.notFound">No Transaction Ingestions found</Translate>
            </div>
          )
        )}
      </div>
      {totalItems ? (
        <div className={transactionIngestionList && transactionIngestionList.length > 0 ? '' : 'd-none'}>
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

export default TransactionIngestion;
