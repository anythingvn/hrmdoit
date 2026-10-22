import React, { useEffect, useState } from 'react';
import { Table } from 'react-bootstrap';
import { JhiItemCount, JhiPagination } from 'react-jhipster';

import { useAppSelector } from 'app/config/store';
import { IDeletionLog, SYSTEM_ADMIN_LOGIN, archiveApi } from 'app/entities/employee/archive/archive-api';
import { errorText } from 'app/entities/employee/detail/detail-api';
import { instantToVn } from 'app/entities/employee/vn-date';
import { ITEMS_PER_PAGE } from 'app/shared/util/pagination.constants';

/** Nhật ký xóa hẳn nhân viên (chỉ tài khoản admin; server trả 403 cho người khác). Không có CCCD, ngân hàng, địa chỉ. */
export const EmployeeDeletions = () => {
  const login: string | undefined = useAppSelector(state => state.authentication.account?.login);
  const allowed = login === SYSTEM_ADMIN_LOGIN;
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState<IDeletionLog[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(allowed);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!allowed) return;
    let live = true;
    setLoading(true);
    archiveApi
      .deletions(page - 1, ITEMS_PER_PAGE)
      .then(r => {
        if (!live) return;
        setRows(r.items);
        setTotal(r.total);
        setError(null);
      })
      .catch(err => live && setError(errorText(err, 'Không tải được nhật ký xóa')))
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
  }, [allowed, page]);

  return (
    <div>
      <h1 className="h3 mb-3">Nhật ký xóa hẳn nhân viên</h1>
      {!allowed ? (
        <div role="alert" className="alert alert-warning">
          Chỉ tài khoản admin xem được nhật ký xóa hẳn nhân viên.
        </div>
      ) : (
        <>
          {error && (
            <div role="alert" className="alert alert-danger">
              {error}
            </div>
          )}
          {rows.length > 0 ? (
            <Table responsive className="align-middle">
              <caption className="visually-hidden">Nhật ký xóa hẳn nhân viên</caption>
              <thead>
                <tr>
                  <th scope="col">Thời điểm</th>
                  <th scope="col">Người xóa</th>
                  <th scope="col">Mã</th>
                  <th scope="col">Họ tên</th>
                  <th scope="col">Lý do</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(r => (
                  <tr key={r.id}>
                    <td>{instantToVn(r.deletedAt, true)}</td>
                    <td>{r.deletedBy}</td>
                    <td>{r.employeeCode ?? '—'}</td>
                    <td>{r.employeeName ?? '—'}</td>
                    <td>{r.reason}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          ) : (
            !loading && !error && <div className="alert alert-secondary">Chưa xóa hẳn hồ sơ nào</div>
          )}
          {total > 0 && (
            <div className="d-flex flex-column align-items-center">
              <JhiItemCount page={page} total={total} itemsPerPage={ITEMS_PER_PAGE} i18nEnabled={false} />
              <JhiPagination activePage={page} onSelect={p => setPage(p)} maxButtons={5} itemsPerPage={ITEMS_PER_PAGE} totalItems={total} />
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default EmployeeDeletions;
