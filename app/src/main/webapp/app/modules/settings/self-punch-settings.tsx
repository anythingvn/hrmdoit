import React, { useEffect, useState } from 'react';
import { Alert, Button, Form } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';

import { settingsApi } from './settings-api';

const MIN_DAYS = 1;
const MAX_DAYS = 3650;

/**
 * Khối "Tự chấm công" của tab Địa điểm chấm công: nội dung thông báo thu thập vị trí (nhân viên đồng ý ở lần chấm đầu; đổi nội dung
 * không bắt đồng ý lại) và số ngày giữ tọa độ, IP của lần chấm (quá hạn thì xóa tọa độ, IP, giữ điểm chấm và kết quả). Chỉ Quản trị
 * sửa; HR xem.
 */
export const SelfPunchSettings = ({ canEdit }: { canEdit: boolean }) => {
  const [text, setText] = useState('');
  const [days, setDays] = useState('');
  const [version, setVersion] = useState<number | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    settingsApi
      .attendanceNotice()
      .then(s => {
        setText(s.noticeText ?? '');
        setDays(String(s.locationRetentionDays));
        setVersion(s.noticeVersion ?? null);
        setLoaded(true);
      })
      .catch(err => setError(errorText(err, 'Không tải được cài đặt tự chấm công')));
  }, []);

  const save = async () => {
    setNotice(null);
    const n = days.trim();
    if (!/^\d+$/.test(n) || Number(n) < MIN_DAYS || Number(n) > MAX_DAYS) {
      setError(`Số ngày giữ tọa độ từ ${MIN_DAYS} đến ${MAX_DAYS}`);
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const s = await settingsApi.saveAttendanceNotice({ noticeText: text.trim() || null, locationRetentionDays: Number(n) });
      setText(s.noticeText ?? '');
      setDays(String(s.locationRetentionDays));
      setVersion(s.noticeVersion ?? null);
      setNotice('Đã lưu cài đặt tự chấm công.');
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="d-flex flex-column gap-3 mt-4" aria-label="Tự chấm công">
      <h2 className="h4 mb-0">Tự chấm công</h2>
      <p className="text-muted mb-0">
        Nhân viên đồng ý nội dung này ở lần chấm đầu tiên. Đổi nội dung không bắt họ đồng ý lại.
        {!canEdit && ' Chỉ Quản trị sửa được.'}
      </p>
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
      {!loaded && !error && <p className="mb-0">Đang tải...</p>}
      {loaded && (
        <form
          noValidate
          className="d-flex flex-column gap-3"
          onSubmit={e => {
            e.preventDefault();
            if (canEdit) void save();
          }}
        >
          <Form.Group controlId="self-punch-notice">
            <Form.Label>Nội dung thông báo</Form.Label>
            <Form.Control
              as="textarea"
              rows={6}
              maxLength={10000}
              readOnly={!canEdit}
              value={text}
              onChange={e => setText(e.target.value)}
            />
            {version !== null && <Form.Text muted>Phiên bản hiện tại: {version}. Sửa nội dung thì phiên bản tăng 1.</Form.Text>}
          </Form.Group>
          <Form.Group controlId="self-punch-retention" style={{ maxWidth: 260 }}>
            <Form.Label>Số ngày giữ tọa độ và IP</Form.Label>
            <Form.Control
              type="number"
              min={MIN_DAYS}
              max={MAX_DAYS}
              readOnly={!canEdit}
              value={days}
              onChange={e => setDays(e.target.value)}
            />
            <Form.Text muted>Từ 1 đến 3650 ngày, mặc định 90. Quá hạn, tọa độ và IP bị xóa; điểm chấm và kết quả vẫn giữ.</Form.Text>
          </Form.Group>
          {canEdit && (
            <div>
              <Button type="submit" style={{ minHeight: 44 }} disabled={busy}>
                Lưu cài đặt tự chấm công
              </Button>
            </div>
          )}
        </form>
      )}
    </section>
  );
};

export default SelfPunchSettings;
