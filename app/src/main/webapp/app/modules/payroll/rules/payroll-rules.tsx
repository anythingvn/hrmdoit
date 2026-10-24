import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Card, Form, Modal, Table } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { formatMoney, parseMoney } from 'app/entities/employee/money';
import MonthInput from 'app/shared/month-input';

import { periodText } from '../payroll-api';

interface IBracket {
  upTo: number | null;
  rate: number;
}

export interface IRuleMilestone {
  id: number;
  effectiveFrom: string;
  personalDeduction: number;
  dependantDeduction: number;
  socialInsuranceCap: number;
  unemploymentInsuranceCap: number;
  brackets: IBracket[];
  legalBasis?: string | null;
  createdBy?: string | null;
  /** Đã dùng cho bảng lương (kỳ đã duyệt hoặc phiên bản đã xác nhận): không sửa, không xóa. */
  used?: boolean;
}

interface IRules {
  knownThrough: string;
  milestones: IRuleMilestone[];
}

const percent = (rate: number) => `${Number((rate * 100).toFixed(2))}%`;

const emptyForm = () => ({
  effectiveFrom: '',
  personalDeduction: '',
  dependantDeduction: '',
  socialInsuranceCap: '',
  unemploymentInsuranceCap: '',
  legalBasis: '',
  brackets: [
    { upTo: '', rate: '' },
    { upTo: '', rate: '' },
  ],
});

const TOUCH = { minHeight: 44 };

const formOf = (m: IRuleMilestone) => ({
  effectiveFrom: m.effectiveFrom,
  personalDeduction: formatMoney(m.personalDeduction),
  dependantDeduction: formatMoney(m.dependantDeduction),
  socialInsuranceCap: formatMoney(m.socialInsuranceCap),
  unemploymentInsuranceCap: formatMoney(m.unemploymentInsuranceCap),
  legalBasis: m.legalBasis ?? '',
  brackets: m.brackets.map(b => ({ upTo: b.upTo == null ? '' : formatMoney(b.upTo), rate: String(Number((b.rate * 100).toFixed(2))) })),
});

/**
 * Mức luật định theo ngày hiệu lực (giảm trừ, trần bảo hiểm, biểu thuế). Mốc mới và mốc đang sửa phải có hiệu lực sau kỳ lương
 * đã duyệt gần nhất; mốc đã dùng cho bảng lương (kỳ đã duyệt hoặc phiên bản đã xác nhận) thì khóa (không sửa, không xóa) nên kết quả các kỳ đã lưu không đổi.
 */
export const PayrollRules = ({ canEdit }: { canEdit: boolean }) => {
  const [rules, setRules] = useState<IRules | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [deleting, setDeleting] = useState<IRuleMilestone | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [knownThrough, setKnownThrough] = useState('');

  const load = () =>
    axios
      .get<IRules>('api/payroll/rules')
      .then(r => {
        setRules(r.data);
        setKnownThrough(r.data.knownThrough);
      })
      .catch(err => setError(errorText(err, 'Không tải được mức luật định')));

  useEffect(() => {
    load();
  }, []);

  const setField = (key: keyof ReturnType<typeof emptyForm>) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm(f => ({ ...f, [key]: e.target.value }));

  const setBracket = (i: number, key: 'upTo' | 'rate') => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm(f => ({ ...f, brackets: f.brackets.map((b, j) => (j === i ? { ...b, [key]: e.target.value } : b)) }));

  const addBracket = () =>
    setForm(f => ({ ...f, brackets: [...f.brackets.slice(0, -1), { upTo: '', rate: '' }, f.brackets[f.brackets.length - 1]] }));

  const save = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setError(null);
    setNotice(null);
    const money = (s: string) => parseMoney(s);
    const amounts = [form.personalDeduction, form.dependantDeduction, form.socialInsuranceCap, form.unemploymentInsuranceCap].map(money);
    const brackets = form.brackets.map((b, i) => ({
      upTo: i === form.brackets.length - 1 ? null : money(b.upTo),
      rate: Number((Number(b.rate.replace(',', '.')) / 100).toFixed(4)),
    }));
    if (
      !/^\d{4}-\d{2}$/.test(form.effectiveFrom) ||
      amounts.some(a => a == null) ||
      brackets.some((b, i) => isNaN(b.rate) || (i < brackets.length - 1 && b.upTo == null))
    ) {
      setError('Nhập đủ tháng hiệu lực, các mức tiền và các bậc thuế');
      return;
    }
    try {
      const body = {
        effectiveFrom: form.effectiveFrom,
        personalDeduction: amounts[0],
        dependantDeduction: amounts[1],
        socialInsuranceCap: amounts[2],
        unemploymentInsuranceCap: amounts[3],
        brackets,
        legalBasis: form.legalBasis.trim(),
      };
      if (editingId == null) {
        await axios.post('api/payroll/rules', body);
        setNotice(`Đã thêm mốc hiệu lực từ ${periodText(form.effectiveFrom)}.`);
      } else {
        await axios.put(`api/payroll/rules/${editingId}`, body);
        setNotice(`Đã sửa mốc hiệu lực từ ${periodText(form.effectiveFrom)}.`);
      }
      setFormOpen(false);
      setEditingId(null);
      setForm(emptyForm());
      await load();
    } catch (err) {
      setError(errorText(err, 'Không lưu được mốc luật'));
    }
  };

  const startEdit = (m: IRuleMilestone) => {
    setError(null);
    setNotice(null);
    setForm(formOf(m));
    setEditingId(m.id);
    setFormOpen(true);
  };

  const cancelForm = () => {
    setFormOpen(false);
    setEditingId(null);
    setForm(emptyForm());
  };

  const confirmDelete = async () => {
    if (!deleting) return;
    const target = deleting;
    setError(null);
    setNotice(null);
    try {
      await axios.delete(`api/payroll/rules/${target.id}`);
      setNotice(`Đã xóa mốc hiệu lực từ ${periodText(target.effectiveFrom)}.`);
      await load();
    } catch (err) {
      setError(errorText(err, 'Không xóa được mốc luật'));
    } finally {
      setDeleting(null);
    }
  };

  const saveKnownThrough = async () => {
    setError(null);
    setNotice(null);
    try {
      await axios.put('api/payroll/rules/known-through', { knownThrough });
      setNotice(`Đã đặt: biết chắc luật đến tháng ${periodText(knownThrough)}.`);
      await load();
    } catch (err) {
      setError(errorText(err));
    }
  };

  return (
    <div>
      <p className="text-muted">
        Mức luật dùng để đối chiếu bảng lương và gợi ý Gross. Mỗi mốc áp dụng từ tháng hiệu lực đến trước mốc kế tiếp. Mốc chưa dùng thì
        sửa, xóa được; mốc đã dùng cho bảng lương (kỳ đã duyệt hoặc phiên bản đã xác nhận) thì khóa, nên các kỳ đã lưu giữ nguyên kết quả.
        Tháng hiệu lực mới phải sau kỳ lương đã duyệt gần nhất và không trùng tháng của mốc đã dùng.
      </p>
      {error && <Alert variant="danger">{error}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}
      {rules && (
        <div className="d-flex flex-wrap align-items-end gap-2 mb-3">
          {canEdit ? (
            <>
              <Form.Group controlId="rules-known-through">
                <Form.Label>Đã biết chắc luật đến tháng (MM/yyyy)</Form.Label>
                <MonthInput value={knownThrough} onChange={setKnownThrough} />
              </Form.Group>
              <Button variant="outline-primary" onClick={saveKnownThrough}>
                Lưu tháng
              </Button>
            </>
          ) : null}
          <span className="text-muted">
            Đã biết chắc luật đến tháng {periodText(rules.knownThrough)}; kỳ sau tháng này chỉ là ước tính.
          </span>
        </div>
      )}
      {canEdit && !formOpen && (
        <Button
          className="mb-3"
          style={TOUCH}
          onClick={() => {
            setEditingId(null);
            setForm(emptyForm());
            setFormOpen(true);
          }}
        >
          Thêm mốc
        </Button>
      )}
      {canEdit && formOpen && (
        <Card className="mb-3">
          <Card.Body>
            <h2 className="h5">{editingId == null ? 'Thêm mốc' : 'Sửa mốc'}</h2>
            <Form onSubmit={save} noValidate>
              <div className="row g-3">
                <Form.Group className="col-12 col-md-4" controlId="rule-from">
                  <Form.Label>Hiệu lực từ tháng (MM/yyyy)</Form.Label>
                  <MonthInput value={form.effectiveFrom} onChange={v => setForm(f => ({ ...f, effectiveFrom: v }))} />
                </Form.Group>
                <Form.Group className="col-12 col-md-4" controlId="rule-personal">
                  <Form.Label>Giảm trừ bản thân</Form.Label>
                  <Form.Control inputMode="numeric" value={form.personalDeduction} onChange={setField('personalDeduction')} />
                </Form.Group>
                <Form.Group className="col-12 col-md-4" controlId="rule-dependant">
                  <Form.Label>Giảm trừ người phụ thuộc</Form.Label>
                  <Form.Control inputMode="numeric" value={form.dependantDeduction} onChange={setField('dependantDeduction')} />
                </Form.Group>
                <Form.Group className="col-12 col-md-6" controlId="rule-si-cap">
                  <Form.Label>Trần đóng BHXH, BHYT</Form.Label>
                  <Form.Control inputMode="numeric" value={form.socialInsuranceCap} onChange={setField('socialInsuranceCap')} />
                </Form.Group>
                <Form.Group className="col-12 col-md-6" controlId="rule-ui-cap">
                  <Form.Label>Trần đóng BHTN</Form.Label>
                  <Form.Control inputMode="numeric" value={form.unemploymentInsuranceCap} onChange={setField('unemploymentInsuranceCap')} />
                </Form.Group>
              </div>
              <h3 className="h6 mt-3">Biểu thuế TNCN (thu nhập tính thuế tháng)</h3>
              {form.brackets.map((b, i) => {
                const last = i === form.brackets.length - 1;
                return (
                  <div className="row g-2 mb-2" key={i}>
                    <Form.Group className="col-7" controlId={`rule-upto-${i}`}>
                      <Form.Label className={last ? 'visually-hidden' : undefined}>Mức trên bậc {i + 1}</Form.Label>
                      {last ? (
                        <div className="form-control-plaintext">Phần còn lại</div>
                      ) : (
                        <Form.Control inputMode="numeric" value={b.upTo} onChange={setBracket(i, 'upTo')} />
                      )}
                    </Form.Group>
                    <Form.Group className="col-5" controlId={`rule-rate-${i}`}>
                      <Form.Label>Thuế suất bậc {i + 1} (%)</Form.Label>
                      <Form.Control inputMode="decimal" value={b.rate} onChange={setBracket(i, 'rate')} />
                    </Form.Group>
                  </div>
                );
              })}
              <Button variant="link" onClick={addBracket}>
                + Thêm bậc
              </Button>
              <Form.Group className="mt-2" controlId="rule-basis">
                <Form.Label>Căn cứ văn bản</Form.Label>
                <Form.Control value={form.legalBasis} onChange={setField('legalBasis')} />
              </Form.Group>
              <div className="d-flex gap-2 mt-3">
                <Button type="submit" style={TOUCH}>
                  Lưu mốc
                </Button>
                <Button variant="outline-secondary" style={TOUCH} onClick={cancelForm}>
                  Hủy
                </Button>
              </div>
            </Form>
          </Card.Body>
        </Card>
      )}
      {rules?.milestones.map(m => (
        <Card key={m.id} className="mb-3" data-testid={`milestone-${m.id}`}>
          <Card.Body>
            <h2 className="h5">Hiệu lực từ {periodText(m.effectiveFrom)}</h2>
            <Table size="sm" className="mb-2">
              <tbody>
                <tr>
                  <th scope="row">Giảm trừ bản thân</th>
                  <td className="text-end">{formatMoney(m.personalDeduction)}</td>
                </tr>
                <tr>
                  <th scope="row">Giảm trừ người phụ thuộc</th>
                  <td className="text-end">{formatMoney(m.dependantDeduction)}</td>
                </tr>
                <tr>
                  <th scope="row">Trần đóng BHXH, BHYT</th>
                  <td className="text-end">{formatMoney(m.socialInsuranceCap)}</td>
                </tr>
                <tr>
                  <th scope="row">Trần đóng BHTN</th>
                  <td className="text-end">{formatMoney(m.unemploymentInsuranceCap)}</td>
                </tr>
              </tbody>
            </Table>
            <Table size="sm" className="mb-2">
              <thead>
                <tr>
                  <th scope="col">Thu nhập tính thuế tháng</th>
                  <th scope="col" className="text-end">
                    Thuế suất
                  </th>
                </tr>
              </thead>
              <tbody>
                {m.brackets.map((b, i) => (
                  <tr key={i}>
                    <td>{b.upTo == null ? 'Phần còn lại' : `Đến ${formatMoney(b.upTo)}`}</td>
                    <td className="text-end">{percent(b.rate)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
            {m.legalBasis && <div className="text-muted small">Căn cứ: {m.legalBasis}</div>}
            {canEdit && m.used && (
              <div className="text-muted small mt-2">
                Đã dùng cho bảng lương (kỳ đã duyệt hoặc phiên bản đã xác nhận) nên không sửa, không xóa.
              </div>
            )}
            {canEdit && !m.used && (
              <div className="d-flex gap-2 mt-2">
                <Button variant="outline-primary" style={TOUCH} onClick={() => startEdit(m)}>
                  Sửa
                </Button>
                <Button variant="outline-danger" style={TOUCH} onClick={() => setDeleting(m)}>
                  Xóa
                </Button>
              </div>
            )}
          </Card.Body>
        </Card>
      ))}
      <Modal show={!!deleting} onHide={() => setDeleting(null)} centered>
        <Modal.Header closeButton closeLabel="Đóng">
          <Modal.Title as="h3" className="h5">
            Xóa mốc hiệu lực từ {deleting && periodText(deleting.effectiveFrom)}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>Mốc và các bậc thuế của mốc sẽ bị xóa, không hoàn tác được. Tiếp tục?</Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" style={TOUCH} onClick={() => setDeleting(null)}>
            Hủy bỏ
          </Button>
          <Button variant="danger" style={TOUCH} onClick={() => void confirmDelete()}>
            Xác nhận xóa
          </Button>
        </Modal.Footer>
      </Modal>
    </div>
  );
};

export default PayrollRules;
