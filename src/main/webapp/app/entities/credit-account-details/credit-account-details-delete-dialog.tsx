import React, { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch } from 'app/config/store';
import { getEntity } from './credit-account-details.reducer';

export const CreditAccountDetailsDeleteDialog = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const handleClose = () => {
    navigate('/credit-account-details');
  };

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose} data-cy="creditAccountDetailsDeleteDialogHeading">
        <Translate contentKey="fintrackApp.creditAccountDetails.delete.title">Cannot delete credit card details</Translate>
      </ModalHeader>
      <ModalBody id="fintrackApp.creditAccountDetails.delete.message" data-cy="creditAccountDetailsDeleteExplanation">
        <Translate contentKey="fintrackApp.creditAccountDetails.delete.message">
          Credit card details cannot be deleted directly. These details are required for credit card accounts. To remove them, you must
          delete the financial account itself.
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
