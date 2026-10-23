import React, { useState } from 'react';
import { Alert, Button, Form, Modal } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';

import { IPunchView, punchReasonText, punchSourceText } from './attendance-api';

const TOUCH = { minHeight: 44 };

interface Props {
  punches?: IPunchView[];
  /** Có nút Duyệt, Từ chối cho lần chờ duyệt (người duyệt của ngày đó). */
  canReview: boolean;
  approve: (id: number) => Promise<unknown>;
  reject: (id: number, note: string) => Promise<unknown>;
}

/**
 * Các lần chấm của một ngày, mỗi lần một dòng "Vào 08:03 · Tự chấm · IP" (lần gõ tay không hiện: đã có ở ô nhập). Lần bị từ chối hiện
 * mờ. Lần chờ duyệt có nút Duyệt, Từ chối (hộp nhập lý do bắt buộc) khi {@code canReview}.
 */
export const PunchList = ({ punches, canReview, approve, reject }: Props) => {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState<IPunchView | null>(null);
  const [note, setNote] = useState('');
  const [noteError, setNoteError] = useState<string | null>(null);

  const shown = (punches ?? []).filter(p => p.source !== 'GO_TAY');
  if (shown.length === 0) return null;

  const doApprove = async (p: IPunchView) => {
    setError(null);
    setBusy(true);
    try {
      await approve(p.id);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const doReject = async () => {
    if (!rejecting) return;
    setNoteError(null);
    setBusy(true);
    try {
      await reject(rejecting.id, note.trim());
      setRejecting(null);
    } catch (err) {
      setNoteError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <ul className="list-unstyled small mb-0 mt-1">
        {shown.map(p => (
          <li
            key={p.id}
            data-testid={`punch-${p.id}`}
            className="d-flex flex-wrap align-items-center gap-2 py-1"
            style={p.status === 'TU_CHOI' ? { opacity: 0.5 } : undefined}
          >
            <span>
              {p.kind === 'VAO' ? 'Vào' : 'Ra'} {p.time} · {punchSourceText(p)}
            </span>
            {punchReasonText(p) && <span className="text-muted">({punchReasonText(p)})</span>}
            {canReview && p.status === 'CHO_DUYET' && (
              <span className="d-flex gap-1">
                <Button size="sm" style={TOUCH} disabled={busy} onClick={() => void doApprove(p)}>
                  Duyệt
                </Button>
                <Button
                  size="sm"
                  variant="outline-danger"
                  style={TOUCH}
                  disabled={busy}
                  onClick={() => {
                    setRejecting(p);
                    setNote('');
                    setNoteError(null);
                  }}
                >
                  Từ chối
                </Button>
              </span>
            )}
          </li>
        ))}
      </ul>
      {error && (
        <Alert variant="danger" className="mt-1 mb-0 py-1 small" role="alert">
          {error}
        </Alert>
      )}
      <Modal show={!!rejecting} onHide={() => setRejecting(null)} centered>
        <Modal.Header closeButton closeLabel="Đóng">
          <Modal.Title as="h3" className="h5">
            Từ chối lần chấm {rejecting && `${rejecting.kind === 'VAO' ? 'Vào' : 'Ra'} ${rejecting.time}`}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {noteError && (
            <Alert variant="danger" role="alert">
              {noteError}
            </Alert>
          )}
          <Form.Group controlId="punch-reject-note">
            <Form.Label>Lý do từ chối</Form.Label>
            <Form.Control as="textarea" rows={3} maxLength={500} value={note} onChange={e => setNote(e.target.value)} />
          </Form.Group>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="outline-secondary" style={TOUCH} onClick={() => setRejecting(null)}>
            Hủy bỏ
          </Button>
          <Button variant="danger" style={TOUCH} disabled={busy || note.trim() === ''} onClick={() => void doReject()}>
            Xác nhận từ chối
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
};

export default PunchList;
