import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Input, Label } from 'reactstrap';

import { BudgetProfileTimeZone, getBudgetProfileTimeZone, updateBudgetProfileTimeZone } from './budget-time-zone-api';

const copy = (locale: string, spanish: string, english: string) => (locale.startsWith('es') ? spanish : english);

export const detectedBrowserTimeZone = (): string | null => {
  try {
    const value = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (!value) return null;
    Intl.DateTimeFormat('en', { timeZone: value }).format(0);
    return value;
  } catch {
    return null;
  }
};

export const supportedTimeZones = (suggestion: string | null, persisted: string | null): string[] => {
  const values = new Set<string>(['UTC']);
  try {
    const intlWithSupportedValues = Intl as typeof Intl & { supportedValuesOf?: (key: string) => string[] };
    intlWithSupportedValues.supportedValuesOf?.('timeZone').forEach(value => values.add(value));
  } catch {
    // Older browsers still allow a manually entered, backend-validated IANA identifier.
  }
  if (suggestion) values.add(suggestion);
  if (persisted) values.add(persisted);
  return [...values].sort((left, right) => left.localeCompare(right));
};

const labelForZone = (zone: string, locale: string) => {
  try {
    const sample = new Intl.DateTimeFormat(locale.startsWith('es') ? 'es-MX' : 'en-US', {
      timeZone: zone,
      timeZoneName: 'long',
    }).format(new Date('2026-06-15T12:00:00Z'));
    return `${zone} — ${sample}`;
  } catch {
    return zone;
  }
};

const problemMessage = (problem: unknown, fallback: string) => {
  const response = problem as { response?: { data?: { detail?: string; message?: string } } };
  return response.response?.data?.detail || response.response?.data?.message || fallback;
};

type Props = { locale: string };

/** Explicitly confirms a profile zone; browser detection only supplies a draft suggestion. */
export const BudgetTimeZoneSettings = ({ locale }: Props) => {
  const suggestion = useMemo(detectedBrowserTimeZone, []);
  const [profile, setProfile] = useState<BudgetProfileTimeZone>();
  const [selected, setSelected] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const options = useMemo(() => supportedTimeZones(suggestion, profile?.timeZone ?? null), [profile?.timeZone, suggestion]);
  const initialValue = profile?.timeZone ?? suggestion ?? '';

  useEffect(() => {
    let active = true;
    void getBudgetProfileTimeZone()
      .then(result => {
        if (!active) return;
        setProfile(result);
        setSelected(result.timeZone ?? suggestion ?? '');
      })
      .catch(problem => {
        if (active)
          setError(problemMessage(problem, copy(locale, 'No se pudo cargar tu zona horaria.', 'Your time zone could not be loaded.')));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [locale, suggestion]);

  const save = async () => {
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const result = await updateBudgetProfileTimeZone(selected.trim());
      setProfile(result);
      setSelected(result.timeZone ?? '');
      const dates = result.effectiveChanges.map(change => `${change.budgetName}: ${change.effectivePeriodStart}`).join(', ');
      setSuccess(
        dates
          ? copy(
              locale,
              `Zona horaria guardada. Los períodos actuales no cambian; se aplicará en los próximos períodos: ${dates}.`,
              `Time zone saved. Current periods do not change; it will apply in the next periods: ${dates}.`,
            )
          : copy(
              locale,
              'Zona horaria guardada. Los períodos actuales de presupuestos no cambian.',
              'Time zone saved. Current Budget periods do not change.',
            ),
      );
    } catch (problem) {
      setError(problemMessage(problem, copy(locale, 'No se pudo guardar la zona horaria.', 'Your time zone could not be saved.')));
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <p className="text-muted mt-4">{copy(locale, 'Cargando zona horaria…', 'Loading time zone…')}</p>;

  return (
    <section className="mt-4 pt-4 border-top" aria-labelledby="budget-time-zone-title" data-cy="budgetTimeZoneSettings">
      <h3 id="budget-time-zone-title" className="h5">
        {copy(locale, 'Zona horaria financiera', 'Financial time zone')}
      </h3>
      <p className="text-muted mb-3">
        {copy(
          locale,
          'Define cómo se interpretan las fechas y períodos de tus presupuestos. Un cambio conserva el período que ya está en curso y se aplica en el siguiente período correspondiente.',
          'This controls how Budget dates and periods are interpreted. A change preserves the period already in progress and applies at the next applicable period.',
        )}
      </p>
      {profile?.configurationRequired ? (
        <Alert color="info" fade={false} data-cy="budgetTimeZoneSuggestion">
          {suggestion
            ? copy(
                locale,
                `Sugerimos la zona horaria de este navegador: ${suggestion}. Confírmala o selecciona otra para guardarla.`,
                `This browser suggests ${suggestion}. Confirm it or choose another zone to save it.`,
              )
            : copy(
                locale,
                'Selecciona una zona horaria para configurarla explícitamente.',
                'Select a time zone to configure it explicitly.',
              )}
        </Alert>
      ) : null}
      {error ? (
        <Alert color="danger" fade={false}>
          {error}
        </Alert>
      ) : null}
      {success ? (
        <Alert color="success" fade={false} role="status">
          {success}
        </Alert>
      ) : null}
      <Label for="budget-profile-time-zone">{copy(locale, 'Zona horaria IANA', 'IANA time zone')}</Label>
      <Input
        id="budget-profile-time-zone"
        type="text"
        list="budget-profile-time-zone-options"
        value={selected}
        onChange={event => setSelected(event.target.value)}
        placeholder="America/Mexico_City"
        autoComplete="off"
        data-cy="budgetTimeZoneInput"
      />
      <datalist id="budget-profile-time-zone-options">
        {options.map(zone => (
          <option key={zone} value={zone} label={labelForZone(zone, locale)} />
        ))}
      </datalist>
      {selected ? <p className="small text-muted mt-2 mb-3">{labelForZone(selected, locale)}</p> : null}
      <div className="d-flex flex-wrap gap-2">
        <Button color="primary" type="button" onClick={save} disabled={saving || !selected.trim()} data-cy="saveBudgetTimeZone">
          {saving ? copy(locale, 'Guardando…', 'Saving…') : copy(locale, 'Confirmar zona horaria', 'Confirm time zone')}
        </Button>
        <Button
          color="secondary"
          outline
          type="button"
          onClick={() => {
            setSelected(initialValue);
            setError(null);
            setSuccess(null);
          }}
          disabled={saving}
          data-cy="cancelBudgetTimeZone"
        >
          {copy(locale, 'Cancelar', 'Cancel')}
        </Button>
      </div>
    </section>
  );
};

export default BudgetTimeZoneSettings;
