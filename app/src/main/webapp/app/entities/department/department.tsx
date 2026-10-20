import React, { useEffect, useState } from 'react';
import { Button, Table } from 'react-bootstrap';
import { Translate, getSortState } from 'react-jhipster';
import { Link, useLocation, useNavigate } from 'react-router';

import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { AUTHORITIES } from 'app/config/constants';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { ASC, DESC } from 'app/shared/util/pagination.constants';

import { getEntities } from './department.reducer';

export const Department = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const departmentList = useAppSelector(state => state.department.entities);
  const canWrite = useAppSelector(state => hasAnyAuthority(state.authentication.account.authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR]));
  const loading = useAppSelector(state => state.department.loading);

  const getAllEntities = () => {
    dispatch(
      getEntities({
        sort: `${sortState.sort},${sortState.order}`,
      }),
    );
  };

  const sortEntities = () => {
    getAllEntities();
    const endURL = `?sort=${sortState.sort},${sortState.order}`;
    if (pageLocation.search !== endURL) {
      navigate(`${pageLocation.pathname}${endURL}`);
    }
  };

  useEffect(() => {
    sortEntities();
  }, [sortState.order, sortState.sort]);

  const sort = p => () => {
    setSortState({
      ...sortState,
      order: sortState.order === ASC ? DESC : ASC,
      sort: p,
    });
  };

  const handleSyncList = () => {
    sortEntities();
  };

  const getSortIconByFieldName = (fieldName: string) => {
    const sortFieldName = sortState.sort;
    const { order } = sortState;
    if (sortFieldName !== fieldName) {
      return faSort;
    }
    return order === ASC ? faSortUp : faSortDown;
  };

  return (
    <div>
      <h2 id="department-heading" data-cy="DepartmentHeading">
        <Translate contentKey="hrmApp.department.home.title">Departments</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" variant="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="hrmApp.department.home.refreshListLabel">Refresh List</Translate>
          </Button>
          {canWrite && (
            <Link
              to="/danh-muc/phong-ban/new"
              className="btn btn-primary jh-create-entity"
              id="jh-create-entity"
              data-cy="entityCreateButton"
            >
              <FontAwesomeIcon icon="plus" />
              &nbsp;
              <Translate contentKey="hrmApp.department.home.createLabel">Create new Department</Translate>
            </Link>
          )}
        </div>
      </h2>
      <div className="table-responsive">
        {departmentList?.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="hrmApp.department.id">ID</Translate> <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('name')}>
                  <Translate contentKey="hrmApp.department.name">Name</Translate> <FontAwesomeIcon icon={getSortIconByFieldName('name')} />
                </th>
                <th className="hand" onClick={sort('active')}>
                  <Translate contentKey="hrmApp.department.active">Active</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('active')} />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {departmentList.map(department => (
                <tr key={`entity-${department.id}`} data-cy="entityTable">
                  <td>
                    <Button as={Link as any} to={`/danh-muc/phong-ban/${department.id}`} variant="link" size="sm">
                      {department.id}
                    </Button>
                  </td>
                  <td>{department.name}</td>
                  <td>{department.active ? 'Có' : 'Không'}</td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button
                        as={Link as any}
                        to={`/danh-muc/phong-ban/${department.id}`}
                        variant="info"
                        size="sm"
                        data-cy="entityDetailsButton"
                      >
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      {canWrite && (
                        <Button
                          as={Link as any}
                          to={`/danh-muc/phong-ban/${department.id}/edit`}
                          variant="primary"
                          size="sm"
                          data-cy="entityEditButton"
                        >
                          <FontAwesomeIcon icon="pencil-alt" />{' '}
                          <span className="d-none d-md-inline">
                            <Translate contentKey="entity.action.edit">Edit</Translate>
                          </span>
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : (
          !loading && (
            <div className="alert alert-warning">
              <Translate contentKey="hrmApp.department.home.notFound">No Departments found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default Department;
