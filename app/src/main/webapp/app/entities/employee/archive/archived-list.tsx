import React, { useEffect, useRef, useState } from 'react';
import { PageHeader, SectionCard } from 'app/shared/ui';
import { Link } from 'react-router';
import { Button, Table } from 'react-bootstrap';
import { JhiItemCount, JhiPagination } from 'react-jhipster';

import { useAppSelector } from 'app/config/store';
import { ITEMS_PER_PAGE } from 'app/shared/util/pagination.constants';

import ConfirmDialog from '../detail/confirm-dialog';
import { errorText } from '../detail/detail-api';
import { instantToVn, isoToVn } from '../vn-date';
import { IArchivedEmployee, SYSTEM_ADMIN_LOGIN, archiveApi } from './archive-api';
import DeleteEmployeeDialog from './delete-employee-dialog';

const label = (e: IArchivedEmployee) => [e.code, e.fullName].filter(Boolean).join(' – ');

/**
 * Vùng lưu trữ (Quản trị, HR): tìm theo mã, họ tên, CCCD; mỗi dòng có Xem và Khôi phục. "Xóa hẳn" chỉ hiện với tài khoản admin
 * (server kiểm tra lại).
 */
export const ArchivedList = () => {
  const login: string | undefined = useAppSelector(state => state.authentication.account?.login);
  const isSystemAdmin = login === SYSTEM_ADMIN_LOGIN;

  const [searchText, setSearchText] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState<IArchivedEmployee[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<React.ReactNode | null>(null);
  const [restoring, setRestoring] = useState<IArchivedEmployee | null>(null);
  const [deleting, setDeleting] = useState<IArchivedEmployee | null>(null);
  const requestRef = useRef(0);

  const load = () => {
    const requested = ++requestRef.current;
    setLoading(true);
    archiveApi
      .list(query, page - 1, ITEMS_PER_PAGE)
      .then(r => {
        if (requestRef.current !== requested) return;
        // Trang cuối trống sau khi khôi phục / xóa hẳn người cuối của trang: lùi một trang.
        if (r.items.length === 0 && page > 1) {
          setPage(page - 1);
          return;
        }
        setRows(r.items);
        setTotal(r.total);
        setError(null);
      })
      .catch(err => requestRef.current === requested && setError(errorText(err, 'Không tải được vùng lưu trữ')))
      .finally(() => requestRef.current === requested && setLoading(false));
  };

  useEffect(load, [query, page]);

  const search = (ev: React.FormEvent) => {
    ev.preventDefault();
    const q = searchText.trim();
    setPage(1);
    if (q === query && page === 1) load();
    else setQuery(q);
  };

  return (
    <div className="doit-page">
      <nav aria-label="Đường dẫn" className="doit-breadcrumb">
        <Link to="/nhan-vien">Nhân viên</Link> / Đã lưu trữ
      </nav>
      <PageHeader
        title="Nhân viên đã lưu trữ"
        subtitle="Hồ sơ đã lưu trữ không hiện ở danh sách, ô chọn, nhắc việc và báo cáo hiện tại; bảng lương, phiếu lương kỳ cũ vẫn giữ nguyên. Khôi phục để đưa hồ sơ về danh sách (trạng thái và dữ liệu giữ nguyên)."
        actions={
          isSystemAdmin && (
            <Link to="/admin/nhat-ky-xoa-nhan-vien" className="btn btn-outline-secondary">
              Nhật ký xóa hẳn
            </Link>
          )
        }
      />

      {notice !== null && (
        <div role="status" className="alert alert-success">
          {notice}
        </div>
      )}
      {error && (
        <div role="alert" className="alert alert-danger">
          {error}
        </div>
      )}

      <SectionCard aria-label="Danh sách hồ sơ lưu trữ">
        <form className="row g-2 align-items-end" role="search" onSubmit={search}>
          <div className="col-12 col-md-5">
            <label htmlFor="archived-search" className="form-label">
              Tìm theo mã, họ tên hoặc CCCD
            </label>
            <input
              id="archived-search"
              type="search"
              className="form-control"
              value={searchText}
              onChange={e => setSearchText(e.target.value)}
              autoComplete="off"
            />
          </div>
          <div className="col-auto">
            <Button type="submit" variant="primary">
              Tìm
            </Button>
          </div>
        </form>

        {rows.length > 0 ? (
          <Table responsive className="align-middle">
            <caption className="visually-hidden">Nhân viên đã lưu trữ</caption>
            <thead>
              <tr>
                <th scope="col">Mã</th>
                <th scope="col">Họ tên</th>
                <th scope="col">Phòng ban cuối</th>
                <th scope="col">Ngày làm việc cuối</th>
                <th scope="col">Ngày lưu trữ</th>
                <th scope="col">Người lưu trữ</th>
                <th scope="col">Ghi chú</th>
                <th scope="col">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.id}>
                  <td>{r.code ?? '—'}</td>
                  <td>{r.fullName ?? '—'}</td>
                  <td>{r.departmentName ?? '—'}</td>
                  <td>{isoToVn(r.lastWorkingDay) || '—'}</td>
                  <td>{instantToVn(r.archivedAt) || '—'}</td>
                  <td>{r.archivedBy ?? '—'}</td>
                  <td>{r.archiveNote ?? '—'}</td>
                  <td>
                    <div className="d-flex flex-wrap gap-2">
                      <Link
                        to={`/nhan-vien/${r.id}`}
                        className="btn btn-sm btn-outline-secondary"
                        aria-label={`Xem hồ sơ ${r.fullName ?? ''}`}
                      >
                        Xem
                      </Link>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline-primary"
                        aria-label={`Khôi phục ${r.fullName ?? ''}`}
                        onClick={() => setRestoring(r)}
                      >
                        Khôi phục
                      </Button>
                      {isSystemAdmin && (
                        <Button
                          type="button"
                          size="sm"
                          variant="outline-danger"
                          aria-label={`Xóa hẳn ${r.fullName ?? ''}`}
                          onClick={() => setDeleting(r)}
                        >
                          Xóa hẳn
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : (
          !loading && !error && <div className="alert alert-warning mb-0">Không có hồ sơ nào trong vùng lưu trữ</div>
        )}

        {total > 0 && (
          <div className="d-flex flex-column align-items-center">
            <JhiItemCount page={page} total={total} itemsPerPage={ITEMS_PER_PAGE} i18nEnabled={false} />
            <JhiPagination activePage={page} onSelect={p => setPage(p)} maxButtons={5} itemsPerPage={ITEMS_PER_PAGE} totalItems={total} />
          </div>
        )}
      </SectionCard>

      {restoring && (
        <ConfirmDialog
          id="restore-archived"
          title="Khôi phục hồ sơ"
          confirmLabel="Xác nhận khôi phục"
          failText="Không khôi phục được, vui lòng thử lại"
          onConfirm={() => archiveApi.restore(restoring.id)}
          onClose={() => setRestoring(null)}
          onDone={() => {
            setNotice(
              <>
                Đã khôi phục hồ sơ {label(restoring)}. <Link to={`/nhan-vien/${restoring.id}`}>Mở hồ sơ</Link>
              </>,
            );
            load();
          }}
        >
          <p className="mb-0">
            Đưa hồ sơ {label(restoring)} về danh sách nhân viên? Trạng thái và dữ liệu giữ nguyên; tài khoản đăng nhập vẫn khóa.
          </p>
        </ConfirmDialog>
      )}

      {deleting && (
        <DeleteEmployeeDialog
          employee={deleting}
          onClose={() => setDeleting(null)}
          onDone={() => {
            setNotice(`Đã xóa hẳn hồ sơ ${label(deleting)}.`);
            load();
          }}
        />
      )}
    </div>
  );
};

export default ArchivedList;
