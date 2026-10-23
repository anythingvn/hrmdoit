import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Button, Form, ListGroup } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';

interface ILead {
  employeeId: number;
  code?: string;
  fullName?: string;
}

interface ICandidate {
  id: number;
  code?: string;
  fullName?: string;
}

const CANDIDATES_URL = 'api/employees?personType.equals=NHAN_VIEN&status.equals=DANG_LAM_VIEC&size=1000&sort=fullName,asc';

interface Props {
  siteId: number;
  canEdit: boolean;
}

/** Trưởng nhóm onsite của một khách hàng: gõ giờ cho người onsite ở khách hàng này vào đúng ngày onsite. */
export const OnsiteLeads = ({ siteId, canEdit }: Props) => {
  const [leads, setLeads] = useState<ILead[]>([]);
  const [candidates, setCandidates] = useState<ICandidate[]>([]);
  const [pick, setPick] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    axios
      .get<ILead[]>(`api/work-sites/${siteId}/onsite-leads`)
      .then(r => setLeads(Array.isArray(r.data) ? r.data : []))
      .catch(err => setError(errorText(err, 'Không tải được trưởng nhóm onsite')));
    if (canEdit) {
      axios
        .get<ICandidate[]>(CANDIDATES_URL)
        .then(r => setCandidates(Array.isArray(r.data) ? r.data : []))
        .catch(() => setCandidates([]));
    }
  }, [siteId, canEdit]);

  const save = async (next: ILead[], ok: string) => {
    setError(null);
    setNotice(null);
    try {
      const r = await axios.put<ILead[]>(`api/work-sites/${siteId}/onsite-leads`, { employeeIds: next.map(l => l.employeeId) });
      setLeads(Array.isArray(r.data) ? r.data : next);
      setNotice(ok);
    } catch (err) {
      setError(errorText(err));
    }
  };

  const add = () => {
    const c = candidates.find(x => String(x.id) === pick);
    if (!c || leads.some(l => l.employeeId === c.id)) return;
    setPick('');
    save([...leads, { employeeId: c.id, code: c.code, fullName: c.fullName }], `Đã thêm ${c.fullName} làm trưởng nhóm onsite.`);
  };

  const remove = (l: ILead) =>
    save(
      leads.filter(x => x.employeeId !== l.employeeId),
      `Đã bỏ ${l.fullName} khỏi trưởng nhóm onsite.`,
    );

  const available = candidates.filter(c => !leads.some(l => l.employeeId === c.id));

  return (
    <section className="mb-4">
      <h2 className="h5">Trưởng nhóm onsite</h2>
      <p className="text-muted small">Trưởng nhóm onsite gõ giờ vào/ra cho người đang onsite ở khách hàng này vào đúng ngày onsite.</p>
      {error && <Alert variant="danger">{error}</Alert>}
      {notice && <Alert variant="success">{notice}</Alert>}
      {leads.length === 0 ? (
        <p>Chưa có trưởng nhóm onsite</p>
      ) : (
        <ListGroup className="mb-2">
          {leads.map(l => (
            <ListGroup.Item key={l.employeeId} className="d-flex justify-content-between align-items-center">
              <span>
                {l.fullName} {l.code && <span className="text-muted">({l.code})</span>}
              </span>
              {canEdit && (
                <Button
                  variant="link"
                  className="text-danger"
                  style={{ minHeight: 44 }}
                  onClick={() => remove(l)}
                  aria-label={`Bỏ ${l.fullName}`}
                >
                  Bỏ
                </Button>
              )}
            </ListGroup.Item>
          ))}
        </ListGroup>
      )}
      {canEdit && (
        <div className="row g-2 align-items-end">
          <Form.Group className="col-12 col-md-6" controlId={`onsite-lead-${siteId}`}>
            <Form.Label>Thêm trưởng nhóm onsite</Form.Label>
            <Form.Select value={pick} onChange={e => setPick(e.target.value)} style={{ minHeight: 44 }}>
              <option value="">Chọn nhân viên</option>
              {available.map(c => (
                <option key={c.id} value={c.id}>
                  {c.fullName} ({c.code})
                </option>
              ))}
            </Form.Select>
          </Form.Group>
          <div className="col-12 col-md-auto">
            <Button onClick={add} disabled={!pick} style={{ minHeight: 44 }}>
              Thêm
            </Button>
          </div>
        </div>
      )}
    </section>
  );
};

export default OnsiteLeads;
