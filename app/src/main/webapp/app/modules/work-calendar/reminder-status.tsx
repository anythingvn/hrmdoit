import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { instantToVn } from 'app/entities/employee/vn-date';

interface IStatus {
  mailConfigured: boolean;
  lastRun?: { runDate: string; startedAt: string; automatic: boolean; recipients: number; items: number; error?: string | null } | null;
  missingRecipients: string[];
}

/** Tình trạng email nhắc việc (Quản trị): SMTP, lần gửi gần nhất, người chưa có email, gửi thử. */
export const ReminderStatus = () => {
  const [status, setStatus] = useState<IStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  const load = () =>
    axios
      .get<IStatus>('api/admin/reminders/status')
      .then(r => setStatus(r.data))
      .catch(err => setError(errorText(err, 'Không tải được tình trạng email nhắc việc')));

  useEffect(() => {
    load();
  }, []);

  const testSend = async () => {
    setError(null);
    setNotice(null);
    setSending(true);
    try {
      const r = await axios.post<{ sentTo: string }>('api/admin/reminders/test-send');
      setNotice(`Đã gửi thử tới ${r.data.sentTo}.`);
    } catch (err) {
      setError(errorText(err, 'Không gửi thử được'));
    } finally {
      setSending(false);
    }
  };

  const run = status?.lastRun;
  return (
    <section className="doit-card" aria-labelledby="reminder-status-title">
      <h2 className="h5" id="reminder-status-title">
        Email nhắc việc
      </h2>
      <p className="text-muted">
        Gửi lúc 7:30 mỗi ngày làm việc: HR, Kế toán, Quản trị nhận mọi mục; quản lý trực tiếp nhận mục về nhân viên của mình. Email không có
        số tiền.
      </p>
      {error && <Alert variant="danger">{error}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}
      {status && !status.mailConfigured && (
        <Alert variant="warning">Chưa cấu hình máy chủ gửi email (SMTP): email nhắc việc chưa được gửi. Cấu hình ở Cài đặt → Email.</Alert>
      )}
      {status && (
        <ul className="mb-2">
          <li>
            {run
              ? `Lần gửi gần nhất: ${instantToVn(run.startedAt, true)} (${run.automatic ? 'tự động' : 'thủ công'}) – ${run.recipients} người nhận, ${run.items} mục.`
              : 'Chưa có lần gửi nào.'}
            {run?.error && <div className="text-danger">Lỗi: {run.error}</div>}
          </li>
          {status.missingRecipients.length > 0 && (
            <li>
              Chưa có email, không nhận được nhắc:
              <ul>
                {status.missingRecipients.map(m => (
                  <li key={m}>{m}</li>
                ))}
              </ul>
            </li>
          )}
        </ul>
      )}
      <Button variant="outline-primary" onClick={testSend} disabled={sending || !status?.mailConfigured}>
        Gửi thử ngay
      </Button>
    </section>
  );
};

export default ReminderStatus;
