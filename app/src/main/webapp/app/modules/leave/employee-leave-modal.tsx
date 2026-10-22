import React, { useEffect, useState } from 'react';
import { Alert, Modal } from 'react-bootstrap';

import LeaveTab from 'app/entities/employee/detail/leave-tab';

import { IEmployeeLeave, leaveApi, leaveErrorText } from './leave-api';

export interface LeavePerson {
  employeeId: number;
  name: string;
}

export const NO_LEAVE_PERMISSION = 'Bạn không có quyền xem nghỉ phép của người này';

/**
 * Nghỉ phép của một người (số dư, đơn, sổ) trong hộp thoại: mở từ Lịch nghỉ và Duyệt đơn, để trưởng nhóm / trưởng phòng không có vai trò
 * nghiệp vụ (không mở được hồ sơ) vẫn xem được. Quyền do server kiểm tra; 403 thì báo không có quyền.
 */
export const EmployeeLeaveModal = ({ person, onClose }: { person: LeavePerson | null; onClose: () => void }) => {
  const [data, setData] = useState<IEmployeeLeave | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setData(null);
    setError(null);
    if (!person) return;
    let live = true;
    leaveApi
      .employeeLeave(person.employeeId)
      .then(d => live && setData(d))
      .catch(err => {
        if (!live) return;
        setError(err?.response?.status === 403 ? NO_LEAVE_PERMISSION : leaveErrorText(err, 'ngày', 'Không tải được nghỉ phép'));
      });
    return () => {
      live = false;
    };
  }, [person?.employeeId]);

  return (
    <Modal show={!!person} onHide={onClose} centered size="xl" className="leave-modal" aria-labelledby="person-leave-title">
      <Modal.Header closeButton closeLabel="Đóng hộp thoại">
        <Modal.Title as="h3" className="h5" id="person-leave-title">
          Nghỉ phép: {person?.name}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {error && (
          <Alert variant="warning" role="alert" className="mb-0">
            {error}
          </Alert>
        )}
        {!error && !data && <p className="mb-0">Đang tải...</p>}
        <LeaveTab data={data} />
      </Modal.Body>
    </Modal>
  );
};

export default EmployeeLeaveModal;
