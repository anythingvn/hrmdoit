import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { AUTHORITIES } from 'app/config/constants';
import { hasAnyAuthority } from 'app/shared/auth/private-route';

import { getEntity } from './work-site.reducer';

export const WorkSiteDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  const canWrite = useAppSelector(state => hasAnyAuthority(state.authentication.account.authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR]));

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const workSiteEntity = useAppSelector(state => state.workSite.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="workSiteDetailsHeading">
          <Translate contentKey="hrmApp.workSite.detail.title">WorkSite</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{workSiteEntity.id}</dd>
          <dt>
            <span id="name">
              <Translate contentKey="hrmApp.workSite.name">Name</Translate>
            </span>
          </dt>
          <dd>{workSiteEntity.name}</dd>
          <dt>
            <span id="type">
              <Translate contentKey="hrmApp.workSite.type">Type</Translate>
            </span>
          </dt>
          <dd>
            <Translate contentKey={`hrmApp.WorkLocationType.${workSiteEntity.type}`} />
          </dd>
          <dt>
            <span id="address">
              <Translate contentKey="hrmApp.workSite.address">Address</Translate>
            </span>
          </dt>
          <dd>{workSiteEntity.address}</dd>
          <dt>
            <span id="active">
              <Translate contentKey="hrmApp.workSite.active">Active</Translate>
            </span>
          </dt>
          <dd>{workSiteEntity.active ? 'Có' : 'Không'}</dd>
        </dl>
        <Button tag={Link} to="/danh-muc/noi-lam-viec" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        {canWrite && (
          <>
            &nbsp;
            <Button tag={Link} to={`/danh-muc/noi-lam-viec/${workSiteEntity.id}/edit`} replace color="primary">
              <FontAwesomeIcon icon="pencil-alt" />{' '}
              <span className="d-none d-md-inline">
                <Translate contentKey="entity.action.edit">Edit</Translate>
              </span>
            </Button>
          </>
        )}
      </Col>
    </Row>
  );
};

export default WorkSiteDetail;
