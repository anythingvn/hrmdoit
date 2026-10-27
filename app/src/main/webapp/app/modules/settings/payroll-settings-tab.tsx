import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Form, Modal } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { formatMoney, parseMoney } from 'app/entities/employee/money';
import PayrollRules from 'app/modules/payroll/rules/payroll-rules';
import { periodText } from 'app/modules/payroll/payroll-api';
import MonthInput from 'app/shared/month-input';

type DayKind = 'WORK' | 'PAID_LEAVE' | 'HOLIDAY' | 'TRIP' | 'SICK';

export interface ICalcSetting {
  id?: number | null;
  effectiveFrom: string;
  bhxhEmployee: number;
  bhytEmployee: number;
  bhtnEmployee: number;
  bhxhCompany: number;
  bhytCompany: number;
  bhtnCompany: number;
  unionFeeEnabled: boolean;
  unionFeeRate: number;
  hoursPerDay: number;
  overtimeBaseIncludesInsuredAllowance: boolean;
  overtimeAllNonTaxable: boolean;
  actualDayKinds: DayKind[];
  allowanceKeywords: Record<string, string[]>;
  withholdingThreshold: number;
  withholdingRate: number;
  payUnusedLeaveOnTermination: boolean;
  locked?: boolean | null;
}

/** Mặc định trong code của máy chủ (CalcSettings.defaults) khi chưa có bộ nào. */
export const DEFAULT_SETTING: ICalcSetting = {
  effectiveFrom: '',
  bhxhEmployee: 0.08,
  bhytEmployee: 0.015,
  bhtnEmployee: 0.01,
  bhxhCompany: 0.175,
  bhytCompany: 0.03,
  bhtnCompany: 0.01,
  unionFeeEnabled: false,
  unionFeeRate: 0.02,
  hoursPerDay: 8,
  overtimeBaseIncludesInsuredAllowance: false,
  overtimeAllNonTaxable: false,
  actualDayKinds: ['WORK', 'PAID_LEAVE', 'HOLIDAY', 'TRIP'],
  allowanceKeywords: {
    PC_CHUYEN_CAN: ['chuyen can'],
    PC_XANG_XE: ['xang'],
    PC_TRACH_NHIEM: ['trach nhiem'],
    PHU_CAP_DONG_BH: ['dong bh', 'dong bao hiem'],
  },
  withholdingThreshold: 2000000,
  withholdingRate: 0.1,
  payUnusedLeaveOnTermination: true,
};

const DAY_KINDS: { key: DayKind; label: string }[] = [
  { key: 'WORK', label: 'Ngày làm' },
  { key: 'PAID_LEAVE', label: 'Phép có lương' },
  { key: 'HOLIDAY', label: 'Lễ' },
  { key: 'TRIP', label: 'Công tác' },
  { key: 'SICK', label: 'Ốm' },
];

const KEYWORD_FIELDS: { key: string; label: string }[] = [
  { key: 'PC_CHUYEN_CAN', label: 'PC chuyên cần' },
  { key: 'PC_XANG_XE', label: 'PC xăng xe' },
  { key: 'PC_TRACH_NHIEM', label: 'PC trách nhiệm' },
  { key: 'PHU_CAP_DONG_BH', label: 'PC đóng BH' },
];

const RATE_FIELDS = [
  { key: 'bhxhEmployee', label: 'BHXH người lao động (%)' },
  { key: 'bhytEmployee', label: 'BHYT người lao động (%)' },
  { key: 'bhtnEmployee', label: 'BHTN người lao động (%)' },
  { key: 'bhxhCompany', label: 'BHXH công ty (%)' },
  { key: 'bhytCompany', label: 'BHYT công ty (%)' },
  { key: 'bhtnCompany', label: 'BHTN công ty (%)' },
] as const;
type RateKey = (typeof RATE_FIELDS)[number]['key'];

/** 0.175 thành "17,5". */
const pct = (rate: number) => String(Number((rate * 100).toFixed(4))).replace('.', ',');
/** "17,5" hoặc "17.5" thành 0.175; sai thì NaN. */
const rateOf = (text: string): number => {
  const t = text.trim().replace(',', '.');
  if (!/^\d+(\.\d+)?$/.test(t)) return NaN;
  const n = Number(t);
  return n > 100 ? NaN : Number((n / 100).toFixed(6));
};
const num = (text: string): number => {
  const t = text.trim().replace(',', '.');
  return /^\d+(\.\d+)?$/.test(t) ? Number(t) : NaN;
};

interface IForm {
  effectiveFrom: string;
  rates: Record<RateKey, string>;
  unionFeeEnabled: boolean;
  unionFeeRate: string;
  hoursPerDay: string;
  overtimeBaseIncludesInsuredAllowance: boolean;
  overtimeAllNonTaxable: boolean;
  actualDayKinds: DayKind[];
  keywords: Record<string, string>;
  withholdingThreshold: string;
  withholdingRate: string;
  payUnusedLeaveOnTermination: boolean;
}

const formOf = (s: ICalcSetting, effectiveFrom: string): IForm => ({
  effectiveFrom,
  rates: Object.fromEntries(RATE_FIELDS.map(f => [f.key, pct(s[f.key])])) as Record<RateKey, string>,
  unionFeeEnabled: s.unionFeeEnabled,
  unionFeeRate: pct(s.unionFeeRate),
  hoursPerDay: String(s.hoursPerDay).replace('.', ','),
  overtimeBaseIncludesInsuredAllowance: s.overtimeBaseIncludesInsuredAllowance,
  overtimeAllNonTaxable: s.overtimeAllNonTaxable,
  actualDayKinds: [...s.actualDayKinds],
  keywords: Object.fromEntries(KEYWORD_FIELDS.map(f => [f.key, (s.allowanceKeywords?.[f.key] ?? []).join(', ')])),
  withholdingThreshold: formatMoney(s.withholdingThreshold),
  withholdingRate: pct(s.withholdingRate),
  payUnusedLeaveOnTermination: s.payUnusedLeaveOnTermination,
});

const summary = (s: ICalcSetting): string[] => [
  `BHXH người lao động ${pct(s.bhxhEmployee)}%, BHYT ${pct(s.bhytEmployee)}%, BHTN ${pct(s.bhtnEmployee)}%`,
  `BHXH công ty ${pct(s.bhxhCompany)}%, BHYT ${pct(s.bhytCompany)}%, BHTN ${pct(s.bhtnCompany)}%`,
  s.unionFeeEnabled ? `Kinh phí công đoàn: bật, ${pct(s.unionFeeRate)}%` : 'Kinh phí công đoàn: tắt',
  `${String(s.hoursPerDay).replace('.', ',')} giờ một ngày công; lương giờ làm thêm tính trên ${
    s.overtimeBaseIncludesInsuredAllowance ? 'lương cơ bản + PC đóng BH' : 'lương cơ bản'
  }; ${s.overtimeAllNonTaxable ? 'toàn bộ tiền làm thêm không chịu thuế' : 'tiền làm thêm tách chịu thuế / không chịu thuế'}`,
  `Ngày công thực tế gồm: ${DAY_KINDS.filter(k => s.actualDayKinds.includes(k.key))
    .map(k => k.label.toLowerCase())
    .join(', ')}`,
  `Thực tập, HĐ dịch vụ: khấu trừ ${pct(s.withholdingRate)}% từ ${formatMoney(s.withholdingThreshold)} đồng/tháng`,
  `Trả phép chưa dùng khi nghỉ việc: ${s.payUnusedLeaveOnTermination ? 'có' : 'không'}`,
];

const TOUCH = { minHeight: 44 };

/**
 * Thông số tính lương theo tháng hiệu lực (tỷ lệ bảo hiểm, giờ làm thêm, từ khóa phụ cấp, khấu trừ thuế...) và mức luật định bên dưới.
 * Quản trị sửa; bộ đã dùng cho kỳ đã duyệt thì khóa, chỉ thêm bộ mới.
 */
export const PayrollSettingsTab = ({ canEdit }: { canEdit: boolean }) => {
  const [list, setList] = useState<ICalcSetting[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [form, setForm] = useState<IForm | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [deleting, setDeleting] = useState<ICalcSetting | null>(null);

  const load = () =>
    axios
      .get<ICalcSetting[]>('api/payroll/calc-settings')
      .then(r => setList([...(r.data ?? [])].sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))))
      .catch(err => setError(errorText(err, 'Không tải được thông số tính lương')));

  useEffect(() => {
    void load();
  }, []);

  const startAdd = () => {
    setError(null);
    setNotice(null);
    setEditingId(null);
    setForm(formOf(list && list.length > 0 ? list[0] : DEFAULT_SETTING, ''));
  };

  const startEdit = (s: ICalcSetting) => {
    setError(null);
    setNotice(null);
    setEditingId(s.id ?? null);
    setForm(formOf(s, s.effectiveFrom));
  };

  const cancel = () => {
    setForm(null);
    setEditingId(null);
  };

  const patch = (p: Partial<IForm>) => setForm(f => (f ? { ...f, ...p } : f));

  const save = async (ev: React.FormEvent) => {
    ev.preventDefault();
    if (!form) return;
    setError(null);
    setNotice(null);
    const rates = RATE_FIELDS.map(f => rateOf(form.rates[f.key]));
    const unionFeeRate = rateOf(form.unionFeeRate);
    const withholdingRate = rateOf(form.withholdingRate);
    const hoursPerDay = num(form.hoursPerDay);
    const threshold = parseMoney(form.withholdingThreshold);
    if (!/^\d{4}-\d{2}$/.test(form.effectiveFrom)) {
      setError('Nhập tháng hiệu lực dạng MM/yyyy');
      return;
    }
    if ([...rates, unionFeeRate, withholdingRate].some(isNaN) || isNaN(hoursPerDay) || hoursPerDay <= 0 || threshold == null) {
      setError('Các tỷ lệ (0 đến 100), số giờ một ngày và ngưỡng khấu trừ phải là số hợp lệ');
      return;
    }
    if (form.actualDayKinds.length === 0) {
      setError('Chọn ít nhất một loại ngày tính vào ngày công thực tế');
      return;
    }
    const body: ICalcSetting = {
      effectiveFrom: form.effectiveFrom,
      ...(Object.fromEntries(RATE_FIELDS.map((f, i) => [f.key, rates[i]])) as Record<RateKey, number>),
      unionFeeEnabled: form.unionFeeEnabled,
      unionFeeRate,
      hoursPerDay,
      overtimeBaseIncludesInsuredAllowance: form.overtimeBaseIncludesInsuredAllowance,
      overtimeAllNonTaxable: form.overtimeAllNonTaxable,
      actualDayKinds: DAY_KINDS.map(k => k.key).filter(k => form.actualDayKinds.includes(k)),
      allowanceKeywords: Object.fromEntries(
        KEYWORD_FIELDS.map(f => [
          f.key,
          form.keywords[f.key]
            .split(',')
            .map(x => x.trim())
            .filter(Boolean),
        ]),
      ),
      withholdingThreshold: threshold,
      withholdingRate,
      payUnusedLeaveOnTermination: form.payUnusedLeaveOnTermination,
    };
    try {
      if (editingId == null) {
        await axios.post('api/payroll/calc-settings', body);
        setNotice(`Đã thêm bộ thông số hiệu lực từ ${periodText(body.effectiveFrom)}.`);
      } else {
        await axios.put(`api/payroll/calc-settings/${editingId}`, body);
        setNotice(`Đã sửa bộ thông số hiệu lực từ ${periodText(body.effectiveFrom)}.`);
      }
      cancel();
      await load();
    } catch (err) {
      setError(errorText(err, 'Không lưu được bộ thông số'));
    }
  };

  const confirmDelete = async () => {
    if (!deleting) return;
    const target = deleting;
    setError(null);
    setNotice(null);
    try {
      await axios.delete(`api/payroll/calc-settings/${target.id}`);
      setNotice(`Đã xóa bộ thông số hiệu lực từ ${periodText(target.effectiveFrom)}.`);
      await load();
    } catch (err) {
      setError(errorText(err, 'Không xóa được bộ thông số'));
    } finally {
      setDeleting(null);
    }
  };

  const check = (id: string, label: string, checked: boolean, onChange: (v: boolean) => void) => (
    <Form.Check id={id} type="checkbox" label={label} checked={checked} onChange={e => onChange(e.target.checked)} />
  );

  return (
    <div className="doit-page">
      <section aria-labelledby="calc-settings-heading" className="mb-4">
        <h2 id="calc-settings-heading" className="h5">
          Thông số tính lương
        </h2>
        <p className="text-muted">
          Mỗi bộ áp dụng từ tháng hiệu lực đến trước bộ kế tiếp. Bộ đã dùng cho kỳ lương đã duyệt thì khóa (không sửa, không xóa); muốn đổi
          thì thêm bộ mới với tháng hiệu lực sau.
        </p>
        {error && <Alert variant="danger">{error}</Alert>}
        {notice && <Alert variant="success">{notice}</Alert>}
        {canEdit && !form && (
          <Button type="button" className="mb-3" style={TOUCH} onClick={startAdd}>
            Thêm bộ thông số
          </Button>
        )}
        {form && (
          <Form onSubmit={e => void save(e)} noValidate className="doit-card mb-3" aria-label="Bộ thông số tính lương">
            <div className="row g-3">
              <Form.Group controlId="calc-effective" className="col-12 col-md-4">
                <Form.Label>Hiệu lực từ tháng (MM/yyyy)</Form.Label>
                <MonthInput value={form.effectiveFrom} onChange={v => patch({ effectiveFrom: v })} />
              </Form.Group>
              {RATE_FIELDS.map(f => (
                <Form.Group key={f.key} controlId={`calc-${f.key}`} className="col-6 col-md-4">
                  <Form.Label>{f.label}</Form.Label>
                  <Form.Control
                    inputMode="decimal"
                    value={form.rates[f.key]}
                    onChange={e => patch({ rates: { ...form.rates, [f.key]: e.target.value } })}
                  />
                </Form.Group>
              ))}
              <div className="col-12 col-md-4">
                {check('calc-union-on', 'Tính kinh phí công đoàn', form.unionFeeEnabled, v => patch({ unionFeeEnabled: v }))}
              </div>
              <Form.Group controlId="calc-union-rate" className="col-6 col-md-4">
                <Form.Label>Tỷ lệ kinh phí công đoàn (%)</Form.Label>
                <Form.Control inputMode="decimal" value={form.unionFeeRate} onChange={e => patch({ unionFeeRate: e.target.value })} />
              </Form.Group>
              <Form.Group controlId="calc-hours" className="col-6 col-md-4">
                <Form.Label>Số giờ một ngày công</Form.Label>
                <Form.Control inputMode="decimal" value={form.hoursPerDay} onChange={e => patch({ hoursPerDay: e.target.value })} />
              </Form.Group>
              <Form.Group controlId="calc-ot-base" className="col-12 col-md-4">
                <Form.Label>Lương giờ làm thêm tính trên</Form.Label>
                <Form.Select
                  value={form.overtimeBaseIncludesInsuredAllowance ? 'with' : 'base'}
                  onChange={e => patch({ overtimeBaseIncludesInsuredAllowance: e.target.value === 'with' })}
                >
                  <option value="base">Lương cơ bản</option>
                  <option value="with">Lương cơ bản + phụ cấp đóng BH</option>
                </Form.Select>
              </Form.Group>
              <Form.Group controlId="calc-ot-tax" className="col-12 col-md-4">
                <Form.Label>Tiền làm thêm giờ</Form.Label>
                <Form.Select
                  value={form.overtimeAllNonTaxable ? 'all' : 'split'}
                  onChange={e => patch({ overtimeAllNonTaxable: e.target.value === 'all' })}
                >
                  <option value="split">Tách theo luật (phần 100% chịu thuế)</option>
                  <option value="all">Toàn bộ không chịu thuế</option>
                </Form.Select>
              </Form.Group>
              <fieldset className="col-12">
                <legend className="form-label fs-6">Loại ngày tính vào ngày công thực tế</legend>
                <div className="d-flex flex-wrap gap-3">
                  {DAY_KINDS.map(k =>
                    check(`calc-kind-${k.key}`, k.label, form.actualDayKinds.includes(k.key), v =>
                      patch({ actualDayKinds: v ? [...form.actualDayKinds, k.key] : form.actualDayKinds.filter(x => x !== k.key) }),
                    ),
                  )}
                </div>
              </fieldset>
              {KEYWORD_FIELDS.map(f => (
                <Form.Group key={f.key} controlId={`calc-kw-${f.key}`} className="col-12 col-md-6">
                  <Form.Label>{`Từ khóa: ${f.label}`}</Form.Label>
                  <Form.Control
                    value={form.keywords[f.key]}
                    onChange={e => patch({ keywords: { ...form.keywords, [f.key]: e.target.value } })}
                  />
                </Form.Group>
              ))}
              <div className="col-12 form-text mt-0">
                Phụ cấp có tên chứa từ khóa (không dấu, không phân biệt hoa thường, cách nhau bằng dấu phẩy) được ghép vào cột tương ứng;
                phụ cấp không khớp cộng vào Thừa/thiếu và có cảnh báo.
              </div>
              <Form.Group controlId="calc-threshold" className="col-6 col-md-4">
                <Form.Label>Ngưỡng khấu trừ thuế (đồng/tháng)</Form.Label>
                <Form.Control
                  inputMode="numeric"
                  value={form.withholdingThreshold}
                  onChange={e => patch({ withholdingThreshold: e.target.value })}
                />
              </Form.Group>
              <Form.Group controlId="calc-withholding" className="col-6 col-md-4">
                <Form.Label>Tỷ lệ khấu trừ thuế (%)</Form.Label>
                <Form.Control inputMode="decimal" value={form.withholdingRate} onChange={e => patch({ withholdingRate: e.target.value })} />
              </Form.Group>
              <div className="col-12 col-md-4">
                {check('calc-leave', 'Trả phép chưa dùng khi nghỉ việc trong kỳ', form.payUnusedLeaveOnTermination, v =>
                  patch({ payUnusedLeaveOnTermination: v }),
                )}
              </div>
            </div>
            <div className="d-flex gap-2 mt-3">
              <Button type="submit" style={TOUCH}>
                Lưu
              </Button>
              <Button type="button" variant="outline-secondary" style={TOUCH} onClick={cancel}>
                Hủy
              </Button>
            </div>
          </Form>
        )}
        {list && list.length === 0 && (
          <div className="doit-card mb-3" data-testid="calc-default">
            <h3 className="h6">Mặc định của hệ thống</h3>
            <p className="text-muted mb-1">Chưa có bộ thông số nào, hệ thống đang dùng các giá trị sau.</p>
            <ul className="mb-0">
              {summary(DEFAULT_SETTING).map(t => (
                <li key={t}>{t}</li>
              ))}
            </ul>
          </div>
        )}
        {(list ?? []).map(s => (
          <div className="doit-card mb-3" key={s.id} data-testid={`calc-setting-${s.id}`}>
            <div className="d-flex flex-wrap align-items-center gap-2 mb-2">
              <h3 className="h6 mb-0">{`Hiệu lực từ ${periodText(s.effectiveFrom)}`}</h3>
              {s.locked && <span className="badge text-bg-secondary">Đã dùng</span>}
              {canEdit && !s.locked && (
                <span className="ms-auto d-flex gap-2">
                  <Button type="button" size="sm" variant="outline-primary" onClick={() => startEdit(s)}>
                    Sửa
                  </Button>
                  <Button type="button" size="sm" variant="outline-danger" onClick={() => setDeleting(s)}>
                    Xóa
                  </Button>
                </span>
              )}
            </div>
            <ul className="mb-0">
              {summary(s).map(t => (
                <li key={t}>{t}</li>
              ))}
            </ul>
          </div>
        ))}
        <Modal show={deleting != null} onHide={() => setDeleting(null)}>
          <Modal.Header closeButton>
            <Modal.Title>Xóa bộ thông số</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            {deleting ? `Xóa bộ thông số hiệu lực từ ${periodText(deleting.effectiveFrom)}? Các kỳ chưa duyệt sẽ dùng bộ trước đó.` : ''}
          </Modal.Body>
          <Modal.Footer>
            <Button variant="outline-secondary" onClick={() => setDeleting(null)}>
              Hủy
            </Button>
            <Button variant="danger" onClick={() => void confirmDelete()}>
              Xóa bộ thông số
            </Button>
          </Modal.Footer>
        </Modal>
      </section>
      <section aria-labelledby="calc-rules-heading">
        <h2 id="calc-rules-heading" className="h5">
          Mức luật định
        </h2>
        <PayrollRules canEdit={canEdit} />
      </section>
    </div>
  );
};

export default PayrollSettingsTab;
