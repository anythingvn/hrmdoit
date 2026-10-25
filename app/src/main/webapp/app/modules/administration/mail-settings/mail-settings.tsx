import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Form } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { instantToVn } from 'app/entities/employee/vn-date';

type Security = 'NONE' | 'STARTTLS' | 'SSL';

interface IMailSettings {
  host?: string | null;
  port: number;
  security: Security;
  username?: string | null;
  passwordSet: boolean;
  fromAddress?: string | null;
  fromName?: string | null;
  source: 'DATABASE' | 'ENV';
  configured: boolean;
  keyAvailable: boolean;
  updatedBy?: string | null;
  updatedAt?: string | null;
}

const SECURITY_LABEL: Record<Security, string> = {
  STARTTLS: 'STARTTLS (thường cổng 587)',
  SSL: 'SSL/TLS (thường cổng 465)',
  NONE: 'Không mã hóa (thường cổng 25)',
};

const emptyForm = { host: '', port: '587', security: 'STARTTLS' as Security, username: '', password: '', fromAddress: '', fromName: '' };

/**
 * Cấu hình gửi email (Quản trị): máy chủ SMTP, tài khoản, người gửi dùng cho mọi thư (đặt lại mật khẩu, nhắc việc, phiếu
 * lương). Có hiệu lực ngay. Mật khẩu không bao giờ hiện lại; để trống = giữ mật khẩu đang lưu.
 */
export const MailSettings = () => {
  const [settings, setSettings] = useState<IMailSettings | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [clearPassword, setClearPassword] = useState(false);
  const [testTo, setTestTo] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const show = (s: IMailSettings) => {
    setSettings(s);
    setForm({
      host: s.host ?? '',
      port: String(s.port ?? 587),
      security: s.security ?? 'STARTTLS',
      username: s.username ?? '',
      password: '',
      fromAddress: s.fromAddress ?? '',
      fromName: s.fromName ?? '',
    });
    setClearPassword(false);
  };

  useEffect(() => {
    axios
      .get<IMailSettings>('api/admin/mail-settings')
      .then(r => show(r.data))
      .catch(err => setError(errorText(err, 'Không tải được cấu hình email')));
  }, []);

  const run = async (action: () => Promise<void>) => {
    setError(null);
    setNotice(null);
    setBusy(true);
    try {
      await action();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const save = (ev: React.FormEvent) => {
    ev.preventDefault();
    run(async () => {
      const r = await axios.put<IMailSettings>('api/admin/mail-settings', {
        host: form.host.trim(),
        port: Number(form.port),
        security: form.security,
        username: form.username.trim() || null,
        password: form.password ? form.password : null,
        clearPassword,
        fromAddress: form.fromAddress.trim(),
        fromName: form.fromName.trim() || null,
      });
      show(r.data);
      setNotice('Đã lưu cấu hình email. Có hiệu lực ngay cho mọi thư.');
    });
  };

  const test = () =>
    run(async () => {
      const r = await axios.post<{ sentTo: string }>('api/admin/mail-settings/test', { to: testTo.trim() || null });
      setNotice(`Đã gửi thư thử tới ${r.data.sentTo}. Kiểm tra hộp thư (cả mục Spam).`);
    });

  const reset = () =>
    run(async () => {
      await axios.delete('api/admin/mail-settings');
      const r = await axios.get<IMailSettings>('api/admin/mail-settings');
      show(r.data);
      setNotice('Đã trở lại cấu hình trong .env.');
    });

  const set = (key: keyof typeof emptyForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setForm(f => ({ ...f, [key]: e.target.value }));

  if (!settings) return error ? <Alert variant="danger">{error}</Alert> : <p>Đang tải...</p>;

  return (
    <div className="doit-card" style={{ maxWidth: 760 }}>
      <h2 className="h4">Cấu hình email</h2>
      <p className="text-muted">
        Máy chủ gửi thư dùng cho mọi email của hệ thống: đặt lại mật khẩu, nhắc việc hằng ngày, phiếu lương.{' '}
        {settings.source === 'DATABASE'
          ? `Đang dùng cấu hình lưu trên giao diện${settings.updatedBy ? ` (${settings.updatedBy}, ${instantToVn(settings.updatedAt, true)})` : ''}.`
          : 'Đang dùng cấu hình trong .env (SPRING_MAIL_*). Lưu ở đây thì cấu hình này được dùng thay.'}
      </p>
      {!settings.keyAvailable && (
        <Alert variant="warning">
          Chưa đặt khóa mã hóa <code>APPLICATION_MAIL_SECRET_KEY</code> trong .env nên chưa lưu được mật khẩu email ở đây. Sinh khóa bằng{' '}
          <code>openssl rand -base64 32</code>, thêm vào .env rồi chạy lại <code>docker compose up -d</code>.
        </Alert>
      )}
      {!settings.configured && <Alert variant="warning">Chưa có máy chủ SMTP: hệ thống chưa gửi được email.</Alert>}
      {error && <Alert variant="danger">{error}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}

      <Form onSubmit={save} noValidate className="row g-3">
        <Form.Group className="col-12 col-md-8" controlId="mail-host">
          <Form.Label>Máy chủ SMTP</Form.Label>
          <Form.Control value={form.host} onChange={set('host')} placeholder="vd mail.congty.vn" />
        </Form.Group>
        <Form.Group className="col-6 col-md-4" controlId="mail-port">
          <Form.Label>Cổng</Form.Label>
          <Form.Control inputMode="numeric" value={form.port} onChange={set('port')} />
        </Form.Group>
        <Form.Group className="col-12 col-md-6" controlId="mail-security">
          <Form.Label>Bảo mật</Form.Label>
          <Form.Select value={form.security} onChange={set('security')}>
            {Object.entries(SECURITY_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
        <Form.Group className="col-12 col-md-6" controlId="mail-username">
          <Form.Label>Tài khoản</Form.Label>
          <Form.Control
            value={form.username}
            onChange={set('username')}
            autoComplete="off"
            placeholder="Để trống nếu máy chủ không yêu cầu"
          />
        </Form.Group>
        <Form.Group className="col-12 col-md-6" controlId="mail-password">
          <Form.Label>Mật khẩu</Form.Label>
          <Form.Control
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={set('password')}
            disabled={!settings.keyAvailable || clearPassword}
            placeholder={settings.passwordSet ? 'Đã đặt mật khẩu — để trống nếu không đổi' : 'Chưa đặt mật khẩu'}
          />
          {settings.passwordSet && settings.source === 'DATABASE' && (
            <Form.Check
              className="mt-1"
              id="mail-clear-password"
              label="Xóa mật khẩu đang lưu"
              checked={clearPassword}
              onChange={e => setClearPassword(e.target.checked)}
            />
          )}
        </Form.Group>
        <Form.Group className="col-12 col-md-6" controlId="mail-from">
          <Form.Label>Email gửi</Form.Label>
          <Form.Control value={form.fromAddress} onChange={set('fromAddress')} placeholder="vd nhansu@congty.vn" />
        </Form.Group>
        <Form.Group className="col-12 col-md-6" controlId="mail-from-name">
          <Form.Label>Tên hiển thị</Form.Label>
          <Form.Control value={form.fromName} onChange={set('fromName')} placeholder="vd Nhân sự DOIT" />
        </Form.Group>
        <div className="col-12 d-flex flex-wrap gap-2">
          <Button type="submit" disabled={busy}>
            Lưu cấu hình
          </Button>
          {settings.source === 'DATABASE' && (
            <Button variant="outline-secondary" disabled={busy} onClick={reset}>
              Dùng lại cấu hình .env
            </Button>
          )}
        </div>
      </Form>

      <hr />
      <h3 className="h5">Gửi thư thử</h3>
      <div className="row g-2 align-items-end">
        <Form.Group className="col-12 col-md-8" controlId="mail-test-to">
          <Form.Label>Gửi thư thử tới</Form.Label>
          <Form.Control value={testTo} onChange={e => setTestTo(e.target.value)} placeholder="Để trống: email của tài khoản bạn" />
        </Form.Group>
        <div className="col-12 col-md-4">
          <Button variant="outline-primary" disabled={busy} onClick={test}>
            Gửi thư thử
          </Button>
        </div>
      </div>
    </div>
  );
};

export default MailSettings;
