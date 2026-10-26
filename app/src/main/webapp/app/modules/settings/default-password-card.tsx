import React, { useEffect, useState } from 'react';
import { Alert, Button, Form } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { PASSWORD_MAX, PASSWORD_MIN } from 'app/modules/administration/user-management/set-password-dialog';
import { SectionCard } from 'app/shared/ui';

import { IDefaultPassword, settingsApi } from './settings-api';

const SOURCE_TEXT = (value: string): Record<IDefaultPassword['source'], string> => ({
  WEB: 'Cài trên web',
  ENV: 'Từ file .env trên máy chủ',
  DEFAULT: `Mặc định ${value}`,
});

/** Thẻ "Mật khẩu mặc định cho tài khoản mới" ở đầu tab Tài khoản (Quản trị): giá trị đang dùng, nguồn, đổi hoặc dùng lại mặc định. */
export const DefaultPasswordCard = () => {
  const [current, setCurrent] = useState<IDefaultPassword | null>(null);
  const [draft, setDraft] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    settingsApi
      .defaultPassword()
      .then(setCurrent)
      .catch(err => setError(errorText(err, 'Không tải được mật khẩu mặc định')));
  }, []);

  const send = async (value: string | null) => {
    setError(null);
    setNotice(null);
    setBusy(true);
    try {
      const res = await settingsApi.saveDefaultPassword(value);
      setCurrent(res);
      setDraft('');
      setNotice('Đã lưu mật khẩu mặc định. Chỉ áp dụng cho tài khoản tạo mới.');
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const save = () => {
    const value = draft.trim();
    if (value.length < PASSWORD_MIN || value.length > PASSWORD_MAX) {
      setError('Mật khẩu mặc định phải có từ 8 đến 100 ký tự.');
      return;
    }
    void send(value);
  };

  return (
    <SectionCard title="Mật khẩu mặc định cho tài khoản mới" titleId="default-password-title" className="mb-3">
      {current && (
        <p className="mb-0">
          Đang dùng: <strong>{current.defaultPassword}</strong> <span className="text-muted">·</span>{' '}
          <span className="text-muted">{SOURCE_TEXT(current.defaultPassword)[current.source]}</span>
        </p>
      )}
      <Form
        noValidate
        className="d-flex flex-wrap align-items-end gap-2"
        onSubmit={e => {
          e.preventDefault();
          save();
        }}
      >
        <Form.Group controlId="default-password-new" style={{ minWidth: 240 }}>
          <Form.Label>Mật khẩu mặc định mới</Form.Label>
          <Form.Control
            style={{ minHeight: 44 }}
            autoComplete="off"
            value={draft}
            maxLength={PASSWORD_MAX}
            placeholder={`Từ ${PASSWORD_MIN} ký tự trở lên`}
            onChange={e => setDraft(e.target.value)}
          />
        </Form.Group>
        <Button type="submit" style={{ minHeight: 44 }} disabled={busy} aria-label="Lưu mật khẩu mặc định">
          Lưu
        </Button>
        <Button variant="outline-secondary" style={{ minHeight: 44 }} disabled={busy} onClick={() => send(null)}>
          Dùng lại mặc định
        </Button>
      </Form>
      {error && (
        <Alert variant="danger" role="alert" className="mb-0">
          {error}
        </Alert>
      )}
      {notice && (
        <Alert variant="success" className="mb-0" onClose={() => setNotice(null)} dismissible>
          {notice}
        </Alert>
      )}
    </SectionCard>
  );
};

export default DefaultPasswordCard;
