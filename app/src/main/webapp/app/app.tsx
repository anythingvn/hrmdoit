import 'react-toastify/dist/ReactToastify.css';
import './app.scss';
import 'app/config/dayjs';

import React, { useEffect } from 'react';
import { Card } from 'reactstrap';
import { BrowserRouter } from 'react-router-dom';
import { ToastContainer } from 'react-toastify';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getSession } from 'app/shared/reducers/authentication';
import { getProfile } from 'app/shared/reducers/application-profile';
import LoadingBar from 'react-redux-loading-bar';
import Sidebar from 'app/shared/layout/sidebar/sidebar';
import ErrorBoundary from 'app/shared/error/error-boundary';
import AppRoutes from 'app/routes';

const baseHref = document.querySelector('base').getAttribute('href').replace(/\/$/, '');

export const App = () => {
  const dispatch = useAppDispatch();

  useEffect(() => {
    dispatch(getSession());
    dispatch(getProfile());
  }, []);

  const isAuthenticated = useAppSelector(state => state.authentication.isAuthenticated);
  const account = useAppSelector(state => state.authentication.account);
  const displayName = [account?.firstName, account?.lastName].filter(Boolean).join(' ') || account?.login || '';

  return (
    <BrowserRouter basename={baseHref}>
      <div className="app-container">
        <ToastContainer position="top-left" className="toastify-container" toastClassName="toastify-toast" />
        <LoadingBar className="loading-bar" />
        {isAuthenticated && (
          <ErrorBoundary>
            <Sidebar authorities={account?.authorities ?? []} displayName={displayName} />
          </ErrorBoundary>
        )}
        <main className={`doit-main${isAuthenticated ? '' : ' no-sidebar'}`} id="app-view-container">
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
