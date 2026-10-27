import React, { useState } from 'react';
import { Alert, Button } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router';

import { errorText } from 'app/entities/employee/detail/detail-api';

import { openCalc } from './calc-api';

/** Nút "Tính lương": tạo / mở bản nháp của kỳ; chưa chốt bảng công thì báo lý do kèm link tới bảng công. */
export const CalcButton = ({ period, disabled }: { period: string; disabled?: boolean }) => {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [notClosed, setNotClosed] = useState(false);
  const [busy, setBusy] = useState(false);

  const start = async () => {
    setError(null);
    setNotClosed(false);
    setBusy(true);
    try {
      const draft = await openCalc(period);
      navigate(`/bang-luong/tinh-luong/${draft.id}`);
    } catch (err: any) {
      setNotClosed(err?.response?.data?.message === 'error.payrollattendancenotclosed');
      setError(errorText(err, 'Không tạo được bản tính lương'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Button variant="outline-primary" style={{ minHeight: 44 }} disabled={disabled || busy} onClick={start}>
        Tính lương
      </Button>
      {error && (
        <Alert variant="danger" className="mt-2 mb-0 w-100">
          {error}{' '}
          {notClosed && (
            <Link to="/bang-cong" className="alert-link">
              Mở bảng công tháng
            </Link>
          )}
        </Alert>
      )}
    </>
  );
};

export default CalcButton;
