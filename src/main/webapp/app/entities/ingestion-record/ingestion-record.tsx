import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, Table } from 'reactstrap';
import { JhiItemCount, JhiPagination, TextFormat, Translate, getPaginationState } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { APP_DATE_FORMAT } from 'app/config/constants';
import { ASC, DESC, ITEMS_PER_PAGE, SORT } from 'app/shared/util/pagination.constants';
import { overridePaginationStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './ingestion-record.reducer';

export const IngestionRecord = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [paginationState, setPaginationState] = useState(
    overridePaginationStateWithQueryParams(getPaginationState(pageLocation, ITEMS_PER_PAGE, 'id'), pageLocation.search),
  );

  const ingestionRecordList = useAppSelector(state => state.ingestionRecord.entities);
  const loading = useAppSelector(state => state.ingestionRecord.loading);
  const totalItems = useAppSelector(state => state.ingestionRecord.totalItems);

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
      <h2 id="ingestion-record-heading" data-cy="IngestionRecordHeading">
        <Translate contentKey="fintrackApp.ingestionRecord.home.title">Ingestion Records</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.ingestionRecord.home.refreshListLabel">Refresh List</Translate>
          </Button>
        </div>
      </h2>
      <Alert color="secondary" fade={false} data-cy="technicalViewBanner">
        <Translate contentKey="fintrackApp.ingestionRecord.technicalView">
          Technical view — ingestion rows are managed from the Transaction Ingestion workflow.
        </Translate>
      </Alert>
      <div className="table-responsive">
        {ingestionRecordList && ingestionRecordList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="fintrackApp.ingestionRecord.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('recordIndex')}>
                  <Translate contentKey="fintrackApp.ingestionRecord.recordIndex">Record Index</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('recordIndex')} />
                </th>
                <th className="hand" onClick={sort('externalRecordId')}>
                  <Translate contentKey="fintrackApp.ingestionRecord.externalRecordId">External Record Id</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('externalRecordId')} />
                </th>
                <th className="hand" onClick={sort('status')}>
                  <Translate contentKey="fintrackApp.ingestionRecord.status">Status</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('status')} />
                </th>
                <th className="hand" onClick={sort('rawData')}>
                  <Translate contentKey="fintrackApp.ingestionRecord.rawData">Raw Data</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('rawData')} />
                </th>
                <th className="hand" onClick={sort('errorCode')}>
                  <Translate contentKey="fintrackApp.ingestionRecord.errorCode">Error Code</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('errorCode')} />
                </th>
                <th className="hand" onClick={sort('errorMessage')}>
                  <Translate contentKey="fintrackApp.ingestionRecord.errorMessage">Error Message</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('errorMessage')} />
                </th>
                <th className="hand" onClick={sort('createdAt')}>
                  <Translate contentKey="fintrackApp.ingestionRecord.createdAt">Created At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('createdAt')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.ingestionRecord.financialTransaction">Financial Transaction</Translate>{' '}
                  <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.ingestionRecord.transactionIngestion">Transaction Ingestion</Translate>{' '}
                  <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {ingestionRecordList.map((ingestionRecord, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/ingestion-record/${ingestionRecord.id}`} color="link" size="sm">
                      {ingestionRecord.id}
                    </Button>
                  </td>
                  <td>{ingestionRecord.recordIndex}</td>
                  <td>{ingestionRecord.externalRecordId}</td>
                  <td>
                    <Translate contentKey={`fintrackApp.IngestionRecordStatus.${ingestionRecord.status}`} />
                  </td>
                  <td>{ingestionRecord.rawData}</td>
                  <td>{ingestionRecord.errorCode}</td>
                  <td>{ingestionRecord.errorMessage}</td>
                  <td>
                    {ingestionRecord.createdAt ? (
                      <TextFormat type="date" value={ingestionRecord.createdAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {ingestionRecord.financialTransaction ? (
                      <Link to={`/financial-transaction/${ingestionRecord.financialTransaction.id}`}>
                        {ingestionRecord.financialTransaction.id}
                      </Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td>
                    {ingestionRecord.transactionIngestion ? (
                      <Link to={`/transaction-ingestion/${ingestionRecord.transactionIngestion.id}`}>
                        {ingestionRecord.transactionIngestion.id}
                      </Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button
                        tag={Link}
                        to={`/ingestion-record/${ingestionRecord.id}`}
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
              <Translate contentKey="fintrackApp.ingestionRecord.home.notFound">No Ingestion Records found</Translate>
            </div>
          )
        )}
      </div>
      {totalItems ? (
        <div className={ingestionRecordList && ingestionRecordList.length > 0 ? '' : 'd-none'}>
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

export default IngestionRecord;
