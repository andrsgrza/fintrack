import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import CreditAccountDetailsTechnicalNotice from './credit-account-details-technical-notice';

export const CreditAccountDetailsDeleteDialog = () => {
  const navigate = useNavigate();

  const handleClose = () => {
    navigate('/credit-account-details');
  };

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose} data-cy="creditAccountDetailsDeleteDialogHeading">
        <Translate contentKey="fintrackApp.creditAccountDetails.delete.title">Cannot delete credit card details</Translate>
      </ModalHeader>
      <ModalBody id="fintrackApp.creditAccountDetails.delete.message" data-cy="creditAccountDetailsDeleteExplanation">
        <CreditAccountDetailsTechnicalNotice />
        <Translate contentKey="fintrackApp.creditAccountDetails.delete.message">
          Credit card details cannot be deleted independently. Manage the parent account instead.
        </Translate>
      </ModalBody>
      <ModalFooter>
        <Button color="primary" onClick={handleClose} data-cy="creditAccountDetailsDeleteCloseButton">
          <FontAwesomeIcon icon="ban" />
          &nbsp;
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default CreditAccountDetailsDeleteDialog;
