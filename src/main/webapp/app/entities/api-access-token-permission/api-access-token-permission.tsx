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

import { getEntities } from './api-access-token-permission.reducer';

export const ApiAccessTokenPermission = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const apiAccessTokenPermissionList = useAppSelector(state => state.apiAccessTokenPermission.entities);
  const loading = useAppSelector(state => state.apiAccessTokenPermission.loading);

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
      <h2 id="api-access-token-permission-heading" data-cy="ApiAccessTokenPermissionHeading">
        <Translate contentKey="fintrackApp.apiAccessTokenPermission.home.title">Api Access Token Permissions</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.apiAccessTokenPermission.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link
            to="/api-access-token-permission/new"
            className="btn btn-primary jh-create-entity"
            id="jh-create-entity"
            data-cy="entityCreateButton"
          >
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.apiAccessTokenPermission.home.createLabel">Create new Api Access Token Permission</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {apiAccessTokenPermissionList && apiAccessTokenPermissionList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="fintrackApp.apiAccessTokenPermission.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('permission')}>
                  <Translate contentKey="fintrackApp.apiAccessTokenPermission.permission">Permission</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('permission')} />
                </th>
                <th className="hand" onClick={sort('createdAt')}>
                  <Translate contentKey="fintrackApp.apiAccessTokenPermission.createdAt">Created At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('createdAt')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.apiAccessTokenPermission.apiAccessToken">Api Access Token</Translate>{' '}
                  <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {apiAccessTokenPermissionList.map((apiAccessTokenPermission, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/api-access-token-permission/${apiAccessTokenPermission.id}`} color="link" size="sm">
                      {apiAccessTokenPermission.id}
                    </Button>
                  </td>
                  <td>
                    <Translate contentKey={`fintrackApp.ApiPermission.${apiAccessTokenPermission.permission}`} />
                  </td>
                  <td>
                    {apiAccessTokenPermission.createdAt ? (
                      <TextFormat type="date" value={apiAccessTokenPermission.createdAt} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {apiAccessTokenPermission.apiAccessToken ? (
                      <Link to={`/api-access-token/${apiAccessTokenPermission.apiAccessToken.id}`}>
                        {apiAccessTokenPermission.apiAccessToken.name}
                      </Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button
                        tag={Link}
                        to={`/api-access-token-permission/${apiAccessTokenPermission.id}`}
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
                        to={`/api-access-token-permission/${apiAccessTokenPermission.id}/edit`}
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
                        onClick={() => (window.location.href = `/api-access-token-permission/${apiAccessTokenPermission.id}/delete`)}
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
              <Translate contentKey="fintrackApp.apiAccessTokenPermission.home.notFound">No Api Access Token Permissions found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default ApiAccessTokenPermission;
