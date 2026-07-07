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

import { getEntities } from './api-ingestion.reducer';

export const ApiIngestion = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const apiIngestionList = useAppSelector(state => state.apiIngestion.entities);
  const loading = useAppSelector(state => state.apiIngestion.loading);

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
      <h2 id="api-ingestion-heading" data-cy="ApiIngestionHeading">
        <Translate contentKey="fintrackApp.apiIngestion.home.title">Api Ingestions</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.apiIngestion.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/api-ingestion/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.apiIngestion.home.createLabel">Create new Api Ingestion</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {apiIngestionList && apiIngestionList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="fintrackApp.apiIngestion.id">ID</Translate> <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('requestId')}>
                  <Translate contentKey="fintrackApp.apiIngestion.requestId">Request Id</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('requestId')} />
                </th>
                <th className="hand" onClick={sort('idempotencyKey')}>
                  <Translate contentKey="fintrackApp.apiIngestion.idempotencyKey">Idempotency Key</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('idempotencyKey')} />
                </th>
                <th className="hand" onClick={sort('sourceSystem')}>
                  <Translate contentKey="fintrackApp.apiIngestion.sourceSystem">Source System</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('sourceSystem')} />
                </th>
                <th className="hand" onClick={sort('apiVersion')}>
                  <Translate contentKey="fintrackApp.apiIngestion.apiVersion">Api Version</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('apiVersion')} />
                </th>
                <th className="hand" onClick={sort('endpoint')}>
                  <Translate contentKey="fintrackApp.apiIngestion.endpoint">Endpoint</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('endpoint')} />
                </th>
                <th className="hand" onClick={sort('clientReference')}>
                  <Translate contentKey="fintrackApp.apiIngestion.clientReference">Client Reference</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('clientReference')} />
                </th>
                <th className="hand" onClick={sort('receivedAt')}>
                  <Translate contentKey="fintrackApp.apiIngestion.receivedAt">Received At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('receivedAt')} />
                </th>
                <th className="hand" onClick={sort('createdAt')}>
                  <Translate contentKey="fintrackApp.apiIngestion.createdAt">Created At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('createdAt')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.apiIngestion.transactionIngestion">Transaction Ingestion</Translate>{' '}
                  <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.apiIngestion.apiAccessToken">Api Access Token</Translate>{' '}
                  <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {apiIngestionList.map((apiIngestion, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/api-ingestion/${apiIngestion.id}`} color="link" size="sm">
                      {apiIngestion.id}
                    </Button>
                  </td>
                  <td>{apiIngestion.requestId}</td>
                  <td>{apiIngestion.idempotencyKey}</td>
                  <td>{apiIngestion.sourceSystem}</td>
                  <td>{apiIngestion.apiVersion}</td>
                  <td>{apiIngestion.endpoint}</td>
                  <td>{apiIngestion.clientReference}</td>
                  <td>
                    {apiIngestion.receivedAt ? <TextFormat type="date" value={apiIngestion.receivedAt} format={APP_DATE_FORMAT} /> : null}
                  </td>
                  <td>
                    {apiIngestion.createdAt ? <TextFormat type="date" value={apiIngestion.createdAt} format={APP_DATE_FORMAT} /> : null}
                  </td>
                  <td>
                    {apiIngestion.transactionIngestion ? (
                      <Link to={`/transaction-ingestion/${apiIngestion.transactionIngestion.id}`}>
                        {apiIngestion.transactionIngestion.id}
                      </Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td>
                    {apiIngestion.apiAccessToken ? (
                      <Link to={`/api-access-token/${apiIngestion.apiAccessToken.id}`}>{apiIngestion.apiAccessToken.name}</Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button tag={Link} to={`/api-ingestion/${apiIngestion.id}`} color="info" size="sm" data-cy="entityDetailsButton">
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      <Button tag={Link} to={`/api-ingestion/${apiIngestion.id}/edit`} color="primary" size="sm" data-cy="entityEditButton">
                        <FontAwesomeIcon icon="pencil-alt" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.edit">Edit</Translate>
                        </span>
                      </Button>
                      <Button
                        onClick={() => (window.location.href = `/api-ingestion/${apiIngestion.id}/delete`)}
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
              <Translate contentKey="fintrackApp.apiIngestion.home.notFound">No Api Ingestions found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default ApiIngestion;
