import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './user-dashboard-preference.reducer';

export const UserDashboardPreferenceDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const userDashboardPreferenceEntity = useAppSelector(state => state.userDashboardPreference.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="userDashboardPreferenceDetailsHeading">
          <Translate contentKey="fintrackApp.userDashboardPreference.detail.title">UserDashboardPreference</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{userDashboardPreferenceEntity.id}</dd>
          <dt>
            <span id="configuration">
              <Translate contentKey="fintrackApp.userDashboardPreference.configuration">Configuration</Translate>
            </span>
          </dt>
          <dd>{userDashboardPreferenceEntity.configuration}</dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.userDashboardPreference.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>
            {userDashboardPreferenceEntity.createdAt ? (
              <TextFormat value={userDashboardPreferenceEntity.createdAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="updatedAt">
              <Translate contentKey="fintrackApp.userDashboardPreference.updatedAt">Updated At</Translate>
            </span>
          </dt>
          <dd>
            {userDashboardPreferenceEntity.updatedAt ? (
              <TextFormat value={userDashboardPreferenceEntity.updatedAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.userDashboardPreference.user">User</Translate>
          </dt>
          <dd>{userDashboardPreferenceEntity.user ? userDashboardPreferenceEntity.user.login : ''}</dd>
        </dl>
        <Button tag={Link} to="/user-dashboard-preference" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/user-dashboard-preference/${userDashboardPreferenceEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default UserDashboardPreferenceDetail;
