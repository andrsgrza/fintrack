import React from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

export const ApiIngestionWriteUnavailable = () => (
  <Row>
    <Col md="8">
      <h2 data-cy="apiIngestionWriteUnavailableHeading">
        <Translate contentKey="fintrackApp.apiIngestion.writeUnavailableTitle">Api Ingestion technical view</Translate>
      </h2>
      <Alert color="secondary" fade={false} data-cy="technicalViewBanner">
        <Translate contentKey="fintrackApp.apiIngestion.technicalView">
          Technical view — API ingestion product flow is deferred. This page is metadata/debug only.
        </Translate>
      </Alert>
      <Alert color="warning" fade={false} data-cy="writeUnavailableBanner">
        <Translate contentKey="fintrackApp.apiIngestion.writeUnavailable">
          Api Ingestion create, edit, and delete are not product actions yet. API ingestion metadata is managed by future API ingestion
          workflow commands.
        </Translate>
      </Alert>
      <Button tag={Link} to="/api-ingestion" replace color="info" data-cy="entityDetailsBackButton">
        <FontAwesomeIcon icon="arrow-left" />{' '}
        <span className="d-none d-md-inline">
          <Translate contentKey="entity.action.back">Back</Translate>
        </span>
      </Button>
    </Col>
  </Row>
);

export default ApiIngestionWriteUnavailable;
