import React, { useEffect, useState } from 'react';
import { Button } from 'reactstrap';

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
import { copy, userFacingError } from './budget-utils';
import { ProductSection } from 'app/shared/ui/product-page';

const asDraft = (value: BudgetNotificationPreferences): BudgetNotificationPreferencesDraft => ({
  percentageAlertsEnabled: value.percentageAlertsEnabled,
  thresholdPercentages: value.thresholdPercentages,
  periodCloseEnabled: value.periodCloseEnabled,
});

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
  const [preferences, setPreferences] = useState<BudgetNotificationPreferences | null>(null);
  const [draft, setDraft] = useState<BudgetNotificationPreferencesDraft>(emptyNotificationPreferences);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const applyPreferences = (next: BudgetNotificationPreferences) => {
    setPreferences(next);
    setDraft(asDraft(next));
  };

  useEffect(() => {
    if (!Number.isSafeInteger(budgetId) || budgetId <= 0) return;
    let active = true;
    setLoading(true);
    setError(null);
    void getBudgetNotificationPreferences(budgetId)
      .then(next => {
        if (active) applyPreferences(next);
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
      applyPreferences(saved);
      setNotice(copy(locale, 'Preferencias de notificación guardadas.', 'Notification preferences saved.'));
    } catch (problem: any) {
      if (problem?.response?.status === 409) {
        setError(
          copy(
            locale,
            'Las preferencias cambiaron en otra sesión. Recargamos la versión actual; revisa tu borrador y guarda de nuevo.',
            'Notification settings changed in another session. The current version was reloaded; review your draft and save again.',
          ),
        );
        // Keep the user's draft; only refresh the authority/version token for an explicit retry.
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
    <ProductSection title={copy(locale, 'Notificaciones', 'Notifications')} className="mb-4" dataCy="budgetNotificationPreferencesSection">
      {loading ? <div className="small text-muted">{copy(locale, 'Cargando preferencias…', 'Loading preferences…')}</div> : null}
      {error ? (
        <div className="alert alert-danger py-2" role="alert" data-cy="budgetNotificationError">
          {error}
        </div>
      ) : null}
      {notice ? (
        <div className="alert alert-success py-2" role="status" data-cy="budgetNotificationNotice">
          {notice}
        </div>
      ) : null}
      {preferences ? (
        completed ? (
          <>
            <p className="small border-start border-3 border-secondary ps-2">
              {copy(
                locale,
                'Este presupuesto terminó. Puedes consultar sus preferencias, pero ya no cambiarlas.',
                'This Budget has finished. You can review its preferences, but cannot change them.',
              )}
            </p>
            <BudgetNotificationPreferencesEditor
              locale={locale}
              type={type}
              value={draft}
              onChange={() => undefined}
              readOnly
              idPrefix="budgetDetailNotification"
            />
          </>
        ) : (
          <>
            <BudgetNotificationPreferencesEditor
              locale={locale}
              type={type}
              value={draft}
              onChange={setDraft}
              disabled={saving}
              idPrefix="budgetDetailNotification"
            />
            <div className="d-flex flex-wrap gap-2 mt-4">
              <Button color="primary" onClick={() => void save()} disabled={saving} data-cy="saveBudgetNotificationPreferences">
                {saving ? copy(locale, 'Guardando…', 'Saving…') : copy(locale, 'Guardar notificaciones', 'Save notifications')}
              </Button>
              <Button
                color="secondary"
                outline
                onClick={() => applyPreferences(preferences)}
                disabled={saving}
                data-cy="cancelBudgetNotificationPreferences"
              >
                {copy(locale, 'Cancelar cambios', 'Cancel changes')}
              </Button>
            </div>
          </>
        )
      ) : null}
    </ProductSection>
  );
};
