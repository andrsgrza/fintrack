import React from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

interface TechnicalWriteUnavailableProps {
  backTo: string;
  dataCyPrefix: string;
  technicalViewContentKey: string;
  technicalViewDefault: string;
  titleContentKey?: string;
  titleDefault?: string;
  messageContentKey?: string;
  messageDefault?: string;
}

export const TechnicalWriteUnavailable = ({
  backTo,
  dataCyPrefix,
  technicalViewContentKey,
  technicalViewDefault,
  titleContentKey = 'entity.technicalWriteUnavailable.title',
  titleDefault = 'Technical write action unavailable',
  messageContentKey = 'entity.technicalWriteUnavailable.message',
  messageDefault = 'This technical metadata page is read-only. Use the ingestion workflow instead.',
}: TechnicalWriteUnavailableProps) => (
  <Row>
    <Col md="8">
      <h2 data-cy={`${dataCyPrefix}WriteUnavailableHeading`}>
        <Translate contentKey={titleContentKey}>{titleDefault}</Translate>
      </h2>
      <Alert color="secondary" fade={false} data-cy="technicalViewBanner">
        <Translate contentKey={technicalViewContentKey}>{technicalViewDefault}</Translate>
      </Alert>
      <Alert color="warning" fade={false} data-cy="writeUnavailableBanner">
        <Translate contentKey={messageContentKey}>{messageDefault}</Translate>
      </Alert>
      <Button tag={Link} to={backTo} replace color="info" data-cy="entityDetailsBackButton">
        <FontAwesomeIcon icon="arrow-left" />{' '}
        <span className="d-none d-md-inline">
          <Translate contentKey="entity.action.back">Back</Translate>
        </span>
      </Button>
    </Col>
  </Row>
);

export default TechnicalWriteUnavailable;
