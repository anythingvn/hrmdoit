import React, { useState } from 'react';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

import { errorText } from '../detail/detail-api';
import { ServerError, TextField } from '../detail/form-fields';
import { archiveApi } from './archive-api';

interface IDeleteEmployeeDialogProps {
  employee: { id: number; code?: string | null; fullName?: string | null };
  onClose: () => void;
  onDone: () => void;
}

const WILL_DELETE = [
  'Hồ sơ và mọi dữ liệu cá nhân trên hồ sơ (CCCD, tài khoản ngân hàng, địa chỉ...)',
  'Hợp đồng, thay đổi lương, phụ cấp, nơi làm việc, quyết định, văn bản đã sinh',
  'Hồ sơ scan: bản ghi và file',
  'Đơn nghỉ, đơn làm thêm giờ, sổ phép',
  'Lịch sử nghỉ việc, các lần làm việc, lịch sử gửi phiếu lương',
  'Lịch sử thay đổi của các dữ liệu trên',
  'Tài khoản đăng nhập đã gắn',
];

const KEPT = [
  'Dòng lương các kỳ đã duyệt: số tiền, mã và họ tên như trong file lương, hiện là “Mã – Họ tên (đã xóa hồ sơ)”',
  'Nhật ký xóa: người xóa, thời điểm, lý do, mã và họ tên (không có CCCD, ngân hàng, địa chỉ)',
];

/**
 * Xóa hẳn hồ sơ đang ở vùng lưu trữ (chỉ tài khoản admin; server kiểm tra). Bắt gõ lại đúng mã nhân viên và nhập lý do.
 */
export const DeleteEmployeeDialog = ({ employee, onClose, onDone }: IDeleteEmployeeDialogProps) => {
  const [confirmCode, setConfirmCode] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const code = employee.code ?? '';
  const ready = code !== '' && confirmCode.trim() === code && reason.trim() !== '' && !busy;
  const title = `Xóa hẳn hồ sơ ${[code, employee.fullName].filter(Boolean).join(' – ')}`;

  const submit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    if (!ready) return;
    setError(null);
    setBusy(true);
    try {
      await archiveApi.deletePermanently(employee.id, confirmCode.trim(), reason.trim());
      onDone();
      onClose();
    } catch (err) {
      setError(errorText(err, 'Không xóa hẳn được, vui lòng thử lại'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal className="doit-modal" show onHide={onClose} aria-labelledby="delete-employee-title" animation={false}>
      <form onSubmit={e => void submit(e)} noValidate>
        <ModalHeader closeButton closeLabel="Đóng">
          <ModalTitle as="h2" id="delete-employee-title">
            {title}
          </ModalTitle>
        </ModalHeader>
        <ModalBody>
          <ServerError message={error} />
          <div className="alert alert-danger">
            <strong>Thao tác này không hoàn tác được.</strong> Dữ liệu bị xóa không khôi phục lại được.
          </div>
          <h3 className="h6">Sẽ bị xóa</h3>
          <ul>
            {WILL_DELETE.map(t => (
              <li key={t}>{t}</li>
            ))}
          </ul>
          <h3 className="h6">Được giữ</h3>
          <ul>
            {KEPT.map(t => (
              <li key={t}>{t}</li>
            ))}
          </ul>
          <div className="row g-3">
            <TextField
              id="delete-confirm-code"
              label={`Gõ lại mã nhân viên (${code})`}
              value={confirmCode}
              onChange={setConfirmCode}
              required
            />
            <TextField
              id="delete-reason"
              label="Lý do xóa hẳn"
              className="col-12"
              value={reason}
              onChange={setReason}
              multiline
              required
              hint="Bắt buộc, tối đa 500 ký tự"
            />
          </div>
        </ModalBody>
        <ModalFooter>
          <Button type="button" variant="outline-secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="danger" disabled={!ready}>
            Xóa hẳn
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
};

export default DeleteEmployeeDialog;
