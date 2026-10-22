import React, { useState } from 'react';
import { Alert, Button, Form } from 'react-bootstrap';

import { vnToIso } from 'app/entities/employee/vn-date';

import { ILeaveBalance, IOvertime, leaveApi, leaveErrorText } from './leave-api';
import { num } from './leave-labels';

interface Props {
  balance?: ILeaveBalance | null;
  onSubmitted: (created: IOvertime) => void;
}

/** Số giờ giữa hai mốc "HH:mm"; kết thúc nhỏ hơn bắt đầu = qua nửa đêm. */
export const hoursBetween = (start: string, end: string): number => {
  const m = (t: string) => {
    const [h, mi] = t.split(':').map(Number);
    return h * 60 + mi;
  };
  if (!/^\d{2}:\d{2}/.test(start) || !/^\d{2}:\d{2}/.test(end)) return 0;
  let d = m(end) - m(start);
  if (d <= 0) d += 24 * 60;
  return Math.round((d / 60) * 100) / 100;
};

/**
 * Đăng ký làm thêm giờ trước khi làm: ngày, giờ bắt đầu – kết thúc, nội dung. Người duyệt chọn trả tiền hay nghỉ bù; sau khi làm,
 * xác nhận giờ thực tế trong bảng bên dưới.
 */
export const OvertimeForm = ({ balance, onSubmitted }: Props) => {
  const [day, setDay] = useState('');
  const [start, setStart] = useState('18:00');
  const [end, setEnd] = useState('20:00');
  const [content, setContent] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const hours = hoursBetween(start, end);

  const submit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setError(null);
    setNotice(null);
    const iso = vnToIso(day);
    if (!iso) {
      setError('Nhập ngày làm dạng dd/MM/yyyy');
      return;
    }
    if (!content.trim()) {
      setError('Nhập nội dung công việc');
      return;
    }
    setBusy(true);
    try {
      const created = await leaveApi.submitOvertime({ workDate: iso, startTime: start, endTime: end, content: content.trim() });
      setNotice(`Đã đăng ký ${num(created?.plannedHours ?? hours)} giờ làm thêm, đang chờ duyệt.`);
      setDay('');
      setContent('');
      onSubmitted(created);
    } catch (err) {
      setError(leaveErrorText(err, 'giờ', 'Không gửi được đơn, vui lòng thử lại'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Form onSubmit={submit} noValidate>
      {error && (
        <Alert variant="danger" role="alert">
          {error}
        </Alert>
      )}
      {notice && <Alert variant="success">{notice}</Alert>}
      <div className="row g-3">
        <Form.Group className="col-12 col-md-4" controlId="ot-day">
          <Form.Label>Ngày làm</Form.Label>
          <Form.Control value={day} placeholder="dd/MM/yyyy" inputMode="numeric" onChange={e => setDay(e.target.value)} />
        </Form.Group>
        <Form.Group className="col-6 col-md-4" controlId="ot-start">
          <Form.Label>Giờ bắt đầu</Form.Label>
          <Form.Control type="time" value={start} onChange={e => setStart(e.target.value)} />
        </Form.Group>
        <Form.Group className="col-6 col-md-4" controlId="ot-end">
          <Form.Label>Giờ kết thúc</Form.Label>
          <Form.Control type="time" value={end} onChange={e => setEnd(e.target.value)} />
        </Form.Group>
        <Form.Group className="col-12" controlId="ot-content">
          <Form.Label>Nội dung công việc</Form.Label>
          <Form.Control as="textarea" rows={2} maxLength={1000} value={content} onChange={e => setContent(e.target.value)} />
        </Form.Group>
      </div>
      <div className="d-flex flex-wrap align-items-center gap-3 mt-3">
        <span>
          {num(hours)} giờ{start > end ? ' (qua nửa đêm)' : ''}
          {balance?.overtime && (
            <span className="text-muted">
              {' '}
              · tháng này đã có {num(balance.overtime.monthHours)}/{balance.overtime.monthCap} giờ
            </span>
          )}
        </span>
        <span className="text-muted">Hệ số 150/200/300% tự tính theo ngày làm; người duyệt chọn trả tiền hoặc nghỉ bù.</span>
        <Button type="submit" className="ms-md-auto" disabled={busy}>
          Gửi đăng ký
        </Button>
      </div>
    </Form>
  );
};

export default OvertimeForm;
