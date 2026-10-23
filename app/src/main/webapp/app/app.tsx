import 'react-toastify/dist/ReactToastify.css';
// Font Be Vietnam Pro (tự host qua gói npm, cập nhật bằng npm): các độ đậm giao diện dùng.
import '@fontsource/be-vietnam-pro/400.css';
import '@fontsource/be-vietnam-pro/500.css';
import '@fontsource/be-vietnam-pro/600.css';
import '@fontsource/be-vietnam-pro/700.css';
import '@fontsource/be-vietnam-pro/800.css';
import './app.scss';
import 'app/config/dayjs';

import React, { useEffect, useRef } from 'react';
import { Card } from 'react-bootstrap';
import { BrowserRouter } from 'react-router';

import { ToastContainer } from 'react-toastify';
import LoadingBar, { LoadingBarRef } from 'react-top-loading-bar';

import { AUTHORITIES } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import AppRoutes from 'app/routes';
import ErrorBoundary from 'app/shared/error/error-boundary';
import BackupBanner from 'app/shared/layout/backup-banner/backup-banner';
import Sidebar from 'app/shared/layout/sidebar/sidebar';
import { applyBrandingToDocument, retryBrandingIfNotLoaded, useBranding } from 'app/shared/branding/branding';
import { getProfile } from 'app/shared/reducers/application-profile';
import { getSession } from 'app/shared/reducers/authentication';

const baseHref = document.querySelector('base')!.getAttribute('href')!.replace(/\/$/, '');

export const App = () => {
  const dispatch = useAppDispatch();

  useEffect(() => {
    dispatch(getSession());
    dispatch(getProfile());
  }, []);

  // Tiêu đề tab và favicon theo cài đặt công ty; chưa tải được thì giữ mặc định trong index.html.
  const branding = useBranding();
  useEffect(() => {
    if (branding.loaded) applyBrandingToDocument(branding);
  }, [branding.loaded, branding.shortName, branding.logoVersion]);

  const isAuthenticated = useAppSelector(state => state.authentication.isAuthenticated);
  // Vừa đăng nhập mà branding chưa tải được (lỗi mạng lúc mở trang) thì thử lại một lần.
  useEffect(() => {
    if (isAuthenticated) void retryBrandingIfNotLoaded();
  }, [isAuthenticated]);
  const account = useAppSelector(state => state.authentication.account);
  const isAdmin = !!account?.authorities?.includes(AUTHORITIES.ADMIN);
  const displayName = [account?.firstName, account?.lastName].filter(Boolean).join(' ') || account?.login || '';

  const loadingBarRef = useRef<LoadingBarRef>(null);
  const loadingCount = useAppSelector(state => state.loadingBar.count);
  useEffect(() => {
    if (loadingCount > 0) {
      loadingBarRef.current?.continuousStart();
    } else {
      loadingBarRef.current?.complete();
    }
  }, [loadingCount]);

  return (
    <BrowserRouter basename={baseHref}>
      <div className="app-container">
        <ToastContainer position="top-left" className="toastify-container" toastClassName="toastify-toast" />
        <LoadingBar ref={loadingBarRef} className="loading-bar" color="#E84424" />
        {isAuthenticated && (
          <ErrorBoundary>
            <Sidebar
              authorities={account?.authorities ?? []}
              displayName={displayName}
              hasEmployeeProfile={!!account?.hasEmployeeProfile}
              leaveApplicable={!!account?.leaveApplicable}
              isApprover={!!account?.isApprover}
              seesLeaveCalendar={!!account?.seesLeaveCalendar}
              entersAttendance={!!account?.entersAttendance}
              viewsAttendanceBoard={!!account?.viewsAttendanceBoard}
              ownsAttendance={!!account?.ownsAttendance}
            />
          </ErrorBoundary>
        )}
        <main className={`doit-main${isAuthenticated ? '' : ' no-sidebar'}`} id="app-view-container">
          {isAdmin && <BackupBanner />}
          <Card className="jh-card">
            <ErrorBoundary>
              <AppRoutes />
            </ErrorBoundary>
          </Card>
        </main>
      </div>
    </BrowserRouter>
  );
};

export default App;
