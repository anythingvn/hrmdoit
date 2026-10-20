import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Button } from 'react-bootstrap';

import { AUTHORITIES, ROLE_LABELS } from 'app/config/constants';

import { errorText, useRoles } from './detail-api';
import { SelectField, ServerError, TextField } from './form-fields';

/** Mật khẩu mặc định của tài khoản mới (application.account.default-password ở server). */
export const DEFAULT_PASSWORD = 'Doit@2014';

/** Mật khẩu ban đầu tùy chọn: để trống = mật khẩu mặc định; nếu nhập thì 8–100 ký tự (giống server). */
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 100;
export const INITIAL_PASSWORD_LABEL = 'Mật khẩu ban đầu (tùy chọn)';
export const INITIAL_PASSWORD_PLACEHOLDER = 'Để trống = mật khẩu mặc định';
export const INITIAL_PASSWORD_ERROR = `Mật khẩu phải từ ${PASSWORD_MIN} đến ${PASSWORD_MAX} ký tự (hoặc để trống)`;

/** Mật khẩu ban đầu không hợp lệ (rỗng là hợp lệ). */
export const initialPasswordInvalid = (password: string) =>
  password !== '' && (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX);

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
  /** Hồ sơ đã nghỉ việc: không tạo / gắn tài khoản (server cũng chặn), chỉ xem và bỏ gắn. */
  terminated?: boolean;
}

const TERMINATED_TEXT = 'Nhân viên đã nghỉ — tài khoản bị khóa';

/**
 * Tab "Tài khoản đăng nhập" trên hồ sơ (ADMIN/HR): tạo tài khoản (tên đăng nhập = số CCCD của hồ sơ, mật khẩu mặc định),
 * gắn tài khoản có sẵn (đổi tên đăng nhập sang số CCCD), bỏ gắn.
 */
export const AccountTab = ({ employeeId, onChanged, terminated = false }: IAccountTabProps) => {
  const { authorities } = useRoles();
  const isAdmin = authorities.includes(AUTHORITIES.ADMIN);
  const [account, setAccount] = useState<IAccount | null>(null);
  const [candidates, setCandidates] = useState<ICandidate[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [confirmUnlink, setConfirmUnlink] = useState(false);

  const [password, setPassword] = useState('');
  const [roles, setRoles] = useState<string[]>([AUTHORITIES.NHAN_VIEN]);
  const [userId, setUserId] = useState('');

  const load = () => {
    setLoadError(null);
    axios
      .get<IAccount>(`api/employees/${employeeId}/account`)
      .then(r => {
        setAccount(r.data ?? {});
        if (!r.data?.login && !terminated) {
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
    setPassword('');
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
    if (initialPasswordInvalid(password)) e.password = INITIAL_PASSWORD_ERROR;
    if (roles.length === 0) e.roles = 'Chọn ít nhất một vai trò';
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    void run(
      () => axios.post(`api/employees/${employeeId}/account`, password ? { password, authorities: roles } : { authorities: roles }),
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
        {terminated && (
          <div role="status" className="alert alert-secondary mb-0">
            {TERMINATED_TEXT}
          </div>
        )}
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

  if (terminated) {
    return (
      <div role="status" className="alert alert-secondary">
        {TERMINATED_TEXT}
      </div>
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
          <p id="account-create-hint">
            Tên đăng nhập là số CCCD trong hồ sơ (cần nhập số CCCD trước). Mật khẩu ban đầu: ô dưới đây, để trống thì dùng mật khẩu mặc định{' '}
            <strong>{DEFAULT_PASSWORD}</strong>. Dù thế nào người dùng cũng phải đổi mật khẩu ngay lần đăng nhập đầu.
          </p>
          <div className="row g-3">
            <TextField
              id="account-password"
              label={INITIAL_PASSWORD_LABEL}
              type="password"
              className="col-md-6"
              value={password}
              onChange={setPassword}
              placeholder={INITIAL_PASSWORD_PLACEHOLDER}
              error={errors.password}
              hint={`Từ ${PASSWORD_MIN} đến ${PASSWORD_MAX} ký tự. Báo mật khẩu cho người dùng qua kênh riêng.`}
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
        <div role="note" className="alert alert-warning">
          Khi gắn, tên đăng nhập của tài khoản được đổi thành số CCCD của hồ sơ (mật khẩu giữ nguyên); phiên đăng nhập đang dùng của tài
          khoản đó hết hiệu lực. Báo người dùng đăng nhập lại bằng số CCCD.
        </div>
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
