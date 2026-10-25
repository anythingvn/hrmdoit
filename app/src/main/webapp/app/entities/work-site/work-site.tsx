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

import { getEntities } from './work-site.reducer';

export const WorkSite = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const workSiteList = useAppSelector(state => state.workSite.entities);
  const canWrite = useAppSelector(state => hasAnyAuthority(state.authentication.account.authorities, [AUTHORITIES.ADMIN, AUTHORITIES.HR]));
  const loading = useAppSelector(state => state.workSite.loading);

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
    <div className="doit-card">
      <div className="doit-card-head">
        <h2 id="work-site-heading" data-cy="WorkSiteHeading" className="doit-card-title">
          <Translate contentKey="hrmApp.workSite.home.title">Work Sites</Translate>
        </h2>
        <div className="doit-card-actions">
          <Button variant="outline-secondary" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="hrmApp.workSite.home.refreshListLabel">Refresh List</Translate>
          </Button>
          {canWrite && (
            <Link
              to="/danh-muc/noi-lam-viec/new"
              className="btn btn-primary jh-create-entity"
              id="jh-create-entity"
              data-cy="entityCreateButton"
            >
              <FontAwesomeIcon icon="plus" />
              &nbsp;
              <Translate contentKey="hrmApp.workSite.home.createLabel">Create new Work Site</Translate>
            </Link>
          )}
        </div>
      </div>
      <div className="table-responsive">
        {workSiteList?.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="hrmApp.workSite.id">ID</Translate> <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('name')}>
                  <Translate contentKey="hrmApp.workSite.name">Name</Translate> <FontAwesomeIcon icon={getSortIconByFieldName('name')} />
                </th>
                <th className="hand" onClick={sort('type')}>
                  <Translate contentKey="hrmApp.workSite.type">Type</Translate> <FontAwesomeIcon icon={getSortIconByFieldName('type')} />
                </th>
                <th className="hand" onClick={sort('address')}>
                  <Translate contentKey="hrmApp.workSite.address">Address</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('address')} />
                </th>
                <th className="hand" onClick={sort('active')}>
                  <Translate contentKey="hrmApp.workSite.active">Active</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('active')} />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {workSiteList.map(workSite => (
                <tr key={`entity-${workSite.id}`} data-cy="entityTable">
                  <td>
                    <Button as={Link as any} to={`/danh-muc/noi-lam-viec/${workSite.id}`} variant="link" size="sm">
                      {workSite.id}
                    </Button>
                  </td>
                  <td>{workSite.name}</td>
                  <td>
                    <Translate contentKey={`hrmApp.WorkLocationType.${workSite.type}`} />
                  </td>
                  <td>{workSite.address}</td>
                  <td>{workSite.active ? 'Có' : 'Không'}</td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button
                        as={Link as any}
                        to={`/danh-muc/noi-lam-viec/${workSite.id}`}
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
                          to={`/danh-muc/noi-lam-viec/${workSite.id}/edit`}
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
              <Translate contentKey="hrmApp.workSite.home.notFound">No Work Sites found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default WorkSite;
