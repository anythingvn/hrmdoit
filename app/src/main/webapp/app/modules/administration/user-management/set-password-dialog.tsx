import React, { useState } from 'react';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { ServerError, TextField } from 'app/entities/employee/detail/form-fields';

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
  const [done, setDone] = useState(false);
  const [saving, setSaving] = useState(false);

  const close = () => {
    setPassword('');
    setConfirm('');
    setErrors({});
    setServerError(null);
    setDone(false);
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
      setDone(true);
    } catch (err) {
      setServerError(errorText(err, 'Không đặt được mật khẩu, vui lòng thử lại'));
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
              Đã đặt mật khẩu mới cho tài khoản {login}.
            </div>
          ) : (
            <>
              <p>Báo mật khẩu cho người dùng qua kênh riêng và nhắc họ đổi mật khẩu sau khi đăng nhập.</p>
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
