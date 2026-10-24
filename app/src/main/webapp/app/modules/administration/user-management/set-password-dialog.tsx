import React, { useState } from 'react';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { useDefaultPassword } from 'app/shared/util/default-password';
import { ServerError, TextField } from 'app/entities/employee/detail/form-fields';

/** Tài khoản Quản trị gốc: không đặt mật khẩu ở màn người dùng (Quản trị dùng chức năng đổi mật khẩu của chính mình). */
const ROOT_LOGIN = 'admin';

/** Có nút Đặt mật khẩu cho tài khoản {@code login}: không phải tài khoản admin, không phải chính người đang đăng nhập. */
export const canSetPassword = (login: string | undefined, currentLogin: string | undefined) =>
  !!login && login !== ROOT_LOGIN && login !== currentLogin;

/** Giống ràng buộc ở server (AdminPasswordDTO). */
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 100;

interface ISetPasswordDialogProps {
  login: string;
  isOpen: boolean;
  toggle: () => void;
}

/** Quản trị đặt mật khẩu cho một tài khoản (dùng khi không có máy chủ thư để gửi link đặt lại). */
export const SetPasswordDialog = ({ login, isOpen, toggle }: ISetPasswordDialogProps) => {
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [confirmDefault, setConfirmDefault] = useState(false);
  const defaultPassword = useDefaultPassword();
  const defaultLabel = defaultPassword ?? 'mật khẩu mặc định';

  const close = () => {
    setPassword('');
    setConfirm('');
    setErrors({});
    setServerError(null);
    setDone(null);
    setConfirmDefault(false);
    toggle();
  };

  const handleSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setServerError(null);
    const e: Record<string, string> = {};
    if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX)
      e.password = `Mật khẩu phải từ ${PASSWORD_MIN} đến ${PASSWORD_MAX} ký tự`;
    if (!e.password && confirm !== password) e.confirm = 'Mật khẩu nhập lại không khớp';
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    setSaving(true);
    try {
      await axios.put(`api/admin/users/${encodeURIComponent(login)}/password`, { newPassword: password });
      setPassword('');
      setConfirm('');
      setDone(`Đã đặt mật khẩu mới cho tài khoản ${login}.`);
    } catch (err) {
      setServerError(errorText(err, 'Không đặt được mật khẩu, vui lòng thử lại'));
    } finally {
      setSaving(false);
    }
  };

  /** Đặt lại mật khẩu mặc định: người dùng phải đổi mật khẩu ngay khi đăng nhập. */
  const resetDefault = async () => {
    setServerError(null);
    setSaving(true);
    try {
      await axios.put(`api/admin/users/${encodeURIComponent(login)}/password/default`);
      setConfirmDefault(false);
      setDone(`Đã đặt lại mật khẩu mặc định (${defaultLabel}) cho tài khoản ${login}; người dùng phải đổi mật khẩu khi đăng nhập.`);
    } catch (err) {
      setServerError(errorText(err, 'Không đặt lại được mật khẩu mặc định, vui lòng thử lại'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal className="doit-modal" show={isOpen} onHide={close} aria-labelledby="set-password-title" animation={false}>
      <form onSubmit={e => void handleSubmit(e)} noValidate>
        <ModalHeader closeButton closeLabel="Đóng">
          <ModalTitle as="h2" id="set-password-title">
            Đặt mật khẩu: {login}
          </ModalTitle>
        </ModalHeader>
        <ModalBody>
          <ServerError message={serverError} />
          {done ? (
            <div role="status" className="alert alert-success mb-0">
              {done}
            </div>
          ) : (
            <>
              <p>Báo mật khẩu cho người dùng qua kênh riêng; người dùng phải đổi mật khẩu ngay khi đăng nhập.</p>
              <div className="row g-3">
                <TextField
                  id="set-password-new"
                  label="Mật khẩu mới"
                  type="password"
                  className="col-12"
                  value={password}
                  onChange={setPassword}
                  error={errors.password}
                  hint={`Từ ${PASSWORD_MIN} đến ${PASSWORD_MAX} ký tự`}
                  required
                />
                <TextField
                  id="set-password-confirm"
                  label="Nhập lại mật khẩu mới"
                  type="password"
                  className="col-12"
                  value={confirm}
                  onChange={setConfirm}
                  error={errors.confirm}
                  required
                />
              </div>
              <div className="mt-3 pt-3 border-top">
                {confirmDefault ? (
                  <div role="alert" className="alert alert-warning d-flex flex-wrap align-items-center gap-2 mb-0">
                    <span>
                      Đặt lại mật khẩu của {login} về mật khẩu mặc định <strong>{defaultLabel}</strong>? Phiên đăng nhập hiện tại hết hiệu
                      lực; người dùng phải đổi mật khẩu khi đăng nhập.
                    </span>
                    <Button type="button" variant="outline-secondary" onClick={() => setConfirmDefault(false)} disabled={saving}>
                      Hủy
                    </Button>
                    <Button type="button" variant="warning" onClick={() => void resetDefault()} disabled={saving}>
                      Xác nhận đặt lại mặc định
                    </Button>
                  </div>
                ) : (
                  <Button type="button" variant="outline-secondary" onClick={() => setConfirmDefault(true)} disabled={saving}>
                    Đặt lại mật khẩu mặc định
                  </Button>
                )}
              </div>
            </>
          )}
        </ModalBody>
        <ModalFooter>
          <Button type="button" variant="outline-secondary" onClick={close}>
            {done ? 'Đóng' : 'Hủy'}
          </Button>
          {!done && (
            <Button type="submit" variant="primary" disabled={saving}>
              Lưu mật khẩu
            </Button>
          )}
        </ModalFooter>
      </form>
    </Modal>
  );
};

export default SetPasswordDialog;
