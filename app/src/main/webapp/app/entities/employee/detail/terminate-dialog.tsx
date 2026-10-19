import React, { useState } from 'react';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';

import { isoToVn, vnToIso } from '../vn-date';
import { TERMINATION_TYPE_LABEL, errorText } from './detail-api';
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

/** Cho nghỉ việc: chốt vị trí đang mở, kết thúc HĐ đang hiệu lực (xử lý ở server). */
export const TerminateDialog = ({ employeeId, employeeName, isOpen, toggle, onDone }: ITerminateDialogProps) => {
  const [lastWorkingDay, setLastWorkingDay] = useState('');
  const [terminationDate, setTerminationDate] = useState('');
  const [terminationDateTouched, setTerminationDateTouched] = useState(false);
  const [terminationType, setTerminationType] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Ngày nghỉ việc mặc định = ngày làm việc cuối + 1, cho đến khi người dùng tự sửa.
  const changeLastWorkingDay = (value: string) => {
    setLastWorkingDay(value);
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
      await axios.post(`api/employees/${employeeId}/terminate`, {
        lastWorkingDay: vnToIso(lastWorkingDay),
        terminationDate: vnToIso(terminationDate) ?? null,
        terminationType,
        terminationReason: reason.trim() || null,
      });
      onDone();
      toggle();
    } catch (err) {
      setServerError(errorText(err, 'Không cho nghỉ việc được, vui lòng thử lại'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal className="doit-modal" isOpen={isOpen} toggle={toggle} labelledBy="terminate-dialog-title" fade={false}>
      <form onSubmit={e => void handleSubmit(e)} noValidate>
        <ModalHeader id="terminate-dialog-title" toggle={toggle} tag="h2">
          Cho nghỉ việc{employeeName ? `: ${employeeName}` : ''}
        </ModalHeader>
        <ModalBody>
          <ServerError message={serverError} />
          <p>Vị trí đang mở sẽ được chốt ở ngày làm việc cuối, hợp đồng đang hiệu lực chuyển “Đã kết thúc”.</p>
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
          <Button type="button" color="secondary" outline onClick={toggle}>
            Hủy
          </Button>
          <Button type="submit" color="danger" disabled={saving}>
            Xác nhận nghỉ việc
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
};

export default TerminateDialog;
