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

import { getEntities } from './file-ingestion.reducer';

export const FileIngestion = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const fileIngestionList = useAppSelector(state => state.fileIngestion.entities);
  const loading = useAppSelector(state => state.fileIngestion.loading);

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
      <h2 id="file-ingestion-heading" data-cy="FileIngestionHeading">
        <Translate contentKey="fintrackApp.fileIngestion.home.title">File Ingestions</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="fintrackApp.fileIngestion.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/file-ingestion/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.fileIngestion.home.createLabel">Create new File Ingestion</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {fileIngestionList && fileIngestionList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="fintrackApp.fileIngestion.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('originalFilename')}>
                  <Translate contentKey="fintrackApp.fileIngestion.originalFilename">Original Filename</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('originalFilename')} />
                </th>
                <th className="hand" onClick={sort('fileType')}>
                  <Translate contentKey="fintrackApp.fileIngestion.fileType">File Type</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('fileType')} />
                </th>
                <th className="hand" onClick={sort('contentType')}>
                  <Translate contentKey="fintrackApp.fileIngestion.contentType">Content Type</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('contentType')} />
                </th>
                <th className="hand" onClick={sort('fileSizeBytes')}>
                  <Translate contentKey="fintrackApp.fileIngestion.fileSizeBytes">File Size Bytes</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('fileSizeBytes')} />
                </th>
                <th className="hand" onClick={sort('checksum')}>
                  <Translate contentKey="fintrackApp.fileIngestion.checksum">Checksum</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('checksum')} />
                </th>
                <th className="hand" onClick={sort('storageKey')}>
                  <Translate contentKey="fintrackApp.fileIngestion.storageKey">Storage Key</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('storageKey')} />
                </th>
                <th className="hand" onClick={sort('parserName')}>
                  <Translate contentKey="fintrackApp.fileIngestion.parserName">Parser Name</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('parserName')} />
                </th>
                <th className="hand" onClick={sort('parserVersion')}>
                  <Translate contentKey="fintrackApp.fileIngestion.parserVersion">Parser Version</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('parserVersion')} />
                </th>
                <th className="hand" onClick={sort('statementStartDate')}>
                  <Translate contentKey="fintrackApp.fileIngestion.statementStartDate">Statement Start Date</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('statementStartDate')} />
                </th>
                <th className="hand" onClick={sort('statementEndDate')}>
                  <Translate contentKey="fintrackApp.fileIngestion.statementEndDate">Statement End Date</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('statementEndDate')} />
                </th>
                <th className="hand" onClick={sort('createdAt')}>
                  <Translate contentKey="fintrackApp.fileIngestion.createdAt">Created At</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('createdAt')} />
                </th>
                <th>
                  <Translate contentKey="fintrackApp.fileIngestion.transactionIngestion">Transaction Ingestion</Translate>{' '}
                  <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {fileIngestionList.map((fileIngestion, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/file-ingestion/${fileIngestion.id}`} color="link" size="sm">
                      {fileIngestion.id}
                    </Button>
                  </td>
                  <td>{fileIngestion.originalFilename}</td>
                  <td>
                    <Translate contentKey={`fintrackApp.ImportFileType.${fileIngestion.fileType}`} />
                  </td>
                  <td>{fileIngestion.contentType}</td>
                  <td>{fileIngestion.fileSizeBytes}</td>
                  <td>{fileIngestion.checksum}</td>
                  <td>{fileIngestion.storageKey}</td>
                  <td>{fileIngestion.parserName}</td>
                  <td>{fileIngestion.parserVersion}</td>
                  <td>
                    {fileIngestion.statementStartDate ? (
                      <TextFormat type="date" value={fileIngestion.statementStartDate} format={APP_LOCAL_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {fileIngestion.statementEndDate ? (
                      <TextFormat type="date" value={fileIngestion.statementEndDate} format={APP_LOCAL_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>
                    {fileIngestion.createdAt ? <TextFormat type="date" value={fileIngestion.createdAt} format={APP_DATE_FORMAT} /> : null}
                  </td>
                  <td>
                    {fileIngestion.transactionIngestion ? (
                      <Link to={`/transaction-ingestion/${fileIngestion.transactionIngestion.id}`}>
                        {fileIngestion.transactionIngestion.id}
                      </Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button tag={Link} to={`/file-ingestion/${fileIngestion.id}`} color="info" size="sm" data-cy="entityDetailsButton">
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      <Button
                        tag={Link}
                        to={`/file-ingestion/${fileIngestion.id}/edit`}
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
                        onClick={() => (window.location.href = `/file-ingestion/${fileIngestion.id}/delete`)}
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
              <Translate contentKey="fintrackApp.fileIngestion.home.notFound">No File Ingestions found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default FileIngestion;
