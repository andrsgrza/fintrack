import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { deleteEntity, getEntity } from './category.reducer';

export const CategoryDeleteDialog = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();

  const [loadModal, setLoadModal] = useState(false);
  const [childCount, setChildCount] = useState<number | null>(null);

  useEffect(() => {
    dispatch(getEntity(id));
    setLoadModal(true);
  }, []);

  useEffect(() => {
    if (id) {
      axios.get<number>(`api/categories/count?parentCategoryId.equals=${id}`).then(response => {
        setChildCount(response.data);
      });
    }
  }, [id]);

  const categoryEntity = useAppSelector(state => state.category.entity);
  const updateSuccess = useAppSelector(state => state.category.updateSuccess);
  const hasChildren = (childCount ?? 0) > 0;

  const handleClose = () => {
    navigate('/category');
  };

  useEffect(() => {
    if (updateSuccess && loadModal) {
      handleClose();
      setLoadModal(false);
    }
  }, [updateSuccess]);

  const confirmDelete = () => {
    dispatch(deleteEntity(categoryEntity.id));
  };

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose} data-cy="categoryDeleteDialogHeading">
        <Translate contentKey="fintrackApp.category.delete.title">Delete category?</Translate>
      </ModalHeader>
      <ModalBody
        id={hasChildren ? 'fintrackApp.category.delete.blockedMessage' : 'fintrackApp.category.delete.leafMessage'}
        data-cy={hasChildren ? 'categoryDeleteBlockedMessage' : 'categoryDeleteLeafMessage'}
      >
        {hasChildren ? (
          <Translate contentKey="fintrackApp.category.delete.blockedMessage">
            This category cannot be deleted because it has subcategories. Delete its subcategories first, then try again.
          </Translate>
        ) : (
          <Translate contentKey="fintrackApp.category.delete.leafMessage">
            This will delete the category and remove it from everything where it is currently used. Transactions and subscriptions using
            this category will keep existing, but their category will be cleared. Budgets will no longer include this category. Rules using
            this category will be disabled because their target category will be removed. This action cannot be undone.
          </Translate>
        )}
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" onClick={handleClose}>
          <FontAwesomeIcon icon="ban" />
          &nbsp;
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        {!hasChildren && childCount !== null && (
          <Button id="jhi-confirm-delete-category" data-cy="entityConfirmDeleteButton" color="danger" onClick={confirmDelete}>
            <FontAwesomeIcon icon="trash" />
            &nbsp;
            <Translate contentKey="entity.action.delete">Delete</Translate>
          </Button>
        )}
      </ModalFooter>
    </Modal>
  );
};

export default CategoryDeleteDialog;
