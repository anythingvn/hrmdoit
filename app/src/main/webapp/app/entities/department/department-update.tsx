import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Button, Col, Row } from 'react-bootstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { Link, useNavigate, useParams } from 'react-router';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { IEmployee } from 'app/shared/model/employee.model';

import { createEntity, getEntity, reset, updateEntity } from './department.reducer';

/** Người chọn được làm trưởng phòng: nhân viên (NHAN_VIEN) đang làm việc. */
const HEAD_CANDIDATES_URL = 'api/employees?personType.equals=NHAN_VIEN&status.equals=DANG_LAM_VIEC&size=1000&sort=fullName,asc';

export const DepartmentUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const departmentEntity = useAppSelector(state => state.department.entity);
  const loading = useAppSelector(state => state.department.loading);
  const updating = useAppSelector(state => state.department.updating);
  const updateSuccess = useAppSelector(state => state.department.updateSuccess);
  const [candidates, setCandidates] = useState<IEmployee[]>([]);

  const handleClose = () => {
    navigate('/danh-muc/phong-ban');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }
    axios
      .get<IEmployee[]>(HEAD_CANDIDATES_URL)
      .then(r => setCandidates(r.data ?? []))
      .catch(() => undefined);
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
      ...departmentEntity,
      ...values,
      headId: values.headId ? Number(values.headId) : null,
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
          ...departmentEntity,
          headId: departmentEntity.headId != null ? String(departmentEntity.headId) : '',
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="hrmApp.department.home.createOrEditLabel" data-cy="DepartmentCreateUpdateHeading">
            <Translate contentKey="hrmApp.department.home.createOrEditLabel">Create or edit a Department</Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? (
            <p>Loading...</p>
          ) : (
            <ValidatedForm mode="all" defaultValues={defaultValues()} onSubmit={saveEntity}>
              {!isNew && (
                <ValidatedField
                  name="id"
                  required
                  readOnly
                  id="department-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              )}
              <ValidatedField
                label={translate('hrmApp.department.name')}
                id="department-name"
                name="name"
                data-cy="name"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  maxLength: { value: 100, message: translate('entity.validation.maxlength', { max: 100 }) },
                }}
              />
              <ValidatedField
                label={translate('hrmApp.department.active')}
                id="department-active"
                name="active"
                data-cy="active"
                check
                type="checkbox"
              />
              <ValidatedField label="Trưởng phòng" id="department-headId" name="headId" data-cy="headId" type="select">
                <option value="">Chưa có</option>
                {departmentEntity.headId != null && !candidates.some(c => c.id === departmentEntity.headId) && (
                  <option value={String(departmentEntity.headId)}>{departmentEntity.headName}</option>
                )}
                {candidates.map(c => (
                  <option key={c.id} value={String(c.id)}>
                    {`${c.code ?? ''} ${c.fullName ?? ''}`.trim()}
                  </option>
                ))}
              </ValidatedField>
              <p className="form-text">
                Trưởng phòng xem lịch nghỉ và sổ phép của người trong phòng; không duyệt đơn. Chỉ chọn được nhân viên đang làm việc.
              </p>
              <Button as={Link as any} id="cancel-save" data-cy="entityCreateCancelButton" to="/danh-muc/phong-ban" replace variant="info">
                <FontAwesomeIcon icon="arrow-left" />
                &nbsp;
                <span className="d-none d-md-inline">
                  <Translate contentKey="entity.action.back">Back</Translate>
                </span>
              </Button>
              &nbsp;
              <Button variant="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={updating}>
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

export default DepartmentUpdate;
