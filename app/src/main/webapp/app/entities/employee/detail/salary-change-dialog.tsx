import React, { useState } from 'react';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

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
  const [nextReview, setNextReview] = useState(isoToVn(salaryChange?.nextReviewOverride));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [moneyValid, setMoneyValid] = useState<Record<string, boolean>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const grossField = useGrossSuggestion(salaryChange?.grossAmount ?? null, dependants);

  const changed = (next: { amount?: number | null; basis?: SalaryBasis; date?: string }) => {
    const a = 'amount' in next ? next.amount : amount;
    const b = next.basis ?? basis;
    const d = next.date ?? effectiveDate;
    grossField.inputsChanged(a ?? null, b, vnToIso(d) ?? null);
  };

  const handleSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setServerError(null);
    const e: Record<string, string> = {};
    if (!effectiveDate.trim()) e.effectiveDate = 'Bắt buộc nhập';
    else if (!vnToIso(effectiveDate)) e.effectiveDate = 'Ngày không hợp lệ, nhập dd/MM/yyyy';
    if (amount == null && moneyValid.amount !== false) e.amount = 'Bắt buộc nhập';
    if (reviewCycle.trim() && !/^\d+$/.test(reviewCycle.trim())) e.reviewCycle = 'Phải là số tháng';
    if (nextReview.trim() && !vnToIso(nextReview)) e.nextReview = 'Ngày không hợp lệ, nhập dd/MM/yyyy';
    // Chỉ xét ô tiền đang hiện; ô tự hiện "Số tiền không hợp lệ", ở đây chỉ chặn gửi.
    ['amount', ...(basis === 'NET' ? ['gross'] : [])].forEach(k => {
      if (moneyValid[k] === false) e[`${k}Money`] = 'invalid';
    });
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    const payload: ISalaryChange = {
      id: salaryChange?.id,
      effectiveDate: vnToIso(effectiveDate) ?? undefined,
      amount: amount ?? undefined,
      basis,
      grossAmount: basis === 'NET' ? grossField.gross : null,
      reviewCycleMonths: reviewCycle.trim() ? Number(reviewCycle) : null,
      note: note.trim() || null,
      nextReviewOverride: vnToIso(nextReview) ?? null,
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
    <Modal className="doit-modal" show={isOpen} onHide={toggle} aria-labelledby="salary-dialog-title" animation={false}>
      <form onSubmit={e => void handleSubmit(e)} noValidate>
        <ModalHeader closeButton closeLabel="Đóng">
          <ModalTitle as="h2" id="salary-dialog-title">
            {salaryChange?.id ? 'Sửa điều chỉnh lương' : 'Điều chỉnh lương'}
          </ModalTitle>
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
                if (v !== 'NET') setMoneyValid(m => ({ ...m, gross: true }));
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
                hint={grossField.hint}
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
            <DateField
              id="salary-next-review"
              label="Hẹn xét lại ngày"
              value={nextReview}
              onChange={setNextReview}
              error={errors.nextReview}
              hint="Để trống: xét lại theo chu kỳ"
            />
            <TextField id="salary-note" label="Ghi chú" className="col-12" value={note} onChange={setNote} multiline />
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

export default SalaryChangeDialog;
