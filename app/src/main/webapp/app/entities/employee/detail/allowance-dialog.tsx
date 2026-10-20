import React, { useState } from 'react';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

import { isoToVn, vnToIso } from '../vn-date';
import { IAllowance, errorText } from './detail-api';
import { DateField, MoneyField, ServerError, TextField } from './form-fields';

interface IAllowanceDialogProps {
  employeeId: number;
  allowance?: IAllowance | null;
  isOpen: boolean;
  toggle: () => void;
  onSaved: () => void;
}

const BAD_DATE = 'Ngày không hợp lệ, nhập dd/MM/yyyy';

/** Hộp thoại thêm / sửa phụ cấp hằng tháng. */
export const AllowanceDialog = ({ employeeId, allowance, isOpen, toggle, onSaved }: IAllowanceDialogProps) => {
  const [name, setName] = useState(allowance?.name ?? '');
  const [amount, setAmount] = useState<number | null>(allowance?.amountPerMonth ?? null);
  const [amountValid, setAmountValid] = useState(true);
  const [fromDate, setFromDate] = useState(isoToVn(allowance?.fromDate));
  const [toDate, setToDate] = useState(isoToVn(allowance?.toDate));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const handleSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setServerError(null);
    const e: Record<string, string> = {};
    if (!name.trim()) e.name = 'Bắt buộc nhập';
    // Ô tiền tự hiện "Số tiền không hợp lệ"; ở đây chỉ chặn gửi.
    if (!amountValid) e.amountMoney = 'invalid';
    else if (amount == null) e.amount = 'Bắt buộc nhập';
    if (!fromDate.trim()) e.fromDate = 'Bắt buộc nhập';
    else if (!vnToIso(fromDate)) e.fromDate = BAD_DATE;
    if (vnToIso(toDate) === undefined) e.toDate = BAD_DATE;
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    const payload: IAllowance = {
      id: allowance?.id,
      name: name.trim(),
      amountPerMonth: amount ?? undefined,
      fromDate: vnToIso(fromDate) ?? undefined,
      toDate: vnToIso(toDate) ?? null,
      employee: { id: employeeId },
    };
    setSaving(true);
    try {
      if (allowance?.id) await axios.put(`api/allowances/${allowance.id}`, payload);
      else await axios.post('api/allowances', payload);
      onSaved();
      toggle();
    } catch (err) {
      setServerError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal className="doit-modal" show={isOpen} onHide={toggle} aria-labelledby="allowance-dialog-title" animation={false}>
      <form onSubmit={e => void handleSubmit(e)} noValidate>
        <ModalHeader closeButton closeLabel="Đóng">
          <ModalTitle as="h2" id="allowance-dialog-title">
            {allowance?.id ? 'Sửa phụ cấp' : 'Thêm phụ cấp'}
          </ModalTitle>
        </ModalHeader>
        <ModalBody>
          <ServerError message={serverError} />
          <div className="row g-3">
            <TextField id="allowance-name" label="Tên phụ cấp" value={name} onChange={setName} error={errors.name} required />
            <MoneyField
              id="allowance-amount"
              label="Số tiền mỗi tháng"
              value={amount}
              onChange={setAmount}
              onValidityChange={setAmountValid}
              error={errors.amount}
            />
            <DateField id="allowance-from" label="Từ ngày" value={fromDate} onChange={setFromDate} error={errors.fromDate} required />
            <DateField id="allowance-to" label="Đến ngày" value={toDate} onChange={setToDate} error={errors.toDate} />
          </div>
        </ModalBody>
        <ModalFooter>
          <Button type="button" variant="outline-secondary" onClick={toggle}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={saving}>
            Lưu
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
};

export default AllowanceDialog;
