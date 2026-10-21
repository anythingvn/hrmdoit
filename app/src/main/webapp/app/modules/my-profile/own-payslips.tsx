import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Button } from 'react-bootstrap';

import { errorText } from 'app/entities/employee/detail/detail-api';
import { instantToVn } from 'app/entities/employee/vn-date';
import { downloadFile } from 'app/shared/util/download';

interface IOwnPayslip {
  period: string;
  version: number;
  sentAt?: string | null;
}

const periodText = (p: string) => `${p.slice(5, 7)}/${p.slice(0, 4)}`;

/** "Hồ sơ của tôi": phiếu lương các kỳ đã gửi cho mình, tải PDF (không mật khẩu vì đã đăng nhập). */
export const OwnPayslips = () => {
  const [items, setItems] = useState<IOwnPayslip[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    axios
      .get<IOwnPayslip[]>('api/account/employee/payslips')
      .then(r => setItems(r.data ?? []))
      .catch(() => setItems(null));
  }, []);

  if (items === null) return null;

  const download = async (p: IOwnPayslip) => {
    setError(null);
    try {
      await downloadFile(`api/account/employee/payslips/${p.period}/pdf`, `PhieuLuong_${p.period.slice(5, 7)}-${p.period.slice(0, 4)}.pdf`);
    } catch (err) {
      setError(errorText(err, 'Không tải được phiếu lương'));
    }
  };

  return (
    <section className="doit-section" aria-labelledby="h-own-payslips">
      <div className="doit-section-head">
        <h2 id="h-own-payslips">Phiếu lương</h2>
      </div>
      {error && (
        <div role="alert" className="alert alert-danger">
          {error}
        </div>
      )}
      {items.length === 0 ? (
        <p className="text-muted mb-0">Chưa có phiếu lương nào được gửi.</p>
      ) : (
        <div className="table-responsive">
          <table className="table doit-table">
            <thead>
              <tr>
                <th scope="col">Kỳ lương</th>
                <th scope="col">Đã gửi</th>
                <th scope="col" aria-label="Tải" />
              </tr>
            </thead>
            <tbody>
              {items.map(p => (
                <tr key={p.period}>
                  <td>{periodText(p.period)}</td>
                  <td>{instantToVn(p.sentAt, true)}</td>
                  <td className="text-end">
                    <Button size="sm" variant="outline-primary" onClick={() => download(p)}>
                      Tải PDF
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
};

export default OwnPayslips;
