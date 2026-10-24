import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Badge, Button, Row } from 'react-bootstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { Link, useParams } from 'react-router';

import { faArrowLeft } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { languages } from 'app/config/translation';

import { getUser } from './user-management.reducer';
import SetPasswordDialog, { canSetPassword } from './set-password-dialog';

export const UserManagementDetail = () => {
  const dispatch = useAppDispatch();

  const { login } = useParams<'login'>();

  useEffect(() => {
    dispatch(getUser(login!));
  }, []);

  const user = useAppSelector(state => state.userManagement.user);
  const account = useAppSelector(state => state.authentication.account);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [unlockMessage, setUnlockMessage] = useState<{ ok: boolean; text: string } | null>(null);

  /** Mở khóa đăng nhập khi tài khoản bị chặn vì nhập sai mật khẩu quá nhiều lần. */
  const unlockLogin = async () => {
    setUnlockMessage(null);
    try {
      await axios.delete(`api/admin/login-attempts/${encodeURIComponent(user.login)}`);
      setUnlockMessage({ ok: true, text: 'Đã mở khóa đăng nhập. Người dùng có thể đăng nhập lại ngay.' });
    } catch {
      setUnlockMessage({ ok: false, text: 'Không mở khóa được, vui lòng thử lại.' });
    }
  };

  return (
    <div>
      <h2 data-cy="userManagementDetailsHeading">
        <Translate contentKey="userManagement.detail.title">User</Translate> [<strong>{user.login}</strong>]
      </h2>
      <Row size="md">
        <dl className="jh-entity-details">
          <dt>
            <Translate contentKey="userManagement.login">Login</Translate>
          </dt>
          <dd>
            <span>{user.login}</span>&nbsp;
            {user.activated ? (
              <Badge bg="success">
                <Translate contentKey="userManagement.activated">Activated</Translate>
              </Badge>
            ) : (
              <Badge bg="danger">
                <Translate contentKey="userManagement.deactivated">Deactivated</Translate>
              </Badge>
            )}
          </dd>
          <dt>
            <Translate contentKey="userManagement.firstName">First Name</Translate>
          </dt>
          <dd>{user.firstName}</dd>
          <dt>
            <Translate contentKey="userManagement.lastName">Last Name</Translate>
          </dt>
          <dd>{user.lastName}</dd>
          <dt>
            <Translate contentKey="userManagement.email">Email</Translate>
          </dt>
          <dd>{user.email}</dd>
          <dt>
            <Translate contentKey="userManagement.langKey">Lang Key</Translate>
          </dt>
          <dd>{user.langKey ? languages[user.langKey].name : undefined}</dd>
          <dt>
            <Translate contentKey="userManagement.createdBy">Created By</Translate>
          </dt>
          <dd>{user.createdBy}</dd>
          <dt>
            <Translate contentKey="userManagement.createdDate">Created Date</Translate>
          </dt>
          <dd>{user.createdDate && <TextFormat value={user.createdDate} type="date" format={APP_DATE_FORMAT} blankOnInvalid />}</dd>
          <dt>
            <Translate contentKey="userManagement.lastModifiedBy">Last Modified By</Translate>
          </dt>
          <dd>{user.lastModifiedBy}</dd>
          <dt>
            <Translate contentKey="userManagement.lastModifiedDate">Last Modified Date</Translate>
          </dt>
          <dd>
            {user.lastModifiedDate && <TextFormat value={user.lastModifiedDate} type="date" format={APP_DATE_FORMAT} blankOnInvalid />}
          </dd>
          <dt>
            <Translate contentKey="userManagement.profiles">Profiles</Translate>
          </dt>
          <dd>
            <ul className="list-unstyled">
              {user.authorities?.map((authority, i) => (
                <li key={`user-auth-${i}`}>
                  <Badge bg="info">{authority}</Badge>
                </li>
              ))}
            </ul>
          </dd>
        </dl>
      </Row>
      <Button as={Link as any} to="/admin/user-management" replace variant="info" data-cy="entityDetailsBackButton">
        <FontAwesomeIcon icon={faArrowLeft} />{' '}
        <span className="d-none d-md-inline">
          <Translate contentKey="entity.action.back">Back</Translate>
        </span>
      </Button>{' '}
      {user.login && (
        <>
          {/* Như danh sách: ẩn với tài khoản admin và tài khoản của chính mình. */}
          {canSetPassword(user.login, account?.login) && (
            <>
              <Button type="button" variant="primary" onClick={() => setPasswordOpen(true)}>
                Đặt mật khẩu
              </Button>
              <SetPasswordDialog login={user.login} isOpen={passwordOpen} toggle={() => setPasswordOpen(false)} />{' '}
            </>
          )}
          <Button type="button" variant="outline-secondary" onClick={() => void unlockLogin()}>
            Mở khóa đăng nhập
          </Button>
          {unlockMessage && (
            <Alert variant={unlockMessage.ok ? 'success' : 'danger'} className="mt-3" role="status">
              {unlockMessage.text}
            </Alert>
          )}
        </>
      )}
    </div>
  );
};

export default UserManagementDetail;
