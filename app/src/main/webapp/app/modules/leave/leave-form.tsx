import React, { useState } from 'react';
import { Alert, Button, Form } from 'react-bootstrap';

import { vnToIso } from 'app/entities/employee/vn-date';

import { ILeaveBalance, ILeaveRequest, leaveApi, leaveErrorText, previewDays } from './leave-api';
import { DayPart, LEAVE_TYPES, LEAVE_TYPE_LABEL, LeaveType, PART_LABEL, PERSONAL_REASONS, PersonalReason, num } from './leave-labels';

interface Props {
  balance?: ILeaveBalance | null;
  onSubmitted: (created: ILeaveRequest) => void;
}

const HOURS_PER_DAY = 8;

/**
 * Form tạo đơn nghỉ. Số ngày ở đây chỉ là ước tính để hiển thị (bỏ Thứ Bảy, Chủ nhật, chưa trừ ngày lễ); số chính thức do server tính
 * khi gửi và hiện trong bảng đơn đã gửi.
 */
export const LeaveForm = ({ balance, onSubmitted }: Props) => {
  const [type, setType] = useState<LeaveType>('PHEP_NAM');
  const [reason, setReason] = useState<PersonalReason | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [part, setPart] = useState<DayPart>('CA_NGAY');
  const [firstPart, setFirstPart] = useState<DayPart>('CA_NGAY');
  const [lastPart, setLastPart] = useState<DayPart>('CA_NGAY');
  const [note, setNote] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const fromIso = vnToIso(from);
  const toIso = vnToIso(to);
  const datesOk = !!fromIso && !!toIso && fromIso <= toIso;
  const singleDay = !toIso || !fromIso || fromIso === toIso;
  const first: DayPart = singleDay ? part : firstPart;
  const last: DayPart = singleDay ? part : lastPart;
  const days = datesOk ? previewDays(fromIso, toIso, first, last) : 0;
  const personal = PERSONAL_REASONS.find(p => p.value === reason);
  const overPersonal = type === 'VIEC_RIENG' && personal && days > personal.maxDays;

  let after: string | null = null;
  if (balance && datesOk && type === 'PHEP_NAM') after = `${num(balance.annual.total - days)} ngày`;
  if (balance && datesOk && type === 'NGHI_BU') after = `${num(balance.compensatory.total - days * HOURS_PER_DAY)} giờ`;

  const reset = () => {
    setFrom('');
    setTo('');
    setPart('CA_NGAY');
    setFirstPart('CA_NGAY');
    setLastPart('CA_NGAY');
    setNote('');
    setFile(null);
    setReason('');
  };

  const submit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setError(null);
    setNotice(null);
    if (fromIso === undefined || toIso === undefined || !fromIso || !toIso) {
      setError('Nhập từ ngày, đến ngày dạng dd/MM/yyyy');
      return;
    }
    if (fromIso > toIso) {
      setError('Đến ngày phải từ ngày bắt đầu trở đi');
      return;
    }
    if (type === 'VIEC_RIENG' && !reason) {
      setError('Chọn lý do nghỉ việc riêng');
      return;
    }
    if (overPersonal) {
      setError(`${personal.label}: vượt quá ${personal.maxDays} ngày được nghỉ`);
      return;
    }
    setBusy(true);
    try {
      const created = await leaveApi.submit({
        type,
        personalReason: type === 'VIEC_RIENG' ? (reason as PersonalReason) : null,
        fromDate: fromIso,
        toDate: toIso,
        firstPart: first,
        lastPart: last,
        reasonText: note.trim(),
      });
      const parts = [`Đã gửi đơn${created?.days != null ? ` (${num(created.days)} ngày làm việc)` : ''}, đang chờ duyệt.`];
      if (created?.sickOverLimit) parts.push('Lưu ý: số ngày nghỉ ốm trong năm đã vượt mức BHXH.');
      if (type === 'OM' && file && created?.id) {
        try {
          await leaveApi.uploadSickDocument(created.id, file);
        } catch (err) {
          parts.push(`Chưa tải được giấy nghỉ ốm (${leaveErrorText(err)}) — tải lại trong bảng đơn đã gửi.`);
        }
      }
      setNotice(parts.join(' '));
      reset();
      onSubmitted(created);
    } catch (err) {
      setError(leaveErrorText(err, type === 'NGHI_BU' ? 'giờ' : 'ngày', 'Không gửi được đơn, vui lòng thử lại'));
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
        <Form.Group className="col-12 col-md-6" controlId="leave-type">
          <Form.Label>Loại đơn</Form.Label>
          <Form.Select value={type} onChange={e => setType(e.target.value as LeaveType)}>
            {LEAVE_TYPES.map(t => (
              <option key={t} value={t}>
                {LEAVE_TYPE_LABEL[t]}
              </option>
            ))}
          </Form.Select>
        </Form.Group>
        {type === 'VIEC_RIENG' && (
          <Form.Group className="col-12 col-md-6" controlId="leave-personal">
            <Form.Label>Lý do nghỉ việc riêng</Form.Label>
            <Form.Select value={reason} onChange={e => setReason(e.target.value as PersonalReason)}>
              <option value="">Chọn lý do</option>
              {PERSONAL_REASONS.map(p => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </Form.Select>
            {personal && <Form.Text>Tối đa {personal.maxDays} ngày.</Form.Text>}
          </Form.Group>
        )}
        <Form.Group className="col-6 col-md-3" controlId="leave-from">
          <Form.Label>Từ ngày</Form.Label>
          <Form.Control value={from} placeholder="dd/MM/yyyy" inputMode="numeric" onChange={e => setFrom(e.target.value)} />
        </Form.Group>
        <Form.Group className="col-6 col-md-3" controlId="leave-to">
          <Form.Label>Đến ngày</Form.Label>
          <Form.Control value={to} placeholder="dd/MM/yyyy" inputMode="numeric" onChange={e => setTo(e.target.value)} />
        </Form.Group>
        {singleDay ? (
          <Form.Group className="col-12 col-md-6" controlId="leave-part">
            <Form.Label>Thời lượng</Form.Label>
            <Form.Select value={part} onChange={e => setPart(e.target.value as DayPart)}>
              {(['CA_NGAY', 'SANG', 'CHIEU'] as DayPart[]).map(p => (
                <option key={p} value={p}>
                  {PART_LABEL[p]}
                </option>
              ))}
            </Form.Select>
          </Form.Group>
        ) : (
          <>
            <Form.Group className="col-6 col-md-3" controlId="leave-first">
              <Form.Label>Ngày đầu</Form.Label>
              <Form.Select value={firstPart} onChange={e => setFirstPart(e.target.value as DayPart)}>
                <option value="CA_NGAY">{PART_LABEL.CA_NGAY}</option>
                <option value="CHIEU">Chỉ buổi chiều</option>
              </Form.Select>
            </Form.Group>
            <Form.Group className="col-6 col-md-3" controlId="leave-last">
              <Form.Label>Ngày cuối</Form.Label>
              <Form.Select value={lastPart} onChange={e => setLastPart(e.target.value as DayPart)}>
                <option value="CA_NGAY">{PART_LABEL.CA_NGAY}</option>
                <option value="SANG">Chỉ buổi sáng</option>
              </Form.Select>
            </Form.Group>
          </>
        )}
        {type === 'OM' && (
          <Form.Group className="col-12" controlId="leave-sickdoc">
            <Form.Label>Giấy chứng nhận nghỉ ốm hưởng BHXH (PDF hoặc ảnh, có thể tải lên sau)</Form.Label>
            <Form.Control
              type="file"
              accept="application/pdf,image/jpeg,image/png"
              onChange={e => setFile((e.target as HTMLInputElement).files?.[0] ?? null)}
            />
          </Form.Group>
        )}
        <Form.Group className="col-12" controlId="leave-note">
          <Form.Label>Ghi chú</Form.Label>
          <Form.Control as="textarea" rows={2} maxLength={1000} value={note} onChange={e => setNote(e.target.value)} />
          <Form.Text>Chỉ bạn, người duyệt, HR và Quản trị xem được; lịch nghỉ không hiện ghi chú.</Form.Text>
        </Form.Group>
      </div>
      <div className="d-flex flex-wrap align-items-center gap-3 mt-3">
        {datesOk && (
          <span>
            Khoảng {num(days)} ngày làm việc <span className="text-muted">(chưa trừ ngày lễ; số chính thức tính khi gửi)</span>
          </span>
        )}
        {after && (
          <span>
            Sau khi duyệt còn: <span className="leave-after">{after}</span> (ước tính)
          </span>
        )}
        {overPersonal && (
          <span className="text-danger" role="alert">
            {personal.label}: vượt quá {personal.maxDays} ngày được nghỉ
          </span>
        )}
        <Button type="submit" className="ms-md-auto" disabled={busy}>
          Gửi đơn
        </Button>
      </div>
    </Form>
  );
};

export default LeaveForm;
