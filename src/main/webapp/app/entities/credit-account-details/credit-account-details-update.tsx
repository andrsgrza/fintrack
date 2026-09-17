import React from 'react';
import { Link } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import CreditAccountDetailsTechnicalNotice from './credit-account-details-technical-notice';

/**
 * The direct child route is deliberately retained for compatibility, but it
 * must not offer a second write path outside FinancialAccount configured save.
 */
export const CreditAccountDetailsUpdate = () => {
  return (
    <Row className="justify-content-center">
      <Col md="8">
        <CreditAccountDetailsTechnicalNotice writeUnavailable />
        <h2 data-cy="CreditAccountDetailsCreateUpdateHeading">
          <Translate contentKey="fintrackApp.creditAccountDetails.technical.writeUnavailableTitle">Direct editing is unavailable</Translate>
        </h2>
        <p>
          <Translate contentKey="fintrackApp.creditAccountDetails.technical.manageFromAccount">
            Create or edit the parent credit card account to manage these details.
          </Translate>
        </p>
        <Button tag={Link} to="/financial-account" color="primary" data-cy="creditAccountDetailsManageAccountsButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="fintrackApp.creditAccountDetails.technical.goToAccounts">Go to accounts</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default CreditAccountDetailsUpdate;
