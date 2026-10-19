import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Button } from 'react-bootstrap';

import { AUTHORITIES, ROLE_LABELS } from 'app/config/constants';

import { errorText, useRoles } from './detail-api';
import { SelectField, ServerError, TextField } from './form-fields';

/** Giống ràng buộc ở server (EmployeeAccountCreateDTO). */
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 100;
const LOGIN_PATTERN = /^[_.@A-Za-z0-9-]+$/;

interface IAccount {
  userId?: number | null;
  login?: string | null;
  activated?: boolean | null;
  authorities?: string[] | null;
}

interface ICandidate {
  id: number;
  login: string;
  name?: string;
  activated?: boolean;
}

const ROLE_ORDER = [AUTHORITIES.NHAN_VIEN, AUTHORITIES.HR, AUTHORITIES.KE_TOAN, AUTHORITIES.BGD, AUTHORITIES.ADMIN];
const ROLE_CHECK_LABEL: Record<string, string> = {
  ...ROLE_LABELS,
  [AUTHORITIES.NHAN_VIEN]: 'Nhân viên (chỉ xem hồ sơ của mình)',
};

const roleNames = (authorities?: string[] | null) =>
  ROLE_ORDER.filter(r => (authorities ?? []).includes(r))
    .map(r => ROLE_LABELS[r])
    .join(', ') || '—';

interface IAccountTabProps {
  employeeId: number;
  onChanged?: () => void;
}

/** Tab "Tài khoản đăng nhập" trên hồ sơ (ADMIN/HR): tạo kèm mật khẩu ban đầu, gắn tài khoản có sẵn, bỏ gắn. */
export const AccountTab = ({ employeeId, onChanged }: IAccountTabProps) => {
  const { authorities } = useRoles();
  const isAdmin = authorities.includes(AUTHORITIES.ADMIN);
  const [account, setAccount] = useState<IAccount | null>(null);
  const [candidates, setCandidates] = useState<ICandidate[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [confirmUnlink, setConfirmUnlink] = useState(false);

  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [roles, setRoles] = useState<string[]>([AUTHORITIES.NHAN_VIEN]);
  const [userId, setUserId] = useState('');

  const load = () => {
    setLoadError(null);
    axios
      .get<IAccount>(`api/employees/${employeeId}/account`)
      .then(r => {
        setAccount(r.data ?? {});
        if (!r.data?.login) {
          axios
            .get<ICandidate[]>('api/employees/account-candidates')
            .then(c => setCandidates(c.data ?? []))
            .catch(() => setCandidates([]));
        }
      })
      .catch(err => setLoadError(errorText(err, 'Không tải được tài khoản')));
  };

  useEffect(() => {
    load();
  }, [employeeId]);

  const done = () => {
    setLogin('');
    setPassword('');
    setConfirm('');
    setRoles([AUTHORITIES.NHAN_VIEN]);
    setUserId('');
    setErrors({});
    setConfirmUnlink(false);
    load();
    onChanged?.();
  };

  const run = async (action: () => Promise<unknown>, fallback: string) => {
    setServerError(null);
    setSaving(true);
    try {
      await action();
      done();
    } catch (err) {
      setServerError(errorText(err, fallback));
    } finally {
      setSaving(false);
    }
  };

  const create = (ev: React.FormEvent) => {
    ev.preventDefault();
    const e: Record<string, string> = {};
    const trimmed = login.trim();
    if (!trimmed) e.login = 'Cần tên đăng nhập';
    else if (trimmed.length > 50 || !LOGIN_PATTERN.test(trimmed)) e.login = 'Chỉ dùng chữ không dấu, số và các ký tự . _ - @ (tối đa 50)';
    if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX)
      e.password = `Mật khẩu phải từ ${PASSWORD_MIN} đến ${PASSWORD_MAX} ký tự`;
    else if (confirm !== password) e.confirm = 'Mật khẩu nhập lại không khớp';
    if (roles.length === 0) e.roles = 'Chọn ít nhất một vai trò';
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    void run(
      () => axios.post(`api/employees/${employeeId}/account`, { login: trimmed, password, authorities: roles }),
      'Không tạo được tài khoản',
    );
  };

  const link = (ev: React.FormEvent) => {
    ev.preventDefault();
    if (!userId) {
      setErrors({ userId: 'Chọn một tài khoản' });
      return;
    }
    setErrors({});
    void run(() => axios.put(`api/employees/${employeeId}/account`, { userId: Number(userId) }), 'Không gắn được tài khoản');
  };

  const unlink = () => void run(() => axios.delete(`api/employees/${employeeId}/account`), 'Không bỏ gắn được tài khoản');

  const toggleRole = (role: string) => setRoles(rs => (rs.includes(role) ? rs.filter(r => r !== role) : [...rs, role]));

  if (loadError) {
    return (
      <div role="alert" className="alert alert-danger">
        {loadError}
      </div>
    );
  }
  if (!account) return <p>Đang tải...</p>;

  if (account.login) {
    return (
      <section className="doit-section d-flex flex-column gap-3" aria-labelledby="account-linked">
        <h2 id="account-linked">Tài khoản đăng nhập</h2>
        <ServerError message={serverError} />
        <dl className="doit-dl">
          <div>
            <dt>Tên đăng nhập</dt>
            <dd>{account.login}</dd>
          </div>
          <div>
            <dt>Trạng thái</dt>
            <dd>{account.activated ? 'Đang mở' : 'Đã khóa'}</dd>
          </div>
          <div>
            <dt>Vai trò</dt>
            <dd>{roleNames(account.authorities)}</dd>
          </div>
        </dl>
        {confirmUnlink ? (
          <div role="alert" className="alert alert-warning d-flex flex-wrap align-items-center gap-2 mb-0">
            <span>Bỏ gắn tài khoản {account.login} khỏi hồ sơ này? Tài khoản vẫn còn, có thể gắn lại sau.</span>
            <Button type="button" variant="outline-secondary" onClick={() => setConfirmUnlink(false)} disabled={saving}>
              Hủy
            </Button>
            <Button type="button" className="emp-btn-accent" onClick={unlink} disabled={saving}>
              Xác nhận bỏ gắn
            </Button>
          </div>
        ) : (
          <div>
            <Button type="button" variant="outline-secondary" onClick={() => setConfirmUnlink(true)}>
              Bỏ gắn tài khoản
            </Button>
          </div>
        )}
      </section>
    );
  }

  const grantable = ROLE_ORDER.filter(r => r !== AUTHORITIES.ADMIN || isAdmin);

  return (
    <div className="d-flex flex-column gap-4">
      <p className="mb-0">Hồ sơ chưa có tài khoản đăng nhập.</p>
      <ServerError message={serverError} />
      <section className="doit-section" aria-labelledby="account-create">
        <h2 id="account-create">Tạo tài khoản mới</h2>
        <form onSubmit={create} noValidate>
          <div className="row g-3">
            <TextField
              id="account-login"
              label="Tên đăng nhập"
              className="col-md-4"
              value={login}
              onChange={setLogin}
              error={errors.login}
              required
            />
            <TextField
              id="account-password"
              label="Mật khẩu ban đầu"
              type="password"
              className="col-md-4"
              value={password}
              onChange={setPassword}
              error={errors.password}
              hint={`Từ ${PASSWORD_MIN} đến ${PASSWORD_MAX} ký tự; đăng nhập được ngay, không cần email`}
              required
            />
            <TextField
              id="account-password-confirm"
              label="Nhập lại mật khẩu"
              type="password"
              className="col-md-4"
              value={confirm}
              onChange={setConfirm}
              error={errors.confirm}
              required
            />
            <fieldset className="col-12">
              <legend className="form-label fs-6">Vai trò</legend>
              <div className="d-flex flex-wrap gap-3">
                {grantable.map(role => (
                  <div className="form-check emp-role-check" key={role}>
                    <input
                      className="form-check-input"
                      type="checkbox"
                      id={`account-role-${role}`}
                      checked={roles.includes(role)}
                      onChange={() => toggleRole(role)}
                    />
                    <label className="form-check-label" htmlFor={`account-role-${role}`}>
                      {ROLE_CHECK_LABEL[role]}
                    </label>
                  </div>
                ))}
              </div>
              {errors.roles && <div className="invalid-feedback d-block">{errors.roles}</div>}
            </fieldset>
          </div>
          <Button type="submit" variant="primary" className="mt-3" disabled={saving}>
            Tạo tài khoản
          </Button>
        </form>
      </section>
      <section className="doit-section" aria-labelledby="account-link">
        <h2 id="account-link">Gắn tài khoản có sẵn</h2>
        <form onSubmit={link} noValidate>
          <div className="row g-3">
            <SelectField
              id="account-existing"
              label="Tài khoản có sẵn"
              className="col-md-6"
              value={userId}
              onChange={setUserId}
              options={candidates.map(c => [String(c.id), c.name ? `${c.login} (${c.name})` : c.login] as [string, string])}
              emptyLabel={candidates.length === 0 ? 'Không có tài khoản nào chưa gắn' : 'Chọn tài khoản'}
              error={errors.userId}
            />
          </div>
          <Button type="submit" variant="outline-secondary" className="mt-3" disabled={saving || candidates.length === 0}>
            Gắn tài khoản
          </Button>
        </form>
      </section>
    </div>
  );
};

export default AccountTab;
