import React, { useEffect, useState } from 'react';
import { Button, Col, FormText, Row } from 'react-bootstrap';
import { Translate, ValidatedField, ValidatedForm, isEmail, translate } from 'react-jhipster';
import { Link, useNavigate, useParams } from 'react-router';

import { faArrowLeft, faSave } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { languages, locales } from 'app/config/translation';

import { ROLE_LABELS } from 'app/config/constants';

import {
  DEFAULT_PASSWORD,
  INITIAL_PASSWORD_ERROR,
  INITIAL_PASSWORD_LABEL,
  INITIAL_PASSWORD_PLACEHOLDER,
  initialPasswordInvalid,
} from 'app/entities/employee/detail/account-tab';
import { createUser, getRoles, getUser, reset, updateUser } from './user-management.reducer';

/** Số CCCD (12 chữ số) hoặc CMND cũ (9 chữ số), giống kiểm tra ở server (IdNumbers). */
export const ID_NUMBER_PATTERN = /^(\d{12}|\d{9})$/;
const ID_NUMBER_MESSAGE = 'Số CCCD gồm 12 chữ số (hoặc 9 chữ số CMND cũ)';

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

  const [idNumber, setIdNumber] = useState('');

  const saveUser = values => {
    // Email không bắt buộc: để trống thì gửi null (server cho phép nhiều tài khoản không có email).
    const email = typeof values.email === 'string' && values.email.trim() !== '' ? values.email.trim() : null;
    if (isNew) {
      // Tên đăng nhập = số CCCD; mật khẩu mặc định (server đặt), người dùng phải đổi ở lần đăng nhập đầu.
      const cccd = String(values.idNumber ?? '').trim();
      // Mật khẩu ban đầu tùy chọn: để trống thì không gửi (server dùng mật khẩu mặc định).
      const password = typeof values.password === 'string' && values.password !== '' ? values.password : undefined;
      dispatch(createUser({ ...values, idNumber: cccd, login: cccd, email, password }));
    } else {
      dispatch(updateUser({ ...values, idNumber: undefined, password: undefined, email }));
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
              {isNew && (
                <ValidatedField
                  type="text"
                  id="user-id-number"
                  name="idNumber"
                  data-cy="idNumber"
                  label="Số CCCD"
                  inputMode="numeric"
                  autoComplete="off"
                  onChange={e => setIdNumber(e.target.value)}
                  validate={{
                    required: { value: true, message: ID_NUMBER_MESSAGE },
                    validate: v => ID_NUMBER_PATTERN.test(String(v ?? '').trim()) || ID_NUMBER_MESSAGE,
                  }}
                />
              )}
              {/* Tên đăng nhập luôn bằng số CCCD (tự điền khi tạo, khóa, không sửa tay). */}
              <div className="mb-3">
                <label htmlFor="user-login" className="form-label">
                  Tên đăng nhập
                </label>
                <input
                  id="user-login"
                  data-cy="login"
                  className="form-control"
                  readOnly
                  value={isNew ? idNumber.trim() : (user.login ?? '')}
                  aria-describedby="user-login-hint"
                />
              </div>
              <FormText id="user-login-hint">
                {isNew
                  ? 'Tên đăng nhập là số CCCD (tự điền, không sửa tay). Trùng CCCD của hồ sơ nhân viên chưa có tài khoản thì tài khoản được gắn vào hồ sơ đó.'
                  : 'Tên đăng nhập là số CCCD; tài khoản gắn hồ sơ đổi tên đăng nhập bằng cách sửa số CCCD trên hồ sơ nhân viên.'}
              </FormText>
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
                  label={INITIAL_PASSWORD_LABEL}
                  placeholder={INITIAL_PASSWORD_PLACEHOLDER}
                  autoComplete="new-password"
                  validate={{ validate: v => !initialPasswordInvalid(String(v ?? '')) || INITIAL_PASSWORD_ERROR }}
                />
              )}
              {isNew && (
                <div role="note" className="alert alert-info">
                  Để trống mật khẩu ban đầu thì dùng mật khẩu mặc định <strong>{DEFAULT_PASSWORD}</strong>. Dù thế nào người dùng cũng phải
                  đổi mật khẩu ngay lần đăng nhập đầu; báo mật khẩu cho người dùng qua kênh riêng.
                </div>
              )}
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
                {authorities
                  // ROLE_USER chỉ còn cho tài khoản admin có sẵn; không cấp mới.
                  .filter(role => role !== 'ROLE_USER')
                  .map(role => (
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
