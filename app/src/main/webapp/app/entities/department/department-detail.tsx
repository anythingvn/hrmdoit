import React, { useEffect } from 'react';
import { Button, Col, Row } from 'react-bootstrap';
import { Translate } from 'react-jhipster';
import { Link, useParams } from 'react-router';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { AUTHORITIES } from 'app/config/constants';
import { hasAnyAuthority } from 'app/shared/auth/private-route';

import { getEntity } from './department.reducer';

export const DepartmentDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  const canWrite = useAppSelector(state => hasAnyAuthority(state.authentication.account.authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR]));

  useEffect(() => {
    dispatch(getEntity(id!));
  }, []);

  const departmentEntity = useAppSelector(state => state.department.entity);
  return (
    <Row className="doit-card mx-0">
      <Col md="8">
        <h2 data-cy="departmentDetailsHeading">
          <Translate contentKey="hrmApp.department.detail.title">Department</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{departmentEntity.id}</dd>
          <dt>
            <span id="name">
              <Translate contentKey="hrmApp.department.name">Name</Translate>
            </span>
          </dt>
          <dd>{departmentEntity.name}</dd>
          <dt>
            <span id="head">Trưởng phòng</span>
          </dt>
          <dd>{departmentEntity.headName ?? '—'}</dd>
          <dt>
            <span id="active">
              <Translate contentKey="hrmApp.department.active">Active</Translate>
            </span>
          </dt>
          <dd>{departmentEntity.active ? 'Có' : 'Không'}</dd>
        </dl>
        <Button as={Link as any} to="/danh-muc/phong-ban" replace variant="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        {canWrite && (
          <>
            &nbsp;
            <Button as={Link as any} to={`/danh-muc/phong-ban/${departmentEntity.id}/edit`} replace variant="primary">
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

export default DepartmentDetail;
