import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { IFinancialTransaction } from 'app/shared/model/financial-transaction.model';

import { createEntity, getEntity, reset, updateEntity } from './internal-transfer.reducer';

export const InternalTransferUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const [outgoingTransactions, setOutgoingTransactions] = useState<IFinancialTransaction[]>([]);
  const [incomingTransactions, setIncomingTransactions] = useState<IFinancialTransaction[]>([]);

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
      axios.get<IFinancialTransaction[]>('api/financial-transactions/outgoing-internal-transfer-candidates').then(response => {
        setOutgoingTransactions(response.data);
      });
      axios.get<IFinancialTransaction[]>('api/financial-transactions/incoming-internal-transfer-candidates').then(response => {
        setIncomingTransactions(response.data);
      });
    } else {
      dispatch(getEntity(id));
    }
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

    const entity = {
      ...internalTransferEntity,
      ...values,
      outgoingTransaction: isNew
        ? outgoingTransactions.find(it => it.id.toString() === values.outgoingTransaction?.toString())
        : internalTransferEntity.outgoingTransaction,
      incomingTransaction: isNew
        ? incomingTransactions.find(it => it.id.toString() === values.incomingTransaction?.toString())
        : internalTransferEntity.incomingTransaction,
    };

    if (isNew) {
      dispatch(createEntity(entity));
    } else {
      dispatch(updateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {}
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
              {!isNew ? (
                <ValidatedField
                  label={translate('fintrackApp.internalTransfer.createdAt')}
                  id="internal-transfer-createdAt"
                  name="createdAt"
                  data-cy="createdAt"
                  type="datetime-local"
                  readOnly
                />
              ) : null}
              {isNew ? (
                <ValidatedField
                  id="internal-transfer-outgoingTransaction"
                  name="outgoingTransaction"
                  data-cy="outgoingTransaction"
                  label={translate('fintrackApp.internalTransfer.outgoingTransaction')}
                  type="select"
                  required
                >
                  <option value="" key="0" />
                  {outgoingTransactions.map(otherEntity => (
                    <option value={otherEntity.id} key={otherEntity.id}>
                      {otherEntity.id}
                    </option>
                  ))}
                </ValidatedField>
              ) : (
                <ValidatedField
                  label={translate('fintrackApp.internalTransfer.outgoingTransaction')}
                  id="internal-transfer-outgoingTransaction"
                  name="outgoingTransaction"
                  data-cy="outgoingTransaction"
                  type="text"
                  readOnly
                />
              )}
              {isNew ? (
                <FormText>
                  <Translate contentKey="entity.validation.required">This field is required.</Translate>
                </FormText>
              ) : null}
              {isNew ? (
                <ValidatedField
                  id="internal-transfer-incomingTransaction"
                  name="incomingTransaction"
                  data-cy="incomingTransaction"
                  label={translate('fintrackApp.internalTransfer.incomingTransaction')}
                  type="select"
                  required
                >
                  <option value="" key="0" />
                  {incomingTransactions.map(otherEntity => (
                    <option value={otherEntity.id} key={otherEntity.id}>
                      {otherEntity.id}
                    </option>
                  ))}
                </ValidatedField>
              ) : (
                <ValidatedField
                  label={translate('fintrackApp.internalTransfer.incomingTransaction')}
                  id="internal-transfer-incomingTransaction"
                  name="incomingTransaction"
                  data-cy="incomingTransaction"
                  type="text"
                  readOnly
                />
              )}
              {isNew ? (
                <FormText>
                  <Translate contentKey="entity.validation.required">This field is required.</Translate>
                </FormText>
              ) : null}
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
