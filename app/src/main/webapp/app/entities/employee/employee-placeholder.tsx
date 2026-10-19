import React from 'react';
import { Link, useParams } from 'react-router-dom';

// Trang chi tiết là Task 15; tạm thời chỉ có liên kết sang trang sửa.
export const EmployeeDetailPlaceholder = () => {
  const { id } = useParams<'id'>();
  return (
    <div>
      <h1 className="h3">Hồ sơ nhân viên</h1>
      <Link to={`/nhan-vien/${id}/sua`} className="btn btn-primary">
        Sửa hồ sơ
      </Link>
    </div>
  );
};

export default EmployeeDetailPlaceholder;
