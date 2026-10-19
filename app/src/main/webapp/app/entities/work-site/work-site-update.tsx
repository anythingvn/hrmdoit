import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { WorkLocationType } from 'app/shared/model/enumerations/work-location-type.model';
import { createEntity, getEntity, reset, updateEntity } from './work-site.reducer';

export const WorkSiteUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const workSiteEntity = useAppSelector(state => state.workSite.entity);
  const loading = useAppSelector(state => state.workSite.loading);
  const updating = useAppSelector(state => state.workSite.updating);
  const updateSuccess = useAppSelector(state => state.workSite.updateSuccess);
  const workLocationTypeValues = Object.keys(WorkLocationType);

  const handleClose = () => {
    navigate('/danh-muc/noi-lam-viec');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
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
      ...workSiteEntity,
      ...values,
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
          type: 'VAN_PHONG',
          ...workSiteEntity,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="hrmApp.workSite.home.createOrEditLabel" data-cy="WorkSiteCreateUpdateHeading">
            <Translate contentKey="hrmApp.workSite.home.createOrEditLabel">Create or edit a WorkSite</Translate>
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
                  id="work-site-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('hrmApp.workSite.name')}
                id="work-site-name"
                name="name"
                data-cy="name"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  maxLength: { value: 200, message: translate('entity.validation.maxlength', { max: 200 }) },
                }}
              />
              <ValidatedField label={translate('hrmApp.workSite.type')} id="work-site-type" name="type" data-cy="type" type="select">
                {workLocationTypeValues.map(workLocationType => (
                  <option value={workLocationType} key={workLocationType}>
                    {translate(`hrmApp.WorkLocationType.${workLocationType}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('hrmApp.workSite.address')}
                id="work-site-address"
                name="address"
                data-cy="address"
                type="text"
                validate={{
                  maxLength: { value: 300, message: translate('entity.validation.maxlength', { max: 300 }) },
                }}
              />
              <ValidatedField
                label={translate('hrmApp.workSite.active')}
                id="work-site-active"
                name="active"
                data-cy="active"
                check
                type="checkbox"
              />
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/danh-muc/noi-lam-viec" replace color="info">
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

export default WorkSiteUpdate;
