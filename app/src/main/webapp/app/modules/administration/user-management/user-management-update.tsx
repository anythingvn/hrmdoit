import React, { useEffect } from 'react';
import { Button, Col, FormText, Row } from 'react-bootstrap';
import { Translate, ValidatedField, ValidatedForm, isEmail, translate } from 'react-jhipster';
import { Link, useNavigate, useParams } from 'react-router';

import { faArrowLeft, faSave } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { languages, locales } from 'app/config/translation';

import { ROLE_LABELS } from 'app/config/constants';

import { PASSWORD_MAX, PASSWORD_MIN } from './set-password-dialog';
import { createUser, getRoles, getUser, reset, updateUser } from './user-management.reducer';

export const UserManagementUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { login } = useParams<'login'>();
  const isNew = login === undefined;

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getUser(login));
    }
    dispatch(getRoles());
    return () => {
      dispatch(reset());
    };
  }, [login]);

  const handleClose = () => {
    navigate('/admin/user-management');
  };

  const saveUser = values => {
    // Email không bắt buộc: để trống thì gửi null (server cho phép nhiều tài khoản không có email).
    const email = typeof values.email === 'string' && values.email.trim() !== '' ? values.email.trim() : null;
    if (isNew) {
      // Có mật khẩu ban đầu thì đăng nhập được ngay, không gửi thư kích hoạt.
      dispatch(createUser({ ...values, email }));
    } else {
      dispatch(updateUser({ ...values, password: undefined, email }));
    }
  };

  const user = useAppSelector(state => state.userManagement.user);
  const loading = useAppSelector(state => state.userManagement.loading);
  const updating = useAppSelector(state => state.userManagement.updating);
  const updateSuccess = useAppSelector(state => state.userManagement.updateSuccess);
  const authorities = useAppSelector(state => state.userManagement.authorities);

  // Navigate back only once the request has completed, otherwise a concurrent request may observe a stale state.
  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h1 data-cy="UserManagementCreateUpdateHeading">
            <Translate contentKey="userManagement.home.createOrEditLabel">Create or edit a User</Translate>
          </h1>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? (
            <p>Loading...</p>
          ) : (
            <ValidatedForm mode="all" onSubmit={saveUser} defaultValues={user}>
              {user.id && (
                <ValidatedField
                  type="text"
                  name="id"
                  data-cy="id"
                  required
                  readOnly
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              )}
              <ValidatedField
                type="text"
                id="user-login"
                name="login"
                data-cy="login"
                label="Tên đăng nhập"
                validate={{
                  required: {
                    value: true,
                    message: translate('register.messages.validate.login.required'),
                  },
                  pattern: {
                    value: /^[a-zA-Z0-9!$&*+=?^_`{|}~.-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*$|^[_.@A-Za-z0-9-]+$/,
                    message: translate('register.messages.validate.login.pattern'),
                  },
                  minLength: {
                    value: 1,
                    message: translate('register.messages.validate.login.minlength'),
                  },
                  maxLength: {
                    value: 50,
                    message: translate('register.messages.validate.login.maxlength'),
                  },
                }}
              />
              <ValidatedField
                type="text"
                name="firstName"
                data-cy="firstName"
                label={translate('userManagement.firstName')}
                validate={{
                  maxLength: {
                    value: 50,
                    message: translate('entity.validation.maxlength', { max: 50 }),
                  },
                }}
              />
              <ValidatedField
                type="text"
                name="lastName"
                data-cy="lastName"
                label={translate('userManagement.lastName')}
                validate={{
                  maxLength: {
                    value: 50,
                    message: translate('entity.validation.maxlength', { max: 50 }),
                  },
                }}
              />
              <FormText>This field cannot be longer than 50 characters.</FormText>
              <ValidatedField
                id="user-email"
                name="email"
                data-cy="email"
                label="Email (không bắt buộc)"
                type="email"
                validate={{
                  minLength: {
                    value: 5,
                    message: translate('global.messages.validate.email.minlength'),
                  },
                  maxLength: {
                    value: 254,
                    message: translate('global.messages.validate.email.maxlength'),
                  },
                  validate: v => !v || isEmail(v) || translate('global.messages.validate.email.invalid'),
                }}
              />
              {isNew && (
                <ValidatedField
                  type="password"
                  id="user-password"
                  name="password"
                  data-cy="password"
                  label="Mật khẩu ban đầu"
                  autoComplete="new-password"
                  validate={{
                    required: { value: true, message: `Mật khẩu phải từ ${PASSWORD_MIN} đến ${PASSWORD_MAX} ký tự` },
                    minLength: { value: PASSWORD_MIN, message: `Mật khẩu phải từ ${PASSWORD_MIN} đến ${PASSWORD_MAX} ký tự` },
                    maxLength: { value: PASSWORD_MAX, message: `Mật khẩu phải từ ${PASSWORD_MIN} đến ${PASSWORD_MAX} ký tự` },
                  }}
                />
              )}
              {isNew && <FormText>Tài khoản đăng nhập được ngay bằng mật khẩu này; báo mật khẩu cho người dùng qua kênh riêng.</FormText>}
              <ValidatedField
                type="checkbox"
                name="activated"
                data-cy="activated"
                check
                value={true}
                label={translate('userManagement.activated')}
              />
              <ValidatedField type="select" name="langKey" data-cy="langKey" label={translate('userManagement.langKey')}>
                {locales.map(locale => (
                  <option value={locale} key={locale}>
                    {languages[locale].name}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField type="select" id="user-authorities" name="authorities" data-cy="profiles" multiple label="Vai trò">
                {authorities.map(role => (
                  <option value={role} key={role}>
                    {ROLE_LABELS[role] ?? role}
                  </option>
                ))}
              </ValidatedField>
              <Button as={Link as any} to="/admin/user-management" replace variant="info" data-cy="entityCreateCancelButton">
                <FontAwesomeIcon icon={faArrowLeft} />
                &nbsp;
                <span className="d-none d-md-inline">
                  <Translate contentKey="entity.action.back">Back</Translate>
                </span>
              </Button>
              &nbsp;
              <Button variant="primary" type="submit" disabled={updating} data-cy="entityCreateSaveButton">
                <FontAwesomeIcon icon={faSave} />
                &nbsp;Lưu
              </Button>
            </ValidatedForm>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default UserManagementUpdate;
