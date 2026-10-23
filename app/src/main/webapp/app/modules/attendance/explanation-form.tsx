import React, { useState } from 'react';
import { Alert, Button, Form, Modal } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { isoToVn } from 'app/entities/employee/vn-date';

import { attendanceApi } from './attendance-api';

const TOUCH = { minHeight: 44 };
const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;

interface Props {
  /** Ngày giải trình, yyyy-MM-dd. */
  day: string;
  initialIn?: string | null;
  initialOut?: string | null;
  onClose: () => void;
  /** Gọi sau khi gửi thành công. */
  onDone: () => void;
}

/** Hộp gửi giải trình: giờ vào và/hoặc giờ ra đề xuất (HH:mm), lý do bắt buộc. */
export const ExplanationForm = ({ day, initialIn, initialOut, onClose, onDone }: Props) => {
  const [proposedIn, setIn] = useState(initialIn ?? '');
  const [proposedOut, setOut] = useState(initialOut ?? '');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const validate = (): string | null => {
    const tin = proposedIn.trim();
    const tout = proposedOut.trim();
    if (!reason.trim()) return 'Cần nhập lý do giải trình';
    if (!tin && !tout) return 'Nhập giờ vào hoặc giờ ra đề xuất';
    if ((tin && !HHMM.test(tin)) || (tout && !HHMM.test(tout))) return 'Giờ phải có dạng HH:mm (ví dụ 08:30)';
    if (tin && tout && tin >= tout) return 'Giờ vào phải trước giờ ra';
    return null;
  };

  const submit = async () => {
    const problem = validate();
    setError(problem);
    if (problem) return;
    setBusy(true);
    try {
      await attendanceApi.submitExplanation({
        workDay: day,
        proposedIn: proposedIn.trim() || null,
        proposedOut: proposedOut.trim() || null,
        reason: reason.trim(),
      });
      onDone();
    } catch (err) {
      setError(errorText(err, 'Không gửi được giải trình, vui lòng thử lại'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal show onHide={onClose} centered aria-labelledby="explanation-title">
      <Modal.Header closeButton closeLabel="Đóng hộp thoại">
        <Modal.Title as="h3" className="h5" id="explanation-title">
          Giải trình {isoToVn(day)}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error && (
          <Alert variant="danger" role="alert">
            {error}
          </Alert>
        )}
        <Form.Group className="mb-3" controlId="explanation-in">
          <Form.Label>Giờ vào (để trống nếu giữ lần đã có)</Form.Label>
          <Form.Control
            style={TOUCH}
            placeholder="HH:mm"
            inputMode="numeric"
            maxLength={5}
            value={proposedIn}
            onChange={e => setIn(e.target.value)}
          />
        </Form.Group>
        <Form.Group className="mb-3" controlId="explanation-out">
          <Form.Label>Giờ ra đề xuất</Form.Label>
          <Form.Control
            style={TOUCH}
            placeholder="HH:mm"
            inputMode="numeric"
            maxLength={5}
            value={proposedOut}
            onChange={e => setOut(e.target.value)}
          />
        </Form.Group>
        <Form.Group className="mb-3" controlId="explanation-reason">
          <Form.Label>Lý do (bắt buộc)</Form.Label>
          <Form.Control as="textarea" rows={3} maxLength={500} value={reason} onChange={e => setReason(e.target.value)} />
        </Form.Group>
        <p className="text-muted small mb-0">
          Người duyệt: trưởng nhóm của bạn, hoặc HR. Mỗi ngày chỉ một giải trình đang chờ; rút được khi còn chờ.
        </p>
      </Modal.Body>
      <Modal.Footer>
        <Button variant="outline-secondary" style={TOUCH} onClick={onClose}>
          Hủy
        </Button>
        <Button style={TOUCH} disabled={busy} onClick={() => void submit()}>
          Gửi giải trình
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default ExplanationForm;
