import React, { useState } from 'react';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

import { PROBATION_RESULT_LABEL, errorText } from 'app/entities/employee/detail/detail-api';
import { DateField, SelectField, ServerError } from 'app/entities/employee/detail/form-fields';
import { vnToIso } from 'app/entities/employee/vn-date';

const todayVn = () => {
  const d = new Date();
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
};

interface IDialogProps {
  employeeName: string;
  onClose: () => void;
  onSaved: () => void;
}

/** Khung chung của hai hộp xử lý nhanh: tiêu đề, lỗi máy chủ, Hủy / Lưu. */
const QuickDialog = ({
  id,
  title,
  onClose,
  onSubmit,
  serverError,
  saving,
  children,
}: {
  id: string;
  title: string;
  onClose: () => void;
  onSubmit: () => void;
  serverError: string | null;
  saving: boolean;
  children: React.ReactNode;
}) => (
  <Modal className="doit-modal" show onHide={onClose} aria-labelledby={`${id}-title`} animation={false}>
    <form
      noValidate
      onSubmit={e => {
        e.preventDefault();
        onSubmit();
      }}
    >
      <ModalHeader closeButton closeLabel="Đóng">
        <ModalTitle as="h2" id={`${id}-title`}>
          {title}
        </ModalTitle>
      </ModalHeader>
      <ModalBody>
        <ServerError message={serverError} />
        <div className="row g-3">{children}</div>
      </ModalBody>
      <ModalFooter>
        <Button type="button" variant="outline-secondary" onClick={onClose}>
          Hủy
        </Button>
        <Button type="submit" variant="primary" disabled={saving}>
          Lưu
        </Button>
      </ModalFooter>
    </form>
  </Modal>
);

const useSave = (onSaved: () => void) => {
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const save = async (request: () => Promise<unknown>) => {
    setSaving(true);
    setServerError(null);
    try {
      await request();
      onSaved();
    } catch (err) {
      setServerError(errorText(err));
    } finally {
      setSaving(false);
    }
  };
  return { saving, serverError, save };
};

/** Ghi kết quả thử việc lên HĐ thử việc; ghi xong thì hết nhắc. */
export const ProbationResultDialog = ({ contractId, employeeName, onClose, onSaved }: IDialogProps & { contractId: number }) => {
  const [result, setResult] = useState('');
  const [date, setDate] = useState(todayVn);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const { saving, serverError, save } = useSave(onSaved);

  const submit = () => {
    const iso = vnToIso(date);
    const e: Record<string, string> = {};
    if (!result) e.result = 'Chọn kết quả thử việc';
    if (!iso) e.date = 'Nhập ngày dạng dd/MM/yyyy';
    setErrors(e);
    if (Object.keys(e).length) return;
    save(() =>
      axios.patch(`api/employment-contracts/${contractId}`, { id: contractId, probationResult: result, probationResultDate: iso }),
    );
  };

  return (
    <QuickDialog
      id="probation-result"
      title={`Ghi kết quả thử việc – ${employeeName}`}
      onClose={onClose}
      onSubmit={submit}
      serverError={serverError}
      saving={saving}
    >
      <SelectField
        id="probation-result-value"
        label="Kết quả thử việc"
        value={result}
        onChange={setResult}
        options={Object.entries(PROBATION_RESULT_LABEL)}
        emptyLabel="Chọn…"
        error={errors.result}
      />
      <DateField id="probation-result-date" label="Ngày ghi kết quả" value={date} onChange={setDate} error={errors.date} />
    </QuickDialog>
  );
};

/** Hẹn ngày xét lại lương trên lần điều chỉnh lương gần nhất; nhắc chuyển sang ngày mới. */
export const ReviewDateDialog = ({ salaryChangeId, employeeName, onClose, onSaved }: IDialogProps & { salaryChangeId: number }) => {
  const [date, setDate] = useState('');
  const [error, setError] = useState<string | undefined>();
  const { saving, serverError, save } = useSave(onSaved);

  const submit = () => {
    const iso = vnToIso(date);
    if (!iso) {
      setError('Nhập ngày dạng dd/MM/yyyy');
      return;
    }
    setError(undefined);
    save(() => axios.patch(`api/salary-changes/${salaryChangeId}`, { id: salaryChangeId, nextReviewOverride: iso }));
  };

  return (
    <QuickDialog
      id="review-date"
      title={`Hẹn xét lại lương – ${employeeName}`}
      onClose={onClose}
      onSubmit={submit}
      serverError={serverError}
      saving={saving}
    >
      <DateField id="review-date-value" label="Hẹn xét lại ngày" value={date} onChange={setDate} error={error} />
    </QuickDialog>
  );
};
