import './sidebar.scss';

import React, { useEffect, useState } from 'react';
import { NavLink, Link, useLocation } from 'react-router';
import { translate } from 'react-jhipster';

import { AUTHORITIES, ROLE_LABELS } from 'app/config/constants';
import doitLogo from '/content/images/doit-logo.png';

import { menuFor } from './menu-items';

const ICONS: Record<string, React.ReactNode> = {
  users: (
    <>
      <circle cx="9" cy="8" r="4"></circle>
      <path d="M2 21c0-4 3-7 7-7s7 3 7 7"></path>
      <path d="M16 4a4 4 0 0 1 0 8M22 21c0-3-2-6-5-7"></path>
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8" r="4"></circle>
      <path d="M4 21c0-4 4-7 8-7s8 3 8 7"></path>
    </>
  ),
  folder: <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>,
  settings: (
    <>
      <circle cx="12" cy="12" r="3"></circle>
      <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"></path>
    </>
  ),
};

const Icon = ({ name }: { name: string }) => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
    {ICONS[name]}
  </svg>
);

export const roleLabel = (authorities: string[] = []) =>
  [AUTHORITIES.ADMIN, AUTHORITIES.HR, AUTHORITIES.KE_TOAN, AUTHORITIES.BGD, AUTHORITIES.NHAN_VIEN]
    .filter(r => authorities.includes(r))
    .map(r => ROLE_LABELS[r])
    .join(', ');

export const initials = (name: string) => {
  const parts = (name || '').trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  const first = parts[0][0];
  const last = parts.length > 1 ? parts[parts.length - 1][0] : '';
  return (first + last).toUpperCase();
};

const ADMIN_LINKS = [
  { to: '/admin/user-management', key: 'userManagement' },
  { to: '/admin/metrics', key: 'metrics' },
  { to: '/admin/health', key: 'health' },
  { to: '/admin/configuration', key: 'configuration' },
  { to: '/admin/logs', key: 'logs' },
];

export interface ISidebarProps {
  authorities: string[];
  displayName: string;
}

const Sidebar = ({ authorities, displayName }: ISidebarProps) => {
  const [open, setOpen] = useState(false);
  const { pathname } = useLocation();
  const items = menuFor(authorities);
  const inAdmin = pathname.startsWith('/admin');

  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  const isOn = (path: string, key: string) => {
    if (key === 'danh-muc') return pathname.startsWith('/danh-muc');
    if (key === 'quan-tri') return inAdmin;
    return pathname === path || pathname.startsWith(`${path}/`);
  };

  return (
    <div className={`doit-sidebar${open ? ' is-open' : ''}`}>
      <div className="doit-sidebar-top">
        <Link to="/" className="doit-brand">
          <img src={doitLogo} alt="DOIT" width="64" height="64" />
          <span className="doit-brand-text">
            <span className="doit-brand-name">DOIT HRM</span>
            <span className="doit-brand-tagline">Do it if you can</span>
          </span>
        </Link>
        <button
          type="button"
          className="doit-menu-toggle"
          aria-label={open ? 'Đóng menu' : 'Mở menu'}
          aria-expanded={open}
          aria-controls="doit-nav"
          onClick={() => setOpen(!open)}
        >
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path d="M3 6h18M3 12h18M3 18h18"></path>
          </svg>
        </button>
      </div>
      <nav id="doit-nav" aria-label="Điều hướng chính" className="doit-nav">
        {items.map(item => (
          <React.Fragment key={item.key}>
            <NavLink to={item.path} className={`nv${isOn(item.path, item.key) ? ' nv-on' : ''}`} end={false}>
              <Icon name={item.icon} />
              {item.label}
            </NavLink>
            {item.key === 'quan-tri' && inAdmin && (
              <div className="nv-sub">
                {ADMIN_LINKS.map(l => (
                  <NavLink key={l.to} to={l.to} className={({ isActive }) => `nv nv-small${isActive ? ' nv-sub-on' : ''}`}>
                    {translate(`global.menu.admin.${l.key}`)}
                  </NavLink>
                ))}
              </div>
            )}
          </React.Fragment>
        ))}
        <div className="doit-spacer"></div>
        <div className="doit-user">
          <div className="doit-avatar" aria-hidden="true">
            {initials(displayName)}
          </div>
          <div className="doit-user-text">
            <span className="doit-user-name">{displayName}</span>
            <span className="doit-user-role">{roleLabel(authorities)}</span>
          </div>
        </div>
        <div className="doit-user-links">
          <Link to="/account/password" className="nv nv-small">
            Đổi mật khẩu
          </Link>
          <Link to="/logout" className="nv nv-small" data-cy="logout">
            Đăng xuất
          </Link>
        </div>
      </nav>
    </div>
  );
};

export default Sidebar;
