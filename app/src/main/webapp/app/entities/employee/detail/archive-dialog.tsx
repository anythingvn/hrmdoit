import React, { useState } from 'react';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

import { IArchiveResult } from '../archive/archive-api';
import { errorText } from './detail-api';
import { ServerError, TextField } from './form-fields';

interface IArchiveDialogProps {
  employeeName?: string;
  onConfirm: (note: string) => Promise<IArchiveResult>;
  onClose: () => void;
  onDone: (result: IArchiveResult) => void;
}

/** Xác nhận đưa hồ sơ vào lưu trữ, với ghi chú không bắt buộc. Không xóa, không sửa dữ liệu nào khác. */
export const ArchiveDialog = ({ employeeName, onConfirm, onClose, onDone }: IArchiveDialogProps) => {
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    if (note.trim().length > 500) {
      setError('Ghi chú lưu trữ tối đa 500 ký tự');
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const result = await onConfirm(note);
      onDone(result);
      onClose();
    } catch (err) {
      setError(errorText(err, 'Không lưu trữ được, vui lòng thử lại'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal className="doit-modal" show onHide={onClose} aria-labelledby="archive-dialog-title" animation={false}>
      <form onSubmit={e => void submit(e)} noValidate>
        <ModalHeader closeButton closeLabel="Đóng">
          <ModalTitle as="h2" id="archive-dialog-title">
            Đưa vào lưu trữ{employeeName ? `: ${employeeName}` : ''}
          </ModalTitle>
        </ModalHeader>
        <ModalBody>
          <ServerError message={error} />
          <p>
            Hồ sơ không còn hiện ở danh sách, ô chọn, nhắc việc và báo cáo hiện tại; bảng lương, phiếu lương kỳ cũ vẫn giữ nguyên. Không dữ
            liệu nào bị xóa hay sửa. Khôi phục được bất cứ lúc nào từ trang “Nhân viên đã lưu trữ”.
          </p>
          <div className="row g-3">
            <TextField id="archive-note" label="Ghi chú (không bắt buộc)" className="col-12" value={note} onChange={setNote} multiline />
          </div>
        </ModalBody>
        <ModalFooter>
          <Button type="button" variant="outline-secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="danger" disabled={busy}>
            Xác nhận lưu trữ
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
};

export default ArchiveDialog;
