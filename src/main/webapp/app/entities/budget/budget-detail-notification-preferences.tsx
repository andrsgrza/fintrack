import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button } from 'reactstrap';
import { Link } from 'react-router-dom';

import {
  BudgetNotificationPreferences,
  BudgetType,
  getBudgetNotificationPreferences,
  updateBudgetNotificationPreferences,
} from './budget-api';
import {
  BudgetNotificationPreferencesEditor,
  BudgetNotificationPreferencesDraft,
  emptyNotificationPreferences,
  validateNotificationPreferences,
} from './budget-notification-preferences';
import { copy, formatBudgetThresholdPercentage, userFacingError } from './budget-utils';
import { ProductSection } from 'app/shared/ui/product-page';

const asDraft = (value: BudgetNotificationPreferences): BudgetNotificationPreferencesDraft => ({
  percentageAlertsEnabled: value.percentageAlertsEnabled,
  thresholdPercentages: value.thresholdPercentages,
  periodCloseEnabled: value.periodCloseEnabled,
});

const preferencesEqual = (left: BudgetNotificationPreferencesDraft, right: BudgetNotificationPreferencesDraft) =>
  left.percentageAlertsEnabled === right.percentageAlertsEnabled &&
  left.periodCloseEnabled === right.periodCloseEnabled &&
  left.thresholdPercentages.length === right.thresholdPercentages.length &&
  left.thresholdPercentages.every((value, index) => value === right.thresholdPercentages[index]);

const alertLabel = (locale: string, type: BudgetType) =>
  type === 'EXPENSE' ? copy(locale, 'Alertas de gasto', 'Spending alerts') : copy(locale, 'Alertas de ingresos', 'Income alerts');

const NotificationPreferenceSummary = ({
  locale,
  type,
  preferences,
}: {
  locale: string;
  type: BudgetType;
  preferences: BudgetNotificationPreferences;
}) => {
  const thresholdSummary = !preferences.percentageAlertsEnabled
    ? copy(locale, 'Desactivadas', 'Disabled')
    : preferences.thresholdPercentages.length
      ? `${copy(locale, 'Activadas', 'Enabled')} · ${preferences.thresholdPercentages
          .map(value => formatBudgetThresholdPercentage(value, locale))
          .join(', ')}`
      : copy(locale, 'Activadas · sin porcentajes configurados', 'Enabled · no percentages configured');

  return (
    <dl className="row mb-0" data-cy="budgetNotificationPreferencesSummary">
      <dt className="col-12 col-sm-5 text-muted fw-normal">{alertLabel(locale, type)}</dt>
      <dd className="col-12 col-sm-7" data-cy="budgetNotificationThresholdSummary">
        {thresholdSummary}
      </dd>
      <dt className="col-12 col-sm-5 text-muted fw-normal">{copy(locale, 'Resumen al cerrar el período', 'Period close summary')}</dt>
      <dd className="col-12 col-sm-7 mb-0" data-cy="budgetNotificationPeriodCloseSummary">
        {preferences.periodCloseEnabled ? copy(locale, 'Activado', 'Enabled') : copy(locale, 'Desactivado', 'Disabled')}
      </dd>
    </dl>
  );
};

const useNotificationPreferences = (budgetId: number, locale: string, refreshKey: number) => {
  const [preferences, setPreferences] = useState<BudgetNotificationPreferences | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isSafeInteger(budgetId) || budgetId <= 0) return;
    let active = true;
    setLoading(true);
    setError(null);
    void getBudgetNotificationPreferences(budgetId)
      .then(next => {
        if (active) setPreferences(next);
      })
      .catch(problem => {
        if (active) {
          setError(
            userFacingError(
              problem,
              copy(locale, 'No se pudieron cargar las notificaciones.', 'Notification settings could not be loaded.'),
            ),
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [budgetId, locale, refreshKey]);

  return { preferences, setPreferences, loading, error, setError };
};

/** Read-only current delivery settings; these never become a historical revision snapshot. */
export const BudgetDetailNotificationPreferences = ({
  budgetId,
  type,
  completed,
  locale,
  refreshKey,
}: {
  budgetId: number;
  type: BudgetType;
  completed: boolean;
  locale: string;
  refreshKey: number;
}) => {
  const { preferences, loading, error } = useNotificationPreferences(budgetId, locale, refreshKey);

  return (
    <ProductSection title={copy(locale, 'Notificaciones', 'Notifications')} className="mb-4" dataCy="budgetNotificationPreferencesSection">
      <p className="small text-muted mb-3">
        {copy(
          locale,
          'Estas preferencias son actuales del presupuesto; no modifican ni describen la revisión histórica del período seleccionado.',
          'These are current Budget preferences; they do not modify or describe the selected period’s historical revision.',
        )}
      </p>
      {loading ? <div className="small text-muted">{copy(locale, 'Cargando preferencias…', 'Loading preferences…')}</div> : null}
      {error ? (
        <div className="alert alert-danger py-2 mb-0" role="alert" data-cy="budgetNotificationError">
          {error}
        </div>
      ) : null}
      {preferences ? <NotificationPreferenceSummary locale={locale} type={type} preferences={preferences} /> : null}
      {!completed ? (
        <Button
          tag={Link}
          color="secondary"
          outline
          size="sm"
          to={`/budgets/${budgetId}/edit#notifications`}
          className="mt-3"
          data-cy="configureBudgetNotifications"
        >
          {copy(locale, 'Configurar notificaciones', 'Configure notifications')}
        </Button>
      ) : null}
    </ProductSection>
  );
};

/** The only editable preference section; Budget Edit composes it with the independent domain commands. */
export const BudgetNotificationPreferencesSection = ({
  budgetId,
  type,
  completed,
  locale,
  onDirtyChange,
}: {
  budgetId: number;
  type: BudgetType;
  completed: boolean;
  locale: string;
  onDirtyChange?: (dirty: boolean) => void;
}) => {
  const { preferences, setPreferences, loading, error, setError } = useNotificationPreferences(budgetId, locale, 0);
  const [draft, setDraft] = useState<BudgetNotificationPreferencesDraft>(emptyNotificationPreferences);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const initializedBudgetId = useRef<number | null>(null);

  const baseline = useMemo(() => (preferences ? asDraft(preferences) : emptyNotificationPreferences()), [preferences]);
  const dirty = preferences ? !preferencesEqual(draft, baseline) : false;

  useEffect(() => {
    if (!preferences || initializedBudgetId.current === budgetId) return;
    setDraft(asDraft(preferences));
    initializedBudgetId.current = budgetId;
  }, [budgetId, preferences]);

  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);

  const save = async () => {
    const invalid = validateNotificationPreferences(draft, locale);
    if (invalid) {
      setError(invalid);
      return;
    }
    if (!preferences || completed || saving) return;
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      const saved = await updateBudgetNotificationPreferences(budgetId, { ...draft, expectedVersion: preferences.version });
      setPreferences(saved);
      setNotice(copy(locale, 'Preferencias de notificación guardadas.', 'Notification preferences saved.'));
    } catch (problem: any) {
      if (problem?.response?.status === 409) {
        setError(
          copy(
            locale,
            'Las preferencias cambiaron en otra sesión. Se cargó la versión actual; revisa tu borrador y guarda de nuevo.',
            'Notification settings changed in another session. The current version was loaded; review your draft and save again.',
          ),
        );
        // Keep the local draft while refreshing only the independent preference version.
        void getBudgetNotificationPreferences(budgetId)
          .then(setPreferences)
          .catch(() => undefined);
      } else {
        setError(
          userFacingError(problem, copy(locale, 'No se pudieron guardar las notificaciones.', 'Notification settings could not be saved.')),
        );
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <div id="notifications" data-cy="budgetEditNotifications">
      <ProductSection title={copy(locale, 'Notificaciones', 'Notifications')}>
        <p className="small text-muted">
          {copy(
            locale,
            'Estas opciones se guardan por separado; no crean una revisión financiera ni cambian el período actual.',
            'These options save separately; they do not create a financial revision or change the current period.',
          )}
        </p>
        {loading ? <div className="small text-muted">{copy(locale, 'Cargando preferencias…', 'Loading preferences…')}</div> : null}
        {error ? (
          <div className="alert alert-danger py-2" role="alert" data-cy="budgetEditNotificationError">
            {error}
          </div>
        ) : null}
        {notice ? (
          <div className="alert alert-success py-2" role="status" data-cy="budgetEditNotificationNotice">
            {notice}
          </div>
        ) : null}
        {preferences && completed ? (
          <>
            <p className="small border-start border-3 border-secondary ps-2" data-cy="budgetEditNotificationReadOnly">
              {copy(
                locale,
                'Este presupuesto terminó. Sus preferencias permanecen disponibles sólo para consulta.',
                'This Budget has finished. Its preferences remain available for review only.',
              )}
            </p>
            <NotificationPreferenceSummary locale={locale} type={type} preferences={preferences} />
          </>
        ) : null}
        {preferences && !completed ? (
          <>
            <BudgetNotificationPreferencesEditor
              locale={locale}
              type={type}
              value={draft}
              onChange={setDraft}
              disabled={saving}
              idPrefix="budgetEditNotification"
            />
            {dirty ? (
              <p className="small text-warning-emphasis mt-3 mb-0" role="status" data-cy="budgetEditNotificationDirty">
                {copy(locale, 'Cambios sin guardar en Notificaciones.', 'Unsaved changes in Notifications.')}
              </p>
            ) : null}
            <div className="d-flex flex-wrap gap-2 mt-4">
              <Button color="primary" onClick={() => void save()} disabled={saving || !dirty} data-cy="saveBudgetNotificationPreferences">
                {saving ? copy(locale, 'Guardando…', 'Saving…') : copy(locale, 'Guardar notificaciones', 'Save notifications')}
              </Button>
              <Button
                color="secondary"
                outline
                onClick={() => {
                  setDraft(baseline);
                  setError(null);
                  setNotice(null);
                }}
                disabled={saving || !dirty}
                data-cy="cancelBudgetNotificationPreferences"
              >
                {copy(locale, 'Cancelar cambios', 'Cancel changes')}
              </Button>
            </div>
          </>
        ) : null}
      </ProductSection>
    </div>
  );
};
