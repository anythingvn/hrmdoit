import React, { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { Button, Table } from 'react-bootstrap';
import { JhiItemCount, JhiPagination } from 'react-jhipster';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { AUTHORITIES } from 'app/config/constants';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import { ITEMS_PER_PAGE } from 'app/shared/util/pagination.constants';
import { IDepartment } from 'app/shared/model/department.model';
import { IWorkSite } from 'app/shared/model/work-site.model';
import { CONTRACT_TYPE_LABEL, PERSON_TYPE_LABEL, STATUS_LABEL } from 'app/shared/model/employee.model';
import axios from 'axios';

import { IEmployeeFilters, exportEntities, getEntities } from './employee.reducer';

const SORTABLE: Record<string, string> = { code: 'Mã', fullName: 'Họ tên' };

export const EmployeeList = () => {
  const dispatch = useAppDispatch();
  const canWrite = useAppSelector(state => hasAnyAuthority(state.authentication.account.authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR]));
  const { entities, loading, totalItems } = useAppSelector(state => state.employee);

  const [filters, setFilters] = useState<IEmployeeFilters>({});
  const [searchText, setSearchText] = useState('');
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState('code,asc');
  const [departments, setDepartments] = useState<IDepartment[]>([]);
  const [sites, setSites] = useState<IWorkSite[]>([]);
  const [exportError, setExportError] = useState(false);

  useEffect(() => {
    axios
      .get<IDepartment[]>('api/departments')
      .then(r => setDepartments(r.data ?? []))
      .catch(() => undefined);
    axios
      .get<IWorkSite[]>('api/work-sites')
      .then(r => setSites(r.data ?? []))
      .catch(() => undefined);
  }, []);

  // Ô tìm gửi sau 300ms ngừng gõ.
  useEffect(() => {
    const t = setTimeout(() => {
      if ((filters.q ?? '') !== searchText) {
        setFilters(f => ({ ...f, q: searchText }));
        setPage(1);
      }
    }, 300);
    return () => clearTimeout(t);
  }, [searchText]);

  useEffect(() => {
    dispatch(getEntities({ ...filters, page: page - 1, size: ITEMS_PER_PAGE, sort }));
  }, [filters, page, sort]);

  const setFilter = (key: keyof IEmployeeFilters) => (e: React.ChangeEvent<HTMLSelectElement>) => {
    setFilters(f => ({ ...f, [key]: e.target.value }));
    setPage(1);
  };

  const toggleSort = (field: string) => () => {
    const [cur, order] = sort.split(',');
    setSort(`${field},${cur === field && order === 'asc' ? 'desc' : 'asc'}`);
  };

  const sortLabel = (field: string) => {
    const [cur, order] = sort.split(',');
    return cur === field ? (order === 'asc' ? 'tăng dần' : 'giảm dần') : undefined;
  };

  const handleExport = async () => {
    setExportError(false);
    try {
      const res = await exportEntities({ ...filters, sort });
      const url = URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'nhan-vien.xlsx';
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setExportError(true);
    }
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
        <h1 id="employee-heading" className="h3 mb-0">
          Nhân viên
        </h1>
        <div className="d-flex gap-2">
          <Button variant="outline-secondary" onClick={() => void handleExport()}>
            Xuất Excel
          </Button>
          {canWrite && (
            <Link to="/nhan-vien/moi" className="btn btn-primary" data-cy="employeeCreateButton">
              Thêm nhân viên
            </Link>
          )}
        </div>
      </div>
      {exportError && (
        <div role="alert" className="alert alert-danger">
          Không xuất được file Excel
        </div>
      )}

      <form className="row g-2 mb-3" role="search" onSubmit={e => e.preventDefault()}>
        <div className="col-12 col-md-4">
          <label htmlFor="employee-search" className="form-label">
            Tìm theo tên hoặc mã
          </label>
          <input
            id="employee-search"
            type="search"
            className="form-control"
            value={searchText}
            onChange={e => setSearchText(e.target.value)}
            placeholder="Ví dụ: quan, nv0142"
          />
        </div>
        <div className="col-6 col-md-2">
          <label htmlFor="filter-status" className="form-label">
            Tình trạng
          </label>
          <select id="filter-status" className="form-select" value={filters.status ?? ''} onChange={setFilter('status')}>
            <option value="">Tất cả</option>
            {Object.entries(STATUS_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </div>
        <div className="col-6 col-md-2">
          <label htmlFor="filter-department" className="form-label">
            Phòng ban
          </label>
          <select id="filter-department" className="form-select" value={filters.departmentId ?? ''} onChange={setFilter('departmentId')}>
            <option value="">Tất cả</option>
            {departments.map(d => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </select>
        </div>
        <div className="col-6 col-md-2">
          <label htmlFor="filter-site" className="form-label">
            Nơi làm việc
          </label>
          <select id="filter-site" className="form-select" value={filters.currentSiteId ?? ''} onChange={setFilter('currentSiteId')}>
            <option value="">Tất cả</option>
            {sites.map(s => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </div>
        <div className="col-6 col-md-2">
          <label htmlFor="filter-contract" className="form-label">
            Loại HĐ hiện tại
          </label>
          <select
            id="filter-contract"
            className="form-select"
            value={filters.currentContractType ?? ''}
            onChange={setFilter('currentContractType')}
          >
            <option value="">Tất cả</option>
            {Object.entries(CONTRACT_TYPE_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </div>
        <div className="col-6 col-md-2">
          <label htmlFor="filter-person-type" className="form-label">
            Loại người
          </label>
          <select id="filter-person-type" className="form-select" value={filters.personType ?? ''} onChange={setFilter('personType')}>
            <option value="">Tất cả</option>
            {Object.entries(PERSON_TYPE_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </div>
      </form>

      <div className="table-responsive">
        {entities.length > 0 ? (
          <Table responsive>
            <caption className="visually-hidden">Danh sách nhân viên</caption>
            <thead>
              <tr>
                {Object.entries(SORTABLE).map(([field, label]) => (
                  <th
                    key={field}
                    scope="col"
                    aria-sort={sortLabel(field) ? (sortLabel(field) === 'tăng dần' ? 'ascending' : 'descending') : 'none'}
                  >
                    <button type="button" className="btn btn-link p-0 fw-bold text-decoration-none" onClick={toggleSort(field)}>
                      {label}
                      {sortLabel(field) ? ` (${sortLabel(field)})` : ''}
                    </button>
                  </th>
                ))}
                <th scope="col">Phòng ban</th>
                <th scope="col">Chức danh</th>
                <th scope="col">Nơi làm việc</th>
                <th scope="col">Tình trạng</th>
              </tr>
            </thead>
            <tbody>
              {entities.map(e => (
                <tr key={e.id} data-cy="employeeRow">
                  <td>
                    <Link to={`/nhan-vien/${e.id}`}>{e.code ?? '—'}</Link>
                  </td>
                  <td>
                    <Link to={`/nhan-vien/${e.id}`}>{e.fullName}</Link>
                  </td>
                  <td>{e.department?.name ?? '—'}</td>
                  <td>{e.position ?? '—'}</td>
                  <td>{e.currentSiteName ?? '—'}</td>
                  <td>{e.status ? STATUS_LABEL[e.status] : '—'}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : (
          !loading && <div className="alert alert-warning">Không tìm thấy nhân viên nào</div>
        )}
      </div>
      {totalItems > 0 && (
        <div className="d-flex flex-column align-items-center">
          <JhiItemCount page={page} total={totalItems} itemsPerPage={ITEMS_PER_PAGE} i18nEnabled={false} />
          <JhiPagination
            activePage={page}
            onSelect={p => setPage(p)}
            maxButtons={5}
            itemsPerPage={ITEMS_PER_PAGE}
            totalItems={totalItems}
          />
        </div>
      )}
    </div>
  );
};

export default EmployeeList;
