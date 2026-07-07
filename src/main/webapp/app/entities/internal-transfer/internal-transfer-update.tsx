import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getFinancialTransactions } from 'app/entities/financial-transaction/financial-transaction.reducer';
import { createEntity, getEntity, reset, updateEntity } from './internal-transfer.reducer';

export const InternalTransferUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const financialTransactions = useAppSelector(state => state.financialTransaction.entities);
  const internalTransferEntity = useAppSelector(state => state.internalTransfer.entity);
  const loading = useAppSelector(state => state.internalTransfer.loading);
  const updating = useAppSelector(state => state.internalTransfer.updating);
  const updateSuccess = useAppSelector(state => state.internalTransfer.updateSuccess);

  const handleClose = () => {
    navigate('/internal-transfer');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getFinancialTransactions({}));
  }, []);

  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  const saveEntity = values => {
    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }
    values.createdAt = convertDateTimeToServer(values.createdAt);

    const entity = {
      ...internalTransferEntity,
      ...values,
      outgoingTransaction: financialTransactions.find(it => it.id.toString() === values.outgoingTransaction?.toString()),
      incomingTransaction: financialTransactions.find(it => it.id.toString() === values.incomingTransaction?.toString()),
    };

    if (isNew) {
      dispatch(createEntity(entity));
    } else {
      dispatch(updateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {
          createdAt: displayDefaultDateTime(),
        }
      : {
          ...internalTransferEntity,
          createdAt: convertDateTimeFromServer(internalTransferEntity.createdAt),
          outgoingTransaction: internalTransferEntity?.outgoingTransaction?.id,
          incomingTransaction: internalTransferEntity?.incomingTransaction?.id,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.internalTransfer.home.createOrEditLabel" data-cy="InternalTransferCreateUpdateHeading">
            <Translate contentKey="fintrackApp.internalTransfer.home.createOrEditLabel">Create or edit a InternalTransfer</Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? (
            <p>Loading...</p>
          ) : (
            <ValidatedForm defaultValues={defaultValues()} onSubmit={saveEntity}>
              {!isNew ? (
                <ValidatedField
                  name="id"
                  required
                  readOnly
                  id="internal-transfer-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.internalTransfer.notes')}
                id="internal-transfer-notes"
                name="notes"
                data-cy="notes"
                type="text"
                validate={{
                  maxLength: { value: 500, message: translate('entity.validation.maxlength', { max: 500 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.internalTransfer.createdAt')}
                id="internal-transfer-createdAt"
                name="createdAt"
                data-cy="createdAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                id="internal-transfer-outgoingTransaction"
                name="outgoingTransaction"
                data-cy="outgoingTransaction"
                label={translate('fintrackApp.internalTransfer.outgoingTransaction')}
                type="select"
                required
              >
                <option value="" key="0" />
                {financialTransactions
                  ? financialTransactions.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <FormText>
                <Translate contentKey="entity.validation.required">This field is required.</Translate>
              </FormText>
              <ValidatedField
                id="internal-transfer-incomingTransaction"
                name="incomingTransaction"
                data-cy="incomingTransaction"
                label={translate('fintrackApp.internalTransfer.incomingTransaction')}
                type="select"
                required
              >
                <option value="" key="0" />
                {financialTransactions
                  ? financialTransactions.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <FormText>
                <Translate contentKey="entity.validation.required">This field is required.</Translate>
              </FormText>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/internal-transfer" replace color="info">
                <FontAwesomeIcon icon="arrow-left" />
                &nbsp;
                <span className="d-none d-md-inline">
                  <Translate contentKey="entity.action.back">Back</Translate>
                </span>
              </Button>
              &nbsp;
              <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={updating}>
                <FontAwesomeIcon icon="save" />
                &nbsp;
                <Translate contentKey="entity.action.save">Save</Translate>
              </Button>
            </ValidatedForm>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default InternalTransferUpdate;
