import React, { useState } from 'react';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

import { isoToVn, vnToIso } from '../vn-date';
import { ITerminationBlocker, LOCATION_TYPE_LABEL, TERMINATION_TYPE_LABEL, errorText } from './detail-api';
import { DateField, SelectField, ServerError, TextField } from './form-fields';

interface ITerminateDialogProps {
  employeeId: number;
  employeeName?: string;
  isOpen: boolean;
  toggle: () => void;
  onDone: () => void;
}

/** Ngày sau một ngày ISO, dạng dd/MM/yyyy. */
const nextDayVn = (iso: string): string => {
  const [y, m, d] = iso.split('-').map(Number);
  return isoToVn(new Date(Date.UTC(y, m - 1, d + 1)).toISOString().slice(0, 10));
};

const autoReturnText = (blockers: ITerminationBlocker[]): string => {
  const items = blockers
    .map(b => `${b.type ? LOCATION_TYPE_LABEL[b.type] : 'Giai đoạn'}${b.unit ? ` – ${b.unit}` : ''} từ ${isoToVn(b.fromDate)}`)
    .join('; ');
  return `Giai đoạn “quay về” do hệ thống tự tạo, chưa bắt đầu, nằm sau ngày làm việc cuối: ${items}. Có thể xóa giai đoạn này (ghi vào nhật ký) rồi cho nghỉ việc.`;
};

/**
 * Cho nghỉ việc: chốt vị trí đang mở, kết thúc HĐ đang hiệu lực, khóa tài khoản (xử lý ở server; không xóa dữ liệu).
 * Ngày làm việc cuối từ hôm nay trở đi → server hẹn lịch. Nếu chỉ vướng giai đoạn "quay về" hệ thống tự tạo thì có nút
 * xóa giai đoạn đó (chức năng xóa có nhật ký) rồi cho nghỉ, trong một lần xác nhận.
 */
export const TerminateDialog = ({ employeeId, employeeName, isOpen, toggle, onDone }: ITerminateDialogProps) => {
  const [lastWorkingDay, setLastWorkingDay] = useState('');
  const [terminationDate, setTerminationDate] = useState('');
  const [terminationDateTouched, setTerminationDateTouched] = useState(false);
  const [terminationType, setTerminationType] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [autoReturns, setAutoReturns] = useState<ITerminationBlocker[]>([]);

  // Ngày nghỉ việc mặc định = ngày làm việc cuối + 1, cho đến khi người dùng tự sửa.
  const changeLastWorkingDay = (value: string) => {
    setLastWorkingDay(value);
    setAutoReturns([]);
    const iso = vnToIso(value);
    if (!terminationDateTouched && iso) setTerminationDate(nextDayVn(iso));
  };

  const changeTerminationDate = (value: string) => {
    setTerminationDateTouched(true);
    setTerminationDate(value);
  };

  const handleSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setServerError(null);
    setAutoReturns([]);
    const e: Record<string, string> = {};
    if (!lastWorkingDay.trim()) e.lastWorkingDay = 'Bắt buộc nhập';
    else if (!vnToIso(lastWorkingDay)) e.lastWorkingDay = 'Ngày không hợp lệ, nhập dd/MM/yyyy';
    const lastIso = vnToIso(lastWorkingDay);
    const termIso = vnToIso(terminationDate);
    if (termIso === undefined) e.terminationDate = 'Ngày không hợp lệ, nhập dd/MM/yyyy';
    else if (termIso && lastIso && termIso < lastIso) e.terminationDate = 'Ngày nghỉ việc không được trước ngày làm việc cuối';
    if (!terminationType) e.terminationType = 'Chọn hình thức nghỉ việc';
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    setSaving(true);
    try {
      await submit();
    } catch (err) {
      setServerError(errorText(err, 'Không cho nghỉ việc được, vui lòng thử lại'));
      if ((err as any)?.response?.data?.message === 'error.terminatebeforeassignment') {
        await loadAutoReturns();
      }
    } finally {
      setSaving(false);
    }
  };

  const submit = async () => {
    await axios.post(`api/employees/${employeeId}/terminate`, {
      lastWorkingDay: vnToIso(lastWorkingDay),
      terminationDate: vnToIso(terminationDate) ?? null,
      terminationType,
      terminationReason: reason.trim() || null,
    });
    onDone();
    toggle();
  };

  /** Các giai đoạn chặn; chỉ đưa ra nút xóa nhanh khi tất cả đều là giai đoạn "quay về" tự tạo, chưa bắt đầu. */
  const loadAutoReturns = async () => {
    try {
      const r = await axios.get<ITerminationBlocker[]>(`api/employees/${employeeId}/termination/blockers`, {
        params: { lastWorkingDay: vnToIso(lastWorkingDay) },
      });
      const blockers = r?.data ?? [];
      setAutoReturns(blockers.length > 0 && blockers.every(b => b.autoReturn) ? blockers : []);
    } catch {
      setAutoReturns([]);
    }
  };

  const deleteAutoReturnsAndTerminate = async () => {
    setServerError(null);
    setSaving(true);
    try {
      for (const b of autoReturns) {
        await axios.delete(`api/employees/${employeeId}/assignments/${b.id}`);
      }
      setAutoReturns([]);
      await submit();
    } catch (err) {
      setServerError(errorText(err, 'Không cho nghỉ việc được, vui lòng thử lại'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal className="doit-modal" show={isOpen} onHide={toggle} aria-labelledby="terminate-dialog-title" animation={false}>
      <form onSubmit={e => void handleSubmit(e)} noValidate>
        <ModalHeader closeButton>
          <ModalTitle as="h2" id="terminate-dialog-title">
            Cho nghỉ việc{employeeName ? `: ${employeeName}` : ''}
          </ModalTitle>
        </ModalHeader>
        <ModalBody>
          <ServerError message={serverError} />
          {autoReturns.length > 0 && (
            <div className="alert alert-warning">
              <p className="mb-2">{autoReturnText(autoReturns)}</p>
              <Button type="button" variant="danger" disabled={saving} onClick={() => void deleteAutoReturnsAndTerminate()}>
                Xóa giai đoạn quay về và cho nghỉ
              </Button>
            </div>
          )}
          <p>
            Vị trí đang mở sẽ được chốt ở ngày làm việc cuối, hợp đồng đang hiệu lực chuyển “Đã kết thúc”, tài khoản đăng nhập bị khóa.
            Không dữ liệu nào bị xóa.
          </p>
          <p>
            Nếu ngày làm việc cuối là hôm nay hoặc sau hôm nay, hệ thống hẹn lịch nghỉ việc: nhân viên vẫn “Đang làm việc” và tài khoản vẫn
            dùng được đến hết ngày làm việc cuối; 00:05 ngày hôm sau hệ thống tự cho nghỉ.
          </p>
          <div className="row g-3">
            <DateField
              id="terminate-last-day"
              label="Ngày làm việc cuối"
              value={lastWorkingDay}
              onChange={changeLastWorkingDay}
              error={errors.lastWorkingDay}
              required
            />
            <DateField
              id="terminate-date"
              label="Ngày nghỉ việc"
              value={terminationDate}
              onChange={changeTerminationDate}
              error={errors.terminationDate}
              hint="Mặc định là ngày sau ngày làm việc cuối"
            />
            <SelectField
              id="terminate-type"
              label="Hình thức nghỉ việc"
              value={terminationType}
              onChange={setTerminationType}
              options={Object.entries(TERMINATION_TYPE_LABEL)}
              emptyLabel="Chọn hình thức"
              error={errors.terminationType}
            />
            <TextField id="terminate-reason" label="Lý do" className="col-12" value={reason} onChange={setReason} multiline />
          </div>
        </ModalBody>
        <ModalFooter>
          <Button type="button" variant="outline-secondary" onClick={toggle}>
            Hủy
          </Button>
          <Button type="submit" variant="danger" disabled={saving}>
            Xác nhận nghỉ việc
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
};

export default TerminateDialog;
