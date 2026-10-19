import React, { useState } from 'react';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';

import { isoToVn, vnToIso } from '../vn-date';
import { ISalaryChange, SalaryBasis, errorText } from './detail-api';
import { DateField, MoneyField, SelectField, ServerError, TextField } from './form-fields';
import { useGrossSuggestion } from './use-gross-suggestion';

interface ISalaryChangeDialogProps {
  employeeId: number;
  dependants: number;
  salaryChange?: ISalaryChange | null;
  isOpen: boolean;
  toggle: () => void;
  onSaved: () => void;
}

/** Hộp thoại điều chỉnh lương (quyết định tăng/giảm lương). */
export const SalaryChangeDialog = ({ employeeId, dependants, salaryChange, isOpen, toggle, onSaved }: ISalaryChangeDialogProps) => {
  const [effectiveDate, setEffectiveDate] = useState(isoToVn(salaryChange?.effectiveDate));
  const [amount, setAmount] = useState<number | null>(salaryChange?.amount ?? null);
  const [basis, setBasis] = useState<SalaryBasis>(salaryChange?.basis ?? 'GROSS');
  const [reviewCycle, setReviewCycle] = useState(salaryChange?.reviewCycleMonths != null ? String(salaryChange.reviewCycleMonths) : '');
  const [note, setNote] = useState(salaryChange?.note ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [moneyValid, setMoneyValid] = useState<Record<string, boolean>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const grossField = useGrossSuggestion(salaryChange?.grossAmount ?? null, dependants);

  const changed = (next: { amount?: number | null; basis?: SalaryBasis; date?: string }) => {
    const a = 'amount' in next ? next.amount : amount;
    const b = next.basis ?? basis;
    const d = next.date ?? effectiveDate;
    grossField.inputsChanged(a, b, vnToIso(d) ?? null);
  };

  const handleSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setServerError(null);
    const e: Record<string, string> = {};
    if (!effectiveDate.trim()) e.effectiveDate = 'Bắt buộc nhập';
    else if (!vnToIso(effectiveDate)) e.effectiveDate = 'Ngày không hợp lệ, nhập dd/MM/yyyy';
    if (amount == null) e.amount = 'Bắt buộc nhập';
    if (reviewCycle.trim() && !/^\d+$/.test(reviewCycle.trim())) e.reviewCycle = 'Phải là số tháng';
    Object.entries(moneyValid).forEach(([k, ok]) => {
      // Ô tiền tự hiện "Số tiền không hợp lệ"; ở đây chỉ chặn gửi.
      if (!ok) e[`${k}Money`] = 'invalid';
    });
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    const payload: ISalaryChange = {
      id: salaryChange?.id,
      effectiveDate: vnToIso(effectiveDate),
      amount,
      basis,
      grossAmount: basis === 'NET' ? grossField.gross : null,
      reviewCycleMonths: reviewCycle.trim() ? Number(reviewCycle) : null,
      note: note.trim() || null,
      employee: { id: employeeId },
    };
    setSaving(true);
    try {
      if (salaryChange?.id) await axios.put(`api/salary-changes/${salaryChange.id}`, payload);
      else await axios.post('api/salary-changes', payload);
      onSaved();
      toggle();
    } catch (err) {
      setServerError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  const validity = (key: string) => (ok: boolean) => setMoneyValid(m => ({ ...m, [key]: ok }));

  return (
    <Modal className="doit-modal" isOpen={isOpen} toggle={toggle} labelledBy="salary-dialog-title" fade={false}>
      <form onSubmit={e => void handleSubmit(e)} noValidate>
        <ModalHeader id="salary-dialog-title" toggle={toggle} tag="h2">
          {salaryChange?.id ? 'Sửa điều chỉnh lương' : 'Điều chỉnh lương'}
        </ModalHeader>
        <ModalBody>
          <ServerError message={serverError} />
          <div className="row g-3">
            <DateField
              id="salary-date"
              label="Ngày hiệu lực"
              value={effectiveDate}
              onChange={v => {
                setEffectiveDate(v);
                changed({ date: v });
              }}
              error={errors.effectiveDate}
              required
            />
            <MoneyField
              id="salary-amount"
              label="Mức lương"
              value={amount}
              onChange={n => {
                setAmount(n);
                changed({ amount: n });
              }}
              onValidityChange={validity('amount')}
              error={errors.amount}
            />
            <SelectField
              id="salary-basis"
              label="Lương tính theo"
              value={basis}
              onChange={v => {
                setBasis(v as SalaryBasis);
                changed({ basis: v as SalaryBasis });
              }}
              options={[
                ['GROSS', 'Gross (trước thuế, bảo hiểm)'],
                ['NET', 'Net (thực nhận)'],
              ]}
            />
            {basis === 'NET' && (
              <MoneyField
                id="salary-gross"
                label="Lương Gross (cho kế toán)"
                value={grossField.gross}
                onChange={grossField.typeGross}
                onValidityChange={validity('gross')}
                error={errors.gross}
                hint="Tự điền theo gợi ý quy đổi Net → Gross, có thể sửa"
              />
            )}
            <TextField
              id="salary-review"
              label="Chu kỳ xét lương (tháng)"
              type="text"
              value={reviewCycle}
              onChange={setReviewCycle}
              error={errors.reviewCycle}
            />
            <TextField id="salary-note" label="Ghi chú" className="col-12" value={note} onChange={setNote} multiline />
          </div>
        </ModalBody>
        <ModalFooter>
          <Button type="button" color="secondary" outline onClick={toggle}>
            Hủy
          </Button>
          <Button type="submit" color="primary" disabled={saving}>
            Lưu
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
};

export default SalaryChangeDialog;
