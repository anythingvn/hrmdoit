import React, { useState } from 'react';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

import { errorText } from './detail-api';
import { ServerError } from './form-fields';

interface IConfirmDialogProps {
  id: string;
  title: string;
  children: React.ReactNode;
  confirmLabel: string;
  /** Thao tác khi xác nhận; lỗi được hiện trong hộp thoại, thành công thì đóng. */
  onConfirm: () => Promise<unknown>;
  onClose: () => void;
  onDone: () => void;
  failText: string;
}

/** Hộp thoại xác nhận một thao tác (hủy lịch nghỉ, hoàn tác nghỉ việc). */
export const ConfirmDialog = ({ id, title, children, confirmLabel, onConfirm, onClose, onDone, failText }: IConfirmDialogProps) => {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const confirm = async () => {
    setError(null);
    setBusy(true);
    try {
      await onConfirm();
      onDone();
      onClose();
    } catch (err) {
      setError(errorText(err, failText));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal className="doit-modal" show onHide={onClose} aria-labelledby={`${id}-title`} animation={false}>
      <ModalHeader closeButton closeLabel="Đóng">
        <ModalTitle as="h2" id={`${id}-title`}>
          {title}
        </ModalTitle>
      </ModalHeader>
      <ModalBody>
        <ServerError message={error} />
        {children}
      </ModalBody>
      <ModalFooter>
        <Button type="button" variant="outline-secondary" onClick={onClose}>
          Đóng
        </Button>
        <Button type="button" variant="danger" disabled={busy} onClick={() => void confirm()}>
          {confirmLabel}
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default ConfirmDialog;
