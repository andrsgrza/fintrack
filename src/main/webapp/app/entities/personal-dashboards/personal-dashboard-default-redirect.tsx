import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { copy } from 'app/entities/analytics-explorer/analytics-explorer-utils';

import { getPersonalDashboardDefault } from './personal-dashboard-api';

const officialDashboardPath = (templateId: string | null) => {
  switch (templateId) {
    case 'movement-analysis':
    case 'accounts-and-position':
    case 'financial-overview':
      return `/analytics/dashboards/${templateId}`;
    default:
      return '/analytics/dashboards/financial-overview';
  }
};

/** Resolves the one owner-scoped personal default, with the official overview as the only fallback. */
const PersonalDashboardDefaultRedirect = () => {
  const locale = useAppSelector(state => state.locale.currentLocale) || 'en';
  const navigate = useNavigate();

  useEffect(() => {
    let active = true;
    void getPersonalDashboardDefault()
      .then(preference => {
        if (!active) return;
        navigate(
          preference.dashboardId == null
            ? officialDashboardPath(preference.officialTemplateId)
            : `/analytics/dashboards/personal/${preference.dashboardId}`,
          { replace: true },
        );
      })
      // A failed or stale preference must never strand the user on a broken personal dashboard route.
      .catch(() => active && navigate('/analytics/dashboards/financial-overview', { replace: true }));
    return () => {
      active = false;
    };
  }, [navigate]);

  return (
    <div className="p-4 text-center" role="status">
      {copy(locale, 'Cargando dashboard…', 'Loading dashboard…')}
    </div>
  );
};

export default PersonalDashboardDefaultRedirect;
