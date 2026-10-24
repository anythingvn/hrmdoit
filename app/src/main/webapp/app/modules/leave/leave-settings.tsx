import './leave.scss';

import React, { useEffect, useState } from 'react';
import { Alert, Button, Form } from 'react-bootstrap';

import { ILeaveSettings, leaveApi, leaveErrorText } from './leave-api';

const SETTING_FIELDS: { key: keyof ILeaveSettings; label: string; min: number; max: number }[] = [
  { key: 'advanceDays', label: 'Số ngày phép được ứng', min: 0, max: 5 },
  { key: 'directorThresholdDays', label: 'Ngưỡng chuyển giám đốc duyệt (ngày làm việc)', min: 1, max: 60 },
  { key: 'otMonthCap', label: 'Giới hạn làm thêm mỗi tháng (giờ)', min: 0, max: 300 },
  { key: 'otYearCap', label: 'Giới hạn làm thêm mỗi năm (giờ)', min: 0, max: 3000 },
  { key: 'carryExpiryDay', label: 'Hạn dùng phép năm trước: ngày', min: 1, max: 31 },
  { key: 'carryExpiryMonth', label: 'Hạn dùng phép năm trước: tháng', min: 1, max: 12 },
  { key: 'pastRequestDays', label: 'Được xin nghỉ cho ngày đã qua tối đa (ngày)', min: 0, max: 366 },
  { key: 'otConfirmDay', label: 'Hạn xác nhận giờ làm thêm (ngày của tháng sau)', min: 1, max: 28 },
  { key: 'seniorityCycleYears', label: 'Chu kỳ thâm niên (năm)', min: 1, max: 10 },
  { key: 'seniorityExtraDays', label: 'Số ngày cộng mỗi chu kỳ thâm niên', min: 0, max: 5 },
];

/**
 * Cài đặt nghỉ phép (Cài đặt → Nghỉ phép, chỉ Quản trị sửa; máy chủ kiểm tra lại quyền). Quy định mới chỉ áp dụng từ nay, không
 * tính lại số đã ghi.
 */
export const LeaveSettings = ({ canEdit = true }: { canEdit?: boolean }) => {
  const [saved, setSaved] = useState<ILeaveSettings | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const fill = (s: ILeaveSettings) => {
    setSaved(s);
    setValues(Object.fromEntries(SETTING_FIELDS.map(f => [f.key, String(s[f.key])])));
  };

  useEffect(() => {
    leaveApi
      .settings()
      .then(fill)
      .catch(err => setError(leaveErrorText(err, 'ngày', 'Không tải được cài đặt')));
  }, []);

  const save = async () => {
    if (!saved) return;
    const dto = { ...saved };
    for (const f of SETTING_FIELDS) {
      const raw = (values[f.key] ?? '').trim();
      const v = Number(raw);
      if (!raw || !Number.isInteger(v) || v < f.min || v > f.max) {
        setError(`${f.label}: nhập số nguyên từ ${f.min} đến ${f.max}`);
        return;
      }
      dto[f.key] = v;
    }
    setError(null);
    setNotice(null);
    setBusy(true);
    try {
      fill(await leaveApi.saveSettings(dto));
      setNotice('Đã lưu cài đặt nghỉ phép. Quy định mới chỉ áp dụng từ nay, không tính lại số đã ghi.');
    } catch (err) {
      setError(leaveErrorText(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="leave-section d-flex flex-column gap-3" aria-label="Cài đặt nghỉ phép">
      {!canEdit && <p className="text-muted mb-0">Chỉ Quản trị sửa được cài đặt.</p>}
      {notice && (
        <Alert variant="success" className="mb-0" onClose={() => setNotice(null)} dismissible>
          {notice}
        </Alert>
      )}
      {error && (
        <Alert variant="danger" role="alert" className="mb-0">
          {error}
        </Alert>
      )}
      {!saved && !error && <p className="mb-0">Đang tải...</p>}
      {saved && (
        <div className="leave-settings-grid">
          {SETTING_FIELDS.map(f => (
            <Form.Group key={f.key} controlId={`setting-${f.key}`}>
              <Form.Label>{f.label}</Form.Label>
              <Form.Control
                type="number"
                min={f.min}
                max={f.max}
                disabled={!canEdit}
                value={values[f.key] ?? ''}
                onChange={e => setValues(s => ({ ...s, [f.key]: e.target.value }))}
              />
            </Form.Group>
          ))}
        </div>
      )}
      {saved && canEdit && (
        <div>
          <Button disabled={busy} onClick={() => void save()}>
            Lưu cài đặt
          </Button>
        </div>
      )}
    </section>
  );
};

export default LeaveSettings;
