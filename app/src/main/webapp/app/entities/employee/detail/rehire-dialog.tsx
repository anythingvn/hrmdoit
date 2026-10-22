import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader, ModalTitle } from 'react-bootstrap';

import { IDepartment } from 'app/shared/model/department.model';
import { PERSON_TYPE_LABEL, PersonType } from 'app/shared/model/employee.model';

import { archiveApi } from '../archive/archive-api';
import { isoToVn, vnToIso } from '../vn-date';
import { errorText } from './detail-api';
import { DateField, SelectField, ServerError, TextField } from './form-fields';

interface IRehireDialogProps {
  employee: { id: number; fullName?: string; lastWorkingDay?: string | null; position?: string | null; personType?: PersonType | null };
  onClose: () => void;
  onDone: () => void;
}

/**
 * Nhận làm lại người đã nghỉ (chưa lưu trữ): giữ mã, ghi lần làm việc trước, xóa trắng các trường nghỉ việc, mở lại tài khoản.
 * Phòng ban, vị trí, loại hồ sơ để trống thì giữ như cũ. Server kiểm tra ngày vào làm mới sau ngày làm việc cuối.
 */
export const RehireDialog = ({ employee, onClose, onDone }: IRehireDialogProps) => {
  const [joinDate, setJoinDate] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [position, setPosition] = useState('');
  const [personType, setPersonType] = useState('');
  const [departments, setDepartments] = useState<IDepartment[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    axios
      .get<IDepartment[]>('api/departments')
      .then(r => setDepartments((r?.data ?? []).filter(d => d.active !== false)))
      .catch(() => setDepartments([]));
  }, []);

  const handleSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setServerError(null);
    const e: Record<string, string> = {};
    const iso = vnToIso(joinDate);
    if (!joinDate.trim()) e.joinDate = 'Bắt buộc nhập';
    else if (!iso) e.joinDate = 'Ngày không hợp lệ, nhập dd/MM/yyyy';
    setErrors(e);
    if (Object.keys(e).length > 0 || !iso) return;
    setSaving(true);
    try {
      await archiveApi.rehire(employee.id, {
        joinDate: iso,
        departmentId: departmentId ? Number(departmentId) : null,
        position: position.trim() || null,
        personType: (personType || null) as PersonType | null,
      });
      onDone();
      onClose();
    } catch (err) {
      setServerError(errorText(err, 'Không nhận làm lại được, vui lòng thử lại'));
    } finally {
      setSaving(false);
    }
  };

  const lastDay = isoToVn(employee.lastWorkingDay);

  return (
    <Modal className="doit-modal" show onHide={onClose} aria-labelledby="rehire-dialog-title" animation={false}>
      <form onSubmit={e => void handleSubmit(e)} noValidate>
        <ModalHeader closeButton closeLabel="Đóng">
          <ModalTitle as="h2" id="rehire-dialog-title">
            Nhận làm lại{employee.fullName ? `: ${employee.fullName}` : ''}
          </ModalTitle>
        </ModalHeader>
        <ModalBody>
          <ServerError message={serverError} />
          <p>
            Hồ sơ giữ mã nhân viên cũ và chuyển sang “Đang làm việc”. Lần làm việc trước được ghi vào “Lịch sử thay đổi”; các trường nghỉ
            việc được xóa trắng. Tài khoản đăng nhập đã gắn được mở lại. Sổ phép và thâm niên tính từ ngày vào làm mới.
          </p>
          <p>Hợp đồng mới thêm như bình thường ở tab “Hợp đồng &amp; lương”; hệ thống không tự tạo.</p>
          <div className="row g-3">
            <DateField
              id="rehire-join-date"
              label="Ngày vào làm mới"
              value={joinDate}
              onChange={setJoinDate}
              error={errors.joinDate}
              hint={lastDay ? `Phải sau ngày làm việc cuối ${lastDay}` : undefined}
              required
            />
            <SelectField
              id="rehire-department"
              label="Phòng ban"
              value={departmentId}
              onChange={setDepartmentId}
              options={departments.filter(d => d.id != null).map(d => [String(d.id), d.name ?? ''])}
              emptyLabel="Giữ như cũ"
            />
            <TextField
              id="rehire-position"
              label="Vị trí"
              value={position}
              onChange={setPosition}
              placeholder={employee.position ?? undefined}
              hint="Để trống thì giữ như cũ"
            />
            <SelectField
              id="rehire-person-type"
              label="Loại hồ sơ"
              value={personType}
              onChange={setPersonType}
              options={Object.entries(PERSON_TYPE_LABEL)}
              emptyLabel={employee.personType ? `Giữ như cũ (${PERSON_TYPE_LABEL[employee.personType]})` : 'Giữ như cũ'}
              hint="Mã nhân viên giữ nguyên dù đổi loại hồ sơ; HR sửa tay được nếu cần"
            />
          </div>
        </ModalBody>
        <ModalFooter>
          <Button type="button" variant="outline-secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={saving}>
            Xác nhận nhận làm lại
          </Button>
        </ModalFooter>
      </form>
    </Modal>
  );
};

export default RehireDialog;
