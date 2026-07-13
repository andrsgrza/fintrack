import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { deleteEntity, getEntity } from './api-access-token.reducer';

export const ApiAccessTokenDeleteDialog = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();

  const [loadModal, setLoadModal] = useState(false);

  useEffect(() => {
    dispatch(getEntity(id));
    setLoadModal(true);
  }, []);

  const apiAccessTokenEntity = useAppSelector(state => state.apiAccessToken.entity);
  const updateSuccess = useAppSelector(state => state.apiAccessToken.updateSuccess);

  const handleClose = () => {
    navigate('/api-access-token');
  };

  useEffect(() => {
    if (updateSuccess && loadModal) {
      handleClose();
      setLoadModal(false);
    }
  }, [updateSuccess]);

  const confirmDelete = () => {
    dispatch(deleteEntity(apiAccessTokenEntity.id));
  };

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose} data-cy="apiAccessTokenDeleteDialogHeading">
        <Translate contentKey="entity.delete.title">Confirm delete operation</Translate>
      </ModalHeader>
      <ModalBody id="fintrackApp.apiAccessToken.delete.question">
        <Translate contentKey="fintrackApp.apiAccessToken.delete.question" interpolate={{ id: apiAccessTokenEntity.id }}>
          Are you sure you want to delete this ApiAccessToken?
        </Translate>
        <p className="mt-2 mb-0">
          <Translate contentKey="fintrackApp.apiAccessToken.delete.message">
            This removes the token and its permissions. Historical API ingestion records are preserved.
          </Translate>
        </p>
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" onClick={handleClose}>
          <FontAwesomeIcon icon="ban" />
          &nbsp;
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        <Button id="jhi-confirm-delete-apiAccessToken" data-cy="entityConfirmDeleteButton" color="danger" onClick={confirmDelete}>
          <FontAwesomeIcon icon="trash" />
          &nbsp;
          <Translate contentKey="entity.action.delete">Delete</Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default ApiAccessTokenDeleteDialog;
