import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Button } from 'react-bootstrap';

import { CONTRACT_STATUS_LABEL, ContractStatus, errorText } from 'app/entities/employee/detail/detail-api';
import { isoToVn } from 'app/entities/employee/vn-date';
import { CONTRACT_TYPE_LABEL, ContractTypeCode } from 'app/shared/model/employee.model';
import { downloadFile } from 'app/shared/util/download';

interface IOwnContract {
  id: number;
  type: ContractTypeCode;
  contractNo?: string | null;
  startDate: string;
  endDate?: string | null;
  status: ContractStatus;
  hasSigned: boolean;
}

/** "Hồ sơ của tôi": hợp đồng của mình (không có lương), tải bản đã ký. */
export const OwnContracts = () => {
  const [contracts, setContracts] = useState<IOwnContract[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    axios
      .get<IOwnContract[]>('api/account/employee/contracts')
      .then(r => setContracts(r.data ?? []))
      .catch(() => setContracts(null));
  }, []);

  if (!contracts || contracts.length === 0) return null;

  const download = async (c: IOwnContract) => {
    setError(null);
    const name = (c.contractNo ?? `hop-dong-${c.id}`).replace(/[\\/:*?"<>|]/g, '-');
    try {
      await downloadFile(`api/account/employee/contracts/${c.id}/signed`, `${name} - ban da ky.pdf`);
    } catch (err) {
      setError(errorText(err, 'Không tải được bản đã ký'));
    }
  };

  return (
    <section className="doit-section" aria-labelledby="h-own-contracts">
      <div className="doit-section-head">
        <h2 id="h-own-contracts">Hợp đồng của tôi</h2>
      </div>
      {error && (
        <div role="alert" className="alert alert-danger">
          {error}
        </div>
      )}
      <div className="table-responsive">
        <table className="table doit-table">
          <thead>
            <tr>
              <th scope="col">Loại / số</th>
              <th scope="col">Hiệu lực</th>
              <th scope="col">Trạng thái</th>
              <th scope="col">Bản đã ký</th>
            </tr>
          </thead>
          <tbody>
            {contracts.map(c => (
              <tr key={c.id}>
                <td>
                  <span className="fw-semibold">{CONTRACT_TYPE_LABEL[c.type]}</span>
                  {c.contractNo && <div className="text-muted">Số {c.contractNo}</div>}
                </td>
                <td className="text-nowrap">
                  {c.endDate ? `${isoToVn(c.startDate)} – ${isoToVn(c.endDate)}` : `từ ${isoToVn(c.startDate)}`}
                </td>
                <td>{CONTRACT_STATUS_LABEL[c.status]}</td>
                <td>
                  {c.hasSigned ? (
                    <Button
                      variant="link"
                      className="doit-row-action"
                      onClick={() => download(c)}
                      aria-label={`Tải bản đã ký ${c.contractNo ?? CONTRACT_TYPE_LABEL[c.type]}`}
                    >
                      Tải về
                    </Button>
                  ) : (
                    <span className="text-muted">Chưa có bản đã ký</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
};

export default OwnContracts;
