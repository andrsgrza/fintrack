import React, { useEffect } from 'react';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { deleteEntity, getEntity } from './description-normalization-rule.reducer';

export const DescriptionNormalizationRuleDeleteDialog = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const rule = useAppSelector(state => state.descriptionNormalizationRule.entity);
  const updateSuccess = useAppSelector(state => state.descriptionNormalizationRule.updateSuccess);

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  useEffect(() => {
    if (updateSuccess) {
      navigate('/description-normalization-rule');
    }
  }, [updateSuccess]);

  const confirmDelete = () => dispatch(deleteEntity(rule.id));
  const handleClose = () => navigate('/description-normalization-rule');

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose}>
        <Translate contentKey="entity.delete.title">Confirm delete operation</Translate>
      </ModalHeader>
      <ModalBody id="fintrackApp.descriptionNormalizationRule.delete.question">
        <Translate contentKey="fintrackApp.descriptionNormalizationRule.delete.question" interpolate={{ id: rule.id }}>
          Are you sure you want to delete this rule?
        </Translate>
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" onClick={handleClose}>
          <FontAwesomeIcon icon="ban" />
          &nbsp;
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        <Button
          id="jhi-confirm-delete-descriptionNormalizationRule"
          data-cy="entityConfirmDeleteButton"
          color="danger"
          onClick={confirmDelete}
        >
          <FontAwesomeIcon icon="trash" />
          &nbsp;
          <Translate contentKey="entity.action.delete">Delete</Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default DescriptionNormalizationRuleDeleteDialog;
