import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';

import { CONTRACT_TYPE_LABEL, ContractTypeCode } from 'app/shared/model/employee.model';

import { formatMoney } from '../money';
import { isoToVn, vnToIso } from '../vn-date';
import { CONTRACT_STATUS_LABEL, ContractStatus, IContract, SalaryBasis, contractEnd, errorText } from './detail-api';
import { DateField, MoneyField, SelectField, ServerError, TextField } from './form-fields';
import { useGrossSuggestion } from './use-gross-suggestion';

const ADVICE = 'Đã ký 2 HĐ xác định thời hạn liên tiếp — HĐ tiếp theo phải là HĐ không xác định thời hạn';
const BAD_DATE = 'Ngày không hợp lệ, nhập dd/MM/yyyy';
const LABOR: ContractTypeCode[] = ['HDLD_XDTH', 'HDLD_KXDTH'];

interface IContractDialogProps {
  employeeId: number;
  dependants: number;
  /** Các hợp đồng đã có — để gợi ý lương thử việc 85% theo HĐLĐ gần nhất. */
  contracts: IContract[];
  contract?: IContract | null;
  isOpen: boolean;
  toggle: () => void;
  onSaved: () => void;
}

interface IValues {
  type: string;
  contractNo: string;
  termMonths: string;
  startDate: string;
  endDate: string;
  position: string;
  salary: number | null;
  salaryBasis: SalaryBasis;
  referenceSalary: number | null;
  raisePolicy: string;
  status: ContractStatus;
}

const fromContract = (c?: IContract | null): IValues => ({
  type: c?.type ?? '',
  contractNo: c?.contractNo ?? '',
  termMonths: c?.termMonths != null ? String(c.termMonths) : '',
  startDate: isoToVn(c?.startDate),
  endDate: isoToVn(c?.endDate),
  position: c?.position ?? '',
  salary: c?.salary ?? null,
  salaryBasis: c?.salaryBasis ?? 'GROSS',
  referenceSalary: null,
  raisePolicy: c?.raisePolicy ?? '',
  status: c?.status ?? 'DA_KY',
});

const termLabel = (months: number) => (months % 12 === 0 ? `${months / 12} năm` : `${months} tháng`);

export const ContractDialog = ({ employeeId, dependants, contracts, contract, isOpen, toggle, onSaved }: IContractDialogProps) => {
  const [values, setValues] = useState<IValues>(fromContract(contract));
  const [terms, setTerms] = useState<number[]>([]);
  const [mustBeIndefinite, setMustBeIndefinite] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [moneyValid, setMoneyValid] = useState<Record<string, boolean>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const grossField = useGrossSuggestion(contract?.grossAmount ?? null, dependants);

  useEffect(() => {
    if (!isOpen) return;
    axios
      .get<number[]>('api/salary-tools/contract-terms')
      .then(r => setTerms(r.data ?? []))
      .catch(() => undefined);
    axios
      .get<{ nextMustBeIndefinite: boolean }>(`api/employees/${employeeId}/contract-advice`)
      .then(r => setMustBeIndefinite(Boolean(r.data?.nextMustBeIndefinite)))
      .catch(() => undefined);
  }, [isOpen, employeeId]);

  /** Đổi giá trị; tự tính ngày kết thúc và hỏi lại Gross khi cần. */
  const update = (patch: Partial<IValues>) => {
    const next = { ...values, ...patch };
    if (next.type === 'HDLD_XDTH' && ('type' in patch || 'termMonths' in patch || 'startDate' in patch)) {
      const startIso = vnToIso(next.startDate);
      if (startIso && next.termMonths) next.endDate = isoToVn(contractEnd(startIso, Number(next.termMonths)));
    }
    if (next.type === 'HDLD_KXDTH') next.endDate = '';
    setValues(next);
    if ('salary' in patch || 'salaryBasis' in patch || 'startDate' in patch) {
      grossField.inputsChanged(next.salary, next.salaryBasis, vnToIso(next.startDate) ?? null);
    }
  };

  const latestLaborSalary = contracts.find(c => c.type && LABOR.includes(c.type) && c.salary)?.salary ?? null;
  const reference = values.referenceSalary ?? latestLaborSalary;
  const probationSuggestion = values.type === 'HDTV' && reference ? Math.round(reference * 0.85) : null;

  const validate = () => {
    const e: Record<string, string> = {};
    if (!values.type) e.type = 'Chọn loại hợp đồng';
    if (!values.startDate.trim()) e.startDate = 'Bắt buộc nhập';
    else if (!vnToIso(values.startDate)) e.startDate = BAD_DATE;
    if (vnToIso(values.endDate) === undefined) e.endDate = BAD_DATE;
    if (values.type === 'HDLD_XDTH' && !values.termMonths) e.termMonths = 'Chọn thời hạn';
    Object.entries(moneyValid).forEach(([k, ok]) => {
      // Ô tiền tự hiện "Số tiền không hợp lệ"; ở đây chỉ chặn gửi.
      if (!ok) e[`${k}Money`] = 'invalid';
    });
    return e;
  };

  const handleSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setServerError(null);
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length > 0) return;
    const payload: IContract = {
      id: contract?.id,
      contractNo: values.contractNo.trim() || null,
      type: values.type as ContractTypeCode,
      termMonths: values.type === 'HDLD_XDTH' ? Number(values.termMonths) : null,
      startDate: vnToIso(values.startDate),
      endDate: values.type === 'HDLD_KXDTH' ? null : (vnToIso(values.endDate) ?? null),
      position: values.position.trim() || null,
      salary: values.salary,
      salaryBasis: values.salary != null ? values.salaryBasis : null,
      grossAmount: values.salaryBasis === 'NET' ? grossField.gross : null,
      raisePolicy: values.raisePolicy.trim() || null,
      status: values.status,
      employee: { id: employeeId },
    };
    setSaving(true);
    try {
      if (contract?.id) await axios.put(`api/employment-contracts/${contract.id}`, payload);
      else await axios.post('api/employment-contracts', payload);
      onSaved();
      toggle();
    } catch (err) {
      setServerError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  const moneyValidity = (key: string) => (ok: boolean) => setMoneyValid(m => ({ ...m, [key]: ok }));

  return (
    <Modal className="doit-modal" isOpen={isOpen} toggle={toggle} size="lg" labelledBy="contract-dialog-title" fade={false}>
      <form onSubmit={e => void handleSubmit(e)} noValidate>
        <ModalHeader id="contract-dialog-title" toggle={toggle} tag="h2">
          {contract?.id ? 'Sửa hợp đồng' : 'Thêm hợp đồng'}
        </ModalHeader>
        <ModalBody>
          <ServerError message={serverError} />
          {mustBeIndefinite && values.type !== 'HDLD_KXDTH' && (
            <div role="status" className="alert alert-warning">
              {ADVICE}
            </div>
          )}
          <div className="row g-3">
            <SelectField
              id="contract-type"
              label="Loại hợp đồng"
              value={values.type}
              onChange={v => update({ type: v })}
              options={Object.entries(CONTRACT_TYPE_LABEL)}
              emptyLabel="Chọn loại"
              error={errors.type}
            />
            <TextField id="contract-no" label="Số hợp đồng" value={values.contractNo} onChange={v => update({ contractNo: v })} />
            {values.type === 'HDLD_XDTH' && (
              <fieldset className="col-12" aria-describedby={errors.termMonths ? 'contract-term-error' : undefined}>
                <legend className="form-label fs-6">Thời hạn</legend>
                <div className="d-flex flex-wrap gap-3">
                  {terms.map(t => (
                    <div key={t} className="form-check doit-radio">
                      <input
                        className="form-check-input"
                        type="radio"
                        name="contract-term"
                        id={`contract-term-${t}`}
                        value={t}
                        checked={values.termMonths === String(t)}
                        onChange={() => update({ termMonths: String(t) })}
                      />
                      <label className="form-check-label" htmlFor={`contract-term-${t}`}>
                        {termLabel(t)}
                      </label>
                    </div>
                  ))}
                </div>
                {errors.termMonths && (
                  <div id="contract-term-error" className="invalid-feedback d-block">
                    {errors.termMonths}
                  </div>
                )}
              </fieldset>
            )}
            <DateField
              id="contract-start"
              label="Ngày bắt đầu"
              value={values.startDate}
              onChange={v => update({ startDate: v })}
              error={errors.startDate}
              required
            />
            {values.type !== 'HDLD_KXDTH' && (
              <DateField
                id="contract-end"
                label="Ngày kết thúc"
                value={values.endDate}
                onChange={v => update({ endDate: v })}
                error={errors.endDate}
                hint={values.type === 'HDLD_XDTH' ? 'Tự điền theo thời hạn, có thể sửa' : undefined}
              />
            )}
            <TextField id="contract-position" label="Chức danh" value={values.position} onChange={v => update({ position: v })} />
            <SelectField
              id="contract-status"
              label="Trạng thái"
              value={values.status}
              onChange={v => update({ status: v as ContractStatus })}
              options={Object.entries(CONTRACT_STATUS_LABEL)}
            />
            {values.type === 'HDTV' && (
              <MoneyField
                id="contract-reference"
                label="Lương chính thức tham chiếu"
                value={values.referenceSalary}
                onChange={n => update({ referenceSalary: n })}
                onValidityChange={moneyValidity('referenceSalary')}
                error={errors.referenceSalary}
                hint={latestLaborSalary ? `Để trống thì lấy lương HĐLĐ gần nhất: ${formatMoney(latestLaborSalary)}` : undefined}
              />
            )}
            <MoneyField
              id="contract-salary"
              label="Mức lương"
              value={values.salary}
              onChange={n => update({ salary: n })}
              onValidityChange={moneyValidity('salary')}
              error={errors.salary}
            />
            <SelectField
              id="contract-basis"
              label="Lương tính theo"
              value={values.salaryBasis}
              onChange={v => update({ salaryBasis: v as SalaryBasis })}
              options={[
                ['GROSS', 'Gross (trước thuế, bảo hiểm)'],
                ['NET', 'Net (thực nhận)'],
              ]}
            />
            {probationSuggestion != null && (
              <div className="col-12 d-flex flex-wrap align-items-center gap-2">
                <span>Gợi ý lương thử việc (85%): {formatMoney(probationSuggestion)}</span>
                <Button type="button" color="link" onClick={() => update({ salary: probationSuggestion })}>
                  Dùng gợi ý
                </Button>
              </div>
            )}
            {values.salaryBasis === 'NET' && (
              <MoneyField
                id="contract-gross"
                label="Lương Gross (cho kế toán)"
                value={grossField.gross}
                onChange={grossField.typeGross}
                onValidityChange={moneyValidity('gross')}
                error={errors.gross}
                hint="Tự điền theo gợi ý quy đổi Net → Gross, có thể sửa"
              />
            )}
            <TextField
              id="contract-raise"
              label="Chính sách tăng lương"
              className="col-12"
              value={values.raisePolicy}
              onChange={v => update({ raisePolicy: v })}
            />
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

export default ContractDialog;
