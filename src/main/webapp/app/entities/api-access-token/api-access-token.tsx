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

import { getEntities } from './api-access-token.reducer';

export const ApiAccessToken = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const apiAccessTokenList = useAppSelector(state => state.apiAccessToken.entities);
  const loading = useAppSelector(state => state.apiAccessToken.loading);

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
      <h2 id="api-access-token-heading" data-cy="ApiAccessTokenHeading">
        <Translate contentKey="fintrackApp.apiAccessToken.home.title">Api Access Tokens</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.apiAccessToken.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/api-access-token/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.apiAccessToken.home.createLabel">Create new Api Access Token</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {apiAccessTokenList && apiAccessTokenList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="fintrackApp.apiAccessToken.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('name')}>
                  <Translate contentKey="fintrackApp.apiAccessToken.name">Name</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('name')} />
                </th>
                <th className="hand" onClick={sort('tokenPrefix')}>
                  <Translate contentKey="fintrackApp.apiAccessToken.tokenPrefix">Token Prefix</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('tokenPrefix')} />
                </th>
                <th className="hand" onClick={sort('status')}>
                  <Translate contentKey="fintrackApp.apiAccessToken.status">Status</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('status')} />
                </th>
                <th className="hand" onClick={sort('createdAt')}>
                  <Translate contentKey="fintrackApp.apiAccessToken.createdAt">Created At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('createdAt')} />
                </th>
                <th className="hand" onClick={sort('updatedAt')}>
                  <Translate contentKey="fintrackApp.apiAccessToken.updatedAt">Updated At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('updatedAt')} />
                </th>
                <th className="hand" onClick={sort('lastUsedAt')}>
                  <Translate contentKey="fintrackApp.apiAccessToken.lastUsedAt">Last Used At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('lastUsedAt')} />
                </th>
                <th className="hand" onClick={sort('expiresAt')}>
                  <Translate contentKey="fintrackApp.apiAccessToken.expiresAt">Expires At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('expiresAt')} />
                </th>
                <th className="hand" onClick={sort('revokedAt')}>
                  <Translate contentKey="fintrackApp.apiAccessToken.revokedAt">Revoked At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('revokedAt')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.apiAccessToken.user">User</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {apiAccessTokenList.map((apiAccessToken, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/api-access-token/${apiAccessToken.id}`} color="link" size="sm">
                      {apiAccessToken.id}
                    </Button>
                  </td>
                  <td>{apiAccessToken.name}</td>
                  <td>{apiAccessToken.tokenPrefix}</td>
                  <td>
                    <Translate contentKey={`fintrackApp.ApiTokenStatus.${apiAccessToken.status}`} />
                  </td>
                  <td>
                    {apiAccessToken.createdAt ? <TextFormat type="date" value={apiAccessToken.createdAt} format={APP_DATE_FORMAT} /> : null}
                  </td>
                  <td>
                    {apiAccessToken.updatedAt ? <TextFormat type="date" value={apiAccessToken.updatedAt} format={APP_DATE_FORMAT} /> : null}
                  </td>
                  <td>
                    {apiAccessToken.lastUsedAt ? (
                      <TextFormat type="date" value={apiAccessToken.lastUsedAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {apiAccessToken.expiresAt ? <TextFormat type="date" value={apiAccessToken.expiresAt} format={APP_DATE_FORMAT} /> : null}
                  </td>
                  <td>
                    {apiAccessToken.revokedAt ? <TextFormat type="date" value={apiAccessToken.revokedAt} format={APP_DATE_FORMAT} /> : null}
                  </td>
                  <td>{apiAccessToken.user ? apiAccessToken.user.login : ''}</td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button tag={Link} to={`/api-access-token/${apiAccessToken.id}`} color="info" size="sm" data-cy="entityDetailsButton">
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      <Button
                        tag={Link}
                        to={`/api-access-token/${apiAccessToken.id}/edit`}
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
                        onClick={() => (window.location.href = `/api-access-token/${apiAccessToken.id}/delete`)}
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
              <Translate contentKey="fintrackApp.apiAccessToken.home.notFound">No Api Access Tokens found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default ApiAccessToken;
