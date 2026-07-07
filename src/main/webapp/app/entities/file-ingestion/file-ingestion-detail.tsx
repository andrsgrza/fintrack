import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT, APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './file-ingestion.reducer';

export const FileIngestionDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const fileIngestionEntity = useAppSelector(state => state.fileIngestion.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="fileIngestionDetailsHeading">
          <Translate contentKey="fintrackApp.fileIngestion.detail.title">FileIngestion</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{fileIngestionEntity.id}</dd>
          <dt>
            <span id="originalFilename">
              <Translate contentKey="fintrackApp.fileIngestion.originalFilename">Original Filename</Translate>
            </span>
          </dt>
          <dd>{fileIngestionEntity.originalFilename}</dd>
          <dt>
            <span id="fileType">
              <Translate contentKey="fintrackApp.fileIngestion.fileType">File Type</Translate>
            </span>
          </dt>
          <dd>{fileIngestionEntity.fileType}</dd>
          <dt>
            <span id="contentType">
              <Translate contentKey="fintrackApp.fileIngestion.contentType">Content Type</Translate>
            </span>
          </dt>
          <dd>{fileIngestionEntity.contentType}</dd>
          <dt>
            <span id="fileSizeBytes">
              <Translate contentKey="fintrackApp.fileIngestion.fileSizeBytes">File Size Bytes</Translate>
            </span>
          </dt>
          <dd>{fileIngestionEntity.fileSizeBytes}</dd>
          <dt>
            <span id="checksum">
              <Translate contentKey="fintrackApp.fileIngestion.checksum">Checksum</Translate>
            </span>
          </dt>
          <dd>{fileIngestionEntity.checksum}</dd>
          <dt>
            <span id="storageKey">
              <Translate contentKey="fintrackApp.fileIngestion.storageKey">Storage Key</Translate>
            </span>
          </dt>
          <dd>{fileIngestionEntity.storageKey}</dd>
          <dt>
            <span id="parserName">
              <Translate contentKey="fintrackApp.fileIngestion.parserName">Parser Name</Translate>
            </span>
          </dt>
          <dd>{fileIngestionEntity.parserName}</dd>
          <dt>
            <span id="parserVersion">
              <Translate contentKey="fintrackApp.fileIngestion.parserVersion">Parser Version</Translate>
            </span>
          </dt>
          <dd>{fileIngestionEntity.parserVersion}</dd>
          <dt>
            <span id="statementStartDate">
              <Translate contentKey="fintrackApp.fileIngestion.statementStartDate">Statement Start Date</Translate>
            </span>
          </dt>
          <dd>
            {fileIngestionEntity.statementStartDate ? (
              <TextFormat value={fileIngestionEntity.statementStartDate} type="date" format={APP_LOCAL_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="statementEndDate">
              <Translate contentKey="fintrackApp.fileIngestion.statementEndDate">Statement End Date</Translate>
            </span>
          </dt>
          <dd>
            {fileIngestionEntity.statementEndDate ? (
              <TextFormat value={fileIngestionEntity.statementEndDate} type="date" format={APP_LOCAL_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.fileIngestion.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>
            {fileIngestionEntity.createdAt ? (
              <TextFormat value={fileIngestionEntity.createdAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.fileIngestion.transactionIngestion">Transaction Ingestion</Translate>
          </dt>
          <dd>{fileIngestionEntity.transactionIngestion ? fileIngestionEntity.transactionIngestion.id : ''}</dd>
        </dl>
        <Button tag={Link} to="/file-ingestion" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/file-ingestion/${fileIngestionEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default FileIngestionDetail;
