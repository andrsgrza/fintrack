import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Col, Input, InputGroup, Modal, ModalBody, ModalFooter, ModalHeader, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { ApiTokenStatus } from 'app/shared/model/enumerations/api-token-status.model';
import { createEntity, getEntity, reset, updateEntity } from './api-access-token.reducer';

export const ApiAccessTokenUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const apiAccessTokenEntity = useAppSelector(state => state.apiAccessToken.entity);
  const loading = useAppSelector(state => state.apiAccessToken.loading);
  const updating = useAppSelector(state => state.apiAccessToken.updating);
  const updateSuccess = useAppSelector(state => state.apiAccessToken.updateSuccess);
  const apiTokenStatusValues = Object.keys(ApiTokenStatus);
  const [revealedRawToken, setRevealedRawToken] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const handleClose = () => {
    navigate('/api-access-token');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }
  }, []);

  useEffect(() => {
    if (updateSuccess && !isNew) {
      handleClose();
    }
  }, [updateSuccess, isNew]);

  const saveEntity = async values => {
    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }

    if (isNew) {
      try {
        const result = await dispatch(createEntity({ name: values.name })).unwrap();
        if (result.data.rawToken) {
          setRevealedRawToken(result.data.rawToken);
          setCopied(false);
        } else {
          handleClose();
        }
      } catch (e) {
        // error handled by reducer
      }
      return;
    }

    values.expiresAt = convertDateTimeToServer(values.expiresAt);

    const entity = {
      id: values.id,
      name: values.name,
      status: values.status,
      expiresAt: values.expiresAt,
    };

    dispatch(updateEntity(entity));
  };

  const defaultValues = () =>
    isNew
      ? {}
      : {
          status: 'ACTIVE',
          ...apiAccessTokenEntity,
          createdAt: convertDateTimeFromServer(apiAccessTokenEntity.createdAt),
          updatedAt: convertDateTimeFromServer(apiAccessTokenEntity.updatedAt),
          lastUsedAt: convertDateTimeFromServer(apiAccessTokenEntity.lastUsedAt),
          expiresAt: convertDateTimeFromServer(apiAccessTokenEntity.expiresAt),
          revokedAt: convertDateTimeFromServer(apiAccessTokenEntity.revokedAt),
        };

  const copyToken = async () => {
    if (!revealedRawToken) {
      return;
    }
    try {
      await navigator.clipboard.writeText(revealedRawToken);
      setCopied(true);
    } catch {
      const input = document.getElementById('api-access-token-raw-token') as HTMLInputElement | null;
      input?.select();
      document.execCommand('copy');
      setCopied(true);
    }
  };

  const closeRevealModal = () => {
    setRevealedRawToken(null);
    setCopied(false);
    handleClose();
  };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.apiAccessToken.home.createOrEditLabel" data-cy="ApiAccessTokenCreateUpdateHeading">
            <Translate contentKey="fintrackApp.apiAccessToken.home.createOrEditLabel">Create or edit a ApiAccessToken</Translate>
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
                  id="api-access-token-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              {isNew ? (
                <Alert color="info">
                  <Translate contentKey="fintrackApp.apiAccessToken.create.help">
                    Enter a name only. The server generates the token secret and shows it once after creation.
                  </Translate>
                </Alert>
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.apiAccessToken.name')}
                id="api-access-token-name"
                name="name"
                data-cy="name"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  minLength: { value: 1, message: translate('entity.validation.minlength', { min: 1 }) },
                  maxLength: { value: 100, message: translate('entity.validation.maxlength', { max: 100 }) },
                }}
              />
              {!isNew ? (
                <>
                  <ValidatedField
                    label={translate('fintrackApp.apiAccessToken.tokenPrefix')}
                    id="api-access-token-tokenPrefix"
                    name="tokenPrefix"
                    data-cy="tokenPrefix"
                    type="text"
                    readOnly
                  />
                  <ValidatedField
                    label={translate('fintrackApp.apiAccessToken.status')}
                    id="api-access-token-status"
                    name="status"
                    data-cy="status"
                    type="select"
                  >
                    {apiTokenStatusValues.map(apiTokenStatus => (
                      <option value={apiTokenStatus} key={apiTokenStatus}>
                        {translate(`fintrackApp.ApiTokenStatus.${apiTokenStatus}`)}
                      </option>
                    ))}
                  </ValidatedField>
                  <ValidatedField
                    label={translate('fintrackApp.apiAccessToken.createdAt')}
                    id="api-access-token-createdAt"
                    name="createdAt"
                    data-cy="createdAt"
                    type="datetime-local"
                    placeholder="YYYY-MM-DD HH:mm"
                    readOnly
                  />
                  <ValidatedField
                    label={translate('fintrackApp.apiAccessToken.updatedAt')}
                    id="api-access-token-updatedAt"
                    name="updatedAt"
                    data-cy="updatedAt"
                    type="datetime-local"
                    placeholder="YYYY-MM-DD HH:mm"
                    readOnly
                  />
                  <ValidatedField
                    label={translate('fintrackApp.apiAccessToken.lastUsedAt')}
                    id="api-access-token-lastUsedAt"
                    name="lastUsedAt"
                    data-cy="lastUsedAt"
                    type="datetime-local"
                    placeholder="YYYY-MM-DD HH:mm"
                  />
                  <ValidatedField
                    label={translate('fintrackApp.apiAccessToken.expiresAt')}
                    id="api-access-token-expiresAt"
                    name="expiresAt"
                    data-cy="expiresAt"
                    type="datetime-local"
                    placeholder="YYYY-MM-DD HH:mm"
                  />
                  <ValidatedField
                    label={translate('fintrackApp.apiAccessToken.revokedAt')}
                    id="api-access-token-revokedAt"
                    name="revokedAt"
                    data-cy="revokedAt"
                    type="datetime-local"
                    placeholder="YYYY-MM-DD HH:mm"
                  />
                </>
              ) : null}
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/api-access-token" replace color="info">
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
      <Modal isOpen={!!revealedRawToken} toggle={closeRevealModal} data-cy="apiAccessTokenRevealDialog">
        <ModalHeader toggle={closeRevealModal}>
          <Translate contentKey="fintrackApp.apiAccessToken.create.rawTokenTitle">API token created</Translate>
        </ModalHeader>
        <ModalBody>
          <p className="mb-3">
            <Translate contentKey="fintrackApp.apiAccessToken.create.rawTokenMessage">
              Copy this token now. It will not be shown again.
            </Translate>
          </p>
          <InputGroup>
            <Input
              id="api-access-token-raw-token"
              data-cy="rawToken"
              type="text"
              readOnly
              value={revealedRawToken ?? ''}
              onFocus={event => event.target.select()}
              onClick={event => event.currentTarget.select()}
            />
            <Button color="primary" onClick={copyToken} data-cy="copyRawToken">
              <FontAwesomeIcon icon="copy" />
              &nbsp;
              <Translate contentKey="fintrackApp.apiAccessToken.create.copyToken">Copy</Translate>
            </Button>
          </InputGroup>
          {copied ? (
            <small className="text-success d-block mt-2" data-cy="rawTokenCopied">
              <Translate contentKey="fintrackApp.apiAccessToken.create.copied">Copied to clipboard.</Translate>
            </small>
          ) : null}
        </ModalBody>
        <ModalFooter>
          <Button color="primary" onClick={closeRevealModal} data-cy="rawTokenDone">
            <Translate contentKey="entity.action.close">Close</Translate>
          </Button>
        </ModalFooter>
      </Modal>
    </div>
  );
};

export default ApiAccessTokenUpdate;
